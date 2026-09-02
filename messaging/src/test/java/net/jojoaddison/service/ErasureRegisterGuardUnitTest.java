package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.jojoaddison.domain.ErasedSubject;
import net.jojoaddison.domain.PepperWitness;
import net.jojoaddison.repository.ErasedSubjectRepository;
import net.jojoaddison.repository.PepperWitnessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What the erasure guard decides, in each state a real deployment can be in — {@code decisions.md} D35.
 *
 * <p>A unit test rather than an integration one on purpose. Proving each refusal end to end would need
 * a second Spring context per case with the pepper removed or changed, and the thing worth pinning is
 * the decision itself: which states refuse and which start.
 *
 * <p><strong>This test cannot see when the guard runs, and that mattered.</strong> Every assertion
 * here passed while the guard fired after the Kafka listener container had already started and
 * consumed — the whole of the defect corrected in D35's addendum. The ordering is pinned separately,
 * by {@link ErasureRegisterGuardOrderingTest}; keep both, because a test of the decision and a test of
 * its timing catch different regressions.
 */
class ErasureRegisterGuardUnitTest {

    private static final String PEPPER = "hc-market-test-pepper-not-a-real-one";

    private final ErasedSubjectRepository register = mock(ErasedSubjectRepository.class);
    private final PepperWitnessRepository witnesses = mock(PepperWitnessRepository.class);

    private ErasureRegisterGuard guard(String pepper) {
        return new ErasureRegisterGuard(register, witnesses, new SubjectPseudonym(pepper));
    }

    /** What the witness row holds when the pepper is the one this database was built with. */
    private PepperWitness witnessFor(String pepper) {
        return new PepperWitness(
            ErasureRegisterGuard.WITNESS_ID,
            new ErasureRegisterGuard(register, witnesses, new SubjectPseudonym(pepper)).currentWitnessAlias(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("no pepper and nobody erased yet: it starts, because nothing can have gone wrong yet")
    void unpepperedAndEmptyIsFine() {
        when(register.count()).thenReturn(0L);
        assertThatCode(() -> guard("").start()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no pepper but somebody HAS been erased: it refuses, naming the variable")
    void unpepperedWithARegisterRefuses() {
        // Their aliases cannot be recomputed, so isErased answers false for a person this service
        // erased, and the next booking event writes their real login back into a fresh conversation.
        when(register.count()).thenReturn(3L);
        assertThatThrownBy(() -> guard("").start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HEALTHCONNECT_PRIVACY_PEPPER")
            .hasMessageContaining("3");
    }

    @Test
    @DisplayName("with a pepper it does not ask whether anyone has been erased")
    void pepperedSkipsTheRegisterCount() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.of(witnessFor(PEPPER)));
        guard(PEPPER).start();
        verify(register, never()).count();
    }

    /**
     * Catches a pre-D35 register being carried into a peppered service — {@code decisions.md} D35.
     *
     * <p>An alias written before the pepper existed is {@code erased-} plus 12 hex characters against
     * today's 16, and nothing derived from now on can ever equal one. The rows stay, still redacted
     * and still correct as redactions, while the subjects behind them are permanently unrecognisable:
     * {@code isErased} answers false about people this service erased, and the next lagging
     * {@code booking.requested} writes their login into a fresh conversation. Nothing else in the
     * estate reads a length, so nothing else could notice.
     *
     * <p>Red before this check existed: the guard returned as soon as it saw a pepper.
     */
    @Test
    @DisplayName("a register holding pre-D35 aliases refuses, and points at the migration section")
    void legacyAliasesRefuse() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.of(witnessFor(PEPPER)));
        when(register.countAliasesNotOfLength(anyInt())).thenReturn(2L);

        assertThatThrownBy(() -> guard(PEPPER).start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("erased_subject")
            .hasMessageContaining("2")
            .hasMessageContaining("D35");
    }

    /** The length it asks about must be today's, not a number remembered in the test or the guard. */
    @Test
    @DisplayName("…and it asks about the length the live derivation actually produces")
    void legacyCheckUsesTheCurrentAliasLength() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.of(witnessFor(PEPPER)));
        ArgumentCaptor<Integer> length = ArgumentCaptor.forClass(Integer.class);

        guard(PEPPER).start();

        verify(register).countAliasesNotOfLength(length.capture());
        assertThat(length.getValue()).isEqualTo(new SubjectPseudonym(PEPPER).of("anybody").length()).isEqualTo(23);
    }

    /**
     * Catches a rotated or mistyped pepper — {@code decisions.md} D35.
     *
     * <p>D35 says a changed pepper "looks exactly like a right one until something fails to match",
     * and until this check there was nothing anywhere in the estate that could tell the difference. A
     * re-peppered service starts, serves, and derives internally consistent aliases that no longer
     * equal the ones in its own rows, so an erasure silently stops applying to the people it was
     * performed on. The first symptom is months away and looks like a bug in the consumer.
     *
     * <p>Red before the witness existed: any pepper at all was accepted.
     */
    @Test
    @DisplayName("a pepper that is not the one this database was built with refuses")
    void aChangedPepperRefuses() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.of(witnessFor("the-pepper-this-database-was-built-with")));

        assertThatThrownBy(() -> guard(PEPPER).start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HEALTHCONNECT_PRIVACY_PEPPER is not the value this database was built with")
            .hasMessageContaining("privacy_pepper_witness");
    }

    @Test
    @DisplayName("the first startup with a pepper records the witness rather than refusing")
    void theFirstPepperedStartupRecordsTheWitness() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.empty());

        guard(PEPPER).start();

        ArgumentCaptor<PepperWitness> saved = ArgumentCaptor.forClass(PepperWitness.class);
        verify(witnesses).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(ErasureRegisterGuard.WITNESS_ID);
        assertThat(saved.getValue().getSubjectAlias()).startsWith("erased-").hasSize(23);
    }

    /**
     * The trap the witness had to avoid: it must not become an answer to "has this service erased
     * anybody". A row in {@code erased_subject} would make {@code count()} non-zero for ever and
     * retract the empty-register allowance that keeps the consumer running unpeppered.
     */
    @Test
    @DisplayName("the witness is never written to the erased-subject register")
    void theWitnessIsNotWrittenToTheRegister() {
        when(witnesses.findById(ErasureRegisterGuard.WITNESS_ID)).thenReturn(Optional.empty());

        guard(PEPPER).start();

        verify(register, never()).save(any(ErasedSubject.class));
        verify(register, never()).saveAndFlush(any(ErasedSubject.class));
    }

    /**
     * Narrows the startup-only gap: an unpeppered instance sharing a database with a peppered one.
     *
     * <p>It passed its own guard while the register was empty, and nothing re-asks — so it answers
     * {@code isErased} = false for everybody its sibling erases afterwards and writes their real
     * logins, with no restart to re-trigger the check. Throttled, and only on the unpeppered path, so
     * a healthy estate runs no extra query at all.
     */
    @Test
    @DisplayName("an unpeppered instance re-asks the register, and refuses once it stops being empty")
    void theUnpepperedPathIsRecheckedPeriodically() {
        ErasureRegisterGuard g = new ErasureRegisterGuard(register, witnesses, new SubjectPseudonym(""), Duration.ZERO);
        when(register.count()).thenReturn(0L);
        assertThatCode(g::assertRegisterStillEmpty).doesNotThrowAnyException();

        when(register.count()).thenReturn(1L);
        assertThatThrownBy(g::assertRegisterStillEmpty)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Every replica shares the estate's pepper");
    }

    @Test
    @DisplayName("…and it is a throttle, not a query per event")
    void theRecheckIsThrottled() {
        ErasureRegisterGuard g = new ErasureRegisterGuard(register, witnesses, new SubjectPseudonym(""), Duration.ofHours(1));
        when(register.count()).thenReturn(0L);

        for (int i = 0; i < 50; i++) {
            g.assertRegisterStillEmpty();
        }

        verify(register).count();
    }
}
