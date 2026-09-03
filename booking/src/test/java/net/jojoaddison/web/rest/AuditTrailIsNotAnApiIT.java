package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.repository.BookingStatusChangeRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/booking-status-changes} must not answer anybody — the WP-11 review's first finding.
 *
 * <p>JHipster generates CRUD for every entity, and {@code BookingStatusChange} is not an entity a
 * client owns: it is the booking's audit trail. The generated resource carried no {@code @PreAuthorize}
 * and {@code SecurityConfiguration} asks only for {@code .authenticated()} under {@code /api/**}, so on
 * the quality estate a plain {@code ROLE_USER} token read <strong>292 rows</strong> of it — every status
 * change in the estate, each with an {@code actor} column holding a real customer's or professional's
 * login beside the erasure aliases. The write half is the worse one: {@code POST}, {@code PUT},
 * {@code PATCH} and {@code DELETE} were open to the same token, so anybody with an account could forge
 * or erase an audit row. That contradicts D34/D39, which file this history as append-only evidence, and
 * {@code BookingTransition}'s claim that {@link net.jojoaddison.service.BookingWorkflow#apply} is the
 * only thing that writes one.
 *
 * <p>The fix is the one this repository has already applied to {@code BookingResource},
 * {@code DisputeResource} and {@code DisputeStatusChangeResource}: delete the generated resource. The
 * history is served, scoped to the caller's own booking, by
 * {@code CustomerBookingResource.one} and the professional's equivalent, and nothing else in the
 * estate ever read the CRUD path — that was checked before deleting it.
 *
 * <p>This test is a <strong>new file</strong>, so a regeneration leaves it alone while it puts the
 * resource back. That is the point of it: the day someone runs
 * {@code jhipster jdl ../jdl/booking.jdl --force} without reading CLAUDE.md's delete table, this goes
 * red rather than the estate quietly re-acquiring a writable audit trail.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.stranger", authorities = { AuthoritiesConstants.USER })
class AuditTrailIsNotAnApiIT {

    private static final String CRUD = "/api/booking-status-changes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private BookingStatusChangeRepository history;

    private BookingStatusChange existing;

    @BeforeEach
    void anAuditRowExists() {
        Booking booking = BookingResourceIT.createEntity();
        em.persist(booking);
        em.flush();
        existing = new BookingStatusChange()
            .fromStatus(BookingStatus.REQUESTED)
            .toStatus(BookingStatus.CONFIRMED)
            .actor("akosua.mensah")
            .occurredAt(Instant.now())
            .note("accepted");
        existing.setBooking(booking);
        em.persist(existing);
        em.flush();
    }

    /**
     * The read half. The assertion is on the body as well as the code, because a check that only asks
     * "was it not 200?" cannot tell a refusal from a 200 holding somebody's login — the wrong-app
     * collision lesson from the workspace guide, in one endpoint.
     */
    @Test
    @Transactional
    @DisplayName("the estate's status-change history is not readable over the API")
    void nobodyMayReadTheHistory() throws Exception {
        mockMvc
            .perform(get(CRUD))
            .andExpect(result -> {
                assertThat(result.getResponse().getStatus()).isIn(401, 403, 404);
                assertThat(result.getResponse().getContentAsString()).doesNotContain("akosua.mensah");
            });
    }

    /** And no single row either, which is the same leak asked for one login at a time. */
    @Test
    @Transactional
    @DisplayName("nor one row of it")
    void nobodyMayReadOneRow() throws Exception {
        mockMvc
            .perform(get(CRUD + "/" + existing.getId()))
            .andExpect(result -> {
                assertThat(result.getResponse().getStatus()).isIn(401, 403, 404);
                assertThat(result.getResponse().getContentAsString()).doesNotContain("akosua.mensah");
            });
    }

    /**
     * The write half, which is the serious one. A forged audit row is a claim that a booking moved,
     * with an actor of the forger's choosing, in a table an operator reads to establish what happened.
     */
    @Test
    @Transactional
    @DisplayName("nobody may forge an audit row")
    void nobodyMayWriteTheHistory() throws Exception {
        long before = history.count();

        mockMvc
            .perform(
                post(CRUD)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    // A complete, valid DTO — the point is that authorization refuses it, not that
                    // bean validation does. A payload the resource rejects at 400 would let the
                    // resource come back without this test noticing.
                    .content(forged(null))
            )
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403, 404, 405));

        assertThat(history.count()).isEqualTo(before);
    }

    /** Append-only means append-only: no edit and no delete, D34/D39. */
    @Test
    @Transactional
    @DisplayName("nobody may edit or erase one")
    void nobodyMayEraseTheHistory() throws Exception {
        mockMvc
            .perform(
                put(CRUD + "/" + existing.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forged(existing.getId()))
            )
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403, 404, 405));

        mockMvc
            .perform(delete(CRUD + "/" + existing.getId()).with(csrf()))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403, 404, 405));

        assertThat(history.findById(existing.getId())).isPresent();
    }

    /**
     * A status change somebody else made, spelled as the generated resource wants it — every
     * {@code @NotNull} present and the booking named by id.
     *
     * @param id null for a create, the row's own id for an edit
     */
    private String forged(Long id) {
        return (
            "{" +
            (id == null ? "" : "\"id\":" + id + ",") +
            "\"fromStatus\":\"REQUESTED\",\"toStatus\":\"COMPLETED\",\"actor\":\"akosua.mensah\"," +
            "\"occurredAt\":\"2026-09-03T09:00:00Z\",\"note\":\"forged\"," +
            "\"booking\":{\"id\":" +
            existing.getBooking().getId() +
            "}}"
        );
    }
}
