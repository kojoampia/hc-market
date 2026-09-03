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
    @DisplayName("the desk reports counsel's three retention periods")
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    void reportsTheRatifiedPeriods() throws Exception {
        mvc
            .perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retention.financialDays").value(2190))
            .andExpect(jsonPath("$.retention.operationalDays").value(365))
            .andExpect(jsonPath("$.retention.careSummaryDays").value(90));
    }

    /**
     * A stated policy is never reported as an applied one.
     *
     * <p>This is the assertion that matters most and the one most likely to be quietly broken. Three
     * populated categories look far more like a working retention regime than the single unset
     * integer they replaced, so the honest {@code enforced: false} beside them is doing more work
     * than it was before. Nothing in this estate schedules a sweep; the day something does, this test
     * should fail and be changed deliberately.
     */
    @Test
    @DisplayName("a configured period is not an enforced one, and says so")
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
    @DisplayName("an unset registration number is null, not blank")
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    void reportsAnAbsentRegistrationAsNull() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.controllerRegistration").doesNotExist());
    }

    /** The policy and the registration number are the desk's, not the public's. */
    @Test
    @DisplayName("the desk endpoint refuses a customer")
    @WithMockUser(username = "ama.customer", authorities = "ROLE_USER")
    void refusesANonDeskCaller() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
    }
}
