package net.jojoaddison.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import net.jojoaddison.domain.Booking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seam's two answers that do not come from a provider — {@code decisions.md} D44.
 *
 * <p>Both are about {@link BookingPayments#take} deciding something on the platform's own account: a
 * booking with nothing to pay, where no provider is asked at all, and a provider that throws instead
 * of answering, where one was asked and gave no answer this platform can use.
 *
 * <p>Tested here rather than only through the endpoint because neither is an HTTP concern. The
 * endpoint's half of it — 201 for the free booking, 502 for the thrown exception — is in
 * {@code PaymentSeamIT}.
 */
class BookingPaymentsUnitTest {

    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final PaymentRecorder recorder = mock(PaymentRecorder.class);
    private final BookingPayments payments = new BookingPayments(provider, recorder);

    private static Booking booking(long priceMinor) {
        return new Booking()
            .reference("b-unit01")
            .customerLogin("kojo.customer")
            .serviceName("Community walking group")
            .priceMinor(priceMinor)
            .currency("GHS");
    }

    /**
     * The package's reason to exist — {@code decisions.md} D44.
     *
     * <p>Two of the eighteen seeded professionals offer a service at {@code priceMinor: 0}, and "from
     * ₵0" is the catalogue working rather than a bug. Asking a provider to authorize 0 pesewas is
     * refused by all three D37 chose, so every free booking in the estate would have become
     * uncreatable the day a provider was configured — with nothing going red before then, because the
     * unconfigured provider answers {@code OFF_PLATFORM} to anything at all.
     *
     * <p>So the provider is not asked. The assertion that matters is
     * {@link org.mockito.Mockito#verifyNoInteractions} rather than the state: a guard that asked and
     * then ignored the answer would still be one round trip to a third party per free booking, and
     * would still be refused.
     */
    @Test
    @DisplayName("a free booking asks no provider anything")
    void nothingToPayAsksNobody() {
        BookingPayments.Taken taken = payments.take(booking(0L));

        verifyNoInteractions(provider);
        // The state itself cannot be red against the old code — the constant did not exist — so it is
        // asserted beside the interaction check rather than instead of it. Same arrangement D43 used
        // for pendingIsThreeAnswers.
        assertThat(taken.outcome().state()).isEqualTo(PaymentState.NOTHING_TO_PAY);
        assertThat(taken.outcome().state().permitsBooking()).isTrue();
        assertThat(taken.outcome().state().holdsMoney()).isFalse();
        // No handle came back, because nobody was asked for one — so there is no row, by D41's rule
        // rather than by a second decision taken here.
        assertThat(taken.attemptId()).isNull();
        verifyNoInteractions(recorder);
    }

    /** A priced booking still goes to the provider. The guard is a condition, not a switch. */
    @Test
    @DisplayName("a priced booking is still authorized")
    void aPricedBookingIsStillAuthorized() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-1"));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.AUTHORIZED);
        assertThat(taken.intent().amountMinor()).isEqualTo(15000L);
    }

    /**
     * An adapter that throws is a provider that could not be asked — {@code decisions.md} D44.
     *
     * <p>{@link PaymentState#FAILED} is exactly that fact, and it existed before this: the seam simply
     * had no route to it from an exception, so a provider whose HTTP client timed out produced a 500
     * and a stack trace where a provider politely answering {@code FAILED} produced a 502. Two answers
     * to one situation, and the one nobody chose was the one every real adapter will actually take.
     */
    @Test
    @DisplayName("a provider that throws is a provider that failed")
    void aThrownExceptionIsAFailure() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenThrow(new IllegalStateException("connect timed out"));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.FAILED);
        assertThat(taken.outcome().state().permitsBooking()).isFalse();
        assertThat(taken.attemptId()).isNull();
    }

    /**
     * The reason travels to the customer in a response body, so it is composed here and not copied.
     *
     * <p>Same rule as D41's {@code attention_note} and D43's next action: a provider's own words are
     * where a customer's name, phone number or card fragment arrives unannounced, and this one is
     * rendered rather than stored. The exception's class is enough for whoever reads the log, and the
     * log gets the whole thing.
     */
    @Test
    @DisplayName("the provider's own words do not reach the response")
    void theProvidersWordsAreNotRelayed() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenThrow(new IllegalStateException("MTN declined 0244123456 for Ama Mensah"));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().reason()).doesNotContain("Ama Mensah").doesNotContain("0244123456");
        assertThat(taken.outcome().reason()).contains("stub").contains("IllegalStateException");
    }

    /**
     * An adapter does not get to declare a priced booking free — {@code decisions.md} D44, as
     * reviewed.
     *
     * <p>{@link PaymentState#NOTHING_TO_PAY} is documented as the one value in the enum no provider
     * reports, and {@link PaymentOutcome#nothingToPay()} is a public factory on the record every
     * adapter constructs — so the documentation was the whole of the guarantee.
     * {@link PaymentState#PENDING} got two compact-constructor invariants for the same class of
     * defect; this had a javadoc.
     *
     * <p>The failure it admits is the quietest one in the seam: a ₵150.00 booking, an adapter mapping
     * an unrecognised provider status onto "free", {@code permitsBooking()} true and the state not
     * {@code PENDING} — so the booking is created in {@code REQUESTED}, {@code booking.requested} is
     * published, the professional is told, no {@code payment_attempt} row is written because no
     * handle came back, and no money moved. There is nothing left in the estate that disagrees with
     * anything.
     *
     * <p>{@code take} is where it is caught because {@code take} is the only place that knows the
     * amount, which is the same reason the zero-amount guard is there rather than at the call site.
     * The answer is {@code FAILED} rather than a thrown exception: a provider answering something
     * this platform cannot use is precisely what that state means, and it lands on the 502 that the
     * fix above exists to give instead of the 500 a throw would produce.
     */
    @Test
    @DisplayName("a provider may not answer NOTHING_TO_PAY for a priced booking")
    void aProviderCannotDeclareAPricedBookingFree() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenReturn(PaymentOutcome.nothingToPay());

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.FAILED);
        assertThat(taken.outcome().state().permitsBooking()).isFalse();
        assertThat(taken.outcome().reason()).contains("stub").contains("NOTHING_TO_PAY");
    }

    /**
     * The handle survives the refusal, because D41's rule does not have exceptions.
     *
     * <p>A provider that answers {@code NOTHING_TO_PAY} <em>and</em> hands back a reference is
     * confused about the amount and still holding a fact about this booking's money. Converting the
     * outcome through {@link PaymentOutcome#failed(String)} would drop that reference — the one field
     * on the record derivable from nothing — so the conversion keeps it and the recorder writes the
     * row it always would have.
     */
    @Test
    @DisplayName("a refused NOTHING_TO_PAY keeps whatever handle came with it")
    void aRefusedNothingToPayKeepsTheHandle() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenReturn(new PaymentOutcome(PaymentState.NOTHING_TO_PAY, "prov-nt1", null));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.FAILED);
        assertThat(taken.outcome().providerReference()).isEqualTo("prov-nt1");
    }

    /**
     * {@code name()} is somebody else's code too — {@code decisions.md} D44, as reviewed.
     *
     * <p>The catch that turns a thrown {@code authorize} into {@code FAILED} called
     * {@code provider.name()} twice inside itself, unwrapped. An adapter whose {@code name()} throws
     * — a lazily-read configuration property, a null region code — therefore re-threw out of the
     * handler and landed back on exactly the 500 that catch was written to remove.
     */
    @Test
    @DisplayName("an adapter whose name() throws is still a provider that failed")
    void aThrowingNameIsStillAFailure() {
        when(provider.name()).thenThrow(new IllegalStateException("no merchant id configured"));
        when(provider.authorize(any())).thenThrow(new IllegalStateException("connect timed out"));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.FAILED);
        assertThat(taken.outcome().reason()).contains("IllegalStateException");
    }

    /**
     * And on the success path, where the same unwrapped call would lose the handle.
     *
     * <p>{@code recorder.record(provider.name(), …)} is the third site. Money is committed by the
     * time it runs, so a {@code name()} that throws there costs the one fact D41 exists to keep —
     * for a reason that has nothing to do with the payment. The row is written under a placeholder
     * instead; {@code provider} is a not-null column and a name nobody can produce is still better
     * recorded than a handle nobody kept.
     */
    @Test
    @DisplayName("an adapter whose name() throws does not cost the handle")
    void aThrowingNameDoesNotCostTheHandle() {
        when(provider.name()).thenThrow(new IllegalStateException("no merchant id configured"));
        when(provider.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-3"));

        BookingPayments.Taken taken = payments.take(booking(15000L));

        assertThat(taken.outcome().state()).isEqualTo(PaymentState.AUTHORIZED);
        org.mockito.Mockito.verify(recorder).record(org.mockito.ArgumentMatchers.eq("unnamed"), any(), any());
    }

    /**
     * A failure to write the handle down is not a payment failure, and must not be turned into one.
     *
     * <p>Only the call to the provider is wrapped. If {@link PaymentRecorder#record} throws, the money
     * may well be committed and this platform has just failed to keep the one thing it cannot
     * reconstruct (D41) — which is a 500 and a loud one, not a 502 that tells the customer to try
     * again and quietly loses the handle.
     */
    @Test
    @DisplayName("a recorder that throws is not dressed up as a provider failure")
    void aRecorderFailureIsNotAProviderFailure() {
        when(provider.name()).thenReturn("stub");
        when(provider.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-2"));
        when(recorder.record(anyString(), any(), any())).thenThrow(new IllegalStateException("the insert failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> payments.take(booking(15000L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("the insert failed");
    }
}
