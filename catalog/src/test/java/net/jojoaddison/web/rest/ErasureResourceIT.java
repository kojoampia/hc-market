package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.FavouriteQueryRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ReviewEraseRepository;
import net.jojoaddison.service.SubjectPseudonym;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure on the catalog service — {@code decisions.md} D24/D31/D34.
 *
 * <p>Catalog had no erasure test at all while both siblings had one, which is how the full-table scan
 * and the untested author redaction both survived D31.
 *
 * <p>The line this class exists to pin is the one D24 argued hardest about: <strong>the review body
 * stays</strong>. A review is public speech about a professional, relied on by other customers and
 * already answered in public by the professional; erasing the person is not the same as retracting
 * what they said. Author name and initials go, the login becomes the alias, the text remains. If
 * counsel decides otherwise, this test is where that decision becomes visible.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureResourceIT {

    private static final String URL = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String BODY = "She rebuilt my whole week of meals around what I can actually buy in Madina market.";

    /**
     * The alias derivation, injected rather than called statically — decisions.md D35. It is peppered
     * from src/test/resources/config/application.yml, and SubjectPseudonymUnitTest pins what it
     * produces; here it is used only so the assertions ask for the same string the service wrote.
     */
    @Autowired
    private SubjectPseudonym pseudonyms;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewEraseRepository reviews;

    @Autowired
    private FavouriteQueryRepository favourites;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private EntityManager em;

    private Professional professional;
    private Review review;

    @BeforeEach
    void aCustomerWhoReviewedAndSaved() {
        professional = professionals.saveAndFlush(ProfessionalResourceIT.createEntity(em));
        review = reviews.saveAndFlush(
            new Review()
                .reference("r-erase-1")
                .customerLogin(CUSTOMER)
                .authorName("Ama To-Be-Forgotten")
                .authorInitials("AT")
                .stars(5)
                .publishedOn(LocalDate.now())
                .body(BODY)
                .bookingReference("b-erase-1")
                .professional(professional)
        );
        favourites.saveAndFlush(new Favourite().customerLogin(CUSTOMER).professionalRef(professional.getReference()).addedAt(Instant.now()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the author is de-identified and what they wrote stays public")
    void deIdentifiesTheAuthorAndKeepsTheReview() throws Exception {
        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewsDeidentified").value(1))
            .andExpect(jsonPath("$.pseudonym").value(pseudonyms.of(CUSTOMER)));

        Review after = reviews.findById(review.getId()).orElseThrow();
        assertThat(after.getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(after.getAuthorName()).doesNotContain("Ama").doesNotContain("Forgotten");
        assertThat(after.getAuthorInitials()).doesNotContain("AT");

        // The decision D24 argued: public speech about a professional is not erased with its author.
        assertThat(after.getBody()).isEqualTo(BODY);
        assertThat(after.getStars()).isEqualTo(5);
        assertThat(after.getBookingReference()).isEqualTo("b-erase-1");
    }

    /**
     * A saved list is purely personal: nothing aggregates over it, it says nothing about the
     * professional, and a tombstoned row would be an orphan nobody can act on. So it is deleted
     * outright rather than pseudonymised — the one place this feature deletes anything.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the saved list is deleted, not tombstoned")
    void deletesFavourites() throws Exception {
        assertThat(favourites.findByCustomerLoginOrderByAddedAtDesc(CUSTOMER)).hasSize(1);

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        assertThat(favourites.findByCustomerLoginOrderByAddedAtDesc(CUSTOMER)).isEmpty();
        assertThat(favourites.findByCustomerLoginOrderByAddedAtDesc(pseudonyms.of(CUSTOMER))).isEmpty();
    }

    /**
     * The rating is a view over the review rows, so de-identifying an author must not move a
     * professional's score. That is "derived, never stored" doing its job — but it is worth pinning,
     * because a future implementation that deleted reviews instead would silently change every
     * rating on the site.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the professional's rating does not move")
    void theRatingIsUnaffected() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        assertThat(reviews.findById(review.getId())).isPresent();
        assertThat(reviews.findByCustomerLogin(CUSTOMER)).isEmpty();
        assertThat(reviews.findByCustomerLogin(pseudonyms.of(CUSTOMER))).hasSize(1);
    }

    /** Nobody else's rows move — a regression that widened the sweep would pass every test above. */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a second customer is left completely alone")
    void doesNotTouchAnybodyElse() throws Exception {
        Review theirs = reviews.saveAndFlush(
            new Review()
                .reference("r-erase-2")
                .customerLogin("kwame.stillhere")
                .authorName("Kwame Still-Here")
                .authorInitials("KS")
                .stars(4)
                .publishedOn(LocalDate.now())
                .body("Good session.")
                .bookingReference("b-erase-2")
                .professional(professional)
        );

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        Review after = reviews.findById(theirs.getId()).orElseThrow();
        assertThat(after.getCustomerLogin()).isEqualTo("kwame.stillhere");
        assertThat(after.getAuthorName()).isEqualTo("Kwame Still-Here");
        assertThat(after.getAuthorInitials()).isEqualTo("KS");
    }

    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user cannot erase anyone")
    void ordinaryUserIsRefused() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());
        assertThat(reviews.findById(review.getId()).orElseThrow().getAuthorName()).isEqualTo("Ama To-Be-Forgotten");
    }
}
