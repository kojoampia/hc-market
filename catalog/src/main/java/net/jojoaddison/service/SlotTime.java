package net.jojoaddison.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one place {@code "07:00"} becomes a {@link LocalTime} and back.
 *
 * <p>{@code AvailabilitySlot.slotTime} was a {@code String maxlength(5)} until decisions.md D26.
 * That column sorted correctly only by the accident of zero-padded 24-hour text, and it accepted
 * {@code "7:00"} and {@code "25:99"} without complaint — the database had no opinion about either.
 * Making it a {@code LocalTime} moves that check into the type, but it moves the *parsing* problem
 * to the edge rather than removing it, so the edge is written down here once instead of three times.
 *
 * <h2>Why format explicitly rather than let Jackson do it</h2>
 *
 * <p>Jackson's ISO serialisation would emit {@code "07:00"} for a whole hour today, and
 * {@code "07:00:00"} the moment a seconds value appears or {@code WRITE_DATES_AS_TIMESTAMPS} is
 * flipped somewhere. The prototype's contract is {@code HH:mm} and every screen that renders a slot
 * assumes exactly five characters, so the wire format is pinned here rather than inherited from a
 * global Jackson setting that nothing in this service controls.
 */
public final class SlotTime {

    /** STRICT, so "7:00" is rejected rather than quietly widened to 07:00. */
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private SlotTime() {}

    /** Always five characters, always zero-padded. */
    public static String format(LocalTime time) {
        return time == null ? null : HH_MM.format(time);
    }

    /**
     * Parses a wire value, rejecting anything that is not {@code HH:mm} with a 400 rather than a
     * 500. The old column would have stored the bad value and surfaced it as a blank on a screen.
     */
    public static LocalTime parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a slot time is required, as HH:mm");
        }
        try {
            return LocalTime.parse(value.trim(), HH_MM);
        } catch (DateTimeParseException notATime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'%s' is not a valid slot time — expected HH:mm, e.g. 07:00".formatted(value));
        }
    }
}
