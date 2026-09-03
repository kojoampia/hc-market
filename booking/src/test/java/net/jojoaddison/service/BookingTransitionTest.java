package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The booking state machine.
 *
 * <p>The interesting tests here are not "accept moves REQUESTED to CONFIRMED" — they are the ones
 * that would catch the machine drifting away from the diagram in spec §5.2: a state that nothing can
 * reach, a state nothing can leave, or a transition quietly gaining a source it should not have.
 */
class BookingTransitionTest {

    private static final List<BookingTransition> ALL = List.of(
        new BookingTransition.Accept(),
        new BookingTransition.Decline("no"),
        new BookingTransition.ProposeReschedule(LocalDate.now(), "10:00"),
        new BookingTransition.Cancel(CancelledBy.CUSTOMER, "changed my mind"),
        new BookingTransition.Complete(),
        new BookingTransition.NoShow(),
        new BookingTransition.PaymentConfirmed(),
        new BookingTransition.PaymentAbandoned("payment was not completed")
    );

    /**
     * The two states a booking can be created in — {@code decisions.md} D43. Neither is reached by a
     * transition, so both are excluded from the reachability test by construction rather than by being
     * forgotten.
     */
    private static final Set<BookingStatus> ENTRY_POINTS = Set.of(BookingStatus.REQUESTED, BookingStatus.PENDING_PAYMENT);

    @Test
    @DisplayName("accepting a request confirms it — there is no ACCEPTED state")
    void acceptGoesStraightToConfirmed() {
        assertThat(new BookingTransition.Accept().to()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(Arrays.stream(BookingStatus.values()).map(Enum::name))
            .as("decisions.md D7 removed ACCEPTED as unreachable; if it is back, the diagram and the enum have diverged again")
            .doesNotContain("ACCEPTED");
    }

    @Test
    @DisplayName("accept is legal from REQUESTED and from a proposed reschedule, and nowhere else")
    void acceptSources() {
        assertThat(new BookingTransition.Accept().from())
            .containsExactlyInAnyOrder(BookingStatus.REQUESTED, BookingStatus.RESCHEDULE_PROPOSED);
    }

    @Test
    @DisplayName("only a confirmed booking can be completed")
    void completeSources() {
        assertThat(new BookingTransition.Complete().from()).containsExactly(BookingStatus.CONFIRMED);
        assertThat(new BookingTransition.Complete().legalFrom(BookingStatus.REQUESTED)).isFalse();
        assertThat(new BookingTransition.Complete().legalFrom(BookingStatus.COMPLETED)).isFalse();
    }

    /**
     * A terminal state is one no transition can leave. Getting this list wrong is how a booking ends
     * up somewhere it can never move on from — or, worse, somewhere it can be completed twice.
     */
    @Test
    @DisplayName("COMPLETED, DECLINED, CANCELLED and NO_SHOW are terminal")
    void terminalStates() {
        Set<BookingStatus> leavable = ALL.stream().flatMap(t -> t.from().stream()).collect(Collectors.toSet());
        assertThat(leavable)
            .doesNotContain(BookingStatus.COMPLETED, BookingStatus.DECLINED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW);
    }

    /**
     * Every state must be reachable, or it is dead code in an enum. A booking is <em>created</em> in
     * one of the two entry points rather than transitioned into it, so those are accounted for
     * separately — and REQUESTED stopped being only an entry point with D43, since a confirmed payment
     * transitions into it.
     */
    @Test
    @DisplayName("every status is either an entry point or reachable by some transition")
    void everyStatusIsReachable() {
        Set<BookingStatus> accountedFor = ALL.stream().map(BookingTransition::to).collect(Collectors.toCollection(java.util.HashSet::new));
        accountedFor.addAll(ENTRY_POINTS);
        assertThat(accountedFor).containsExactlyInAnyOrder(BookingStatus.values());
    }

    /**
     * Nothing puts a booking <em>back</em> into {@code PENDING_PAYMENT} — {@code decisions.md} D43.
     *
     * <p>It is a state a booking is created in and leaves once, and that one-way property is what
     * makes the professional's inbox safe: a booking they can see cannot become invisible again
     * because a provider sent a late callback, and a confirmed booking cannot be returned to a state
     * where the money is unsettled by anything a webhook says.
     */
    @Test
    @DisplayName("no transition leads back into PENDING_PAYMENT")
    void nothingReturnsToPendingPayment() {
        assertThat(ALL.stream().map(BookingTransition::to)).doesNotContain(BookingStatus.PENDING_PAYMENT);
    }

    /**
     * A booking whose money has not arrived is not a request anybody may act on — D43.
     *
     * <p>The professional's inbox filters by status, but the refusal that matters is this one: if
     * {@code Accept} listed {@code PENDING_PAYMENT} among its sources, a professional who guessed a
     * reference could confirm a booking nobody has paid for. Only the two payment transitions leave
     * that state, and both are applied by a verified provider callback.
     */
    @Test
    @DisplayName("only the payment transitions leave PENDING_PAYMENT")
    void onlyPaymentLeavesPendingPayment() {
        List<String> leaving = ALL.stream().filter(t -> t.legalFrom(BookingStatus.PENDING_PAYMENT)).map(BookingTransition::action).toList();
        assertThat(leaving).containsExactlyInAnyOrder("payment confirmed", "payment abandoned");
    }

    @Test
    @DisplayName("a confirmed booking can still be cancelled, by either side")
    void confirmedCanBeCancelled() {
        assertThat(new BookingTransition.Cancel(CancelledBy.CUSTOMER, null).legalFrom(BookingStatus.CONFIRMED)).isTrue();
        assertThat(new BookingTransition.Cancel(CancelledBy.PROFESSIONAL, null).legalFrom(BookingStatus.REQUESTED)).isTrue();
        assertThat(new BookingTransition.Cancel(CancelledBy.PLATFORM, null).legalFrom(BookingStatus.COMPLETED))
            .as("a completed session cannot be un-happened by cancelling it")
            .isFalse();
    }

    @Test
    @DisplayName("no transition claims a source it also produces — nothing is a self-loop")
    void noSelfLoops() {
        for (BookingTransition t : ALL) {
            assertThat(t.from()).as("%s would loop on itself", t.action()).doesNotContain(t.to());
        }
    }

    @Test
    @DisplayName("every transition names itself, and the names are distinct")
    void actionsAreDistinct() {
        List<String> actions = ALL.stream().map(BookingTransition::action).toList();
        assertThat(actions).doesNotContainNull().doesNotHaveDuplicates();
    }

    /**
     * The sealed hierarchy is the point of the design: adding a permitted subtype must break every
     * switch that has not been updated. This asserts the list above is complete, so that a new
     * transition fails here too rather than silently going untested.
     */
    @Test
    @DisplayName("this test knows about every permitted subtype")
    void coversEveryPermittedSubtype() {
        Class<?>[] permitted = BookingTransition.class.getPermittedSubclasses();
        assertThat(permitted).isNotNull();
        Set<Class<?>> tested = ALL.stream().map(Object::getClass).collect(Collectors.toSet());
        assertThat(tested)
            .as("a new BookingTransition was added without being added to ALL in this test")
            .containsExactlyInAnyOrder(permitted);
    }
}
