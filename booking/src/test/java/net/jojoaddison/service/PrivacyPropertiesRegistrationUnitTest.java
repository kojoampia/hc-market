package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.PrivacyResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Blank and absent are the same thing, and the desk reports null for both — D42's rule, D88's move.
 *
 * <p>This was an integration assertion until D88 committed the real registration number, at which point
 * it could no longer be made from a running container: the shared test config now carries a value, so an
 * IT asserting the field is absent would be asserting the ambient config rather than the mechanism.
 *
 * <p><strong>Here it cannot be made vacuous by a config file</strong>, which is the whole reason for the
 * move. The property is constructed directly, so the two cases that matter — unset and set-to-blank —
 * are the test's own subject rather than something a yml happens to supply.
 *
 * <p>Why it matters at all: the number is a claim about a real organisation's relationship with a
 * regulator. An empty string travelling to the desk would render as {@code ""}, which reads as
 * "registered, with a number nobody can see" — the reading D42 built this rule to prevent.
 */
class PrivacyPropertiesRegistrationUnitTest {

    private static PrivacyProperties withRegistration(String value) {
        PrivacyProperties p = new PrivacyProperties();
        p.setControllerRegistration(value);
        p.getRetention().setFinancialDays(2190);
        p.getRetention().setOperationalDays(365);
        return p;
    }

    @Test
    @DisplayName("null, empty and whitespace all count as no registration")
    void blankIsAbsent() {
        for (String value : new String[] { null, "", "   ", "\t" }) {
            assertThat(withRegistration(value).registrationIsAbsent()).as("value=[%s]", value).isTrue();
        }
    }

    @Test
    @DisplayName("a real number counts as present")
    void aNumberIsPresent() {
        assertThat(withRegistration("P0021484082").registrationIsAbsent()).isFalse();
    }

    /**
     * The desk reports {@code null} and never the empty string, for every shape of absence.
     *
     * <p>Asserted through {@link PrivacyResource} rather than through the property, because the
     * substitution happens at the resource and that is the line a refactor would drop.
     */
    @Test
    @DisplayName("the desk reports null for a blank registration, never an empty string")
    void theDeskReportsNullNotBlank() {
        for (String value : new String[] { null, "", "  " }) {
            PrivacyResource.Policy policy = new PrivacyResource(withRegistration(value)).policy();
            assertThat(policy.controllerRegistration()).as("value=[%s]", value).isNull();
        }

        assertThat(new PrivacyResource(withRegistration("P0021484082")).policy().controllerRegistration()).isEqualTo("P0021484082");
    }

    /** The third category is gone from the reported body — D88. */
    @Test
    @DisplayName("the reported retention has two periods and no care-summary field")
    void twoPeriodsOnly() {
        PrivacyResource.RetentionView view = new PrivacyResource(withRegistration("P0021484082")).policy().retention();

        assertThat(view.financialDays()).isEqualTo(2190);
        assertThat(view.operationalDays()).isEqualTo(365);
        assertThat(PrivacyResource.RetentionView.class.getRecordComponents())
            .as("a third component would be a period governing data this estate does not hold")
            .hasSize(2);
    }
}
