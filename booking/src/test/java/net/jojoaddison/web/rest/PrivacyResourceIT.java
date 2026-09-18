package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The retention desk reports what this deployment is actually running — {@code decisions.md} D42.
 *
 * <p>These exist because the values now come from the <strong>environment</strong>, which is exactly
 * the class of configuration that drifts between two deployments with nothing failing. A policy
 * endpoint nobody asserts against is a policy nobody notices is wrong.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PrivacyResourceIT {

    private static final String URL = "/api/desk/privacy";

    @Autowired
    private MockMvc mvc;

    /**
     * Counsel's ratified figures are what an unconfigured estate runs.
     *
     * <p>Red-first: this asserts the committed fallbacks in {@code application.yml}, so it fails
     * against any edit that changes them silently — confirmed by flipping {@code financial-days} to
     * 1095, which fails with {@code expected:<2190> but was:<1095>}. It is deliberately an exact
     * assertion rather than a range: the point is that the numbers came from counsel and nobody may
     * adjust them in passing.
     */
    @Test
    @DisplayName("the desk reports counsel's two retention periods, and no third")
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    void reportsTheRatifiedPeriods() throws Exception {
        mvc
            .perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retention.financialDays").value(2190))
            .andExpect(jsonPath("$.retention.operationalDays").value(365))
            // THE THIRD IS GONE AND ITS ABSENCE IS ASSERTED — decisions.md D88. It governed nothing:
            // Booking.careSummaryShared is a Boolean and no field in this estate has ever held a
            // condition, an allergy or a medication. Asserted absent rather than dropped from this
            // test, because a period coming back null would read as "kept for ever" and a test that
            // stopped mentioning it could not tell the two apart.
            .andExpect(jsonPath("$.retention.careSummaryDays").doesNotExist());
    }

    /**
     * A stated policy is never reported as an applied one — and this estate does not apply one.
     *
     * <p>This is the assertion that matters most and the one most likely to be quietly broken.
     * Populated categories look far more like a working retention regime than the single unset integer
     * they replaced, so the honest {@code enforced: false} beside them is doing more work than it was
     * before.
     *
     * <p><strong>It said "nothing in this estate schedules a sweep; the day something does, this test
     * should fail and be changed deliberately" — and that day was {@code decisions.md} D96.</strong>
     * So it has been changed deliberately, and what changed is not the expected value: it is still
     * {@code false}, because {@link net.jojoaddison.service.RetentionSweep} is off by default and, when
     * enabled, is in dry run by default. What changed is <em>why</em> — the resource derives the flag
     * from the sweep's own two switches now instead of returning a constant, so this asserts that an
     * unconfigured estate is not enforcing rather than that the field is hardcoded.
     *
     * <p>Which means this case can no longer see the derivation being wrong: it passes against
     * {@code enforced = false} restored as a literal, and it would pass against any expression that
     * happens to be false on the test config. That half is
     * {@code TheRetentionSweepIsOffUntilTwoDecisionsUnitTest.theDeskReportsTheDerivation}, which drives
     * all three states through this same resource, and it is where a widening belongs — <strong>not
     * here</strong>: making the flag true from an IT means a second Spring context for one boolean.
     * What this case still does, and no unit test can, is prove the wire body carries the field at all.
     */
    @Test
    @DisplayName("a configured period is not an enforced one, and this estate enforces none")
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    void doesNotClaimToEnforceAnything() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.enforced").value(false));
    }

    /**
     * No registration number is configured in test, and the desk says so with a null.
     *
     * <p>Reported as null rather than as an empty string so "not configured" cannot be mistaken for
     * "registered with a number nobody can read". The number is a claim about a real organisation's
     * relationship with a regulator, so an invented or placeholder value would be worse than absence
     * — see {@code PrivacyProperties}. Red-first: fails with {@code expected null} against a version
     * that passes the blank through.
     */
    @Test
    @DisplayName("the desk reports the committed registration number")
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    void reportsTheRegistrationNumber() throws Exception {
        // D42 LEFT THIS UNSET AND D88 SET IT. The real number arrived and a DPC registration is a
        // public registry identifier, not a secret — and `null` had stopped meaning "not configured"
        // and started asserting "not registered", which is false. The "blank counts as absent, and the
        // desk reports null rather than an empty string" half of D42's rule moved to
        // PrivacyPropertiesRegistrationUnitTest, where no config file can make it vacuous: it cannot
        // be asserted from here any more, because the shared test config now carries a value.
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.controllerRegistration").value("P0021484082"));
    }

    /** The policy and the registration number are the desk's, not the public's. */
    @Test
    @DisplayName("the desk endpoint refuses a customer")
    @WithMockUser(username = "ama.customer", authorities = "ROLE_USER")
    void refusesANonDeskCaller() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
    }
}
