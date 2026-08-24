package net.jojoaddison.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The payout service's slice of {@code demo/seed-data.json}: the brokerage configuration and the
 * completed sessions that become ledger rows.
 *
 * <p>Each service reads <strong>only its own top-level sections</strong>, which is what lets the
 * same file load into five services without collision — so {@code ignoreUnknown} is load-bearing
 * rather than defensive. {@code professionals}, {@code reviews} and the rest belong to other
 * services and must be ignored here, not rejected.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedFile(@JsonProperty("$meta") Meta meta, Brokerage brokerage, List<SeedSession> sessions) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, LocalDate demoToday, String note) {}

    /**
     * The prototype's hard-coded constants, which become the first {@code BrokerageConfig} version.
     * Everything downstream prices against the config in force when a booking completed, so this is
     * a row with an {@code effectiveFrom}, never a constant in code.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Brokerage(
        BigDecimal commissionRate,
        Integer payoutLagDays,
        String currency,
        Integer freeCancellationHours,
        BigDecimal lateCancellationPct
    ) {}

    /** One completed session. 256 of them, ₵81,620 gross, all belonging to p1 in the prototype. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedSession(
        String ref,
        String professionalRef,
        /** Carried alongside the ref so the ownership check never needs the catalog service. */
        String professionalLogin,
        String customerLogin,
        String serviceRef,
        String serviceName,
        String deliveryMode,
        LocalDate completedDate,
        Long grossMinor,
        String currency,
        String status
    ) {}
}
