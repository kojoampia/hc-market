package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Credential;
import net.jojoaddison.domain.Highlight;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog's four surviving generated CRUD resources must not answer anybody — backlog NEW-15,
 * decisions.md D54.
 *
 * <p>Catalog had eleven entities and seven of its generated resources were deleted long ago; these
 * four were left behind by the same omission that left {@code BrokerageConfigResource} in payout.
 * Two of them are the siblings of rows already on CLAUDE.md's delete table, which is what makes the
 * omission legible: {@code AvailabilityRuleResource} and {@code AvailabilityOverrideResource} were
 * deleted because generated CRUD would let any authenticated user edit anyone's availability, and
 * {@code AvailabilitySlotResource} — the third resource from that same generator run, and the one
 * holding the {@code unique_availability_slot} row a double booking collides on — was not.
 *
 * <p>What each door was:
 *
 * <ul>
 *   <li>{@code /api/service-offerings} — the price list. D22 says nothing a client sends decides what
 *       a booking costs, because {@code BookingCreator} reads the price from the catalogue. This let
 *       any authenticated user edit the catalogue.</li>
 *   <li>{@code /api/availability-slots} — the bookable row itself (D20). Create slots in somebody
 *       else's calendar, or delete the one a confirmed booking is standing on.</li>
 *   <li>{@code /api/credentials} — public claims about a real practitioner's qualifications, which is
 *       the category {@code VerificationReviewResource} was deleted for.</li>
 *   <li>{@code /api/highlights} — the same, one notch down: the bullets on somebody else's profile.</li>
 * </ul>
 *
 * <p>The scoped replacement for the first two is {@link ProWorkspaceResource}, which resolves the
 * professional from the JWT subject and takes no professional parameter at all. Credentials and
 * highlights have no write path in this estate — they are seeded, and read as part of the public
 * profile {@link MarketplaceResource} serves.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/catalog.jdl --force} leaves it
 * in place while it puts all four resources back. Every failure names the path it was asked about.
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
     *                   Asserting only "it was not 200" cannot tell a refusal from a 200 holding the
     *                   row.
     */
    private record Door(String path, String entity, String disclosure) {
        @Override
        public String toString() {
            return path;
        }
    }

    private static final Door SERVICE_OFFERING = new Door("/api/service-offerings", "ServiceOffering", "SVC-GUARD-0001");
    private static final Door AVAILABILITY_SLOT = new Door("/api/availability-slots", "AvailabilitySlot", "2031-02-03");
    private static final Door CREDENTIAL = new Door("/api/credentials", "Credential", "MSc Guard Studies, University of Nowhere");
    private static final Door HIGHLIGHT = new Door("/api/highlights", "Highlight", "Guarded highlight, not a real claim");

    private static Stream<Door> doors() {
        return Stream.of(SERVICE_OFFERING, AVAILABILITY_SLOT, CREDENTIAL, HIGHLIGHT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    /** The id of the row planted for each door, so the edit and delete halves have a target. */
    private final Map<Door, Long> planted = new HashMap<>();

    /** All four entities hang off a professional, and a forged row needs a real one to name. */
    private Professional professional;

    @BeforeEach
    void aRowExistsBehindEveryDoor() {
        professional = ProfessionalResourceIT.createEntity(em);
        em.persist(professional);

        ServiceOffering offering = new ServiceOffering()
            .reference(SERVICE_OFFERING.disclosure())
            .name("Guarded consultation")
            .durationMinutes(60)
            .priceMinor(28000L)
            .currency("GHS")
            .active(true)
            .sortOrder(1);
        offering.setProfessional(professional);
        em.persist(offering);

        AvailabilitySlot slot = new AvailabilitySlot()
            // The marker is the date, because a slot carries no text of its own. Far enough out that
            // no other fixture in this suite can have written the same one.
            .slotDate(LocalDate.parse(AVAILABILITY_SLOT.disclosure()))
            .slotTime(LocalTime.parse("07:00"))
            .taken(false);
        slot.setProfessional(professional);
        em.persist(slot);

        Credential credential = new Credential().label(CREDENTIAL.disclosure()).sortOrder(1);
        credential.setProfessional(professional);
        em.persist(credential);

        Highlight highlight = new Highlight().label(HIGHLIGHT.disclosure()).sortOrder(1);
        highlight.setProfessional(professional);
        em.persist(highlight);

        em.flush();

        planted.put(SERVICE_OFFERING, offering.getId());
        planted.put(AVAILABILITY_SLOT, slot.getId());
        planted.put(CREDENTIAL, credential.getId());
        planted.put(HIGHLIGHT, highlight.getId());
    }

    /**
     * A positive control, because every other test here is a negative one — D54's review, finding 3.
     *
     * <p>The refusal set is {@code 401/403/404} rather than a bare {@code 404} on purpose: deletion
     * gives 404 today and a future gate would give 403, and pinning 404 would make this guard go red
     * on the correct fix. The cost of that latitude is that <strong>a 404 for the wrong reason reads
     * exactly like a 404 for the right one</strong> — pointing a {@code Door} at a misspelt path left
     * both read cases green, verified. So one endpoint that must answer establishes that the
     * application is up, mapped and serving in the same run.
     *
     * <p>It does not rescue a typo in a single {@code Door}'s own path, and nothing can: the door is
     * defined by its path. A typo in {@code Door.entity} <em>is</em> caught, because {@link #rows}
     * builds JPQL from it and an unknown entity throws.
     */
    @Test
    @Transactional
    @DisplayName("the service is actually serving — otherwise every refusal below is meaningless")
    void theSurvivingApiStillAnswers() throws Exception {
        mockMvc.perform(get("/api/professionals")).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("doors")
    @Transactional
    @DisplayName("no generated collection is readable")
    void nobodyMayReadTheCollection(Door door) throws Exception {
        thePlantingActuallyHappened(door);
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
        thePlantingActuallyHappened(door);
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
     * The write half. A forged {@code ServiceOffering} is a price the professional never set and a
     * booking is made at it; a forged {@code AvailabilitySlot} is an hour of somebody's day.
     *
     * <p>The body is a complete, valid DTO on purpose, and it never collides with the planted row on a
     * unique column. A payload the resource would refuse — at 400 for validation or at 409 for
     * {@code unique_availability_slot} — lets the resource come back while the row count stays put,
     * which is the assertion that really matters here.
     */
    @ParameterizedTest(name = "POST {0}")
    @MethodSource("doors")
    @Transactional
    @DisplayName("nobody may create one")
    void nobodyMayCreate(Door door) throws Exception {
        thePlantingActuallyHappened(door);
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
        thePlantingActuallyHappened(door);
        Long id = planted.get(door);
        long before = rows(door);

        mockMvc
            .perform(put(door.path() + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(forged(door, id)))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("PUT %s/{id}", door.path()).isIn(401, 403, 404, 405));

        // PATCH is a fourth write verb and was unexercised until D54's review. The generated
        // partialUpdate accepts application/json as well as merge-patch+json, so the same body works.
        mockMvc
            .perform(patch(door.path() + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(forged(door, id)))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("PATCH %s/{id}", door.path()).isIn(401, 403, 404, 405));

        mockMvc
            .perform(delete(door.path() + "/" + id).with(csrf()))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).as("DELETE %s/{id}", door.path()).isIn(401, 403, 404, 405));

        assertThat(rows(door)).as("%s must still hold every row it held", door.path()).isEqualTo(before);
    }

    /**
     * The guard's own foundation, asserted rather than assumed — D54's review, finding 2.
     *
     * <p>{@code aRowExistsBehindEveryDoor} was listed first among the things this guard does that a
     * bare status check would not, and <strong>nothing checked that it had done it</strong>. With its
     * body replaced by a comment the whole file still reported {@code Failures: 0}: the disclosure
     * marker is unconditioned on there being anything to disclose, and the row count compares 0 to 0.
     * A {@code Door} added without a row planted for it would go green the same way.
     */
    private void thePlantingActuallyHappened(Door door) {
        assertThat(planted.get(door))
            .as("nothing was planted behind %s — every assertion about it would pass vacuously", door.path())
            .isNotNull();
        assertThat(rows(door)).as("%s has no row behind it, so this guard proves nothing", door.path()).isPositive();
    }

    /** Counted through JPQL rather than a repository per door, so the list stays a list. */
    private long rows(Door door) {
        em.flush();
        return em.createQuery("select count(e) from " + door.entity() + " e", Long.class).getSingleResult();
    }

    /**
     * A row somebody else's service wrote, spelled the way the generated resource wants it — every
     * {@code @NotNull} present and the professional named by id.
     *
     * @param id null for a create, the planted row's own id for an edit
     */
    private String forged(Door door, Long id) {
        String key = id == null ? "" : "\"id\":" + id + ",";
        String parent = "\"professional\":{\"id\":" + professional.getId() + "}";
        return switch (door.path()) {
            case "/api/service-offerings" -> "{" +
            key +
            "\"reference\":\"SVC-FORGED-0001\",\"name\":\"Forged consultation\",\"durationMinutes\":60," +
            "\"priceMinor\":1,\"currency\":\"GHS\",\"description\":\"forged\",\"active\":true," +
            "\"sortOrder\":1," +
            parent +
            "}";
            // A different date from the planted slot, or unique_availability_slot refuses the write
            // before authorization gets the chance to and the count assertion proves nothing.
            case "/api/availability-slots" -> "{" +
            key +
            "\"slotDate\":\"2031-02-04\",\"slotTime\":\"08:00:00\",\"taken\":true," +
            parent +
            "}";
            case "/api/credentials" -> "{" + key + "\"label\":\"Forged doctorate\",\"sortOrder\":2," + parent + "}";
            case "/api/highlights" -> "{" + key + "\"label\":\"Forged highlight\",\"sortOrder\":2," + parent + "}";
            default -> throw new IllegalStateException("no forged body for " + door.path());
        };
    }
}
