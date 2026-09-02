package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the erased-subject alias to a known answer — {@code decisions.md} D35.
 *
 * <h2>THIS FILE IS COPIED VERBATIM INTO booking, catalog AND messaging</h2>
 *
 * <p>So is {@link SubjectPseudonym} itself, and CI asserts both copies are byte-identical in the
 * three services. This test is the other half of that guarantee: the check proves the source has not
 * drifted, and the known answer proves the derivation still produces what it produced yesterday, so
 * a change made carefully in all three at once is still caught.
 *
 * <p>That matters because the failure is invisible. Three services computing three different aliases
 * would each keep working: booking would redact its own rows, messaging would redact its own, and
 * only a cross-service lookup — reconciling a redacted booking against a payout ledger row, or
 * messaging asking its register whether a login has been erased — would come back empty, months
 * later, with nothing having failed at the time.
 *
 * <p>The expected value below was computed independently of this code:
 *
 * <pre>
 * printf 'ama.mensah' | openssl dgst -sha256 -hmac 'hc-market-test-pepper-not-a-real-one'
 * </pre>
 *
 * <p>which is the point of a known-answer test — a value produced by the implementation under test
 * pins nothing but the implementation's own agreement with itself.
 */
class SubjectPseudonymUnitTest {

    /**
     * The same literal as {@code src/test/resources/config/application.yml} in all three services. It
     * is a fixture, not a secret; a real deployment injects {@code HEALTHCONNECT_PRIVACY_PEPPER}.
     */
    private static final String PEPPER = "hc-market-test-pepper-not-a-real-one";

    private final SubjectPseudonym pseudonyms = new SubjectPseudonym(PEPPER);

    @Test
    @DisplayName("the alias for a known login under a known pepper is fixed, in all three services")
    void knownAnswer() {
        assertThat(pseudonyms.of("ama.mensah")).isEqualTo("erased-fdb1d88da1c9bc37");
    }

    @Test
    @DisplayName("the advisory-lock key comes from the same MAC and is pinned with it")
    void lockKeyIsPinnedToo() {
        // The first eight bytes of the same HMAC, big-endian and signed — messaging's erasure and its
        // booking-event consumer must land on this number or they serialise on different locks and
        // the race D34 closed reopens.
        assertThat(pseudonyms.lockKey("ama.mensah")).isEqualTo(-166113608419656649L);
    }

    @Test
    @DisplayName("a different pepper is a different alias, which is the whole point of peppering it")
    void thePepperChangesTheAnswer() {
        assertThat(new SubjectPseudonym("something-else").of("ama.mensah")).isNotEqualTo(pseudonyms.of("ama.mensah"));
    }

    @Test
    @DisplayName("the alias is deterministic, prefixed, and contains nothing of the login")
    void shapeOfTheAlias() {
        String once = pseudonyms.of("ama.mensah");
        assertThat(pseudonyms.of("ama.mensah")).isEqualTo(once);
        assertThat(once).startsWith("erased-").doesNotContain("ama").hasSize("erased-".length() + 16);
        assertThat(pseudonyms.of("someone.else")).isNotEqualTo(once);
    }

    /**
     * The guarantee the absent-pepper decision rests on. An alias is written into rows in place and
     * cannot be taken back, so "no pepper" must never degrade to a weaker derivation — it has to
     * refuse, and refuse before anything is written.
     */
    @Test
    @DisplayName("without a pepper nothing derives at all, rather than falling back to a bare digest")
    void refusesWithoutAPepper() {
        SubjectPseudonym none = new SubjectPseudonym("   ");
        assertThat(none.isConfigured()).isFalse();
        assertThatThrownBy(() -> none.of("ama.mensah")).isInstanceOf(IllegalStateException.class).hasMessageContaining("pepper");
        assertThatThrownBy(() -> none.lockKey("ama.mensah")).isInstanceOf(IllegalStateException.class);
        assertThat(new SubjectPseudonym(PEPPER).isConfigured()).isTrue();
    }
}
