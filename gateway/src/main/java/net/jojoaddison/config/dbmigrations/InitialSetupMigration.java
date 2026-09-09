package net.jojoaddison.config.dbmigrations;

import java.time.Instant;
import java.util.function.Supplier;
import net.jojoaddison.config.Constants;
import net.jojoaddison.domain.Authority;
import net.jojoaddison.domain.User;
import net.jojoaddison.security.AuthoritiesConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tech.jhipster.config.JHipsterConstants;

/**
 * Seeds the authorities and the first accounts when they are missing — {@code decisions.md} D61.
 *
 * <p>This was the untouched generated Mongock changeunit until 2026-09-09, and what it did was
 * create {@code admin} and {@code user} with JHipster's published bcrypt hashes, activated, in
 * <strong>every</strong> profile. {@code mongock.migration-scan-package} is declared in the base
 * {@code application.yml} with no {@code prod} override, so nothing excluded it from production, and
 * a first production deploy creates exactly the empty database it runs against. The gateway issues
 * the estate's tokens and {@code /api/admin/**} is {@code hasAuthority(ADMIN)}, so that account was
 * `ROLE_ADMIN` on a public host with a password anybody could read out of a public repository.
 *
 * <p>It is an {@link ApplicationRunner} rather than a {@code @ChangeUnit} for two reasons, and both
 * are load-bearing. A changeunit is not a Spring bean: it cannot be given an {@link Environment}, a
 * {@code @Value} or a {@link PasswordEncoder}, so there is nowhere to put the profile decision or
 * the configured password. And Mongock records a changeunit as executed and never runs it again,
 * which makes "seed whatever is still missing" impossible to express — an authority added later
 * would never appear on a database that had already run version 001. The siblings reached the same
 * shape independently: {@code hc-professional/gateway} is the file this one follows.
 *
 * <p><strong>Idempotent, and it has to stay that way.</strong> It runs on every start, so anything
 * it does destructively it does to live data. {@code saveUserIfMissing} seeds what is absent and
 * touches nothing that exists, so an administrator who has rotated their password through the UI
 * keeps it across restarts and across deploys.
 */
@Component
public class InitialSetupMigration implements ApplicationRunner {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InitialSetupMigration.class);

    private static final String ADMIN_LOGIN = "admin";
    private static final String USER_LOGIN = "user";

    private final MongoTemplate template;
    private final PasswordEncoder passwordEncoder;

    /**
     * The administrator's password for a database that has none yet, from {@code gateway.admin-password}
     * — {@code HC_GATEWAY_ADMIN_PASSWORD} in the production compose file.
     *
     * <p>It applies only when the account does not already exist. Changing it later does not reset a
     * password that has since been rotated, which is why it is safe to leave the same value in
     * {@code secrets.env} for the life of the host.
     */
    private final String configuredAdminPassword;

    /**
     * Whether to seed the demo accounts — today just {@code user}.
     *
     * <p>Its password is derived from its own login by a rule written a few lines below, in a public
     * repository, so it has no business on a deployment anybody else can reach. It is useful for
     * local work and for exercising the {@code ROLE_USER} path, so it is seeded everywhere except
     * production.
     *
     * <p>The predicate is <em>not production</em> rather than an allow-list of {@code dev} and
     * {@code test}. An allow-list is the trap this repository has already recorded once: a
     * {@code spring.profiles.group} member is active without appearing in
     * {@code SPRING_PROFILES_ACTIVE} — the quality gateway reports {@code secret-samples, kafka,
     * api-docs, dev, test} for an environment that sets only {@code dev,test} — so a list of profile
     * names is a list somebody will one day be surprised by. There is exactly one profile whose
     * answer matters here, and it is the one being excluded.
     */
    private final boolean seedDemoAccounts;

    public InitialSetupMigration(
        MongoTemplate template,
        PasswordEncoder passwordEncoder,
        @Value("${gateway.admin-password:}") String configuredAdminPassword,
        Environment environment
    ) {
        this.template = template;
        this.passwordEncoder = passwordEncoder;
        this.configuredAdminPassword = configuredAdminPassword;
        this.seedDemoAccounts = !environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_PRODUCTION));
    }

    @Override
    public void run(ApplicationArguments args) {
        // Authorities first, and unconditionally. They are not credentials, every profile needs both,
        // and a role must go on existing whether or not the account that would once have introduced
        // it is still seeded here.
        Authority userAuthority = saveAuthorityIfMissing(createAuthority(AuthoritiesConstants.USER));
        Authority adminAuthority = saveAuthorityIfMissing(createAuthority(AuthoritiesConstants.ADMIN));

        // The administrator is seeded in every profile, production included: on an empty production
        // database it is the only way in. Skipping it there — which is hc-admin's answer to this same
        // defect — leaves a fresh estate with no operator account at all, and that is a different
        // failure rather than a fix (decisions.md D61).
        //
        // A supplier, not a value: the account is only built when it is actually missing. That is
        // what makes the refusal below a refusal to CREATE a well-known credential rather than a
        // refusal to start, so an estate whose administrator already exists and has rotated their
        // password keeps deploying without the variable being present at all.
        saveUserIfMissing(ADMIN_LOGIN, () -> createAdmin(adminAuthority, userAuthority));

        if (!seedDemoAccounts) {
            logger.info("Demo accounts not seeded under the prod profile — their passwords are derived from their logins");
            return;
        }

        saveUserIfMissing(USER_LOGIN, () -> createUser(userAuthority));
    }

    private Authority createAuthority(String authority) {
        Authority created = new Authority();
        created.setName(authority);
        return created;
    }

    private Authority saveAuthorityIfMissing(Authority authority) {
        Authority existing = template.findById(authority.getName(), Authority.class);
        if (existing != null) {
            return existing;
        }
        return template.save(authority);
    }

    /**
     * Seeds {@code login} only when no such account exists. The factory is not invoked otherwise, so
     * an existing account is never rebuilt, re-encoded, or announced in the log — a boot that finds
     * everything present says nothing, which is what makes a line from this class worth reading.
     */
    private void saveUserIfMissing(String login, Supplier<User> factory) {
        if (template.exists(Query.query(Criteria.where("login").is(login)), User.class)) {
            logger.debug("Account {} already exists — left unchanged", login);
            return;
        }
        template.save(factory.get());
    }

    /**
     * {@code user} -> {@code User@123}. Public by construction: the rule is right here, in a public
     * repository, which is exactly why nothing derived this way may exist under {@code prod}.
     */
    private String derivedPassword(String login) {
        StringBuilder password = new StringBuilder(Character.toUpperCase(login.charAt(0)) + login.substring(1) + "@");
        for (int i = 1; i < login.length(); i++) {
            password.append(i);
        }
        return password.toString();
    }

    private User createAdmin(Authority adminAuthority, Authority userAuthority) {
        User adminUser = new User();
        adminUser.setId("user-1");
        adminUser.setLogin(ADMIN_LOGIN);
        adminUser.setPassword(passwordEncoder.encode(adminPassword()));
        adminUser.setFirstName("admin");
        adminUser.setLastName("Administrator");
        adminUser.setEmail("admin@localhost");
        adminUser.setActivated(true);
        adminUser.setLangKey("en");
        adminUser.setCreatedBy(Constants.SYSTEM);
        adminUser.setCreatedDate(Instant.now());
        adminUser.getAuthorities().add(adminAuthority);
        adminUser.getAuthorities().add(userAuthority);
        return adminUser;
    }

    /**
     * The administrator's password, or a refusal to start.
     *
     * <p>Under {@code prod} with nothing configured this throws, and the estate does not come up.
     * That is a deliberate departure from {@code hc-professional}, which falls back to the derived
     * value with a warning; a warning in a log nobody is reading at 02:00 is not a control, and the
     * fallback it guards is a credential this repository publishes. It follows D35 and D45 instead —
     * the rule this estate already applies to {@code JWT_BASE64_SECRET} and {@code HC_PRIVACY_PEPPER}
     * — that a required secret with no safe default is refused rather than defaulted. A production
     * deploy that trips this is rolled back by {@code deploy-prod.sh}'s health gate (D49), so the
     * failure mode is a deploy that does not land, not an estate that is down.
     *
     * <p>The value itself is never logged. These logs are shipped off the host, and a password in
     * them outlives the terminal it was printed to.
     */
    private String adminPassword() {
        if (StringUtils.hasText(configuredAdminPassword)) {
            logger.info("Creating admin with login: {} and the configured gateway.admin-password", ADMIN_LOGIN);
            return configuredAdminPassword;
        }
        if (!seedDemoAccounts) {
            throw new IllegalStateException(
                "No administrator exists and gateway.admin-password is not set. Refusing to create '" +
                ADMIN_LOGIN +
                "' with a password derived from its login, because that value is published in this repository. " +
                "Set HC_GATEWAY_ADMIN_PASSWORD in secrets.env on the host — see decisions.md D61. " +
                "This is only asked of a database that has no administrator yet; once one exists, and however " +
                "its password is later rotated, this variable is no longer consulted."
            );
        }
        logger.warn(
            "Creating admin with login: {} and a password derived from it — this value is public. " +
            "It is only reachable outside prod; set gateway.admin-password for anything else.",
            ADMIN_LOGIN
        );
        return derivedPassword(ADMIN_LOGIN);
    }

    private User createUser(Authority userAuthority) {
        User userUser = new User();
        userUser.setId("user-2");
        userUser.setLogin(USER_LOGIN);
        userUser.setPassword(passwordEncoder.encode(derivedPassword(USER_LOGIN)));
        userUser.setFirstName("User");
        userUser.setLastName("User");
        userUser.setEmail("user@localhost");
        userUser.setActivated(true);
        userUser.setLangKey("en");
        userUser.setCreatedBy(Constants.SYSTEM);
        userUser.setCreatedDate(Instant.now());
        userUser.getAuthorities().add(userAuthority);
        return userUser;
    }
}
