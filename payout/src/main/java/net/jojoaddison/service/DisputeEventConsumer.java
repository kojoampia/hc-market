package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.ProcessedEvent;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
import net.jojoaddison.repository.ReversalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reverses a professional's earning when the brokerage desk upholds a dispute — decisions.md D23.
 *
 * <h2>Compensating entries, never deletion</h2>
 *
 * <p>The original {@link Ledger} row is not touched. A reversal is a <em>new</em> row with negative
 * amounts, so the ledger stays append-only and every earnings figure remains a plain aggregate over
 * rows that are all still there. Deleting or editing the original would make the ledger disagree
 * with a receipt already shown to two people, and would need every derived total recomputed —
 * exactly the drift the "derived, never stored" rule exists to prevent. It is also the same
 * one-directional discipline that governs reviews: there is no endpoint to delete one, only to reply.
 *
 * <h2>Why the reversal cannot reuse the booking reference</h2>
 *
 * <p>{@code Ledger.bookingReference} is <strong>unique</strong>, and that uniqueness is the guard
 * against a replayed {@code booking.completed} double-crediting a professional. A second row for the
 * same booking would collide with it. So the compensating entry carries the <em>dispute</em>
 * reference there — itself unique, so a replayed {@code dispute.resolved} cannot double-reverse
 * either — and names the booking it reverses in {@code reversalOf}.
 *
 * <h2>Where this lives</h2>
 *
 * <p>In {@code service}, not {@code broker}. {@code TechnicalStructureTest} lets only
 * {@code repository}, {@code service}, {@code security}, {@code web} and {@code config} reach
 * {@code domain}, so a {@code @KafkaListener} in {@code broker} could touch neither the entities nor
 * a service that does. Reacting to a domain event <em>is</em> application logic. Putting one in
 * {@code broker} previously cost 51 violations.
 */
@Service
public class DisputeEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeEventConsumer.class);

    private final LedgerRepository ledger;
    private final ReversalRepository ledgerQueries;
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;

    public DisputeEventConsumer(
        LedgerRepository ledger,
        ReversalRepository ledgerQueries,
        ProcessedEventRepository processed,
        ObjectMapper mapper
    ) {
        this.ledger = ledger;
        this.ledgerQueries = ledgerQueries;
        this.processed = processed;
        this.mapper = mapper;
    }

    @KafkaListener(
        // Each carries its canonical name as an INLINE DEFAULT, and that is not belt-and-braces.
        // JHipster's src/test/resources/config/application.yml SHADOWS the main one, so the
        // composed properties do not exist under test and a bare placeholder fails the context with
        // "Could not resolve placeholder" — which reads as a typo rather than as a config file that
        // was never loaded. The default also keeps the real topic name visible at the listener, and
        // survives a regeneration of application.yml.
        topics = { "${healthconnect.topics.dispute-resolved:healthconnect.dispute.resolved}" },
        groupId = "${healthconnect.kafka.group-id:healthconnect-payout}",
        autoStartup = "${healthconnect.kafka.consumer-enabled:true}"
    )
    @Transactional
    public void onDisputeResolved(String message) {
        try {
            JsonNode envelope = mapper.readTree(message);
            String eventId = envelope.path("eventId").asText();
            if (eventId.isBlank() || processed.existsById(eventId)) {
                LOG.debug("skipping already-processed event {}", eventId);
                return;
            }
            reverse(envelope.path("payload"));
            processed.save(new ProcessedEvent(eventId, envelope.path("type").asText(), Instant.now()));
        } catch (Exception e) {
            // Rethrown, matching BookingEventConsumer: the container retries rather than
            // acknowledging an event that was never handled. A reversal that is late is recoverable;
            // one that was silently dropped leaves a professional credited for a session the desk
            // decided they should not be paid for, and nothing records that it was owed.
            throw new IllegalStateException("could not handle dispute event: " + e.getMessage(), e);
        }
    }

    private void reverse(JsonNode p) {
        String disputeRef = p.path("disputeRef").asText();
        String bookingRef = p.path("bookingRef").asText();
        if (disputeRef.isBlank() || bookingRef.isBlank()) {
            throw new IllegalArgumentException("a dispute event needs both disputeRef and bookingRef");
        }

        // Unique on bookingReference, so this is what stops a redelivered event reversing twice.
        if (ledgerQueries.findByBookingReference(disputeRef).isPresent()) {
            LOG.debug("dispute {} has already been reversed", disputeRef);
            return;
        }

        // A dispute can be upheld on a booking that never produced an earning — a no-show, for
        // instance. There is nothing to reverse and that is not an error, so this returns rather
        // than throwing: throwing would retry forever against a row that will never appear.
        Ledger original = ledgerQueries.findByBookingReference(bookingRef).orElse(null);
        if (original == null) {
            LOG.info("dispute {} upheld for {}, but there is no ledger entry to reverse", disputeRef, bookingRef);
            return;
        }

        // The reversal MIRRORS its original: same professional, currency, delivery mode and service.
        // Taking them from the row being reversed rather than from the event means the two cannot
        // disagree, and removes a second source for facts that already exist here.
        long grossToReverse = refundAmount(p, original);
        long commissionToReverse = proportionalCommission(original, grossToReverse);

        ledger.save(
            new Ledger()
                .bookingReference(disputeRef)
                .reversalOf(bookingRef)
                .professionalRef(original.getProfessionalRef())
                .professionalLogin(original.getProfessionalLogin())
                .grossMinor(-grossToReverse)
                .commissionMinor(-commissionToReverse)
                .netMinor(-(grossToReverse - commissionToReverse))
                .currency(original.getCurrency())
                .deliveryMode(original.getDeliveryMode())
                .serviceRef(original.getServiceRef())
                .serviceName("Dispute reversal — " + original.getServiceName())
                // Dated today, not backdated to the original. The reversal is a thing that happened
                // now; backdating it would silently rewrite a month that has already been reported.
                .earnedOn(LocalDate.now())
        );
        LOG.info("dispute {} reversed {} of booking {} (commission {})", disputeRef, grossToReverse, bookingRef, commissionToReverse);
    }

    /** A null or absent {@code refundMinor} means the whole earning; anything larger is capped at it. */
    private static long refundAmount(JsonNode p, Ledger original) {
        long full = original.getGrossMinor() == null ? 0L : original.getGrossMinor();
        JsonNode refund = p.path("refundMinor");
        if (refund.isMissingNode() || refund.isNull()) {
            return full;
        }
        long asked = refund.asLong();
        if (asked < 0) {
            throw new IllegalArgumentException("refundMinor is an amount, not a signed adjustment: " + asked);
        }
        // Capped rather than rejected: a partial refund larger than the earning is the desk being
        // generous with a number it half-remembers, and reversing more than was ever credited would
        // leave the professional owing money on a session they were paid for.
        return Math.min(asked, full);
    }

    /**
     * The brokerage's share of a partial refund, in the same proportion it took originally.
     *
     * <p>Recomputed from the ORIGINAL row's own rate, not from whatever
     * {@code BrokerageConfig} is in force today — the config is effective-dated precisely so that a
     * historical row keeps the terms it was written under, and pricing a reversal at today's rate
     * would refund a commission that was never charged.
     */
    private static long proportionalCommission(Ledger original, long grossToReverse) {
        long originalGross = original.getGrossMinor() == null ? 0L : original.getGrossMinor();
        long originalCommission = original.getCommissionMinor() == null ? 0L : original.getCommissionMinor();
        if (originalGross <= 0) {
            return 0L;
        }
        if (grossToReverse == originalGross) {
            return originalCommission; // full reversal: give back exactly what was taken
        }
        return Math.round((double) originalCommission * grossToReverse / originalGross);
    }
}
