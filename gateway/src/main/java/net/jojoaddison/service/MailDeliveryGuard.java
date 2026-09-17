package net.jojoaddison.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

/**
 * Refuses a production estate whose activation and password-reset mail would go nowhere, or whose
 * links would point at the wrong host — {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>Every part of it was generated code doing exactly what it was generated to do.
 * {@code POST /api/register} is {@code permitAll}; {@code UserService.registerUser} writes the account
 * with {@code activated = false} and hands off to {@code MailService} <em>without waiting</em>, so the
 * response is {@code 201 CREATED} whatever happens next; {@code MailService} catches
 * {@code MailException | MessagingException} and logs {@code LOG.warn}; {@code spring.mail.host} was
 * {@code localhost:25} in both {@code application-dev.yml} and {@code application-prod.yml}, no compose
 * file in any of the three environments passed a single {@code SPRING_MAIL_*}, and production's
 * {@code jhipster.mail.base-url} was the literal generator placeholder
 * {@code http://my-server-url-to-change}. Then the sweep this package also rewrote deleted the account
 * three days later.
 *
 * <p><strong>201, no mail, cannot authenticate, deleted in three days, and one WARN line.</strong> Not
 * one test in the estate was red: {@code MailServiceIT} mocks {@code JavaMailSender} with
 * {@code @MockitoBean}, so nothing here has ever sent a message.
 *
 * <h2>What it refuses, and why each one is not caught by anything else</h2>
 *
 * <p>Three of these are {@code :?} variables in {@code docker-compose.prod.yml} and unresolvable
 * placeholders in {@code application-prod.yml}, so a containerised production estate never reaches
 * this class at all — compose refuses to interpolate and, failing that, Spring fails to resolve
 * {@code ${SPRING_MAIL_HOST}} while evaluating the mail autoconfiguration's condition, naming the
 * variable. This class is what catches the cases those two cannot see:
 *
 * <ul>
 *   <li><strong>A value present and empty.</strong> {@code SPRING_MAIL_HOST=} satisfies a placeholder
 *       and every {@code :?} check, and means nothing. Blank is treated as absent everywhere in this
 *       estate (D45, D57) and here it is a refusal rather than a default.</li>
 *   <li><strong>{@code localhost} under {@code prod}.</strong> This is the committed default that
 *       reached the production profile, and inside a container it is the container's own loopback,
 *       where no SMTP server has ever listened. An estate really relaying through an MTA on the host
 *       names it — a compose service name, a hostname, or the host's gateway address — so refusing
 *       loopback costs a configuration nobody runs and closes the one value that looks configured and
 *       mails nowhere.</li>
 *   <li><strong>The generator's placeholder base-url, on every profile.</strong>
 *       {@code http://my-server-url-to-change} is never right anywhere, and it is the value production
 *       shipped with.</li>
 *   <li><strong>A base-url that is not an absolute URL.</strong> {@code market.abofonsa.com} with no
 *       scheme, or a trailing slash away from a doubled path, produces a mail whose activation link is
 *       dead — and the mail is sent successfully, so nothing anywhere goes red. This is the one value
 *       that fails silently <em>after</em> delivery works.</li>
 * </ul>
 *
 * <h2>What it deliberately does NOT do</h2>
 *
 * <p><strong>It does not open a connection.</strong> {@code JavaMailSenderImpl.testConnection()} would
 * prove more, and it would make SMTP a hard startup dependency of the estate's front door: a provider
 * blip would then refuse a deploy that was otherwise fine, and {@code deploy-prod.sh}'s health gate
 * would roll it back. That is D57's argument about the Kafka binder indicator, one service along, and
 * it is also why {@code management.health.mail.enabled} stays {@code false} — a mail health indicator
 * puts SMTP inside the aggregate {@code /management/health} that the compose healthcheck greps for
 * {@code UP}, so an unreachable relay would take the gateway <em>unhealthy</em> and revert a healthy
 * deployment. What replaces it is {@link MailDeliveryInfoContributor}: the configuration is readable
 * at {@code /management/info}, where a deploy's smoke test can require it and an operator can see
 * where mail is pointed, and where being wrong costs a refusal rather than an outage.
 *
 * <p><strong>It does not change what happens when a send fails.</strong> A registration whose mail
 * fails is still {@code 201} and still one WARN line. Making the response depend on delivery is a
 * different decision with a worse failure mode — a registration rolled back because a relay was slow —
 * and D94 §5 surfaces it as a question rather than taking it.
 *
 * <p>It is a constructor-time check on a {@code @Component}, so the refusal fails the context. The
 * message names the variable and the remedy, because the operator reading it is deploying.
 */
@Component
public class MailDeliveryGuard {

    private static final Logger LOG = LoggerFactory.getLogger(MailDeliveryGuard.class);

    /**
     * JHipster's own placeholder, which {@code application-prod.yml} carried until D94. The only
     * occurrence of the string in any {@code .yml} in this repository was that one.
     */
    public static final String GENERATOR_PLACEHOLDER_BASE_URL = "http://my-server-url-to-change";

    public MailDeliveryGuard(Environment environment, MailProperties mailProperties, JHipsterProperties jHipsterProperties) {
        boolean production = environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_PRODUCTION));
        String host = trimmed(mailProperties.getHost());
        String baseUrl = trimmed(jHipsterProperties.getMail().getBaseUrl());

        checkBaseUrl(baseUrl, production);
        checkHost(host, production);

        if (!host.isEmpty()) {
            // "ADDRESSED", NOT "WORKING", AND THE WORDING IS THE POINT OF THE LINE. Nothing here opens
            // a socket (see the class javadoc), so a developer estate whose catcher is not running
            // printed the same sentence as a production estate whose relay answers — and the symptom
            // of a dead catcher is then back to one WARN per send, which is the silence this package
            // exists to remove. Found at review, on the walk's own log line.
            //
            // So it says what it knows: where mail is addressed, that nothing has been contacted, and
            // — off production, where a catcher is what this points at — where to look first. An INFO
            // that reads as a guarantee is worse than no INFO.
            LOG.info(
                "Mail is ADDRESSED to {}:{} as '{}', with links under {} — nothing has contacted that relay, " +
                    "and a send that fails is one WARN line{} (decisions.md D94)",
                host,
                mailProperties.getPort(),
                jHipsterProperties.getMail().getFrom(),
                baseUrl.isEmpty() ? "<no base-url: links will be relative and will not work>" : baseUrl,
                production ? "" : ". On this estate that is a local mail catcher: check it is up before concluding registration is broken"
            );
        }
    }

    /**
     * The base-url is checked on every profile, unlike the host: the placeholder and a
     * non-absolute URL are wrong everywhere, and on a developer estate a dead link in a mail that
     * arrives is exactly as invisible as it is in production.
     */
    private static void checkBaseUrl(String baseUrl, boolean production) {
        if (GENERATOR_PLACEHOLDER_BASE_URL.equals(baseUrl)) {
            throw refusal(
                "jhipster.mail.base-url",
                baseUrl,
                "that is JHipster's placeholder and it is the value application-prod.yml shipped with. " +
                    "Every activation and password-reset link this estate sends would point at a host that " +
                    "does not exist, and the mail would be delivered successfully"
            );
        }
        if (baseUrl.isEmpty()) {
            if (production) {
                throw refusal(
                    "jhipster.mail.base-url",
                    "",
                    "a production estate must state the origin its activation and password-reset links " +
                        "point at. Absent, the links are relative, the mail is still delivered, and nobody " +
                        "can act on it. Set JHIPSTER_MAIL_BASE_URL in secrets.env"
                );
            }
            return;
        }
        URI parsed;
        try {
            parsed = new URI(baseUrl);
        } catch (URISyntaxException notAUri) {
            throw refusal("jhipster.mail.base-url", baseUrl, "it is not a URL");
        }
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || parsed.getHost() == null) {
            throw refusal(
                "jhipster.mail.base-url",
                baseUrl,
                "it must be an absolute http or https URL with a host — a value like " +
                    "'market.abofonsa.com' with no scheme produces a mail that is delivered and whose link " +
                    "is dead, which nothing anywhere reports"
            );
        }
        if (baseUrl.endsWith("/")) {
            throw refusal(
                "jhipster.mail.base-url",
                baseUrl,
                "it must not end in a slash: the mail templates compose '${baseUrl}/account/activate', " +
                    "so a trailing slash sends a link with a doubled separator"
            );
        }
    }

    /**
     * The host is only refused under {@code prod}. A developer running {@code ./mvnw} with no catcher
     * is a working configuration and must not be refused — it just has to be told, because the
     * symptom otherwise is a registration that answers 201 and a mail nobody receives.
     */
    private static void checkHost(String host, boolean production) {
        if (host.isEmpty()) {
            if (production) {
                throw refusal(
                    "spring.mail.host",
                    "",
                    "a production estate cannot register a customer without one: the account is created " +
                        "unactivated, the response is still 201, the mail failure is one WARN line, and the " +
                        "account is deleted by the sweep some days later. Set SPRING_MAIL_HOST and " +
                        "SPRING_MAIL_PORT in secrets.env"
                );
            }
            LOG.warn(
                "No spring.mail.host is set, so NO MAIL LEAVES THIS ESTATE. Registration still answers 201 and " +
                    "the account is created unactivated, so nobody registered here can log in, and the " +
                    "unactivated-account sweep removes them after the configured window (decisions.md D94). " +
                    "Dev and quality pass a local mail catcher — start it with the stack rather than on its own"
            );
            return;
        }
        if (production && isLoopback(host)) {
            throw refusal(
                "spring.mail.host",
                host,
                "inside a container that is the container's own loopback, where no SMTP server has ever " +
                    "listened — it is the committed generator default that reached the production profile, " +
                    "and it is the difference between 'mail is configured' and 'mail goes nowhere'. Name the " +
                    "relay: a compose service name, a provider's hostname, or the host's gateway address"
            );
        }
    }

    private static boolean isLoopback(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(lower) || "127.0.0.1".equals(lower) || "::1".equals(lower) || "[::1]".equals(lower);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static IllegalStateException refusal(String property, String value, String why) {
        return new IllegalStateException(
            (
                "%s is '%s' and %s. The gateway refuses to start rather than accept a front door that " +
                "swallows people — see decisions.md D94 and backlog NEW-47"
            ).formatted(property, value, why)
        );
    }
}
