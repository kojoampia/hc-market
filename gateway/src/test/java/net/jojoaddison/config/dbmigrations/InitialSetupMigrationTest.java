package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jojoaddison.domain.Authority;
import net.jojoaddison.domain.User;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * What the gateway puts in an empty {@code jhi_user} collection, per profile — {@code decisions.md} D61.
 *
 * <p>Until 2026-09-09 the answer was {@code admin} with a bcrypt hash of the string {@code admin},
 * committed in a public repository, in every profile including {@code prod}. The first assertion
 * below is that defect stated directly: it was red against the generated changeunit, and the value
 * it caught was the login itself.
 *
 * <p>A mocked {@link MongoTemplate} rather than a Spring context, so this stays a unit test. What is
 * under test is a decision about profiles and a configured value, and neither needs a database to
 * be wrong.
 */
class InitialSetupMigrationTest {

    private static final String PUBLISHED_DEFAULT = "admin";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final List<Object> saved = new ArrayList<>();
    private final Set<String> existingLogins = new HashSet<>();
    private final Set<String> existingAuthorities = new HashSet<>();

    /**
     * A template that answers from {@link #existingLogins} / {@link #existingAuthorities} and records
     * everything written, so "what did this boot actually change" is a list rather than an inference.
     */
    private MongoTemplate template() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(template.exists(any(Query.class), eq(User.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Object login = query.getQueryObject().get("login");
            return existingLogins.contains(String.valueOf(login));
        });
        when(template.findById(any(), eq(Authority.class))).thenAnswer(invocation -> {
            // Typed as Object deliberately: getArgument is generic, so an inline String.valueOf(...)
            // infers char[] and the stub dies on a ClassCastException that reads as a Mongo problem.
            Object id = invocation.getArgument(0);
            String name = String.valueOf(id);
            if (!existingAuthorities.contains(name)) {
                return null;
            }
            Authority authority = new Authority();
            authority.setName(name);
            return authority;
        });
        return template;
    }

    private InitialSetupMigration migration(String activeProfile, String configuredAdminPassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return new InitialSetupMigration(template(), passwordEncoder, configuredAdminPassword, environment);
    }

    private Optional<User> savedUser(String login) {
        return saved.stream().filter(User.class::isInstance).map(User.class::cast).filter(u -> login.equals(u.getLogin())).findFirst();
    }

    private List<String> savedAuthorityNames() {
        return saved.stream().filter(Authority.class::isInstance).map(Authority.class::cast).map(Authority::getName).toList();
    }

    // --- prod, the case the item exists for -----------------------------------------------------

    @Test
    @DisplayName("prod with a configured password: the administrator is created with it, and nothing else is created")
    void prodSeedsOnlyTheConfiguredAdministrator() {
        migration("prod", "a-real-secret-from-secrets-env").run(null);

        User admin = savedUser("admin").orElseThrow();
        assertThat(passwordEncoder.matches("a-real-secret-from-secrets-env", admin.getPassword()))
            .as("the administrator must be created with the configured password")
            .isTrue();
        assertThat(passwordEncoder.matches(PUBLISHED_DEFAULT, admin.getPassword()))
            .as("the administrator's password must not be a value anybody can read out of this repository")
            .isFalse();
        assertThat(admin.getAuthorities()).extracting(Authority::getName).contains(AuthoritiesConstants.ADMIN);
        assertThat(admin.isActivated()).isTrue();

        assertThat(savedUser("user")).as("the demo account has no business on a production estate").isEmpty();
    }

    @Test
    @DisplayName("prod with no configured password: refuses to start rather than publishing a known credential")
    void prodWithoutAConfiguredPasswordRefusesToStart() {
        assertThatThrownBy(() -> migration("prod", "").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HC_GATEWAY_ADMIN_PASSWORD");

        assertThat(savedUser("admin")).as("no administrator may be written when the refusal fires").isEmpty();
        assertThat(savedUser("user")).isEmpty();
    }

    @Test
    @DisplayName("prod with no configured password, on an estate that already has an administrator: deploys fine")
    void prodWithoutAConfiguredPasswordIsSilentOnceAnAdministratorExists() {
        existingLogins.add("admin");

        assertThatCode(() -> migration("prod", "").run(null))
            .as("the variable is only consulted for a database with no administrator — an estate whose admin has rotated their password must keep deploying")
            .doesNotThrowAnyException();

        assertThat(saved).filteredOn(User.class::isInstance).isEmpty();
    }

    // --- dev and test, which keep their accounts ------------------------------------------------

    @Test
    @DisplayName("dev seeds both accounts, and neither password is the login")
    void devSeedsTheDemoAccounts() {
        migration("dev", "").run(null);

        User admin = savedUser("admin").orElseThrow();
        User user = savedUser("user").orElseThrow();
        assertThat(passwordEncoder.matches("Admin@1234", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("User@123", user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(PUBLISHED_DEFAULT, admin.getPassword()))
            .as("not even a dev estate should carry the generator's published hash")
            .isFalse();
        assertThat(user.getAuthorities()).extracting(Authority::getName).containsExactly(AuthoritiesConstants.USER);
    }

    @Test
    @DisplayName("test seeds both accounts too — the decision is 'not prod', never an allow-list of profile names")
    void testProfileSeedsTheDemoAccounts() {
        migration("test", "").run(null);

        assertThat(savedUser("admin")).isPresent();
        assertThat(savedUser("user")).isPresent();
    }

    @Test
    @DisplayName("a profile nobody anticipated still seeds, because only prod is excluded")
    void anUnanticipatedProfileIsNotProduction() {
        migration("secret-samples", "").run(null);

        assertThat(savedUser("admin")).isPresent();
        assertThat(savedUser("user")).isPresent();
    }

    // --- the second boot ------------------------------------------------------------------------

    @Test
    @DisplayName("a second boot writes nothing: no duplicate, no re-encoding, no re-announcement")
    void aSecondBootChangesNothing() {
        existingLogins.add("admin");
        existingLogins.add("user");
        existingAuthorities.add(AuthoritiesConstants.USER);
        existingAuthorities.add(AuthoritiesConstants.ADMIN);

        migration("dev", "").run(null);

        assertThat(saved).as("an existing account is never rebuilt — a rotated password must survive a restart").isEmpty();
    }

    @Test
    @DisplayName("both authorities are ensured in every profile, whether or not an account introduces them")
    void authoritiesAreSeededInEveryProfile() {
        migration("prod", "a-real-secret-from-secrets-env").run(null);

        assertThat(savedAuthorityNames()).containsExactlyInAnyOrder(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN);
    }
}
