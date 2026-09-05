package net.jojoaddison.service.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import net.jojoaddison.service.SlotTime;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.BookingHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the booking service's slice of the seed.
 *
 * <p>All four sections land in the one {@code Booking} table. Each seeded booking also gets one
 * {@code BookingStatusChange} recording how it arrived in its state, so the audit is not empty from
 * day one — a history that starts only at the first live transition would make every seeded booking
 * look like it appeared from nowhere.
 */
@Service
public class BookingSeeder {

    /**
     * The zone every seeded booking's wall clock belongs to — {@code decisions.md} D21. Ghana is
     * UTC+0 all year, so this was correct while it was implicit; what changes is that it is written
     * down. Live bookings take theirs from the professional, through the catalogue.
     */
    static final String DEFAULT_ZONE_ID = "Africa/Accra";

    private static final Logger LOG = LoggerFactory.getLogger(BookingSeeder.class);

    private final BookingQueryRepository bookingRepository;
    private final BookingHistoryRepository statusChangeRepository;

    public BookingSeeder(BookingQueryRepository bookingRepository, BookingHistoryRepository statusChangeRepository) {
        this.bookingRepository = bookingRepository;
        this.statusChangeRepository = statusChangeRepository;
    }

    public boolean alreadySeeded() {
        return bookingRepository.count() > 0;
    }

    @Transactional
    public void clear() {
        statusChangeRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
    }

    @Transactional
    public void load(SeedFile seed, boolean anchorDates) {
        // decisions.md D48. Not LocalDate.now(): the calendar is the estate's, and the four seeded
        // services have to arrive at the same number or their dates stop lining up with each other.
        // This one is the pivot — catalog's slot dates and payout's ledger rows are both read against
        // what this shift writes.
        long shift = SeedCalendar.shiftDays(seed.meta().demoToday(), anchorDates);
        if (shift != 0) {
            // The day is DERIVED from the shift rather than read from the clock a second time: two
            // reads either side of Accra midnight would print a date the seed was not loaded against,
            // in the one log line whose job is to explain a disagreement between services.
            LOG.info(
                "shifting every seed date by {} days: {} -> {} in {}",
                shift,
                seed.meta().demoToday(),
                seed.meta().demoToday().plusDays(shift),
                SeedCalendar.SEED_ZONE
            );
        } else {
            // A run that shifts nothing used to log nothing at all, and "no line" is ambiguous between
            // the two ways of getting here. The quality box is always the first of them, so the one
            // estate anybody audits said nothing whatever about its own calendar. One line, naming
            // which case and the zone, is enough to read it off the log.
            LOG.info(
                "seed dates unshifted ({}): loading them as written against {} in {}",
                anchorDates ? "anchored" : "today IS the demo day",
                seed.meta().demoToday(),
                SeedCalendar.SEED_ZONE
            );
        }

        int n = 0;
        for (SeedFile.SeedRequest r : orEmpty(seed.requests())) {
            n += save(base(r, shift).scheduledDate(r.requestedDate().plusDays(shift)).scheduledTime(SlotTime.parse(r.requestedTime()))
                .status(BookingStatus.valueOf(r.status())).customerNote(r.note())
                .raisedAt(atStartOfDay(r.raisedOn(), shift)).reviewed(false));
        }
        for (SeedFile.SeedBooking b : orEmpty(seed.bookings())) {
            BookingStatus status = BookingStatus.valueOf(b.status());
            Booking booking = base(b, shift).scheduledDate(b.scheduledDate().plusDays(shift)).scheduledTime(SlotTime.parse(b.scheduledTime()))
                .status(status).customerNote(b.customerNote()).onBehalfOf(b.onBehalfOf())
                .raisedAt(atStartOfDay(b.scheduledDate().minusDays(3), shift))
                .reviewed(Boolean.TRUE.equals(b.reviewed()));
            if (status == BookingStatus.COMPLETED) {
                booking.completedAt(atStartOfDay(b.scheduledDate(), shift));
            }
            if (status == BookingStatus.CANCELLED) {
                booking.cancelledAt(atStartOfDay(b.scheduledDate().minusDays(1), shift));
            }
            n += save(booking);
        }
        for (SeedFile.SeedAppointment a : orEmpty(seed.appointments())) {
            n += save(base(a, shift).scheduledDate(a.scheduledDate().plusDays(shift)).scheduledTime(SlotTime.parse(a.scheduledTime()))
                .status(BookingStatus.valueOf(a.status())).customerNote(a.note())
                .raisedAt(atStartOfDay(a.scheduledDate().minusDays(2), shift)).reviewed(false));
        }
        for (SeedFile.SeedSession s : orEmpty(seed.sessions())) {
            n += save(base(s, shift).scheduledDate(s.completedDate().plusDays(shift)).scheduledTime(SlotTime.parse(s.startedTime()))
                .status(BookingStatus.valueOf(s.status()))
                .raisedAt(atStartOfDay(s.completedDate().minusDays(5), shift))
                .completedAt(atStartOfDay(s.completedDate(), shift))
                // The prototype's history predates its review data, so nothing here is marked
                // reviewed. Leaving it false is what makes the seeded sessions reviewable, which
                // spec §14's cycle test needs.
                .reviewed(false));
        }
        LOG.info("seeded {} bookings across requests, bookings, appointments and sessions", n);
    }

    /** Everything the four sections have in common. */
    private Booking base(SeedFile.Common c, long shift) {
        return new Booking()
            .reference(c.ref())
            .customerLogin(c.customerLogin())
            .customerName(c.customerName())
            .professionalRef(c.professionalRef())
            .professionalLogin(c.professionalLogin())
            .serviceRef(c.serviceRef())
            .serviceName(c.serviceName())
            .priceMinor(c.priceMinor())
            .currency(c.currency())
            .deliveryMode(DeliveryMode.valueOf(c.deliveryMode()))
            // decisions.md D21. Not in the seed file: the seed is REGENERATED from the prototype
            // and asserts its figures, and the prototype carries no zone — every session in it is
            // in Accra implicitly, which is the assumption D21 makes explicit rather than one to
            // encode into 256 extracted records. Catalog's seeder defaults the same way.
            .zoneId(DEFAULT_ZONE_ID)
            // The prototype has no per-booking consent flag; sharing is off unless chosen, which is
            // the safe default for something that exposes conditions, allergies and medications.
            .careSummaryShared(false);
    }

    private int save(Booking booking) {
        Booking saved = bookingRepository.save(booking);
        statusChangeRepository.save(
            new BookingStatusChange()
                .fromStatus(null)
                .toStatus(saved.getStatus())
                .actor("seed")
                .occurredAt(saved.getRaisedAt())
                .note("loaded from demo/seed-data.json")
                .booking(saved)
        );
        return 1;
    }

    private static Instant atStartOfDay(LocalDate date, long shift) {
        return date.plusDays(shift).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
