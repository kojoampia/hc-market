package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import net.jojoaddison.domain.Booking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The free-cancellation window.
 *
 * <p>This decides whether a customer is charged 50% of a session they did not attend, so it is worth
 * pinning precisely. {@code isLate} touches neither repository, so the collaborators are null here on
 * purpose — a Spring context would add a minute to the build and prove nothing this does not.
 *
 * <p>The two properties that matter are both easy to get backwards:
 *
 * <ul>
 *   <li>the window is measured from <strong>now to the appointment</strong>, not from when the
 *       booking was made — a booking made in January and cancelled an hour before is late;
 *   <li>a booking already in the past is <strong>not</strong> late — that is a no-show or an
 *       administrative tidy-up, and charging for cancelling something that already failed to happen
 *       would be wrong.
 * </ul>
 */
class BookingWorkflowLateCancellationTest {

    private static final int WINDOW_HOURS = 24;

    /** Collaborators are unused by isLate; passing null keeps this a unit test. */
    private final BookingWorkflow workflow = new BookingWorkflow(null, null, null, WINDOW_HOURS);

    private static Booking at(Instant when) {
        LocalDate date = LocalDate.ofInstant(when, ZoneOffset.UTC);
        LocalTime time = LocalTime.ofInstant(when, ZoneOffset.UTC).withSecond(0).withNano(0);
        return new Booking().reference("b-test").scheduledDate(date).scheduledTime("%02d:%02d".formatted(time.getHour(), time.getMinute()));
    }

    @Test
    @DisplayName("three hours before the appointment is late")
    void insideTheWindowIsLate() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        assertThat(workflow.isLate(at(now.plusSeconds(3 * 3600)), now)).isTrue();
    }

    @Test
    @DisplayName("ten days before the appointment is not late")
    void outsideTheWindowIsNotLate() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        assertThat(workflow.isLate(at(now.plusSeconds(10 * 24 * 3600)), now)).isFalse();
    }

    /**
     * The boundary. At exactly the window the cancellation is free — {@code < freeCancellationHours}
     * rather than {@code <=} — because "free cancellation up to 24 hours before" reads to a customer
     * as 24 hours being still free.
     */
    @Test
    @DisplayName("exactly 24 hours before is free; a minute later is not")
    void theBoundaryFavoursTheCustomer() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        assertThat(workflow.isLate(at(now.plusSeconds(24 * 3600)), now)).as("exactly 24h out").isFalse();
        assertThat(workflow.isLate(at(now.plusSeconds(24 * 3600 - 60)), now)).as("23h59m out").isTrue();
    }

    @Test
    @DisplayName("an appointment already in the past is not a late cancellation")
    void pastBookingsAreNotLate() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        assertThat(workflow.isLate(at(now.minusSeconds(3600)), now)).as("an hour ago").isFalse();
        assertThat(workflow.isLate(at(now.minusSeconds(30L * 24 * 3600)), now)).as("a month ago").isFalse();
    }

    /**
     * The window is relative to the appointment, not to when the booking was made. Two bookings for
     * the same slot, made months apart, must be treated identically.
     */
    @Test
    @DisplayName("when the booking was made does not affect the window")
    void ageOfTheBookingIsIrrelevant() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        Booking bookedLongAgo = at(now.plusSeconds(2 * 3600)).raisedAt(now.minusSeconds(200L * 24 * 3600));
        Booking bookedMinutesAgo = at(now.plusSeconds(2 * 3600)).raisedAt(now.minusSeconds(600));
        assertThat(workflow.isLate(bookedLongAgo, now)).isTrue();
        assertThat(workflow.isLate(bookedMinutesAgo, now)).isTrue();
    }

    @Test
    @DisplayName("a malformed time falls back to midday rather than throwing")
    void malformedTimeDoesNotThrow() {
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        Booking b = new Booking().reference("b-bad").scheduledDate(LocalDate.of(2026, 8, 26)).scheduledTime("not-a-time");
        // 2026-08-26T12:00Z is 27 hours out, so it is outside the window — the point is that a bad
        // value degrades to a defensible answer instead of failing a cancellation the customer is
        // entitled to make.
        assertThat(workflow.isLate(b, now)).isFalse();
    }

    @Test
    @DisplayName("the configured window is what is used, not a hard-coded 24")
    void windowIsConfigurable() {
        BookingWorkflow twoHourWindow = new BookingWorkflow(null, null, null, 2);
        Instant now = Instant.parse("2026-08-25T09:00:00Z");
        Booking threeHoursOut = at(now.plusSeconds(3 * 3600));
        assertThat(twoHourWindow.isLate(threeHoursOut, now)).as("outside a 2h window").isFalse();
        assertThat(workflow.isLate(threeHoursOut, now)).as("inside the 24h window").isTrue();
        assertThat(twoHourWindow.freeCancellationHours()).isEqualTo(2);
    }
}
