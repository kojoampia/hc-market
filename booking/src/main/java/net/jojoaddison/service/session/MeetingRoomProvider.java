package net.jojoaddison.service.session;

/**
 * The online-session seam — {@code decisions.md} D86, backlog WP-17. Built on D17's shape.
 *
 * <h2>D17's v1 needs no provider at all, and that is the first thing to know</h2>
 *
 * <p>WP-17 has sat BLOCKED on "budget for a video provider" since D17, and re-reading D17 shows the
 * block applies to the <strong>upgrade</strong> and not to v1. Its recommendation is:
 * <em>"the professional supplies their own meeting link (Meet, Zoom, whatever they already use); the
 * platform stores it and reveals it an hour before, which is the promise the prototype makes."</em>
 * That needs no account, no budget and no integration — a link on the booking and a read-time
 * visibility rule. Daily.co is named as <em>"the upgrade if a no-account, in-browser room with a
 * waiting room is wanted later"</em>.
 *
 * <p>So this interface is the seam for the upgrade, and {@link UnconfiguredMeetingRoomProvider} —
 * which is what answers on every estate today — is not a refusal. It reports
 * {@link MeetingRoom#professionalSupplied()}, which <strong>is</strong> D17's v1 as far as this seam is
 * concerned: nobody hosts a room, and whatever link exists came from the professional.
 *
 * <h2>What this deliberately does not model</h2>
 *
 * <p><strong>No recording, and no method that could start one.</strong> D17 is explicit:
 * <em>"hosting a room where health matters are discussed drags in recording, retention and consent —
 * three problems the platform does not otherwise have. Recommend no recording in v1, which keeps it
 * that way."</em> An interface with a {@code startRecording} on it would have decided that question
 * the way {@code PaymentProvider} refuses to decide who pays the professional, and for the same
 * reason: a wrong abstraction costs more to remove than no abstraction costs to add.
 *
 * <p><strong>No scheduled reveal.</strong> D17 notes there is no scheduler anywhere in this estate and
 * that the honest cheap version computes visibility at read time from the booking's own
 * {@code scheduledDate}/{@code scheduledTime}. Nothing here pushes, notifies or wakes up; a room is
 * created or it is not, and <em>when the customer may see it</em> is a read-side question this seam has
 * no opinion about.
 *
 * <p><strong>No identity, no waiting room, no moderation.</strong> Those are the features an upgrade
 * would be bought for, and every one of them is a different provider's vocabulary. Naming them here
 * would be inventing the API of a provider nobody has chosen — which is what D50 refused for payments
 * and what this package refuses for sessions.
 *
 * <h2>Nothing calls this yet</h2>
 *
 * <p>It is reachable from {@code SessionConfiguration} and from nowhere else, deliberately: a booking
 * has no field to store a room in ({@code decisions.md} D17 — <em>"Booking has nowhere to put a
 * link"</em>), and adding one is a JDL change with a Liquibase changelog behind it. That is the work
 * WP-17's v1 actually needs, it is <strong>unblocked</strong>, and it is recorded as its own item
 * rather than smuggled in behind a seam.
 */
public interface MeetingRoomProvider {
    /**
     * What this provider answers to. A single lower-case word: it is a property key and a log value, and
     * the payments seam learned the hard way that a hyphen has to survive more places than you expect.
     */
    String name();

    /**
     * Create, or decline to create, a room for one booking.
     *
     * <p>Takes the booking's reference and nothing else. Not the customer's login, not the
     * professional's, not the care summary — a provider that hosts a room needs to know that a room is
     * wanted and needs to know nothing about who is in it or why. That narrowness is the point: it is
     * what keeps this seam off the erasure sweep's list entirely (D31/D35/D38/D39), because there is
     * nothing about a person here to come back and redact.
     *
     * @param bookingReference the booking wanting a room, for the provider's own idempotency
     * @return where the room is, or {@link MeetingRoom#professionalSupplied()} when nobody hosts one
     */
    MeetingRoom create(String bookingReference);
}
