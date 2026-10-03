package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.service.BookingClient;
import net.jojoaddison.service.BookingClient.BookingSummary;
import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * A suppressed author name is audible, and the line says nothing about who was suppressed — NEW-89,
 * {@code decisions.md} D110.
 *
 * <p>D104 made {@code A BridgeCare customer} the public author whenever the booking named nobody, and
 * that value is correct. What was missing is that the suppression was <strong>indistinguishable from
 * the ordinary case in every log, metric and response</strong>: a client that stopped sending
 * {@code customerName} looked exactly like one that never sent it, and if {@code booking} ever stopped
 * sending {@code customerLogin} the fail-closed arm in {@link net.jojoaddison.service.ReviewAuthor}
 * would refuse <em>every</em> name — turning every subsequent review into the anonymous label, correct
 * by the rule and wrong about the world. There is deliberately no endpoint that can correct a review,
 * so the window between such a regression and somebody noticing is written permanently into rows
 * nothing can edit.
 *
 * <h2>The absence assertions are the point, and they matter more than the presence ones</h2>
 *
 * <p>This is a path where a disclosure would be permanent in a different way from the column itself:
 * a log is a place the erasure sweep does not reach and cannot re-key (D96's rule for the two sweeps).
 * So every value this test hands the resource is <em>unmistakable</em> — {@link #CALLER},
 * {@link #BOOKING_LOGIN} and {@link #SUPPLIED_NAME} appear in no vocabulary the resource could compose
 * from — and the two refusal cases assert their <strong>absence</strong> from the rendered line. A test
 * asserting only that the booking reference is present would pass a line that also carried the name.
 *
 * <p>What is deliberately <em>not</em> asserted absent is the two-letter monogram the suppressed name
 * would have produced: a two-character substring is short enough to collide with ordinary English in
 * the message, so such an assertion would be red on a correct tree one re-wording from now. The name
 * itself and its first word are asserted instead, which is what a leak would actually carry.
 *
 * <h2>Why the control is not decoration</h2>
 *
 * <p>{@link #anOrdinaryReviewSaysNothing()} is what stops this being satisfied by a resource that
 * warns on every review — which would spend the signal rather than add one, and would read as this
 * estate suppressing every name. And {@link #aRefusedWriteClaimsNoPublication()} holds the
 * <em>position</em> of the line: it is emitted after the row exists, so a 409 on the unique
 * {@code bookingReference} does not announce a publication that never happened.
 *
 * <h2>WARN, and the reason it is not ERROR</h2>
 *
 * <p>D97: an ERROR line is a fact about this estate that is wrong and that nobody chose; a caller
 * asking for something not allowed is a WARN. A booking made without a display name is an ordinary
 * caller state. The estate's ERROR channel is its one free signal — the only way a dead collector is
 * ever visible (D64, D73) — and nothing pages on it, so spending it here would cost more than it buys.
 * {@link #neitherReasonIsAnError()} asserts that separately from the two presence cases, because a
 * level is one edit away from being the thing that changed.
 */
class TheSuppressedAuthorNameIsAudibleTest {

    /** The JWT subject. Unmistakable on purpose: it must appear in no line this resource writes. */
    private static final String CALLER = "zzz.unmistakable.caller";

    /**
     * The booking summary's own {@code customerLogin}, deliberately a different string from
     * {@link #CALLER} so that an arm refusing on the <em>caller's</em> login can be told from one
     * refusing on the booking's. Both are identifiers and neither may be logged.
     */
    private static final String BOOKING_LOGIN = "yyy.unmistakable.booking";

    /** A genuine display name — a real person's words on a public page, and never a log's. */
    private static final String SUPPLIED_NAME = "Akosua Unmistakable Nkrumah";

    /** The booking reference the summary answers with. Platform-minted, so it may be logged. */
    private static final String REFERENCE = "b-2f8c11a4";

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger resourceLogger;

    /**
     * Scoped to the resource's own logger and to nothing wider. An appender on {@code ROOT} would make
     * every assertion here a statement about whatever else the JVM logged while it was attached.
     */
    @BeforeEach
    void listenToTheResourceAlone() {
        lines.start();
        resourceLogger = (Logger) LoggerFactory.getLogger(ReviewWriteResource.class);
        resourceLogger.addAppender(lines);
    }

    @AfterEach
    void stopListening() {
        resourceLogger.detachAppender(lines);
        lines.stop();
        SecurityContextHolder.clearContext();
    }

    // ----------------------------------------------------------------- the suppression is audible --

    @Test
    @DisplayName("a booking that named nobody is recorded at WARN, naming the booking")
    void theBookingThatNamedNobodyIsRecorded() {
        // booking launders the login into customerName for a booking made without a display name, so
        // this is the live shape of "the booking named nobody" rather than an absent field.
        List<ILoggingEvent> recorded = publish(CALLER, CALLER, BOOKING_LOGIN);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(recorded.get(0).getFormattedMessage()).contains(REFERENCE).contains("nobody");
    }

    @Test
    @DisplayName("an identifier that could not be read is recorded at WARN, naming the booking")
    void theUnreadableIdentifierIsRecorded() {
        List<ILoggingEvent> recorded = publish(SUPPLIED_NAME, CALLER, null);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(recorded.get(0).getFormattedMessage()).contains(REFERENCE).contains("identifier");
    }

    /**
     * Two facts, two lines — D104 §5's own rule, which refused to let "nobody supplied a name" and
     * "this person was erased" collapse onto one column value, applied one layer along to the log.
     *
     * <p>The comparison is on the <strong>pattern</strong> as well as on the rendered line, because
     * that is the half a single shared message survives: two arms interpolating the same reference into
     * one format string render identically, and comparing only the rendered text would then be
     * comparing two copies of the same sentence and reporting them equal for the right reason.
     */
    @Test
    @DisplayName("the two suppression reasons are two distinguishable lines")
    void theTwoReasonsAreDistinguishable() {
        ILoggingEvent namedNobody = only(publish(CALLER, CALLER, BOOKING_LOGIN));
        ILoggingEvent unreadable = only(publish(SUPPLIED_NAME, CALLER, null));

        assertThat(namedNobody.getMessage()).isNotEqualTo(unreadable.getMessage());
        assertThat(namedNobody.getFormattedMessage()).isNotEqualTo(unreadable.getFormattedMessage());
    }

    /**
     * The one input the two arms both answer, and which of them wins.
     *
     * <p>A booking that sent neither a {@code customerName} nor a {@code customerLogin} satisfies both
     * guards in {@code ReviewAuthor.authorship}, and it is reachable: the two fields travel on one
     * summary, so a booking release that dropped both drops both together. It is reported as the
     * nameless fact, because nothing was at risk of being published — there was no name to rule out.
     * Reporting an estate fault here would point an operator at {@code booking}'s identifier when what
     * happened is that nobody typed a name.
     */
    @Test
    @DisplayName("a booking with neither a name nor an identifier is reported as the nameless fact")
    void theNamelessBookingWithNoIdentifierIsTheNamelessFact() {
        String nameless = only(publish(null, CALLER, null)).getFormattedMessage();
        String unreadable = only(publish(SUPPLIED_NAME, CALLER, null)).getFormattedMessage();

        assertThat(nameless).contains("nobody").isNotEqualTo(unreadable);
    }

    // --------------------------------------------------------- and it carries nothing identifying --

    @Test
    @DisplayName("the line for a booking that named nobody carries neither identifier")
    void nothingIdentifyingReachesTheLineWhenTheBookingNamedNobody() {
        String line = only(publish(CALLER, CALLER, BOOKING_LOGIN)).getFormattedMessage();

        // CALLER is simultaneously the JWT subject and the laundered customerName here, which is
        // exactly the value D104 exists to keep off a public page.
        assertThat(line).doesNotContain(CALLER).doesNotContain(BOOKING_LOGIN);
    }

    @Test
    @DisplayName("the line for an unreadable identifier carries neither the supplied name nor the login")
    void nothingIdentifyingReachesTheLineWhenAnIdentifierIsUnreadable() {
        String line = only(publish(SUPPLIED_NAME, CALLER, null)).getFormattedMessage();

        assertThat(line).doesNotContain(SUPPLIED_NAME).doesNotContain("Akosua").doesNotContain(CALLER);
    }

    // ------------------------------------------------------------------------------ the controls --

    @Test
    @DisplayName("a review published under a genuine display name logs nothing at all")
    void anOrdinaryReviewSaysNothing() {
        assertThat(publish(SUPPLIED_NAME, CALLER, BOOKING_LOGIN)).isEmpty();
    }

    @Test
    @DisplayName("neither suppression is logged at ERROR")
    void neitherReasonIsAnError() {
        assertThat(publish(CALLER, CALLER, BOOKING_LOGIN)).noneMatch(event -> event.getLevel() == Level.ERROR);
        assertThat(publish(SUPPLIED_NAME, CALLER, null)).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    /**
     * The line claims a publication, so it may only be written once there is one.
     *
     * <p>The unique constraint on {@code bookingReference} is the real mutex and it fires after the
     * status checks above it, so a refused write is a reachable state on a path whose author name was
     * already suppressed. A line emitted before the save would announce a review that does not exist,
     * in a booking reference somebody would then go looking for.
     */
    @Test
    @DisplayName("a write the unique constraint refuses announces no publication")
    void aRefusedWriteClaimsNoPublication() {
        ReviewRepository reviews = Mockito.mock(ReviewRepository.class);
        MarketplaceQueryRepository marketplace = Mockito.mock(MarketplaceQueryRepository.class);
        BookingClient booking = Mockito.mock(BookingClient.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(CALLER, null, List.of()));
        when(booking.findBooking(anyString(), anyString())).thenReturn(
            Optional.of(new BookingSummary(REFERENCE, BOOKING_LOGIN, CALLER, "p1", "COMPLETED", false))
        );
        when(marketplace.findByReference("p1")).thenReturn(Optional.of(new Professional()));
        when(reviews.save(any(Review.class))).thenThrow(new DataIntegrityViolationException("unique_review_booking_reference"));

        ReviewWriteResource resource = new ReviewWriteResource(reviews, marketplace, booking, new MarketCalendar());

        assertThatThrownBy(() ->
            resource.publish(new ReviewWriteResource.PublishReview(REFERENCE, 5, "Punctual, patient and clear."), "Bearer token")
        )
            .isInstanceOf(ResponseStatusException.class);

        assertThat(lines.list).isEmpty();
    }

    // ----------------------------------------------------------------------------------------- --

    /**
     * Publishes one review and answers whatever the resource's own logger recorded while it ran.
     *
     * <p>The mocks are built per call rather than injected as fields, for the reason
     * {@code TheReviewAuthorIsNeverALoginTest} gives at the same helper: two of the cases here publish
     * twice, and {@code reviews.save(…)} verification is exactly-once.
     */
    private List<ILoggingEvent> publish(String customerName, String callerLogin, String bookingCustomerLogin) {
        ReviewRepository reviews = Mockito.mock(ReviewRepository.class);
        MarketplaceQueryRepository marketplace = Mockito.mock(MarketplaceQueryRepository.class);
        BookingClient booking = Mockito.mock(BookingClient.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(callerLogin, null, List.of()));
        when(booking.findBooking(anyString(), anyString())).thenReturn(
            Optional.of(new BookingSummary(REFERENCE, bookingCustomerLogin, customerName, "p1", "COMPLETED", false))
        );
        when(marketplace.findByReference("p1")).thenReturn(Optional.of(new Professional()));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // True, so the one WARN this resource already had cannot be mistaken for one of ours.
        when(booking.markReviewed(anyString(), anyString())).thenReturn(true);

        int before = lines.list.size();
        ReviewWriteResource resource = new ReviewWriteResource(reviews, marketplace, booking, new MarketCalendar());
        resource.publish(new ReviewWriteResource.PublishReview(REFERENCE, 5, "Punctual, patient and clear."), "Bearer token");

        return List.copyOf(lines.list.subList(before, lines.list.size()));
    }

    /** The one event of a publication that must produce exactly one. */
    private ILoggingEvent only(List<ILoggingEvent> recorded) {
        assertThat(recorded).hasSize(1);
        return recorded.get(0);
    }
}
