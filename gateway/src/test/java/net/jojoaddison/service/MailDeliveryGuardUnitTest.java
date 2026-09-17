package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

/**
 * {@link MailDeliveryGuard} — {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>Every case has a positive control beside it</h2>
 *
 * <p>A guard is two claims — it refuses what it should and it accepts what it should — and only the
 * second one catches a guard that refuses everything. {@link #aConfiguredProductionEstateStarts()} is
 * that control, and it is the case that would go red on a guard tightened until it is useless.
 *
 * <p>No mocks. {@code MailProperties}, {@code JHipsterProperties} and {@code MockEnvironment} are all
 * constructible, so these cases are the real objects with real values in them — which matters here
 * because the thing under test is a value's <em>shape</em>, and a mock returning what the test told it
 * to return proves nothing about how the property binds.
 */
class MailDeliveryGuardUnitTest {

    private static final String GOOD_BASE_URL = "https://market.abofonsa.com";

    private MailDeliveryGuard guard(String profile, String host, String baseUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        MailProperties mail = new MailProperties();
        mail.setHost(host);
        JHipsterProperties jhipster = new JHipsterProperties();
        jhipster.getMail().setBaseUrl(baseUrl);
        return new MailDeliveryGuard(environment, mail, jhipster);
    }

    // --- production refuses a configuration that mails nowhere -------------------------------------

    @Test
    void productionWithNoMailHostIsRefused() {
        assertThatThrownBy(() -> guard("prod", null, GOOD_BASE_URL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("spring.mail.host")
            .hasMessageContaining("SPRING_MAIL_HOST");
    }

    @Test
    void productionWithAnEmptyMailHostIsRefused() {
        // The case no `:?` and no placeholder can see: SPRING_MAIL_HOST= is a value that is present.
        assertThatThrownBy(() -> guard("prod", "   ", GOOD_BASE_URL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRelayingThroughLoopbackIsRefused() {
        // `localhost` is the committed generator default that reached application-prod.yml. Inside a
        // container it is the container's own loopback.
        assertThatThrownBy(() -> guard("prod", "localhost", GOOD_BASE_URL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("loopback");
        assertThatThrownBy(() -> guard("prod", "127.0.0.1", GOOD_BASE_URL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionWithNoBaseUrlIsRefused() {
        assertThatThrownBy(() -> guard("prod", "smtp.example.net", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("jhipster.mail.base-url")
            .hasMessageContaining("JHIPSTER_MAIL_BASE_URL");
    }

    // --- the base-url is checked on EVERY profile ---------------------------------------------------

    @Test
    void theGeneratorsPlaceholderIsRefusedEvenInDev() {
        // The literal application-prod.yml shipped with. It is never right anywhere, and a mail
        // carrying it is DELIVERED — so no estate should be allowed to send one.
        assertThatThrownBy(() -> guard("dev", "localhost", MailDeliveryGuard.GENERATOR_PLACEHOLDER_BASE_URL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-server-url-to-change");
    }

    @Test
    void aBaseUrlWithNoSchemeIsRefused() {
        assertThatThrownBy(() -> guard("dev", "localhost", "market.abofonsa.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("absolute http or https URL");
    }

    @Test
    void aBaseUrlWithATrailingSlashIsRefused() {
        assertThatThrownBy(() -> guard("dev", "localhost", "https://market.abofonsa.com/"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trailing slash");
    }

    @Test
    void aBaseUrlThatIsNotAUrlAtAllIsRefused() {
        assertThatThrownBy(() -> guard("dev", "localhost", "http://:::nonsense")).isInstanceOf(IllegalStateException.class);
    }

    // --- and the controls: what must still start ---------------------------------------------------

    @Test
    void aConfiguredProductionEstateStarts() {
        assertThatCode(() -> guard("prod", "smtp.example.net", GOOD_BASE_URL)).doesNotThrowAnyException();
    }

    @Test
    void aDevEstateWithNoCatcherStartsAndIsNotRefused() {
        // Deliberate: `./mvnw` with nothing listening on 1025 is a working developer configuration.
        // It warns — the WARN is the only thing between a developer and "registration answers 201 and
        // no mail arrives" — but refusing it would make the estate unrunnable locally.
        assertThatCode(() -> guard("dev", "", "http://127.0.0.1:8080")).doesNotThrowAnyException();
    }

    @Test
    void aDevEstateOnLoopbackStarts() {
        // The mirror of productionRelayingThroughLoopbackIsRefused: the same value, the other profile.
        // Without this case the loopback rule could be tightened to every profile and nothing would
        // notice until a developer could not start the gateway.
        assertThatCode(() -> guard("dev", "localhost", "http://127.0.0.1:8080")).doesNotThrowAnyException();
    }
}
