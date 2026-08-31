package net.jojoaddison.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns {@code booking.completed} into a ledger row, and {@code booking.cancelled} into a late fee.
 *
 * <h2>Why this is in {@code service} and not {@code broker}</h2>
 *
 * <p>{@code TechnicalStructureTest} enforces a layered architecture in which {@code domain} may only
 * be reached from {@code repository}, {@code service}, {@code security}, {@code web} or
 * {@code config}, and {@code service} may only be reached from {@code web} or {@code config}. A
 * listener in {@code broker} can therefore touch neither the entities nor a service that does — it
 * would have to be a transport shim that forwards to nothing, which is what the generated
 * {@code broker.KafkaConsumer} is.
 *
 * <p>Reacting to a domain event IS application logic, so it belongs in the service layer. Nothing
 * calls this class — Spring invokes it reflectively — so it introduces no inbound dependency of its
 * own. Putting it in {@code broker} cost 51 architecture violations and taught this the hard way.
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
        // Property-driven, not literal — decisions.md D29. The prefix is empty in production and
        // quality and `dev.` in the dev estate, which is what stops two stacks on the one shared
        // broker consuming each other's events. The switch below is untouched: it matches the
        // envelope's event TYPE, which is never prefixed, rather than the topic it arrived by.
        // Each carries its canonical name as an INLINE DEFAULT, and that is not belt-and-braces.
        // JHipster's src/test/resources/config/application.yml SHADOWS the main one, so the
        // composed properties do not exist under test and a bare placeholder fails the context with
        // "Could not resolve placeholder" — which reads as a typo rather than as a config file that
        // was never loaded. The default also keeps the real topic name visible at the listener, and
        // survives a regeneration of application.yml.
        topics = { "${healthconnect.topics.booking-completed:healthconnect.booking.completed}", "${healthconnect.topics.booking-cancelled:healthconnect.booking.cancelled}" },
        groupId = "${healthconnect.kafka.group-id:healthconnect-payout}",
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
                .currency(currencyOf(p, config))
                .deliveryMode(deliveryModeOf(p))
                .serviceRef(p.path("serviceRef").asText(null))
                .serviceName(p.path("serviceName").asText(null))
                .earnedOn(LocalDate.now())
        );
        LOG.info("ledger entry for {} — gross {} commission {}", bookingRef, gross, commission);
    }

    /**
     * The delivery mode of a ledger row, refused rather than guessed — {@code decisions.md} D22's
     * second recorded gap, closed by D29.
     *
     * <p>This replaced {@code DeliveryMode.valueOf(p.path("deliveryMode").asText("ONLINE"))}, the
     * same shape as the currency default beside it and with the same consequence: an event missing
     * the field minted an {@code ONLINE} row regardless, so the earnings-by-format breakdown on the
     * professional's Overview reported income under a mode the session was never delivered in. The
     * totals stay right, which is what makes it hard to see — only the split moves, and only
     * against a figure nobody has an independent copy of.
     *
     * <p>{@code deliveryMode} is {@code required} on {@code Booking}, so an event without one is
     * malformed rather than merely sparse. Throwing means the caller rethrows, the container
     * retries and the event is never marked processed — the same trade the currency check makes,
     * and the same warning applies: with no dead-letter topic a genuinely unfixable event will
     * retry indefinitely. Better a stalled partition somebody notices than a silent mislabel
     * nobody does.
     */
    private static DeliveryMode deliveryModeOf(JsonNode p) {
        String mode = p.path("deliveryMode").asText(null);
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException(
                "booking event for %s carries no deliveryMode; refusing to guess one".formatted(p.path("bookingRef").asText())
            );
        }
        try {
            return DeliveryMode.valueOf(mode);
        } catch (IllegalArgumentException unknown) {
            // Named explicitly. valueOf's own message is "No enum constant ...DeliveryMode.HYBRID",
            // which reads as a code fault rather than as an event this service is too old to
            // understand — which is what it usually is.
            throw new IllegalArgumentException(
                "booking event for %s carries an unknown deliveryMode '%s'".formatted(p.path("bookingRef").asText(), mode)
            );
        }
    }

    /**
     * The currency of a ledger row, which must be the booking's and must be one the brokerage
     * config actually prices (decisions.md D22).
     *
     * <p>This replaced {@code p.path("currency").asText("GHS")}, which had two failure modes and
     * neither said anything at the time. An event carrying no currency minted a GHS row regardless
     * of what the booking was denominated in; and nothing checked the row against the
     * {@link BrokerageConfig} whose {@code commissionRate} had just been applied to it, so a
     * booking in another currency would have been charged a commission computed from a rate that
     * was never set for it. Both produce a ledger that looks entirely normal.
     *
     * <p>Throws rather than defaulting. The caller rethrows, so the container retries and the event
     * is never marked processed — a ledger row that is late and correct beats one that is prompt
     * and wrong, and for money that trade is not close.
     */
    private String currencyOf(JsonNode p, BrokerageConfig config) {
        String currency = p.path("currency").asText(null);
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                "booking event for %s carries no currency; refusing to guess one".formatted(p.path("bookingRef").asText())
            );
        }
        if (!currency.equals(config.getCurrency())) {
            throw new IllegalArgumentException(
                "booking %s is in %s but the brokerage config in force prices %s — its commission rate does not apply".formatted(
                        p.path("bookingRef").asText(),
                        currency,
                        config.getCurrency()
                    )
            );
        }
        return currency;
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
                .currency(currencyOf(p, config))
                .deliveryMode(deliveryModeOf(p))
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
