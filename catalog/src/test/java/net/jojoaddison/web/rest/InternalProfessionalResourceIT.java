package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InternalProfessionalResource} — the ref-to-login lookup booking needs, {@code decisions.md}
 * D28.
 *
 * <p><strong>Deliberately no {@code @WithMockUser}.</strong> Every other resource IT in this module
 * carries one; this endpoint must answer an <em>unauthenticated</em> caller, because the booking
 * service holds no credential of its own — this estate has no service-to-service authentication.
 * The absence of the annotation is the assertion, so it is stated rather than left to be noticed.
 *
 * <p>What keeps that from being a hole is that no gateway route matches {@code /internal/**} in any
 * environment. That cannot be asserted here — it lives in the three compose files — so the check
 * that matters most about this endpoint is not in this file. Said out loud so nobody reads a green
 * suite as proof the path is unreachable.
 */
@IntegrationTest
@AutoConfigureMockMvc
class InternalProfessionalResourceIT {

    private static final String URL = "/internal/professionals/{ref}/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private EntityManager em;

    private Professional professional;

    @BeforeEach
    void persistAProfessional() {
        professional = professionals.saveAndFlush(ProfessionalResourceIT.createEntity(em));
    }

    @Test
    @Transactional
    void answersWithTheLoginThatOwnsTheReference() throws Exception {
        mockMvc
            .perform(get(URL, professional.getReference()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reference").value(professional.getReference()))
            .andExpect(jsonPath("$.login").value(professional.getUserLogin()));
    }

    /**
     * 404 rather than 200-with-null. A caller that cannot establish who a reference belongs to must
     * refuse to create a booking, and an empty body is far too easy to treat as an absent field.
     */
    @Test
    @Transactional
    void unknownReferenceIs404() throws Exception {
        mockMvc.perform(get(URL, "p-does-not-exist")).andExpect(status().isNotFound());
    }

    /**
     * A professional whose login is blank cannot be attributed to anybody, which is the same answer
     * as not existing — see {@code MarketplaceService.loginOf}.
     *
     * <p>Blank, not null: the column is {@code required unique} in the JDL, so {@code @NotNull}
     * rejects a null at persist time and there is no such row to serve. {@code @NotNull} says
     * nothing about the empty string, though, so that one reaches the database — which is exactly
     * why the service filters on blankness rather than on presence.
     */
    @Test
    @Transactional
    void aProfessionalWithABlankLoginIs404() throws Exception {
        Professional anonymous = professionals.saveAndFlush(ProfessionalResourceIT.createEntity(em).userLogin(""));
        mockMvc.perform(get(URL, anonymous.getReference())).andExpect(status().isNotFound());
    }

    /**
     * The prefix is read-only, and {@code InternalApiSecurityConfiguration} denies everything that
     * is not a GET so it cannot quietly acquire a write endpoint. 401 or 403 both satisfy this —
     * what matters is that it is not carried out.
     */
    @Test
    @Transactional
    void nonGetIsRefused() throws Exception {
        mockMvc.perform(post(URL, professional.getReference()).with(csrf())).andExpect(status().is4xxClientError());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
