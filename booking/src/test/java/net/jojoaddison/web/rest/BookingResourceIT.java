package net.jojoaddison.web.rest;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;

/**
 * Test fixtures for {@link Booking}.
 *
 * <p><strong>This class holds no tests, and that is deliberate.</strong> It survives only because
 * the generated {@code BookingStatusChangeResourceIT} calls {@code BookingResourceIT.createEntity(em)}
 * to build its required parent.
 *
 * <p>The generated CRUD suite that JHipster puts here — 141 tests against {@code /api/bookings} —
 * went with the generated {@code BookingResource}, which {@link CustomerBookingResource} and
 * {@link ProBookingResource} replaced. The generated resource mapped {@code /api/bookings/{id}} on a
 * numeric id while spec §6 needs {@code /api/bookings/{ref}} on the business reference, and two
 * controllers claiming one path template is an ambiguous mapping that stops the app booting.
 *
 * <p><strong>Regeneration restores those 141 tests over this file</strong>, and they then fail
 * against endpoints that no longer exist — 137 failures, which is how this was found. Deleting
 * {@code BookingResource.java} after a regeneration is not enough; this file has to be restored too.
 *
 * <p>Keeping the {@code ...IT} name is deliberate: the caller is a generated file and will be
 * rewritten with this exact reference every time.
 */
public final class BookingResourceIT {

    private BookingResourceIT() {}

    /**
     * A valid Booking with every required column set, including the ones added by hand.
     *
     * <p>Two overloads because the generated callers use both forms — {@code BookingStatusChangeResourceIT}
     * calls {@code createEntity()} in one place and {@code createEntity(em)} in another, and which
     * one a regeneration emits has changed between JHipster versions. Supporting both means a
     * regeneration cannot break the build by picking the other.
     */
    public static Booking createEntity() {
        return createEntity(null);
    }

    public static Booking createUpdatedEntity() {
        return createUpdatedEntity(null);
    }

    public static Booking createEntity(EntityManager em) {
        return new Booking()
            .reference("b-test-" + System.nanoTime())
            .customerLogin("test.customer")
            .customerName("Test Customer")
            .professionalRef("p1")
            // Required, and added after generation (decisions.md D12) — a generated fixture would
            // not set it and every insert would fail on the not-null constraint.
            .professionalLogin("akosua.mensah")
            .serviceRef("s1a")
            .serviceName("Test service")
            .priceMinor(15000L)
            .currency("GHS")
            .scheduledDate(LocalDate.now().plusDays(7))
            .scheduledTime(LocalTime.of(10, 0))
            .deliveryMode(DeliveryMode.ONLINE)
            .status(BookingStatus.REQUESTED)
            .careSummaryShared(false)
            .raisedAt(Instant.now())
            .reviewed(false);
    }

    public static Booking createUpdatedEntity(EntityManager em) {
        return createEntity(em).status(BookingStatus.CONFIRMED).scheduledTime(LocalTime.of(14, 30));
    }
}
