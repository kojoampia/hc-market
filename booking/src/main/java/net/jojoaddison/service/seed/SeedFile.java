package net.jojoaddison.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * The booking service's slice of {@code demo/seed-data.json}.
 *
 * <p>Four sections all become rows in the one {@code Booking} table, which is the whole point of
 * the aggregate: the prototype kept requests, the schedule and the history in separate arrays, and
 * they were separate only because a JavaScript file has no way to say "these are the same thing at
 * different points in its life".
 *
 * <pre>
 *   requests      -> REQUESTED    the professional's inbox
 *   bookings      -> as stated    the customer's four tabs
 *   appointments  -> CONFIRMED    the professional's schedule
 *   sessions      -> COMPLETED    the professional's history
 * </pre>
 *
 * <p>The date and time fields are named differently in each section — {@code requestedDate},
 * {@code scheduledDate}, {@code completedDate} — because the prototype named them for the screen
 * they appeared on. They are one field on the aggregate.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedFile(
    @JsonProperty("$meta") Meta meta,
    List<SeedBooking> bookings,
    List<SeedRequest> requests,
    List<SeedAppointment> appointments,
    List<SeedSession> sessions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, LocalDate demoToday, String note) {}

    /** Fields every section shares. Kept as an interface so the seeder can treat them uniformly. */
    public interface Common {
        String ref();
        String customerLogin();
        String customerName();
        String professionalRef();
        String professionalLogin();
        String serviceRef();
        String serviceName();
        String deliveryMode();
        String currency();
        Long priceMinor();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedBooking(
        String ref, String customerLogin, String customerName, String professionalRef, String professionalLogin,
        String serviceRef, String serviceName, String deliveryMode, String currency, Long priceMinor,
        LocalDate scheduledDate, String scheduledTime, String status, String customerNote, String onBehalfOf,
        Boolean reviewed
    ) implements Common {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedRequest(
        String ref, String customerLogin, String customerName, String professionalRef, String professionalLogin,
        String serviceRef, String serviceName, String deliveryMode, String currency, Long priceMinor,
        LocalDate requestedDate, String requestedTime, String status, String note, LocalDate raisedOn
    ) implements Common {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedAppointment(
        String ref, String customerLogin, String customerName, String professionalRef, String professionalLogin,
        String serviceRef, String serviceName, String deliveryMode, String currency, Long priceMinor,
        LocalDate scheduledDate, String scheduledTime, String status, String note
    ) implements Common {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedSession(
        String ref, String customerLogin, String customerName, String professionalRef, String professionalLogin,
        String serviceRef, String serviceName, String deliveryMode, String currency, Long grossMinor,
        LocalDate completedDate, String startedTime, String status
    ) implements Common {
        /** Sessions call it {@code grossMinor}; on the aggregate it is the price that was charged. */
        @Override
        public Long priceMinor() {
            return grossMinor;
        }
    }
}
