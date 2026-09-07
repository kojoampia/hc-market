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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.Direction;
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
 * Messaging's two generated CRUD resources must not answer anybody — backlog NEW-15, decisions.md D54.
 *
 * <p>These were the worst two doors of the nine. {@code GET /api/messages} returned every message in
 * the estate to any {@code ROLE_USER} — the body a customer wrote to a professional, and who wrote it
 * to whom — and {@code GET /api/conversations} returned the thread list, which is the same disclosure
 * at one remove: which customer is talking to which professional, and about which booking. Nothing
 * about either was scoped to the caller, because {@code SecurityConfiguration} asks only for
 * {@code .requestMatchers("/api/**").authenticated()} and neither resource carried a
 * {@code @PreAuthorize}. The write half let anybody put words in a named person's mouth.
 *
 * <p>It is also an erasure surface (D31, D36, D39). {@code ErasureWorkflow} redacts
 * {@code customer_login} and message bodies, and the receipt an operator files says how many rows it
 * changed. A CRUD path that lets anybody write a fresh row naming an erased customer makes that
 * receipt a statement about a moment rather than about the estate.
 *
 * <p>The scoped replacement is {@link MessagingResource} — {@code /api/threads} and
 * {@code /api/threads/{ref}/messages}, both filtered to the caller. Note the singular/plural: the
 * hand-written resource is {@code MessagingResource}, the generated ones were {@code MessageResource}
 * and {@code ConversationResource}, and that difference is the only thing that stopped them colliding.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/messaging.jdl --force} leaves
 * it in place while it puts both resources back. Every failure names the path it was asked about.
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
     *                   somebody's private message.
     */
    private record Door(String path, String entity, String disclosure) {
        @Override
        public String toString() {
            return path;
        }
    }

    private static final Door CONVERSATION = new Door("/api/conversations", "Conversation", "akosua.guard");
    private static final Door MESSAGE = new Door("/api/messages", "Message", "said in confidence to one person");

    private static Stream<Door> doors() {
        return Stream.of(CONVERSATION, MESSAGE);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    /** The id of the row planted for each door, so the edit and delete halves have a target. */
    private final Map<Door, Long> planted = new HashMap<>();

    /** Messages need a parent, and a forged one needs a real conversation id to name. */
    private Conversation conversation;

    @BeforeEach
    void aRowExistsBehindEveryDoor() {
        conversation = new Conversation()
            .reference("CNV-GUARD-0001")
            .customerLogin(CONVERSATION.disclosure())
            .professionalRef("p-guard")
            .bookingReference("BKG-GUARD-0001")
            .lastMessageAt(Instant.parse("2026-07-15T09:00:00Z"));
        em.persist(conversation);

        Message message = new Message()
            .direction(Direction.CUSTOMER_TO_PROFESSIONAL)
            .body(MESSAGE.disclosure())
            .sentAt(Instant.parse("2026-07-15T09:00:00Z"));
        message.setConversation(conversation);
        em.persist(message);

        em.flush();

        planted.put(CONVERSATION, conversation.getId());
        planted.put(MESSAGE, message.getId());
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
        mockMvc.perform(get("/api/threads")).andExpect(status().isOk());
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
     * The write half. A forged {@code Message} is a sentence attributed to a real person in a thread
     * they cannot delete it from — there is no endpoint to remove one, by design.
     *
     * <p>The body is a complete, valid DTO on purpose. A payload the resource would reject at 400 lets
     * the resource come back while this test still asserts something about it.
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
     * {@code @NotNull} present.
     *
     * @param id null for a create, the planted row's own id for an edit
     */
    private String forged(Door door, Long id) {
        String key = id == null ? "" : "\"id\":" + id + ",";
        return switch (door.path()) {
            case "/api/conversations" -> "{" +
            key +
            "\"reference\":\"CNV-FORGED-0001\",\"customerLogin\":\"a.stranger\"," +
            "\"professionalRef\":\"p-forged\",\"bookingReference\":\"BKG-FORGED-0001\"," +
            "\"lastMessageAt\":\"2026-07-15T09:00:00Z\"}";
            case "/api/messages" -> "{" +
            key +
            "\"direction\":\"PROFESSIONAL_TO_CUSTOMER\",\"body\":\"words this person never wrote\"," +
            "\"sentAt\":\"2026-07-15T09:00:00Z\",\"conversation\":{\"id\":" +
            conversation.getId() +
            "}}";
            default -> throw new IllegalStateException("no forged body for " + door.path());
        };
    }
}
