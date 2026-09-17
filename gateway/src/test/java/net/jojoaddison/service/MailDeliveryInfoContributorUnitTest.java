package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import tech.jhipster.config.JHipsterProperties;

/**
 * {@link MailDeliveryInfoContributor} — {@code decisions.md} D94, backlog NEW-47.
 *
 * <p>Two things are being pinned, and they pull in opposite directions: that {@code /management/info}
 * carries enough for a deploy to refuse on and an operator to diagnose with, and that it carries no
 * credential. {@code /management/info} is {@code permitAll} in the gateway's own security chain, so
 * the second one is not hygiene.
 */
class MailDeliveryInfoContributorUnitTest {

    private Info info(String host, String username) {
        MailProperties mail = new MailProperties();
        mail.setHost(host);
        mail.setPort(2525);
        mail.setUsername(username);
        mail.setPassword("hunter2");
        JHipsterProperties jhipster = new JHipsterProperties();
        jhipster.getMail().setBaseUrl("https://market.abofonsa.com");
        jhipster.getMail().setFrom("bridgecare@abofonsa.com");
        Info.Builder builder = new Info.Builder();
        new MailDeliveryInfoContributor(mail, jhipster, new AccountRetention()).contribute(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mailDetails(Info info) {
        return (Map<String, Object>) info.getDetails().get("mail");
    }

    @Test
    void aConfiguredEstateReportsWhereMailGoes() {
        Map<String, Object> mail = mailDetails(info("smtp.example.net", "postmaster@abofonsa.com"));
        assertThat(mail.get("configured")).isEqualTo(true);
        assertThat(mail.get("host")).isEqualTo("smtp.example.net");
        assertThat(mail.get("port")).isEqualTo(2525);
        assertThat(mail.get("from")).isEqualTo("bridgecare@abofonsa.com");
        assertThat(mail.get("baseUrl")).isEqualTo("https://market.abofonsa.com");
        assertThat(mail.get("authenticated")).isEqualTo(true);
    }

    @Test
    void anUnconfiguredEstateSaysSoInOneField() {
        // The single fact deploy-prod.sh's smoke test reads. It must be false rather than absent: a
        // missing key and a false key are one jq expression apart and only one of them is a refusal.
        Map<String, Object> mail = mailDetails(info("  ", null));
        assertThat(mail.get("configured")).isEqualTo(false);
        assertThat(mail.get("authenticated")).isEqualTo(false);
    }

    @Test
    void theCredentialIsNeverPublished() {
        // Not "the password is not under mail.password" — the whole rendered document, because the
        // interesting regression is somebody adding the username "for diagnostics" under any key.
        String rendered = info("smtp.example.net", "postmaster@abofonsa.com").getDetails().toString();
        assertThat(rendered).doesNotContain("hunter2");
        assertThat(rendered).doesNotContain("postmaster@abofonsa.com");
    }

    @Test
    void theRetentionWindowIsReportedBesideIt() {
        // Before D94 this number was a literal in a generated file that no endpoint, log line or
        // document reported. Its being readable is part of the answer to D91.
        @SuppressWarnings("unchecked")
        Map<String, Object> accounts = (Map<String, Object>) info("smtp.example.net", null).getDetails().get("accounts");
        assertThat(accounts.get("unactivatedRetentionDays")).isEqualTo(3L);
        assertThat(accounts.get("unactivatedSweepCron")).isEqualTo("0 0 1 * * ?");
    }
}
