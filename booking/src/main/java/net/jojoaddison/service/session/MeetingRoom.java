package net.jojoaddison.service.session;

/**
 * Where an online session happens, or the fact that this platform is not hosting it — D86, WP-17.
 *
 * <p>Two states and no more, because D17 chose two: either a provider hosts a room and there is a URL,
 * or the professional supplies their own link and this platform relays it. There is deliberately no
 * "pending", no "failed" and no "expired" — a room either exists now or nobody here is making one, and
 * every richer state belongs to a provider nobody has chosen.
 *
 * @param hosted whether a provider created a room
 * @param url where it is, or {@code null} when {@code hosted} is false
 * @param provider which provider hosts it, or {@code null}. Never a reason or a message: a provider's
 *     prose does not travel through this record, for the reason D44 gives one seam along — it is the
 *     road by which somebody else's words end up in something this estate keeps.
 */
public record MeetingRoom(boolean hosted, String url, String provider) {
    /**
     * D17's v1: nobody hosts a room and the professional's own link is the session.
     *
     * <p><strong>Not a failure.</strong> This is the recommended shape and what answers on every estate
     * today, so it must not read as a refusal at a call site — the same distinction
     * {@code PaymentState.OFF_PLATFORM} draws from {@code FAILED} next door, and for the same reason: a
     * caller that treats "we are not doing this" as "this went wrong" builds an error path for the
     * normal case.
     */
    public static MeetingRoom professionalSupplied() {
        return new MeetingRoom(false, null, null);
    }

    /** A provider created a room. */
    public static MeetingRoom hostedAt(String url, String provider) {
        if (url == null || url.isBlank()) {
            // A hosted room with no URL is the one internally inconsistent value this record could
            // hold, and it would read at a call site as a room the customer can be sent to.
            throw new IllegalArgumentException("a hosted meeting room must have a url");
        }
        return new MeetingRoom(true, url, provider);
    }
}
