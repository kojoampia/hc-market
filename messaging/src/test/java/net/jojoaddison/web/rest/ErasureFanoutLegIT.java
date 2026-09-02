package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.SubjectPseudonym;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messaging as a <em>leg</em> of booking's erasure fan-out — {@code decisions.md} D37 and D38.
 *
 * <p>The tokens here are minted with messaging's own {@code JwtEncoder}, which is not a shortcut: the
 * five hc-market services share one signing secret, so a token booking signs and a token minted here
 * are the same token as far as this service's decoder is concerned. That is the whole mechanism D37
 * chose, and standing booking up alongside this service to demonstrate it is not something one
 * repository of five standalone Maven projects can do — the same limitation D28 records, with the
 * same conclusion, which is that the wire gets checked against a running estate and the suite checks
 * the rules.
 *
 * <p>What is under test is therefore <strong>what this service will and will not accept</strong>. The
 * fan-out authority must let booking do the one thing it is for, and must be worth nothing at all for
 * anything else — including erasing a second person, which is the difference between a credential
 * scoped to a request and a standing licence.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureFanoutLegIT {

    private static final String URL = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String BYSTANDER = "kojo.stillhere";
    private static final String PRO = "akosua.mensah";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private SubjectPseudonym pseudonyms;

    /**
     * A fan-out token exactly as {@code FanoutTokenMinter} builds one in booking.
     *
     * <p>Deliberately restated here rather than shared. There is no shared library, and a test that
     * called booking's minter could not exist anyway — but more to the point, this is the assertion:
     * <em>anything holding the estate key can produce this</em>, and this service must decide what to
     * do with it on the strength of the claims alone.
     */
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

    private Conversation thread(String customer, String reference) {
        return conversations.saveAndFlush(
            new Conversation()
                .reference(reference)
                .customerLogin(customer)
                .professionalRef(PRO)
                .bookingReference("b-" + reference)
                .lastMessageAt(Instant.now())
        );
    }

    /** The leg working, which is the only thing this authority is for. */
    @Test
    @Transactional
    @DisplayName("a fan-out token erases the customer it was minted for")
    void theFanOutTokenErasesTheCustomerItNames() throws Exception {
        Conversation mine = thread(CUSTOMER, "c-fanout");

        mockMvc
            .perform(
                post(URL, CUSTOMER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofSeconds(30)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1));

        assertThat(conversations.findById(mine.getId()).orElseThrow().getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
    }

    /**
     * <strong>The narrowing that keeps this from being a standing licence.</strong>
     *
     * <p>A fan-out token names the customer it authorises, so a copy of one taken off the wire — or
     * held by a service that has been taken over — buys an erasure of the person it was already being
     * used to erase, and nothing else. Without this claim the authority would mean "erase anybody",
     * which is {@code ROLE_BROKERAGE} minus the audit trail.
     */
    @Test
    @Transactional
    @DisplayName("a fan-out token minted for one customer cannot erase another")
    void theFanOutTokenCannotEraseAnyoneElse() throws Exception {
        Conversation theirs = thread(BYSTANDER, "c-bystander");

        mockMvc
            .perform(
                post(URL, BYSTANDER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofSeconds(30)))
            )
            .andExpect(status().isForbidden());

        assertThat(conversations.findById(theirs.getId()).orElseThrow().getCustomerLogin()).isEqualTo(BYSTANDER);
    }

    /**
     * <strong>Short-lived, enforced where it can be.</strong>
     *
     * <p>The shared key means a compromised service can mint anything, and this stops none of that.
     * What it stops is the ordinary decay: a later caller reusing a user token's twenty-four hours for
     * a fan-out, which would be accepted estate-wide for a day and would look exactly like the real
     * thing. The lifetime is part of the contract, so it is checked by the side that has to live with
     * it rather than promised by the side that issues it.
     */
    @Test
    @Transactional
    @DisplayName("a fan-out token that would outlive the request is refused")
    void aLongLivedFanOutTokenIsRefused() throws Exception {
        Conversation mine = thread(CUSTOMER, "c-longlived");

        mockMvc
            .perform(
                post(URL, CUSTOMER)
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fanOutToken(CUSTOMER, Duration.ofHours(24)))
            )
            .andExpect(status().isForbidden());

        assertThat(conversations.findById(mine.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
    }

    /**
     * The authority on its own, with no token behind it, is worth nothing.
     *
     * <p>It cannot happen through the gateway — every authority in this estate arrives inside a JWT —
     * but the check fails closed rather than assuming that, because the alternative is a permissive
     * branch guarding an irreversible action, and because a test double is exactly how an authority
     * ends up in a context without a token.
     */
    @Test
    @Transactional
    @WithMockUser(username = "someone", authorities = "ROLE_CUSTOMER_ERASURE")
    @DisplayName("the fan-out authority without a fan-out token erases nobody")
    void theAuthorityAloneIsRefused() throws Exception {
        Conversation mine = thread(CUSTOMER, "c-noclaim");

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());

        assertThat(conversations.findById(mine.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
    }
}
