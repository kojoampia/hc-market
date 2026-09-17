package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * The desk can settle a professional, and nobody else can — {@code decisions.md} D95, backlog NEW-51.
 *
 * <h2>What only a real database can establish</h2>
 *
 * <p>The double-payment guard is {@code payout is null}, which is a property of a query and of an
 * attachment: {@code PayoutRunTest} asserts that the run attaches every row it summed, and only a
 * real schema can say that the attached rows are then invisible to the next run. So the second batch
 * over the same period is asserted here, against Postgres, through the endpoint.
 *
 * <p>It also closes the loop NEW-51 is about. {@code GET /api/pro/payouts} read a table nothing wrote
 * and answered {@code []} on every estate; the last test here asks it, as the professional, and finds
 * the batch the desk just recorded.
 *
 * <h2>Its own brokerage config, and why</h2>
 *
 * <p>The payout lag bounds which earnings are payable, and it comes from the {@code BrokerageConfig}
 * in force. Integration tests here share one database and some of them <em>commit</em> config rows
 * (see {@code AFreshEstateCanPriceABookingIT}), so this class writes its own with
 * {@code effectiveFrom} at the start of today — the largest value the selector will still accept for
 * a run made today, and newest by id among any tie — which makes the lag this test's own rather than
 * whatever another class happened to leave behind. The periods are then set far enough back that no
 * plausible lag could move the outcome.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "a.stranger", authorities = { AuthoritiesConstants.USER })
class PayoutDeskResourceIT {

    private static final String PRO_REF = "p-desk-51";
    private static final String PRO_LOGIN = "desk.fiftyone";

    /** ₵280.00 at 12% — the seed's s1a. */
    private static final long GROSS = 28_000L;
    private static final long COMMISSION = 3_360L;
    private static final long NET = 24_640L;

    private static final int LAG_DAYS = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private MarketCalendar calendar;

    private LocalDate today;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void anEstateWithTermsAndOneEarning() {
        today = calendar.today();
        // Far enough back that any lag up to 60 days leaves this period payable. The boundary itself
        // is pinned in PayoutRunTest against a fixed clock, where the lag cannot be anybody else's.
        periodStart = today.minusDays(120);
        periodEnd = today.minusDays(60);

        em.persist(
            new BrokerageConfig()
                .commissionRate(new BigDecimal("0.120000"))
                .payoutLagDays(LAG_DAYS)
                .freeCancellationHours(24)
                .lateCancellationPct(new BigDecimal("0.500000"))
                .currency("GHS")
                .effectiveFrom(today.atStartOfDay(MarketCalendar.MARKET_ZONE).toInstant())
        );
        em.persist(earning("BKG-DESK-51-A", GROSS, COMMISSION, NET, today.minusDays(100)));
        em.flush();
    }

    private static Ledger earning(String bookingRef, long gross, long commission, long net, LocalDate on) {
        return new Ledger()
            .bookingReference(bookingRef)
            .professionalRef(PRO_REF)
            .professionalLogin(PRO_LOGIN)
            .grossMinor(gross)
            .commissionMinor(commission)
            .netMinor(net)
            .currency("GHS")
            .deliveryMode(DeliveryMode.ONLINE)
            .earnedOn(on);
    }

    /**
     * A compensating entry, spelled the way {@code DisputeEventConsumer} spells one — the DISPUTE
     * reference in {@code bookingReference}, the booking in {@code reversalOf}, all three amounts
     * negated.
     */
    private static Ledger reversalOf(String disputeRef, String bookingRef, LocalDate on) {
        return earning(disputeRef, -GROSS, -COMMISSION, -NET, on).reversalOf(bookingRef);
    }

    private String openBody(LocalDate from, LocalDate to) {
        return "{\"professionalRef\":\"%s\",\"periodStart\":\"%s\",\"periodEnd\":\"%s\"}".formatted(PRO_REF, from, to);
    }

    private long rowsAttachedTo(String reference) {
        em.flush();
        return em
            .createQuery("select count(l) from Ledger l where l.payout.reference = :reference", Long.class)
            .setParameter("reference", reference)
            .getSingleResult();
    }

    private long unsettledRows() {
        em.flush();
        return em
            .createQuery("select count(l) from Ledger l where l.professionalRef = :ref and l.payout is null", Long.class)
            .setParameter("ref", PRO_REF)
            .getSingleResult();
    }

    private long batches() {
        em.flush();
        return em
            .createQuery("select count(p) from Payout p where p.professionalRef = :ref", Long.class)
            .setParameter("ref", PRO_REF)
            .getSingleResult();
    }

    /**
     * A column of one batch, read back off the database rather than out of the response.
     *
     * <p>{@code flush} before {@code clear}, in that order and never the other way round:
     * {@code EntityManager.clear()} <strong>discards</strong> unflushed changes, so clearing first
     * would throw away the very write under assertion and the query would then report the old value
     * as though nothing had happened.
     */
    private <T> T columnOf(String reference, String jpqlColumn, Class<T> type) {
        em.flush();
        em.clear();
        return em
            .createQuery("select " + jpqlColumn + " from Payout p where p.reference = :r", type)
            .setParameter("r", reference)
            .getSingleResult();
    }

    // -------------------------------------------------------------- the positive control --

    /**
     * Every assertion below is about a refusal or about one endpoint, so one endpoint that must answer
     * establishes that the application is up, mapped and serving in the same run — the lesson
     * {@code GeneratedCrudIsNotAnApiIT} records, where a {@code Door} pointed at a path that does not
     * exist left both read cases green.
     */
    @Test
    @DisplayName("the service is actually serving — otherwise every refusal below is meaningless")
    void theServiceIsServing() throws Exception {
        mockMvc.perform(get("/api/pro/earnings")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ the authorisation --

    /**
     * The reason the generated {@code PayoutResource} was deleted (D54), asserted on its replacement.
     *
     * <p>A plain {@code ROLE_USER} is the token every customer and every professional on the estate
     * holds. Each assertion names the mapping it was asked about, and each also asserts that nothing
     * was written or disclosed: a 403 that had already created a batch, or one whose body carried
     * somebody's settlement, would satisfy a status check alone.
     */
    @Test
    @DisplayName("a plain ROLE_USER may not run a payout")
    void aPlainUserMayNotRunAPayout() throws Exception {
        long before = batches();

        mockMvc
            .perform(
                post("/api/desk/payouts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(openBody(periodStart, periodEnd))
            )
            .andExpect(status().isForbidden());

        assertThat(batches()).as("POST /api/desk/payouts must have written nothing").isEqualTo(before);
        assertThat(unsettledRows()).as("and must have attached nothing").isEqualTo(1);
    }

    @Test
    @DisplayName("a plain ROLE_USER may not record a settlement, nor read one back")
    void aPlainUserMayNotSettleOrRead() throws Exception {
        String reference = theDeskOpensABatch();

        mockMvc
            .perform(
                post("/api/desk/payouts/" + reference + "/settled")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"settledOn\":\"" + today + "\",\"bankReference\":\"GTB-FORGED\"}")
            )
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/desk/payouts/" + reference)).andExpect(result -> {
            assertThat(result.getResponse().getStatus()).as("GET /api/desk/payouts/{reference}").isEqualTo(403);
            // The reference itself IS echoed, in the ProblemDetail's `instance` and `path` — it
            // is the URL the caller just asked for, so it discloses only what they already sent.
            // What must not come back is the batch: the amounts, whose it is, and what it was
            // settled against. Asserting only "it was not 200" cannot tell a refusal from a 403
            // carrying somebody's earnings, which is the lesson GeneratedCrudIsNotAnApiIT
            // records one door at a time.
            assertThat(result.getResponse().getContentAsString())
                .as("a refusal must disclose nothing about the batch")
                .doesNotContain(String.valueOf(NET))
                .doesNotContain(String.valueOf(GROSS))
                .doesNotContain(PRO_LOGIN);
        });

        assertThat(columnOf(reference, "p.status", PayoutStatus.class))
            .as("the batch must still be unsettled")
            .isEqualTo(PayoutStatus.OPEN);
    }

    // --------------------------------------------------------------------------- the run --

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("the desk computes a batch from the period's unsettled rows and attaches them")
    void theDeskComputesABatch() throws Exception {
        mockMvc
            .perform(
                post("/api/desk/payouts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(openBody(periodStart, periodEnd))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.professionalRef").value(PRO_REF))
            .andExpect(jsonPath("$.grossMinor").value(GROSS))
            .andExpect(jsonPath("$.commissionMinor").value(COMMISSION))
            .andExpect(jsonPath("$.netMinor").value(NET))
            .andExpect(jsonPath("$.currency").value("GHS"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.entries").value(1));

        assertThat(unsettledRows()).as("the row is no longer payable").isZero();
        assertThat(batches()).isEqualTo(1);
        assertThat(columnOf(theOnlyBatchReference(), "p.settledOn", LocalDate.class))
            .as("an OPEN batch carries no settlement day")
            .isNull();
    }

    private String theOnlyBatchReference() {
        em.flush();
        return em
            .createQuery("select p.reference from Payout p where p.professionalRef = :ref", String.class)
            .setParameter("ref", PRO_REF)
            .getSingleResult();
    }

    /**
     * The double-payment guard, through the endpoint and against the real schema.
     *
     * <p>The second run over the same period finds nothing, because the first attached every row.
     * That is the guarantee, and it is a row rather than a rule: there is no seen-set and no
     * idempotency key anywhere here.
     */
    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a ledger row already in a batch is not batched again")
    void aRowAlreadyInABatchIsNotBatchedAgain() throws Exception {
        String first = theDeskOpensABatch();
        assertThat(rowsAttachedTo(first)).isEqualTo(1);

        mockMvc
            .perform(
                post("/api/desk/payouts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(openBody(periodStart, periodEnd))
            )
            .andExpect(status().isConflict());

        assertThat(batches()).as("no second batch was written").isEqualTo(1);
        assertThat(rowsAttachedTo(first)).as("and the first batch still holds its row").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a reversal is inside the batch's arithmetic, with its own sign")
    void aReversalIsInsideTheArithmetic() throws Exception {
        em.persist(earning("BKG-DESK-51-B", 15_000L, 1_800L, 13_200L, today.minusDays(95)));
        em.persist(reversalOf("DSP-DESK-51-A", "BKG-DESK-51-A", today.minusDays(90)));
        em.flush();

        mockMvc
            .perform(
                post("/api/desk/payouts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(openBody(periodStart, periodEnd))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.grossMinor").value(15_000))
            .andExpect(jsonPath("$.commissionMinor").value(1_800))
            .andExpect(jsonPath("$.netMinor").value(13_200))
            .andExpect(jsonPath("$.entries").value(3));

        assertThat(unsettledRows()).as("the reversal is settled too, or it would discount a later period").isZero();
    }

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a professional with no unsettled rows yields no batch rather than an empty one")
    void noUnsettledRowsYieldsNoBatch() throws Exception {
        mockMvc
            .perform(
                post("/api/desk/payouts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"professionalRef\":\"p-nobody-51\",\"periodStart\":\"%s\",\"periodEnd\":\"%s\"}".formatted(
                            periodStart,
                            periodEnd
                        )
                    )
            )
            .andExpect(status().isConflict());

        assertThat(
            em.createQuery("select count(p) from Payout p where p.professionalRef = 'p-nobody-51'", Long.class).getSingleResult()
        ).isZero();
    }

    /**
     * The payout lag, through the endpoint. The exact boundary day is pinned in
     * {@code PayoutRunTest} against a fixed clock; here the assertion is only that a period running to
     * today is refused and writes nothing, which holds for any lag of at least one day.
     */
    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a period running past the payout lag is refused")
    void aPeriodPastTheLagIsRefused() throws Exception {
        mockMvc
            .perform(post("/api/desk/payouts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(openBody(periodStart, today)))
            .andExpect(status().isConflict());

        assertThat(batches()).isZero();
        assertThat(unsettledRows()).isEqualTo(1);
    }

    // -------------------------------------------------------------------- the settlement --

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("the desk records the transfer a human made, against the bank's own reference")
    void theDeskRecordsTheTransfer() throws Exception {
        String reference = theDeskOpensABatch();

        mockMvc
            .perform(
                post("/api/desk/payouts/" + reference + "/settled")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"settledOn\":\"" + today + "\",\"bankReference\":\"GTB-99887766\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.settledOn").value(today.toString()))
            .andExpect(jsonPath("$.bankReference").value("GTB-99887766"))
            .andExpect(jsonPath("$.entries").value(1));
    }

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a second attempt to settle the same batch is refused")
    void aSecondSettlementIsRefused() throws Exception {
        String reference = theDeskOpensABatch();
        settle(reference, "GTB-99887766").andExpect(status().isOk());

        settle(reference, "GTB-SECOND-GO").andExpect(status().isConflict());

        assertThat(columnOf(reference, "p.bankReference", String.class))
            .as("the first settlement's reference must survive the second attempt")
            .isEqualTo("GTB-99887766");
    }

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("PAID needs both a day and a bank reference — neither alone will do")
    void paidNeedsBothHalves() throws Exception {
        String reference = theDeskOpensABatch();

        mockMvc
            .perform(
                post("/api/desk/payouts/" + reference + "/settled")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"settledOn\":\"" + today + "\"}")
            )
            .andExpect(status().isBadRequest());

        mockMvc
            .perform(
                post("/api/desk/payouts/" + reference + "/settled")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bankReference\":\"GTB-99887766\"}")
            )
            .andExpect(status().isBadRequest());

        assertThat(columnOf(reference, "p.status", PayoutStatus.class))
            .as("neither half alone may move the batch")
            .isEqualTo(PayoutStatus.OPEN);
    }

    @Test
    @WithMockUser(username = "the.desk", authorities = { MarketplaceAuthorities.BROKERAGE })
    @DisplayName("a reference no batch carries is a 404")
    void anUnknownReferenceIsNotFound() throws Exception {
        settle("PAY-000000-nobody-01", "GTB-99887766").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/desk/payouts/PAY-000000-nobody-01")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------- the loop --

    /**
     * NEW-51's whole subject: {@code GET /api/pro/payouts} read a table nothing wrote.
     *
     * <p>Asked as the professional, whose token carries a login and no reference —
     * {@code EarningsRepository.professionalRefsFor} resolves it from their own ledger rows, which is
     * what scopes the read to the caller.
     */
    @Test
    @DisplayName("the professional's own payouts endpoint now has something to show")
    void theProfessionalCanSeeTheSettlement() throws Exception {
        String reference = theDeskOpensABatch();
        settleAsDesk(reference);

        mockMvc
            .perform(get("/api/pro/payouts").with(asTheProfessional()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reference").value(reference))
            .andExpect(jsonPath("$[0].netMinor").value(NET))
            .andExpect(jsonPath("$[0].status").value("PAID"))
            .andExpect(jsonPath("$[0].bankReference").value("GTB-99887766"));
    }

    /**
     * And it is still the caller's own, which the desk endpoint above does not establish: a payouts
     * read is scoped by the JWT subject and a stranger's token resolves to no references at all.
     */
    @Test
    @DisplayName("somebody else's payouts are not on anybody else's earnings screen")
    void aStrangerSeesNothing() throws Exception {
        String reference = theDeskOpensABatch();
        settleAsDesk(reference);

        mockMvc
            .perform(get("/api/pro/payouts"))
            .andExpect(status().isOk())
            .andExpect(result ->
                assertThat(result.getResponse().getContentAsString())
                    .as("a.stranger holds no ledger row, so no batch is theirs")
                    .doesNotContain(reference)
            );
    }

    // ------------------------------------------------------------------------- plumbing --

    private ResultActions settle(String reference, String bankReference) throws Exception {
        return mockMvc.perform(
            post("/api/desk/payouts/" + reference + "/settled")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settledOn\":\"" + today + "\",\"bankReference\":\"" + bankReference + "\"}")
        );
    }

    /** Runs the desk's own two calls under the desk's authority, whatever the test method holds. */
    private String theDeskOpensABatch() throws Exception {
        String body = mockMvc
            .perform(
                post("/api/desk/payouts")
                    .with(csrf())
                    .with(asTheDesk())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBody(periodStart, periodEnd))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return referenceIn(body);
    }

    private void settleAsDesk(String reference) throws Exception {
        mockMvc
            .perform(
                post("/api/desk/payouts/" + reference + "/settled")
                    .with(csrf())
                    .with(asTheDesk())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"settledOn\":\"" + today + "\",\"bankReference\":\"GTB-99887766\"}")
            )
            .andExpect(status().isOk());
    }

    /**
     * A per-request authority, which overrides whatever {@code @WithMockUser} the method carries.
     *
     * <p>The desk's own two calls go through this rather than through a method annotation, so a test
     * whose <em>subject</em> is a plain {@code ROLE_USER} can still have a real batch in front of it
     * to be refused against.
     */
    private static RequestPostProcessor asTheDesk() {
        return user("the.desk").authorities(new SimpleGrantedAuthority(MarketplaceAuthorities.BROKERAGE));
    }

    private static RequestPostProcessor asTheProfessional() {
        return user(PRO_LOGIN).authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER));
    }

    /**
     * The reference out of a response body, read without a JSON parser because it is one field and
     * because a parser here would be a second dependency for a string this file also asserts on.
     */
    private static String referenceIn(String body) {
        String needle = "\"reference\":\"";
        int from = body.indexOf(needle);
        assertThat(from).as("the response must name the batch it created: %s", body).isNotNegative();
        int start = from + needle.length();
        return body.substring(start, body.indexOf('"', start));
    }

    /** Unused guard against an empty fixture — every test above depends on this row existing. */
    @Test
    @DisplayName("the fixture actually planted an earning — otherwise every arithmetic assertion is vacuous")
    void theFixtureActuallyPlantedSomething() {
        assertThat(unsettledRows()).isEqualTo(1);
        assertThat(
            em
                .createQuery("select l.grossMinor from Ledger l where l.bookingReference = :r", Long.class)
                .setParameter("r", "BKG-DESK-51-A")
                .getSingleResult()
        ).isEqualTo(GROSS);
        assertThat(List.of(periodStart, periodEnd))
            .as("the period must be in the past")
            .allMatch(d -> d.isBefore(today));
    }
}
