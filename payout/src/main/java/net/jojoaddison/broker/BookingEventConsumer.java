package net.jojoaddison.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.ProcessedEvent;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
import net.jojoaddison.service.Commission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns {@code booking.completed} into a ledger row, and {@code booking.cancelled} into a late fee.
 *
 * <h2>Idempotency</h2>
 *
 * <p>Outbox delivery is at-least-once, so this listener must be able to see the same event twice.
 * Two independent guards make that safe: {@link ProcessedEvent} records the {@code eventId}, and
 * {@code Ledger.bookingReference} is unique. Either alone would do for this event; both together
 * mean a duplicate cannot credit a professional twice even if one guard is later loosened.
 *
 * <h2>Why the ledger is written here and not by booking</h2>
 *
 * <p>Because the commission rate belongs to the payout service's {@code BrokerageConfig}, and it is
 * versioned by {@code effectiveFrom} so that a booking prices against the rate in force when it
 * completed. Booking has no business knowing the rate, and a booking service that computed
 * commission would have to be redeployed every time the brokerage changed its terms.
 */
@Component
public class BookingEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final LedgerRepository ledger;
    private final EarningsRepository ledgerQueries;
    private final BrokerageConfigRepository brokerage;
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;

    public BookingEventConsumer(
        LedgerRepository ledger,
        EarningsRepository ledgerQueries,
        BrokerageConfigRepository brokerage,
        ProcessedEventRepository processed,
        ObjectMapper mapper
    ) {
        this.ledger = ledger;
        this.ledgerQueries = ledgerQueries;
        this.brokerage = brokerage;
        this.processed = processed;
        this.mapper = mapper;
    }

    @KafkaListener(
        topics = { "healthconnect.booking.completed", "healthconnect.booking.cancelled" },
        groupId = "healthconnect-payout",
        autoStartup = "${healthconnect.kafka.consumer-enabled:true}"
    )
    @Transactional
    public void onBookingEvent(String message) {
        try {
            JsonNode envelope = mapper.readTree(message);
            String eventId = envelope.path("eventId").asText();
            String type = envelope.path("type").asText();
            JsonNode payload = envelope.path("payload");

            if (eventId.isBlank() || processed.existsById(eventId)) {
                LOG.debug("skipping already-processed event {}", eventId);
                return;
            }

            switch (type) {
                case "healthconnect.booking.completed" -> writeLedgerEntry(payload);
                case "healthconnect.booking.cancelled" -> writeLateFeeIfAny(payload);
                default -> LOG.debug("ignoring {}", type);
            }

            processed.save(new ProcessedEvent(eventId, type, Instant.now()));
        } catch (Exception e) {
            // Rethrown so the container's error handler retries rather than acknowledging a message
            // that was never handled. Swallowing here would turn a transient database blip into a
            // permanently missing ledger row, with nothing recording that it was owed.
            throw new IllegalStateException("could not handle booking event: " + e.getMessage(), e);
        }
    }

    private void writeLedgerEntry(JsonNode p) {
        String bookingRef = p.path("bookingRef").asText();
        if (ledgerQueries.existsByBookingReference(bookingRef)) {
            LOG.debug("ledger already has an entry for {}", bookingRef);
            return;
        }
        long gross = p.path("priceMinor").asLong();
        BrokerageConfig config = configInForce();
        long commission = Commission.on(gross, config.getCommissionRate());

        ledger.save(
            new Ledger()
                .bookingReference(bookingRef)
                .professionalRef(p.path("professionalRef").asText())
                .professionalLogin(p.path("professionalLogin").asText())
                .grossMinor(gross)
                .commissionMinor(commission)
                .netMinor(gross - commission)
                .currency(p.path("currency").asText("GHS"))
                .deliveryMode(DeliveryMode.valueOf(p.path("deliveryMode").asText("ONLINE")))
                .serviceRef(p.path("serviceRef").asText(null))
                .serviceName(p.path("serviceName").asText(null))
                .earnedOn(LocalDate.now())
        );
        LOG.info("ledger entry for {} — gross {} commission {}", bookingRef, gross, commission);
    }

    /**
     * A late cancellation earns the professional a fee. Spec §5.2: "Cancellation inside 24 hours
     * sets {@code lateCancellation = true}, which the payout service reads to raise a 50% fee to
     * the professional. Everything else is free."
     */
    private void writeLateFeeIfAny(JsonNode p) {
        if (!p.path("lateCancellation").asBoolean(false)) {
            return;
        }
        String bookingRef = p.path("bookingRef").asText();
        BrokerageConfig config = configInForce();
        long full = p.path("priceMinor").asLong();
        long fee = Commission.lateCancellationFee(full, config.getLateCancellationPct());
        long commission = Commission.on(fee, config.getCommissionRate());

        ledger.save(
            new Ledger()
                .bookingReference(bookingRef)
                .professionalRef(p.path("professionalRef").asText())
                .professionalLogin(p.path("professionalLogin").asText())
                .grossMinor(fee)
                .commissionMinor(commission)
                .netMinor(fee - commission)
                .currency(p.path("currency").asText("GHS"))
                .deliveryMode(DeliveryMode.valueOf(p.path("deliveryMode").asText("ONLINE")))
                .serviceRef(p.path("serviceRef").asText(null))
                .serviceName("Late cancellation fee")
                .earnedOn(LocalDate.now())
        );
        LOG.info("late cancellation fee for {} — {} of {}", bookingRef, fee, full);
    }

    /**
     * The config in force. Versioned by {@code effectiveFrom}, so this takes the latest one that has
     * already taken effect rather than simply the newest row — a rate scheduled for next month must
     * not price today's bookings.
     */
    private BrokerageConfig configInForce() {
        Instant now = Instant.now();
        List<BrokerageConfig> all = brokerage.findAll();
        return all
            .stream()
            .filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(now))
            .max(Comparator.comparing(BrokerageConfig::getEffectiveFrom))
            .orElseThrow(() -> new IllegalStateException("no BrokerageConfig in force — cannot price a booking"));
    }
}
