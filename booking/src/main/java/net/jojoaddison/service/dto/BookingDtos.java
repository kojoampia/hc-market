package net.jojoaddison.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The booking service's public shapes — spec §6.
 *
 * <p>Money is minor units throughout. The 12% brokerage fee is inside {@code priceMinor}, not added
 * to it, so a receipt's gross is the price the customer already agreed to.
 */
public final class BookingDtos {

    private BookingDtos() {}

    public record BookingView(
        String reference,
        String customerLogin,
        String customerName,
        String professionalRef,
        String serviceRef,
        String serviceName,
        long priceMinor,
        String currency,
        LocalDate scheduledDate,
        String scheduledTime,
        String deliveryMode,
        String status,
        String customerNote,
        String onBehalfOf,
        boolean careSummaryShared,
        Instant raisedAt,
        Instant respondedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelledBy,
        String cancellationReason,
        Boolean lateCancellation,
        boolean reviewed,
        /**
         * What the customer must do about the money — {@code decisions.md} D43. Null everywhere
         * except the response to {@code POST /api/bookings}, and null there too unless the payment
         * came back pending.
         *
         * <p>It is on this record rather than in a wrapper around it because the create response is
         * the one place a client both needs it and is already reading a booking, and changing the
         * shape of that response would break every client in the estate over a field that is null in
         * almost every one of them. It is <em>not</em> a property of the booking: a next action is
         * something to do now, and re-reading a booking an hour later must not hand out a payment link
         * again.
         */
        PaymentAction payment
    ) {}

    /**
     * The customer's next move on a pending payment — {@code decisions.md} D43.
     *
     * <p>Flat strings rather than the seam's own types, for the reason every DTO here exists: the wire
     * shape should not change because an enum was renamed. {@code action} is a
     * {@code PaymentNextAction.Kind} name — {@code VISIT_URL} or {@code AWAIT_DEVICE_PROMPT} — and a
     * client switches on it rather than on {@code url} being present, so "a prompt is on your phone"
     * is a case it handles rather than a missing link.
     *
     * @param state the {@code PaymentState} name, so a client can tell "waiting on you" from
     *     "nothing to pay" without inferring it from the booking's status
     * @param action what to do
     * @param url where to send them, for {@code VISIT_URL} and null otherwise
     */
    public record PaymentAction(String state, String action, String url) {}

    /** The append-only audit — every transition a booking has made, oldest first. */
    public record StatusChangeView(String fromStatus, String toStatus, String actor, Instant occurredAt, String note) {}

    public record BookingDetail(BookingView booking, List<StatusChangeView> history) {}

    /** Wizard step 4. The professional and service are references; the price is looked up, not sent. */
    public record CreateBooking(
        String professionalRef,
        String professionalLogin,
        /**
         * The customer's display name. Supplied by the client because the JWT carries only a login,
         * and "kojo.ampia.addison asked for a Follow-up consultation" is what a professional
         * otherwise reads in their inbox. Falls back to the login when absent.
         */
        String customerName,
        String serviceRef,
        String serviceName,
        Long priceMinor,
        String currency,
        LocalDate scheduledDate,
        String scheduledTime,
        String deliveryMode,
        String customerNote,
        String onBehalfOf,
        String visitAddress,
        Boolean careSummaryShared
    ) {}

    public record DeclineRequest(String reason) {}

    public record ProposeRequest(LocalDate date, String time) {}

    public record CancelRequest(String reason) {}

    /**
     * What the cancel modal shows <em>before</em> the customer commits.
     *
     * <p>The prototype tells you the fee before you cancel, not after, and that is the whole
     * usefulness of the screen — so this is a preview endpoint, separate from the cancel itself.
     */
    public record CancellationPreview(
        String reference,
        boolean lateCancellation,
        int freeCancellationHours,
        long hoursUntilAppointment,
        long priceMinor,
        String currency
    ) {}

    /**
     * The receipt modal — spec §6's "gross, commission, total".
     *
     * <p>{@code grossMinor} is the price the customer agreed to; the brokerage fee is INSIDE it, not
     * added to it, so {@code total} equals {@code gross}. That is the prototype's convention and
     * saying it in the payload is cheaper than a reader inferring it wrongly.
     */
    public record Receipt(
        String bookingReference,
        String serviceName,
        String professionalRef,
        LocalDate scheduledDate,
        String scheduledTime,
        String status,
        long grossMinor,
        long commissionMinor,
        long netMinor,
        long totalMinor,
        String commissionRate,
        String currency
    ) {}

    /** The professional's schedule, grouped by day as the screen renders it. */
    public record ScheduleDay(LocalDate date, List<BookingView> bookings) {}
}
