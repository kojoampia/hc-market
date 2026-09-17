package net.jojoaddison.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterProperties;

/**
 * Where this estate's mail goes, and how long an unactivated account lives — readable at
 * {@code GET /management/info} under {@code mail} and {@code accounts}, which is what
 * {@code deploy-prod.sh}'s smoke test asks for and what an operator reads when a customer says no mail
 * arrived. {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>Why an info contributor and not a health indicator</h2>
 *
 * <p>Same argument as payout's {@link BrokerageTermsInfoContributor} (D57), one service along and with
 * a sharper edge: the obvious way to make "mail works" visible is
 * {@code management.health.mail.enabled: true}, which JHipster generates as {@code false}. Turning it
 * on puts an SMTP connection attempt inside the aggregate {@code /management/health} — which is
 * exactly what both compose healthchecks grep for {@code UP} and what docker decides a container's
 * health from. An unreachable relay would then take the gateway unhealthy, and
 * {@code deploy-prod.sh}'s health gate would roll a perfectly good deployment back over somebody
 * else's outage. A deploy that <em>refuses</em> because mail is unconfigured is the right failure; an
 * estate that goes down because a mail server blinked is not.
 *
 * <p>So this reports <em>configuration</em>, which is a fact this process holds, rather than
 * <em>reachability</em>, which is a question about the network. {@link MailDeliveryGuard} refuses the
 * configurations that cannot work; this makes the one in force visible; and neither opens a socket.
 *
 * <h2>What it publishes, and what it must never publish</h2>
 *
 * <p>The host, the port, the {@code from} address, the base-url, and {@code authenticated} —
 * <strong>a boolean, never the username and never the password</strong>. A password in
 * {@code /management/info} is a credential published on an endpoint that is {@code permitAll} in the
 * gateway's own security chain; the username is one half of that credential and is not needed to
 * diagnose anything. The host, port and base-url are not secrets: the base-url appears in every mail
 * this estate sends, and the relay's name is in the deploy's own configuration.
 *
 * <p>{@code /management} is {@code return 404} at both edges ({@code prod-server/hc-market-app.conf}
 * and {@code quality/host-site.conf} narrow it), and in production the gateway publishes on
 * {@code 127.0.0.1} only — so in practice this is readable from the host rather than from the
 * internet. That is a second line, not the argument: the argument is that nothing here is a secret.
 *
 * <p>{@code accounts.unactivatedRetentionDays} is here for the same reason the mail block is: it is
 * the estate's answer to "how long do you keep a part-finished registration", it is a number
 * {@code docs/privacy-notice.md} §7.1 states to a data subject, and before D94 it was a literal in a
 * generated file that no endpoint, log line or document reported.
 */
@Component
public class MailDeliveryInfoContributor implements InfoContributor {

    private final MailProperties mailProperties;

    private final JHipsterProperties jHipsterProperties;

    private final AccountRetention retention;

    public MailDeliveryInfoContributor(MailProperties mailProperties, JHipsterProperties jHipsterProperties, AccountRetention retention) {
        this.mailProperties = mailProperties;
        this.jHipsterProperties = jHipsterProperties;
        this.retention = retention;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("mail", mail());
        builder.withDetail("accounts", accounts());
    }

    private Map<String, Object> mail() {
        Map<String, Object> details = new LinkedHashMap<>();
        String host = mailProperties.getHost() == null ? "" : mailProperties.getHost().trim();
        // `configured` is the single fact the smoke test reads. False means every activation and
        // password-reset mail this estate composes is discarded with one WARN line, which is the whole
        // of NEW-47 — so a deploy may refuse on this alone, without parsing a host out of a string.
        details.put("configured", !host.isEmpty());
        details.put("host", host);
        details.put("port", mailProperties.getPort());
        details.put("from", jHipsterProperties.getMail().getFrom());
        details.put("baseUrl", jHipsterProperties.getMail().getBaseUrl());
        String username = mailProperties.getUsername();
        details.put("authenticated", username != null && !username.isBlank());
        return details;
    }

    private Map<String, Object> accounts() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("unactivatedRetentionDays", retention.unactivatedRetention().toDays());
        details.put("unactivatedSweepCron", retention.unactivatedSweepCron());
        return details;
    }
}
