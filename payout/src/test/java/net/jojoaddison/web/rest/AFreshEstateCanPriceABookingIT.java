package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.service.BookingEventConsumer;
import net.jojoaddison.service.BrokerageTermsHealthIndicator;
import net.jojoaddison.service.BrokerageTermsInfoContributor;
import net.jojoaddison.service.FoundingTerms;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * An estate that has never seeded can price a completed booking and serve a receipt — the two things
 * NEW-18 said it could not. {@code decisions.md} D57.
 *
 * <h2>Why an integration test, and why it plants nothing</h2>
 *
 * <p>Every other test in this package sets its own {@code BrokerageConfig} rows up before asserting
 * anything. This one deliberately does not, and that is the whole point: what is under test is that the
 * <em>application context</em> founded the estate on its way up, exactly as a production container does.
 * A fixture would prove the selector works, which D53 and D56 already prove, and nothing about the gap
 * this package closes.
 *
 * <p>So this is the closest thing in the repository to the production case. The remaining difference is
 * named in D57 and is real: production runs {@code prod}, this runs {@code test,testdev}, and neither
 * profile seeds — {@code SeedDataLoader} needs {@code test & dev}. What cannot be exercised anywhere is
 * an actual {@code prod} boot against an empty database, because nothing here has ever deployed one.
 *
 * <h2>1970, and not 2020</h2>
 *
 * <p>The moments below are the second day of the epoch. The usual reason applies — a test whose asserted
 * moment could equal a real clock's passes against the defect on some days (D51) — and there is a second
 * one specific to this file. It shares a database with every other integration test in the JVM, and one
 * of them commits {@code BrokerageConfig} rows of its own; asking about a moment before any of them can
 * exist means the only row that can answer is the founding one.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.customer", authorities = { AuthoritiesConstants.USER })
class AFreshEstateCanPriceABookingIT {

    /** ₵280.00 — the seeded price of a nutrition assessment, so the arithmetic reads familiarly. */
    private static final long AMOUNT = 28_000L;

    /** Later than the founding row and earlier than anything any other test here writes. */
    private static final Instant THE_DAY_AFTER_THE_BEGINNING = Instant.parse("1970-01-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrokerageConfigRepository configs;

    @Autowired
    private LedgerRepository ledger;

    @Autowired
    private BookingEventConsumer consumer;

    @Autowired
    private BrokerageTermsHealthIndicator brokerage;

    @Autowired
    private BrokerageTermsInfoContributor info;

    @Test
    @DisplayName("the context founded this estate's terms on its way up, with nobody asking it to")
    void theEstateIsFounded() {
        assertThat(configs.findAll())
            .as("BrokerageBootstrap must have written the founding row during the refresh")
            .anySatisfy(config -> {
                assertThat(config.getEffectiveFrom()).isEqualTo(FoundingTerms.EFFECTIVE_FROM);
                assertThat(config.getCommissionRate()).isEqualByComparingTo("0.12");
                assertThat(config.getCurrency()).isEqualTo("GHS");
            });
    }

    @Test
    @DisplayName("the receipt is a 200 and not a 503")
    void theReceiptIsServed() throws Exception {
        // BrokerageResource.inForce threw SERVICE_UNAVAILABLE on an empty table, so before D57 every
        // receipt on a production estate was a 503 — confirmed live on the quality box against a
        // deliberately emptied table.
        mockMvc
            .perform(get("/api/internal/brokerage/split").param("amountMinor", String.valueOf(AMOUNT)).param("at", THE_DAY_AFTER_THE_BEGINNING.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value("0.12"))
            .andExpect(jsonPath("$.commissionMinor").value(3360))
            .andExpect(jsonPath("$.netMinor").value(24640))
            .andExpect(jsonPath("$.currency").value("GHS"));
    }

    @Test
    @Transactional
    @DisplayName("a completed booking writes its ledger row instead of retrying for ever")
    void theLedgerRowIsWritten() {
        // configInForce threw IllegalStateException on an empty table and onBookingEvent rethrows, so
        // the container retried the same event indefinitely: no ledger row, /api/pro/earnings stuck at
        // zero, and booking perfectly happy because the failure was entirely inside payout.
        String reference = "d57-" + System.nanoTime();
        consumer.onBookingEvent(completed(reference));

        assertThat(ledger.findAll())
            .filteredOn(row -> reference.equals(row.getBookingReference()))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.getGrossMinor()).isEqualTo(AMOUNT);
                assertThat(row.getCommissionMinor()).isEqualTo(3_360L);
                assertThat(row.getNetMinor()).isEqualTo(24_640L);
            });
    }

    @Test
    @DisplayName("and payout tells a deploy it can price something, which is what smoke_test reads")
    void theServiceSaysItCanPrice() {
        // The property that matters is that neither of these can report a problem on a correctly
        // bootstrapped estate. A smoke test that fails on a healthy stack rolls it back (D49), which is
        // a worse outcome than the check not existing at all. The founding row is dated to the epoch,
        // so it is in force at every moment a clock can produce.
        //
        // Both surfaces, because they are read by different things and only one of them is the deploy's:
        // the INFO contributor is what smoke_test greps, and the health indicator is what a dashboard
        // and the compose healthcheck see. The deploy deliberately does NOT read the aggregate — a real
        // prod boot with no broker reported it DOWN with the founding row present and correct.
        // Through contribute() rather than the helper behind it, so what is asserted is what the
        // endpoint actually publishes — including the "brokerage" key the deploy's grep depends on.
        Info.Builder published = new Info.Builder();
        info.contribute(published);

        assertThat(published.build().getDetails())
            .extractingByKey("brokerage")
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("termsInForce", true)
            .containsEntry("commissionRate", "0.12");
        assertThat(brokerage.health().getStatus()).isEqualTo(Status.UP);
    }

    private static String completed(String reference) {
        return """
        {
          "eventId": "%s",
          "type": "healthconnect.booking.completed",
          "occurredAt": "1970-01-02T00:00:00.004Z",
          "payload": {
            "bookingRef": "%s",
            "bookingCompletedAt": "1970-01-02T00:00:00Z",
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            "currency": "GHS",
            "deliveryMode": "ONLINE",
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """.formatted(reference, reference);
    }
}
