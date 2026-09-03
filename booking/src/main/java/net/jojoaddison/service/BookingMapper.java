package net.jojoaddison.service;

import java.util.Comparator;
import java.util.List;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.service.dto.BookingDtos.BookingView;
import net.jojoaddison.service.dto.BookingDtos.PaymentAction;
import net.jojoaddison.service.dto.BookingDtos.StatusChangeView;
import org.springframework.stereotype.Component;

/**
 * Entity to view mapping, written by hand rather than generated.
 *
 * <p>{@code visitAddress} is deliberately <strong>not</strong> on {@link BookingView}. It is a
 * customer's home address, needed by the professional attending a home visit and by nobody else, so
 * it does not travel on the shape that every list endpoint returns. The same instinct as keeping
 * {@code customerLogin} off the public review DTO.
 */
@Component
public class BookingMapper {

    public BookingView toView(Booking b) {
        return toView(b, null);
    }

    /**
     * The same view, carrying what the customer must do about a pending payment —
     * {@code decisions.md} D43.
     *
     * <p>One caller: the create endpoint, and only when the provider answered {@code PENDING}. Every
     * other view has null there, deliberately — a payment link is a thing to do now, not a property of
     * a booking that can be read back.
     */
    public BookingView toView(Booking b, PaymentAction payment) {
        return new BookingView(
            b.getReference(),
            b.getCustomerLogin(),
            b.getCustomerName(),
            b.getProfessionalRef(),
            b.getServiceRef(),
            b.getServiceName(),
            b.getPriceMinor() == null ? 0L : b.getPriceMinor(),
            b.getCurrency(),
            b.getScheduledDate(),
            SlotTime.format(b.getScheduledTime()),
            b.getDeliveryMode() == null ? null : b.getDeliveryMode().name(),
            b.getStatus() == null ? null : b.getStatus().name(),
            b.getCustomerNote(),
            b.getOnBehalfOf(),
            Boolean.TRUE.equals(b.getCareSummaryShared()),
            b.getRaisedAt(),
            b.getRespondedAt(),
            b.getCompletedAt(),
            b.getCancelledAt(),
            b.getCancelledBy() == null ? null : b.getCancelledBy().name(),
            b.getCancellationReason(),
            b.getLateCancellation(),
            Boolean.TRUE.equals(b.getReviewed()),
            payment
        );
    }

    public List<BookingView> toViews(List<Booking> bookings) {
        return bookings.stream().map(this::toView).toList();
    }

    /** Oldest first: the audit reads as a story, and a story runs forwards. */
    public List<StatusChangeView> toHistory(List<BookingStatusChange> changes) {
        return changes
            .stream()
            .sorted(Comparator.comparing(BookingStatusChange::getOccurredAt, Comparator.nullsFirst(Comparator.naturalOrder())))
            .map(c ->
                new StatusChangeView(
                    c.getFromStatus() == null ? null : c.getFromStatus().name(),
                    c.getToStatus() == null ? null : c.getToStatus().name(),
                    c.getActor(),
                    c.getOccurredAt(),
                    c.getNote()
                )
            )
            .toList();
    }
}
