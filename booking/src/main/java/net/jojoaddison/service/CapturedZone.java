package net.jojoaddison.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one place a calendar offered by the catalogue becomes a {@code Booking.zoneId}.
 *
 * <p>{@code decisions.md} D60, backlog NEW-20. This is the write-side counterpart of
 * {@link BookingWorkflow}'s private {@code zoneOf}, and the two deliberately answer differently:
 * <strong>capture refuses a value it cannot read; the read side falls back.</strong> D58 argued the
 * read side — a zone tzdb cannot parse would otherwise make a booking impossible to preview
 * <em>and</em> impossible to cancel, a 500 on the money path over a string — and the point of
 * refusing here is that nothing this service writes can reach that fallback afterwards.
 *
 * <p>{@link SlotTime} is the shape this follows: the other string on the same builder chain that
 * has to become a time before it is stored, parsed in {@code service} and refused with a status
 * rather than stored and mis-read later.
 *
 * <h2>What it costs to get wrong</h2>
 *
 * <p>{@code booking.zone_id} is a {@code varchar(64)} with no parse, no check constraint and no enum
 * behind it, and until D60 whatever string catalog put on a professional's card was stored in it
 * verbatim. {@link BookingWorkflow#scheduledAt} reads that column to decide the instant an
 * appointment happens at, which is the boundary the 50% late-cancellation fee turns on. So an
 * unreadable value was stored once and read as Africa/Accra for ever afterwards, at WARN, on a
 * figure a customer is charged.
 *
 * <h2>Absent is a state; unreadable is an error</h2>
 *
 * <p>The two are not the same fault and do not get the same answer.
 *
 * <p><strong>Absent or blank still defaults</strong>, exactly as it did before D60. A catalogue one
 * release behind sends no {@code zoneId} at all — booking and catalog roll independently, which is
 * the deployment {@code decisions.md} D56 keeps payout's {@code on} parameter for — and refusing
 * there would fail every booking in the estate over a field that is empty for a reason and changes
 * nothing today. Ghana is UTC+0 all year.
 *
 * <p><strong>A non-blank value tzdb cannot read is refused.</strong> That is not deployment skew: no
 * release of catalog produces it, so it can only be a row somebody wrote wrong, and the two ways of
 * accepting it are both worse than refusing. Storing it makes the appointment unreadable for ever;
 * quietly replacing it with Accra records this platform's calendar for a professional who is
 * explicitly not in it, on a booking whose cancellation boundary is then off by up to fourteen
 * hours, with nothing anywhere disagreeing. This estate refuses at a boundary rather than storing
 * something wrong — a provider name it does not offer (D45), a currency and a mis-prefixed secret
 * (D50), a commission rate written as {@code 12} rather than {@code 0.12} (D57) — and this is the
 * same call one service along.
 *
 * <h2>What is stored round-trips by construction</h2>
 *
 * <p>The value written is {@link ZoneId#getId()} of the parsed zone rather than the string that was
 * offered, so anything in the column is readable by the same {@code ZoneId.of} that accepted it.
 * "Parsed at capture" is otherwise a property of the moment rather than of the row.
 *
 * <p>That is a real difference and not a formality, measured on JDK 25 across every id
 * {@code ZoneId.getAvailableZoneIds()} offers: all <strong>604</strong> region names are their own
 * {@code getId()}, so <em>no</em> professional's actual calendar is ever rewritten here — and the
 * offset spellings do move ({@code GMT+0} becomes {@code GMT}, {@code UT+1} becomes
 * {@code UT+01:00}). One column holding the same zone written two ways is a column that cannot be
 * compared as text, which is what it is.
 *
 * <p>The same measurement is why there is no length check: the longest region id is
 * <strong>32</strong> characters against a {@code varchar(64)}, and the offset forms are shorter
 * still, so a check here could never fire. An unreachable guard nobody removes is exactly the shape
 * this decision spent its argument on.
 *
 * <h2>No fourth zone constant</h2>
 *
 * <p>The default is {@link MarketCalendar#MARKET_ZONE} and {@code CustomerBookingResource}'s own
 * {@code DEFAULT_ZONE_ID} string is gone with it. The three named zone constants stay three, and
 * D58's read-side argument — that an unreadable row is read "in the same calendar it would have been
 * written in" — becomes structural rather than two spellings of Africa/Accra that happen to agree.
 * It is the right constant on its own terms too: when the professional's calendar is unknown, the
 * one being recorded is the marketplace's, which is the half of D55's split that {@code MARKET_ZONE}
 * exists for.
 */
public final class CapturedZone {

    private static final Logger LOG = LoggerFactory.getLogger(CapturedZone.class);

    private CapturedZone() {}

    /**
     * The zone to store on a booking, given what the catalogue offered for the professional.
     *
     * <p>Null or blank yields the marketplace's own calendar. Anything else is parsed, and a value
     * {@link ZoneId#of} will not accept is a <strong>502</strong>.
     *
     * <p>502 rather than the resource's other three refusals, and the distinction is the one
     * {@link CatalogClient} already draws: 503 is "the catalogue could not be asked", 404 is "it
     * answered, and there is no such thing", 409 is "it answered, and it disagrees with what you
     * sent". This is none of those — it answered, and the answer is unusable — which is what 502
     * means. A retry will not help until the row is fixed, so 503 would be a lie about the remedy.
     *
     * <p><strong>The refusal names neither the value nor the reference.</strong> The value arrived
     * over HTTP from another service and is free text in a {@code varchar(64)}: D44's rule that no
     * provider's words reach a response body is the same rule one wire along, and
     * {@code ExceptionTranslator} renders a {@code ResponseStatusException}'s reason as the
     * ProblemDetail's {@code detail}. The reference is the caller's own and telling it back teaches
     * nobody anything (D45). Both go to the ERROR below, because the person who can fix this reads
     * the log and needs to know which row.
     *
     * @param offered the catalogue's {@code zoneId} for the professional, possibly null or blank
     * @param professionalRef what to name in the log — never in the response
     */
    public static String of(String offered, String professionalRef) {
        if (offered == null || offered.isBlank()) {
            return MarketCalendar.MARKET_ZONE.getId();
        }
        try {
            return ZoneId.of(offered).getId();
        } catch (DateTimeException notAZone) {
            // ERROR rather than the read side's WARN: this one turned a customer away, and it stays
            // broken for every customer of that professional until somebody edits the row.
            LOG.error("professional {} carries a zoneId {} that is not a readable zone; no booking was made", professionalRef, offered);
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "the professional's calendar could not be read, so this booking was not made"
            );
        }
    }
}
