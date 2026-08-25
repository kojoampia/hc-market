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
        boolean reviewed
    ) {}

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
