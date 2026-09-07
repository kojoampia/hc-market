package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payout's three generated CRUD resources must not answer anybody — backlog NEW-15, decisions.md D54.
 *
 * <p>JHipster generates a full CRUD resource per entity, and payout's
 * {@code SecurityConfiguration} asks only for {@code .requestMatchers("/api/**").authenticated()}.
 * None of the three carried a {@code @PreAuthorize}, so every one of them — read, create, edit and
 * delete — was reachable by any token this estate accepts, and the gateway routes
 * {@code /services/healthconnectpayout/api/**} straight at them. This is the money service: the
 * three doors were the commission rate every completed booking is priced against, the ledger that
 * records what each professional earned, and the payout batches that say what has been settled.
 *
 * <p>The fix is the one this repository has applied eight times before: delete the generated
 * resource, and put it on CLAUDE.md's delete table so a regeneration does not quietly restore it.
 * Nothing in the repository called any of the three — checked before deleting — and the seeders and
 * the two event consumers write through repositories, never over HTTP.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/payout.jdl --force} leaves it
 * in place while it puts all three resources back. That is the point of it. It is one test walking a
 * list rather than three near-identical classes, and every failure names the path it was asked
 * about — a battery that fires as one number cannot tell you which door opened.
 *
 * @see AuditTrailIsNotAnApiIT in booking, which is the pattern this follows
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.stranger", authorities = { AuthoritiesConstants.USER })
class GeneratedCrudIsNotAnApiIT {

    /**
     * One generated resource that must not exist.
     *
     * @param path       the CRUD path JHipster mounts it on
     * @param entity     the JPQL entity name, so the write half can count rows without a repository
     *                   per door
     * @param disclosure a value planted in the row below that must never appear in a response body.
     *                   Asserting only "it was not 200" cannot tell a refusal from a 200 holding
     *                   somebody's earnings — the wrong-app collision lesson, one endpoint at a time.
     */
    private record Door(String path, String entity, String disclosure) {
        @Override
        public String toString() {
            return path;
        }
    }

    // The marker is an INTEGER field, deliberately. A BigDecimal's serialised scale is decided by the
    // column, the driver and Jackson between them, so "0.999000" could come back as "0.999" and the
    // disclosure assertion would then pass against a body that had just served the whole row — a
    // check that fails open on a formatting detail. 4242 is 4242 on every road out of the database.
    private static final Door BROKERAGE_CONFIG = new Door("/api/brokerage-configs", "BrokerageConfig", "4242");
    private static final Door LEDGER = new Door("/api/ledgers", "Ledger", "kofi.asante.guard");
    private static final Door PAYOUT = new Door("/api/payouts", "Payout", "PAY-GUARD-0001");

    private static Stream<Door> doors() {
        return Stream.of(BROKERAGE_CONFIG, LEDGER, PAYOUT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    /** The id of the row planted for each door, so the edit and delete halves have a target. */
    private final Map<Door, Long> planted = new HashMap<>();

    @BeforeEach
    void aRowExistsBehindEveryDoor() {
        BrokerageConfig config = new BrokerageConfig()
            .commissionRate(new BigDecimal("0.999000"))
            .payoutLagDays(3)
            // The marker: nothing else in this schema is 4242, so a body containing it is this row.
            .freeCancellationHours(4242)
            .lateCancellationPct(new BigDecimal("0.500000"))
            .currency("GHS")
            .effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
        em.persist(config);

        Payout payout = new Payout()
            .reference(PAYOUT.disclosure())
            .professionalRef("p-guard")
            .periodStart(LocalDate.parse("2026-07-01"))
            .periodEnd(LocalDate.parse("2026-07-31"))
            .grossMinor(28000L)
            .commissionMinor(3360L)
            .netMinor(24640L)
            .currency("GHS")
            .status(PayoutStatus.OPEN);
        em.persist(payout);

        Ledger ledger = new Ledger()
            .bookingReference("BKG-GUARD-0001")
            .professionalRef("p-guard")
            .professionalLogin(LEDGER.disclosure())
            .grossMinor(28000L)
            .commissionMinor(3360L)
            .netMinor(24640L)
            .currency("GHS")
            .deliveryMode(DeliveryMode.ONLINE)
            .earnedOn(LocalDate.parse("2026-07-15"));
        em.persist(ledger);

        em.flush();

        planted.put(BROKERAGE_CONFIG, config.getId());
        planted.put(LEDGER, ledger.getId());
        planted.put(PAYOUT, payout.getId());
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("doors")
    @Transactional
    @DisplayName("no generated collection is readable")
    void nobodyMayReadTheCollection(Door door) throws Exception {
        mockMvc
            .perform(get(door.path()))
            .andExpect(result -> {
                assertThat(result.getResponse().getStatus()).as("GET %s", door.path()).isIn(401, 403, 404);
                assertThat(result.getResponse().getContentAsString())
                    .as("GET %s must not disclose %s", door.path(), door.disclosure())
                    .doesNotContain(door.disclosure());
            });
    }

    @ParameterizedTest(name = "GET {0}/<id>")
    @MethodSource("doors")
    @Transactional
    @DisplayName("nor one row of it, which is the same disclosure asked for one row at a time")
    void nobodyMayReadOneRow(Door door) throws Exception {
        mockMvc
            .perform(get(door.path() + "/" + planted.get(door)))
            .andExpect(result -> {
                assertThat(result.getResponse().getStatus()).as("GET %s/{id}", door.path()).isIn(401, 403, 404);
                assertThat(result.getResponse().getContentAsString())
                    .as("GET %s/{id} must not disclose %s", door.path(), door.disclosure())
                    .doesNotContain(door.disclosure());
            });
    }

    /**
     * The write half, which is the serious one here. A forged {@code BrokerageConfig} reprices every
     * booking completed after its {@code effectiveFrom} (D53); a forged {@code Ledger} row is an
     * earning nobody worked for; a forged {@code Payout} is a settlement that never happened.
     *
     * <p>The body is a complete, valid DTO on purpose. A payload the resource would reject at 400 lets
     * the resource come back while this test still asserts something about it — and the row count,
     * which is the assertion that really matters, would never move either way.
     */
    @ParameterizedTest(name = "POST {0}")
    @MethodSource("doors")
    @Transactional
    @DisplayName("nobody may create one")
    void nobodyMayCreate(Door door) throws Exception {
        long before = rows(door);

        mockMvc
            .perform(post(door.path()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(forged(door, null)))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("POST %s", door.path()).isIn(401, 403, 404, 405));

        assertThat(rows(door)).as("POST %s must have written nothing", door.path()).isEqualTo(before);
    }

    @ParameterizedTest(name = "PUT/DELETE {0}/<id>")
    @MethodSource("doors")
    @Transactional
    @DisplayName("nobody may edit or erase one")
    void nobodyMayEditOrErase(Door door) throws Exception {
        Long id = planted.get(door);
        long before = rows(door);

        mockMvc
            .perform(put(door.path() + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(forged(door, id)))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("PUT %s/{id}", door.path()).isIn(401, 403, 404, 405));

        mockMvc
            .perform(delete(door.path() + "/" + id).with(csrf()))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("DELETE %s/{id}", door.path()).isIn(401, 403, 404, 405));

        assertThat(rows(door)).as("%s must still hold every row it held", door.path()).isEqualTo(before);
    }

    /** Counted through JPQL rather than a repository per door, so the list stays a list. */
    private long rows(Door door) {
        em.flush();
        return em.createQuery("select count(e) from " + door.entity() + " e", Long.class).getSingleResult();
    }

    /**
     * A row somebody else's service wrote, spelled the way the generated resource wants it — every
     * {@code @NotNull} present.
     *
     * @param id null for a create, the planted row's own id for an edit
     */
    private String forged(Door door, Long id) {
        String key = id == null ? "" : "\"id\":" + id + ",";
        return switch (door.path()) {
            case "/api/brokerage-configs" -> "{" +
            key +
            "\"commissionRate\":0.010000,\"payoutLagDays\":0,\"freeCancellationHours\":0," +
            "\"lateCancellationPct\":0.000000,\"currency\":\"GHS\",\"effectiveFrom\":\"2020-01-01T00:00:00Z\"}";
            case "/api/ledgers" -> "{" +
            key +
            "\"bookingReference\":\"BKG-FORGED-0001\",\"professionalRef\":\"p-forged\"," +
            "\"professionalLogin\":\"a.stranger\",\"grossMinor\":900000,\"commissionMinor\":0," +
            "\"netMinor\":900000,\"currency\":\"GHS\",\"deliveryMode\":\"ONLINE\"," +
            "\"earnedOn\":\"2026-07-15\"}";
            case "/api/payouts" -> "{" +
            key +
            "\"reference\":\"PAY-FORGED-0001\",\"professionalRef\":\"p-forged\"," +
            "\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-07-31\",\"grossMinor\":900000," +
            "\"commissionMinor\":0,\"netMinor\":900000,\"currency\":\"GHS\",\"status\":\"PAID\"}";
            default -> throw new IllegalStateException("no forged body for " + door.path());
        };
    }
}
