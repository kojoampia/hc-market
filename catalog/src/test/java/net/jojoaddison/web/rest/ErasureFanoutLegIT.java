package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ReviewEraseRepository;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.SubjectPseudonym;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog as a <em>leg</em> of booking's erasure fan-out — {@code decisions.md} D37 and D38.
 *
 * <p>The tokens are minted with catalog's own {@code JwtEncoder}, which is the same token booking
 * would produce: the five services share one signing secret, and that sharing is the mechanism D37
 * chose rather than something this test is working around.
 *
 * <p><strong>The rejection tests are the point of this class.</strong> Catalog is where the fan-out
 * authority sits closest to something it must never reach: {@code ROLE_BROKERAGE} also guards
 * {@code /api/desk/professionals/**}, where a decision is recorded that puts a VERIFIED badge next to
 * a real person's name on a public profile. A fan-out credential that could write there would be a
 * service able to make a public claim about somebody's trustworthiness, which is precisely the harm
 * the generated {@code VerificationReviewResource} was deleted to prevent.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureFanoutLegIT {

    private static final String ERASE = "/api/desk/customers/{login}/erase";
    private static final String VERIFICATION = "/api/desk/professionals/{ref}/verification";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String BYSTANDER = "kojo.stillhere";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private ReviewEraseRepository reviews;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private EntityManager em;

    @Autowired
    private SubjectPseudonym pseudonyms;

    /** A fan-out token exactly as {@code FanoutTokenMinter} builds one in booking. */
    private String fanOutToken(String login, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("hc-market-booking")
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .subject(ErasureFanoutToken.SUBJECT)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, MarketplaceAuthorities.CUSTOMER_ERASURE)
            .claim(ErasureFanoutToken.SUBJECT_CLAIM, login)
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Mirrors {@code ErasureResourceIT}'s fixture: a review is keyed to a professional and a booking. */
    private Review review(String customer, String reference) {
        Professional professional = professionals.saveAndFlush(ProfessionalResourceIT.createEntity(em));
        return reviews.saveAndFlush(
            new Review()
                .reference("r-" + reference)
                .customerLogin(customer)
                .authorName("Ama To-Be-Forgotten")
                .authorInitials("AT")
                .stars(5)
                .publishedOn(LocalDate.now())
                .body("She was punctual and thorough.")
                .bookingReference(reference)
                .professional(professional)
        );
    }

    @Test
    @Transactional
    @DisplayName("a fan-out token de-identifies the reviews of the customer it was minted for")
    void theFanOutTokenErasesTheCustomerItNames() throws Exception {
        Review mine = review(CUSTOMER, "b-fanout-1");

        mockMvc
            .perform(
                post(ERASE, CUSTOMER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofSeconds(30)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewsDeidentified").value(1));

        Review after = reviews.findById(mine.getId()).orElseThrow();
        assertThat(after.getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(after.getAuthorName()).isEqualTo("[erased]");
        // The text stays — D24's deliberate line, and still deliberate when the caller is a service.
        assertThat(after.getBody()).contains("punctual");
    }

    @Test
    @Transactional
    @DisplayName("a fan-out token minted for one customer cannot erase another")
    void theFanOutTokenCannotEraseAnyoneElse() throws Exception {
        Review theirs = review(BYSTANDER, "b-fanout-2");

        mockMvc
            .perform(
                post(ERASE, BYSTANDER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofSeconds(30)))
            )
            .andExpect(status().isForbidden());

        assertThat(reviews.findById(theirs.getId()).orElseThrow().getCustomerLogin()).isEqualTo(BYSTANDER);
    }

    @Test
    @Transactional
    @DisplayName("a fan-out token that would outlive the request is refused")
    void aLongLivedFanOutTokenIsRefused() throws Exception {
        Review mine = review(CUSTOMER, "b-fanout-3");

        mockMvc
            .perform(
                post(ERASE, CUSTOMER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofHours(24)))
            )
            .andExpect(status().isForbidden());

        assertThat(reviews.findById(mine.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
    }

    /**
     * <strong>Where the fan-out authority must not be accepted, and the reason it has its own name.</strong>
     *
     * <p>Both halves of the verification desk, because the read is a disclosure and the write is a
     * public claim: {@code GET} would hand a service the reviewer's login and the evidence reference
     * that D16 keeps behind {@code ROLE_BROKERAGE}, and {@code POST} would let it award or withdraw a
     * badge customers use to decide who comes into their home. The {@code POST} carries a valid body
     * on purpose — method security runs after argument binding, so a malformed one would be refused
     * with 400 by the wrong mechanism and the test would prove nothing.
     */
    @Test
    @Transactional
    @DisplayName("a fan-out token cannot read or write the verification desk")
    void theFanOutTokenCannotReachTheVerificationDesk() throws Exception {
        String token = fanOutToken(CUSTOMER, Duration.ofSeconds(30));

        mockMvc.perform(get(VERIFICATION, "p1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc
            .perform(
                post(VERIFICATION, "p1")
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"VERIFIED\",\"evidenceRef\":\"forged\"}")
            )
            .andExpect(status().isForbidden());
    }
}
