package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jojoaddison.web.rest.PrivacyResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The retention sweep's three switches, and the two independent decisions between an estate and an
 * irreversible deletion — {@code decisions.md} D96, backlog NEW-52.
 *
 * <p>Constructed directly rather than driven through a running context, for
 * {@code PrivacyPropertiesRegistrationUnitTest}'s reason: <strong>a config file cannot make any of
 * these vacuous</strong>. "Off by default" asserted against an ambient yml is an assertion about that
 * yml; here the unset and set-to-blank cases are the test's own subject, which is exactly what compose
 * produces with {@code ${X:-}}.
 */
class TheRetentionSweepIsOffUntilTwoDecisionsUnitTest {

    private static PrivacyProperties.Retention retention() {
        PrivacyProperties.Retention r = new PrivacyProperties.Retention();
        r.setFinancialDays(2190);
        r.setOperationalDays(365);
        return r;
    }

    /**
     * THE BOUND FLAG, not the presence of a property.
     *
     * <p>Red-first: fails with {@code expected: false but was: true} against a
     * {@code DEFAULT_SWEEP_ENABLED} of {@code "true"}. Asserting that the yml has no value would pass
     * against a Java default of "on", which is the mistake this phrasing exists to avoid.
     */
    @Test
    @DisplayName("an estate that configures nothing does not sweep")
    void offByDefault() {
        assertThat(retention().sweepEnabled()).isFalse();
    }

    /**
     * And "on" does not mean "deleting". This is the second of the two decisions.
     *
     * <p>Red-first: fails against a {@code DEFAULT_SWEEP_DRY_RUN} of {@code "false"}, which is the
     * one-character edit that turns a newly-enabled sweep from a report into an erasure.
     */
    @Test
    @DisplayName("a sweep that is switched on is still in dry run")
    void dryRunByDefault() {
        assertThat(retention().sweepDryRun()).isTrue();
    }

    /** Blank is absent — which is what compose's {@code ${X:-}} actually delivers. */
    @Test
    @DisplayName("null, empty and whitespace all mean the documented default")
    void blankIsTheDefault() {
        for (String value : new String[] { null, "", "   ", "\t" }) {
            PrivacyProperties.Retention r = retention();
            r.setSweepEnabled(value);
            r.setSweepDryRun(value);
            r.setSweepCron(value);
            assertThat(r.sweepEnabled()).as("enabled=[%s]", value).isFalse();
            assertThat(r.sweepDryRun()).as("dryRun=[%s]", value).isTrue();
            assertThat(r.sweepCron()).as("cron=[%s]", value).isEqualTo(PrivacyProperties.Retention.DEFAULT_SWEEP_CRON);
        }
    }

    /**
     * A misread boolean must refuse, and the direction is the whole point.
     *
     * <p>{@code Boolean.parseBoolean} answers {@code false} for every string that is not "true", so
     * {@code sweep-dry-run=fales} would read as <em>dry run off</em> — an estate deleting its
     * customers' records because somebody mistyped a word. Every value here is refused rather than
     * interpreted. Red-first: with {@code parseStrictBoolean} replaced by {@code Boolean.parseBoolean}
     * this fails on the first case, reporting no exception thrown.
     */
    @Test
    @DisplayName("a value that is neither true nor false refuses startup rather than being guessed")
    void anUnreadableBooleanRefuses() {
        for (String typo : new String[] { "fales", "yes", "no", "0", "1", "TRUEISH", "off" }) {
            PrivacyProperties.Retention r = retention();
            r.setSweepDryRun(typo);
            assertThatThrownBy(r::sweepDryRun)
                .as("dry-run=[%s]", typo)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sweep-dry-run")
                .hasMessageContaining(typo);

            PrivacyProperties.Retention e = retention();
            e.setSweepEnabled(typo);
            assertThatThrownBy(e::sweepEnabled).as("enabled=[%s]", typo).isInstanceOf(IllegalStateException.class);
        }
    }

    /** Both spellings of the two readable values, since an operator may well shout. */
    @Test
    @DisplayName("true and false are read case-insensitively and nothing else is read at all")
    void theTwoReadableValues() {
        for (String yes : new String[] { "true", "TRUE", "True", " true " }) {
            PrivacyProperties.Retention r = retention();
            r.setSweepEnabled(yes);
            assertThat(r.sweepEnabled()).as("enabled=[%s]", yes).isTrue();
        }
        for (String no : new String[] { "false", "FALSE", "False", " false " }) {
            PrivacyProperties.Retention r = retention();
            r.setSweepDryRun(no);
            assertThat(r.sweepDryRun()).as("dryRun=[%s]", no).isFalse();
        }
    }

    /** A cron the scheduler cannot parse fails at startup, not at 02:30. */
    @Test
    @DisplayName("an invalid cron expression refuses startup")
    void anInvalidCronRefuses() {
        PrivacyProperties.Retention r = retention();
        r.setSweepCron("every second tuesday");
        assertThatThrownBy(r::sweepCron)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sweep-cron")
            .hasMessageContaining("six-field cron");
    }

    /**
     * {@code enforced} is true for exactly one of the four combinations.
     *
     * <p>Red-first: an {@code isEnforcing()} written as a bare {@code sweepEnabled()} fails the
     * enabled-and-dry-run row, which is the combination a newly-enabled estate is actually in and
     * therefore the one that would have been reported wrongly.
     */
    @Test
    @DisplayName("only enabled AND not dry-run counts as enforcing")
    void enforcingIsTheConjunction() {
        assertThat(enforcing("false", "true")).as("off, dry run").isFalse();
        assertThat(enforcing("false", "false")).as("off, would erase if it ran — but it does not run").isFalse();
        assertThat(enforcing("true", "true")).as("on, dry run: counts and erases nothing").isFalse();
        assertThat(enforcing("true", "false")).as("on and erasing").isTrue();
    }

    /**
     * And the desk reports that derivation rather than a literal.
     *
     * <p>Asserted through {@link PrivacyResource} because the substitution happens there and that is
     * the line a refactor would drop back to a constant — which is what it was until D96.
     */
    @Test
    @DisplayName("the desk's enforced flag follows the sweep, and is false on an unconfigured estate")
    void theDeskReportsTheDerivation() {
        assertThat(deskEnforced("", "")).as("unconfigured").isFalse();
        assertThat(deskEnforced("true", "true")).as("dry run is not enforcement").isFalse();
        assertThat(deskEnforced("true", "false")).as("enforcing").isTrue();
    }

    private static boolean enforcing(String enabled, String dryRun) {
        PrivacyProperties.Retention r = retention();
        r.setSweepEnabled(enabled);
        r.setSweepDryRun(dryRun);
        return r.isEnforcing();
    }

    private static boolean deskEnforced(String enabled, String dryRun) {
        PrivacyProperties p = new PrivacyProperties();
        p.getRetention().setFinancialDays(2190);
        p.getRetention().setOperationalDays(365);
        p.getRetention().setSweepEnabled(enabled);
        p.getRetention().setSweepDryRun(dryRun);
        return new PrivacyResource(p).policy().enforced();
    }
}
