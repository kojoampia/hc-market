package net.jojoaddison.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Retention policy — {@code decisions.md} D24/D31.
 *
 * <h2>There is deliberately no default</h2>
 *
 * <p>{@code retentionDays} is {@code null} unless somebody sets it, and that is the whole point. A
 * default here would be a legal position taken by whoever typed it — "we keep booking records for
 * N days" is a claim about Ghanaian data protection law and about the retention obligations that sit
 * on financial records, and neither is a developer's to invent. A plausible-looking {@code 365}
 * would be worse than nothing, because it would stop anyone asking.
 *
 * <p>So the code supplies the mechanism and counsel supplies the number. Until it is set, the
 * estate's honest answer to "what is your retention period" is "none has been set", which is what
 * {@code GET /api/desk/privacy} reports and what this logs at startup.
 *
 * <h2>Nothing enforces it yet, and that is not an oversight to fix here</h2>
 *
 * <p>Enforcing a retention period means a scheduled sweep, and there is no scheduler anywhere in this
 * estate — the same gap {@code Dispute.dueBy} records. When one exists, it calls
 * {@code ErasureWorkflow.eraseCustomer} on everything past the window; the erasure semantics are
 * already decided and tested, so what is missing is the trigger and nothing else.
 *
 * <p>{@code @Component}-annotated rather than listed on the generated application class, so a
 * regeneration leaves it alone. Same reason as {@code SeedProperties}.
 *
 * <p>In {@code service} rather than {@code config} because {@code TechnicalStructureTest} lets no
 * layer reach {@code config} at all, and {@code PrivacyResource} has to read it. The generated
 * ArchUnit rule is right about the general case — configuration should not be a dependency of
 * request handling — and a properties holder is the exception it does not distinguish.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.privacy")
public class PrivacyProperties {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacyProperties.class);

    private Integer retentionDays;

    @PostConstruct
    void announce() {
        if (retentionDays == null) {
            LOG.info("privacy: no retention period configured; records are kept until erased on request (decisions.md D24)");
        } else {
            LOG.info("privacy: retention period is {} days, but nothing schedules a sweep yet (decisions.md D24)", retentionDays);
        }
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }
}
