package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.VerificationState;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.VerificationReviewQueryRepository;
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
 * The verification desk — {@code decisions.md} D16/D29.
 *
 * <p>Three properties matter here and they are the three this file asserts: the decision and the
 * professional's public state move <strong>together</strong>, the reviewer is taken from the token
 * rather than the body, and an ordinary user cannot touch any of it.
 */
@IntegrationTest
@AutoConfigureMockMvc
class VerificationDeskResourceIT {

    private static final String URL = "/api/desk/professionals/{ref}/verification";
    private static final String DESK = "ama.brokerage";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private VerificationReviewQueryRepository reviews;

    @Autowired
    private EntityManager em;

    private Professional professional;

    @BeforeEach
    void persistAPendingProfessional() {
        professional = professionals.saveAndFlush(ProfessionalResourceIT.createEntity(em).verification(VerificationState.PENDING));
    }

    /**
     * The pairing the whole decision rests on. {@code Professional.verification} is the projection of
     * the latest review, so a desk action that wrote one without the other would leave the badge
     * customers see disagreeing with the only record of how it was decided — and the visible one
     * would be the one nobody can audit.
     */
    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("a decision appends a row and moves the professional, together")
    void decisionAppendsAndMoves() throws Exception {
        mockMvc
            .perform(
                post(URL, professional.getReference())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"VERIFIED\",\"evidenceRef\":\"CID-2026-0041\",\"note\":\"Ghana Card and police clearance seen\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("VERIFIED"))
            .andExpect(jsonPath("$.evidenceRef").value("CID-2026-0041"));

        assertThat(professionals.findById(professional.getId()).orElseThrow().getVerification()).isEqualTo(VerificationState.VERIFIED);
        assertThat(reviews.findByProfessionalReferenceOrderByReviewedAtDesc(professional.getReference()))
            .singleElement()
            .satisfies(r -> {
                assertThat(r.getDecision()).isEqualTo(VerificationState.VERIFIED);
                assertThat(r.getEvidenceRef()).isEqualTo("CID-2026-0041");
            });
    }

    /** The reviewer on the recorded row is the JWT subject, and there is no other way for it to be set. */
    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("the reviewer is the token subject")
    void reviewerComesFromTheToken() throws Exception {
        mockMvc
            .perform(post(URL, professional.getReference()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"UNVERIFIED\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reviewer").value(DESK));
    }

    /**
     * And a caller that tries to name someone else cannot.
     *
     * <p>{@code DecideVerification} has no {@code reviewer} component, and JHipster configures Jackson
     * not to fail on unknown properties — so the field is <strong>ignored</strong> rather than
     * refused. Measured, not assumed: this test was first written expecting a 400 and got a 201.
     *
     * <p>Ignoring is weaker than refusing, and the reason it is acceptable is that the field can
     * never reach the row: {@code reviewer} is read from the token inside the resource and there is
     * no code path that consults the body for it. What this asserts is exactly that — the recorded
     * and returned reviewer is the authenticated subject no matter what was sent. If a
     * {@code reviewer} component is ever added to the request record, this fails.
     */
    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("a body naming a different reviewer cannot change who is recorded")
    void aSuppliedReviewerCannotWin() throws Exception {
        mockMvc
            .perform(
                post(URL, professional.getReference())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"UNVERIFIED\",\"reviewer\":\"someone.else\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reviewer").value(DESK));

        assertThat(reviews.findByProfessionalReferenceOrderByReviewedAtDesc(professional.getReference()))
            .singleElement()
            .satisfies(r -> assertThat(r.getReviewer()).isEqualTo(DESK));
    }

    /** Append-only: a second decision supersedes the first without erasing it. */
    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("a superseding decision is appended, and the earlier one survives")
    void historyIsAppendOnly() throws Exception {
        decide("PENDING");
        decide("VERIFIED");

        mockMvc
            .perform(get(URL, professional.getReference()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
        assertThat(professionals.findById(professional.getId()).orElseThrow().getVerification()).isEqualTo(VerificationState.VERIFIED);
    }

    /**
     * Empty rather than 404. Every professional seeded before this existed has a state and no history
     * behind it, and saying so plainly is more useful than claiming the professional is unknown.
     */
    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("a professional with no history yet returns an empty list, not a 404")
    void noHistoryIsAnEmptyList() throws Exception {
        mockMvc.perform(get(URL, professional.getReference())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Transactional
    @WithMockUser(username = DESK, authorities = "ROLE_BROKERAGE")
    @DisplayName("an unknown professional is 404")
    void unknownProfessionalIs404() throws Exception {
        mockMvc
            .perform(post(URL, "p-nope").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VERIFIED\"}"))
            .andExpect(status().isNotFound());
    }

    /**
     * ROLE_USER is not enough, and this is the test that matters most: the generated
     * {@code VerificationReviewResource} this class replaced would have let exactly this caller forge
     * a public claim about somebody's trustworthiness.
     */
    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user can neither decide nor read the history")
    void ordinaryUserIsRefused() throws Exception {
        mockMvc
            .perform(
                post(URL, professional.getReference()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VERIFIED\"}")
            )
            .andExpect(status().isForbidden());
        mockMvc.perform(get(URL, professional.getReference())).andExpect(status().isForbidden());
    }

    private void decide(String decision) throws Exception {
        mockMvc
            .perform(
                post(URL, professional.getReference())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"%s\"}".formatted(decision))
            )
            .andExpect(status().isCreated());
    }
}
