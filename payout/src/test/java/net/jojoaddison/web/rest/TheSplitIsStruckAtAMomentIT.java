package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The receipt's split is struck at the <em>moment</em> the booking completed, not at the start of
 * the day it completed on — {@code decisions.md} D56, backlog NEW-16.
 *
 * <h2>What this is standing on</h2>
 *
 * <p>D53 moved the ledger onto the completion instant and left the receipt on a
 * {@code LocalDate}: booking sent {@code on=<the day>} and payout read it back as
 * {@code atStartOfDay(MARKET_ZONE)}. So a rate taking effect at <strong>noon</strong> on the day a
 * booking completed at 14:00 priced the ledger under the new terms and the receipt under the old —
 * the same two-numbers-that-disagree shape as NEW-13, one granularity down. It bites only when
 * {@code effectiveFrom} is not midnight in Accra, which the one row in either estate is.
 *
 * <h2>Why an integration test rather than a call into the resource</h2>
 *
 * <p>Half of what this package changes is a <strong>parameter binding</strong>: whether an
 * {@code Instant} {@code @RequestParam} binds at all, and in what format. Constructing
 * {@code BrokerageResource} directly and calling {@code split(28000, someInstant, someDate)} would
 * assert the selection rule and prove nothing about the thing most likely to break — the wire.
 * Every request below is written as a real query string so MockMvc parses it the way a servlet
 * container would, and the conversion that turns {@code 2020-06-01T14:00:00Z} into an
 * {@code Instant} is the application's own.
 *
 * <h2>Every instant here is in 2019–2022</h2>
 *
 * <p>Borrowed from {@code TheRateIsStruckWhenTheBookingHappenedTest} and not decoration. A test
 * whose asserted moment could equal a real clock's is a test that passes against the defect on some
 * days — the trap D51 recorded. The {@code LATEST} config below exists for exactly that reason: it
 * is the row {@code Instant.now()} selects, and it is never the right answer to any question asked
 * here, so a resource that falls back to a clock cannot accidentally agree with one of these
 * assertions.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.customer", authorities = { AuthoritiesConstants.USER })
class TheSplitIsStruckAtAMomentIT {

    private static final String SPLIT = "/api/internal/brokerage/split";

    /** ₵280.00. The seeded price of a nutrition assessment, so the arithmetic below is familiar. */
    private static final long AMOUNT = 28_000L;

    /** The terms a 2020 booking was sold under. */
    private static final String OLD_RATE = "0.12";

    /** The terms that take effect at NOON on the day the booking completes. */
    private static final String NOON_RATE = "0.30";

    /** Later than anything asked about here, and earlier than any real clock. */
    private static final String LATEST_RATE = "0.44";

    /**
     * Noon <strong>in the marketplace's calendar</strong>, written through {@code MARKET_ZONE}
     * rather than as {@code 12:00:00Z}. Ghana is UTC+0 all year so the two are the same instant, and
     * spelling it this way is what stops the test asserting a coincidence: "the rate changed at noon
     * on the day of the booking" is a statement about Accra's clock.
     */
    private static final Instant NOON = LocalDateTime.of(2020, 6, 1, 12, 0).atZone(MarketCalendar.MARKET_ZONE).toInstant();

    /** The session completed two hours AFTER the new terms took effect. This is the defect's case. */
    private static final Instant COMPLETED_AT_1400 = LocalDateTime.of(2020, 6, 1, 14, 0)
        .atZone(MarketCalendar.MARKET_ZONE)
        .toInstant();

    /** The same day, two hours BEFORE. The control: the old rate is right here, day or moment. */
    private static final Instant COMPLETED_AT_1000 = LocalDateTime.of(2020, 6, 1, 10, 0)
        .atZone(MarketCalendar.MARKET_ZONE)
        .toInstant();

    /** The day both of the above fall on, which is what the caller used to send and nothing else. */
    private static final String THE_DAY = "2020-06-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void threeVersionsOfTheBrokeragesTerms() {
        // Whatever else the schema holds, these three are the whole history as far as this test is
        // concerned. Inside the test transaction, so it rolls back.
        em.createQuery("delete from BrokerageConfig").executeUpdate();
        em.persist(config(OLD_RATE, Instant.parse("2019-01-01T00:00:00Z")));
        em.persist(config(NOON_RATE, NOON));
        em.persist(config(LATEST_RATE, Instant.parse("2022-01-01T00:00:00Z")));
        em.flush();
    }

    private static BrokerageConfig config(String rate, Instant effectiveFrom) {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal(rate))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(effectiveFrom);
    }

    @Test
    @Transactional
    @DisplayName("a booking completed at 14:00 is priced under terms that took effect at noon")
    void theMomentDecidesAndNotTheDay() throws Exception {
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=" + COMPLETED_AT_1400 + "&on=" + THE_DAY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value(NOON_RATE))
            // 30% of ₵280.00. Asserted beside the rate because a receipt is read as money, not as a
            // percentage, and the two disagreeing is the whole failure this endpoint can produce.
            .andExpect(jsonPath("$.commissionMinor").value(8400))
            .andExpect(jsonPath("$.netMinor").value(19600));
    }

    @Test
    @Transactional
    @DisplayName("…and one completed at 10:00 the same day is still priced under the old terms")
    void theMomentDecidesTheOtherWayToo() throws Exception {
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=" + COMPLETED_AT_1000 + "&on=" + THE_DAY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value(OLD_RATE))
            .andExpect(jsonPath("$.commissionMinor").value(3360));
    }

    @Test
    @Transactional
    @DisplayName("both parameters present and disagreeing: the instant wins and the day is ignored")
    void theInstantIsPreferred() throws Exception {
        // A caller with two different ideas of when. Only a bug produces this pair, and the answer
        // is the instant either way — refusing it would cost a receipt and buy nothing.
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=2021-03-01T09:00:00Z&on=2019-06-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value(NOON_RATE));
    }

    @Test
    @Transactional
    @DisplayName("the instant alone is enough — no day, and no fallback to a clock")
    void theInstantAloneIsEnough() throws Exception {
        // LATEST_RATE is what Instant.now() selects. Asserting NOON_RATE here is therefore also an
        // assertion that nothing on this path read a clock.
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=" + COMPLETED_AT_1400))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value(NOON_RATE));
    }

    @Test
    @Transactional
    @DisplayName("the day alone still answers, at the start of the marketplace's day")
    void theDayAloneIsTheCompatibilityAnswer() throws Exception {
        // The old booking service sends only this, and so does a new one asked for the receipt of a
        // booking that has not completed: there is no instant, only a day. Unchanged behaviour, and
        // the reason `on` is still sent rather than dropped.
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&on=" + THE_DAY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commissionRate").value(OLD_RATE));
    }

    @Test
    @Transactional
    @DisplayName("neither parameter is refused, never priced at now")
    void aRequestThatSaysNothingAboutWhenIsRefused() throws Exception {
        // The shape D53 removed from the consumer, in the other service. A request that failed to
        // say when is not a request about now: answering it would price a receipt at today's terms
        // and look exactly like a correct one.
        mockMvc.perform(get(SPLIT + "?amountMinor=" + AMOUNT)).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("`at` is really parsed as an instant — a malformed one is refused, not ignored")
    void theInstantIsBoundAndNotMerelyAccepted() throws Exception {
        // Half of this package is whether an Instant @RequestParam binds. If `at` bound as a String
        // and were quietly dropped, every assertion above would still describe the endpoint and this
        // one would return 200 on the day parameter beside it.
        mockMvc.perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=yesterday&on=" + THE_DAY)).andExpect(status().isBadRequest());
    }

    /**
     * <strong>This one is not the tie-break's detector, and it was measured not to be.</strong>
     *
     * <p>Removing the tie-break entirely leaves it GREEN: PostgreSQL returned the two rows in an
     * order where {@code Stream.max}, which keeps the first maximal element it sees, happened to land
     * on the same row the rule names. That is the defect's own signature — an answer that depends on
     * the order a table came back in cannot be pinned by a test that reads the table once — and it is
     * why {@code BrokerageTermsUnitTest.theTieBreakDoesNotDependOnRowOrder} controls the list and
     * asserts both orders. Kept anyway, because what it does establish is that the rule survives the
     * round trip through a real database and a real query plan.
     */
    @Test
    @Transactional
    @DisplayName("two configs sharing an effectiveFrom resolve to one of them, and always the same one")
    void aTieIsBrokenAndNotLeftToTheStream() throws Exception {
        // Nothing in the schema stops this pair existing. Both selectors used to take Stream.max
        // over an unordered findAll(), which returns an arbitrary element among equals — so a
        // receipt and a ledger row could disagree with no rate change between them.
        em.persist(config("0.55", NOON));
        em.flush();
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=" + COMPLETED_AT_1400))
            .andExpect(status().isOk())
            // The newest row wins — highest id, which in this schema is the most recently inserted.
            .andExpect(jsonPath("$.commissionRate").value("0.55"));
    }

    @Test
    @Transactional
    @DisplayName("the endpoint still answers the rest of the receipt, and still refuses a negative amount")
    void theRestOfTheContractIsUnchanged() throws Exception {
        mockMvc
            .perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=" + COMPLETED_AT_1400))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.grossMinor").value(AMOUNT))
            .andExpect(jsonPath("$.currency").value("GHS"))
            .andExpect(jsonPath("$.freeCancellationHours").value(24))
            .andExpect(jsonPath("$.lateCancellationPct").value("0.50"));

        mockMvc.perform(get(SPLIT + "?amountMinor=-1&at=" + COMPLETED_AT_1400)).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("with no terms in force at that moment the receipt is refused rather than guessed")
    void noConfigInForceIsRefused() throws Exception {
        // Before the brokerage had any terms at all. A guessed 12% here would be a financial
        // statement this platform never issued.
        int status = mockMvc.perform(get(SPLIT + "?amountMinor=" + AMOUNT + "&at=2018-01-01T00:00:00Z")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(503);
    }
}
