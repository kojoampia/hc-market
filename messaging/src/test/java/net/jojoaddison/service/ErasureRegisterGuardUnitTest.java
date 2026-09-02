package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.jojoaddison.repository.ErasedSubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one place an absent pepper stops a service starting — {@code decisions.md} D35.
 *
 * <p>A unit test rather than an integration one on purpose. Proving the refusal end to end would need
 * a second Spring context with the pepper removed and a row already in the register, and the thing
 * worth pinning is the decision itself: which of the three states refuses and which two do not.
 *
 * <p><strong>This test cannot see when the guard runs, and that mattered.</strong> Every assertion
 * here passed while the guard fired after the Kafka listener container had already started and
 * consumed — the whole of the defect corrected in D35's addendum. The ordering is pinned separately,
 * by {@link ErasureRegisterGuardOrderingTest}; keep both, because a test of the decision and a test of
 * its timing catch different regressions. The only change here is the entry point: {@code run(args)}
 * became {@code start()} when the guard became a {@code SmartLifecycle}.
 */
class ErasureRegisterGuardUnitTest {

    private final ErasedSubjectRepository register = mock(ErasedSubjectRepository.class);

    @Test
    @DisplayName("no pepper and nobody erased yet: it starts, because nothing can have gone wrong yet")
    void unpepperedAndEmptyIsFine() {
        when(register.count()).thenReturn(0L);
        assertThatCode(() -> new ErasureRegisterGuard(register, new SubjectPseudonym("")).start()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no pepper but somebody HAS been erased: it refuses, naming the variable")
    void unpepperedWithARegisterRefuses() {
        // Their aliases cannot be recomputed, so isErased answers false for a person this service
        // erased, and the next booking event writes their real login back into a fresh conversation.
        when(register.count()).thenReturn(3L);
        assertThatThrownBy(() -> new ErasureRegisterGuard(register, new SubjectPseudonym("")).start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HEALTHCONNECT_PRIVACY_PEPPER")
            .hasMessageContaining("3");
    }

    @Test
    @DisplayName("with a pepper it asks the database nothing")
    void pepperedSkipsTheQuery() {
        SubjectPseudonym peppered = new SubjectPseudonym("hc-market-test-pepper-not-a-real-one");
        assertThat(peppered.isConfigured()).isTrue();
        new ErasureRegisterGuard(register, peppered).start();
        verify(register, never()).count();
    }
}
