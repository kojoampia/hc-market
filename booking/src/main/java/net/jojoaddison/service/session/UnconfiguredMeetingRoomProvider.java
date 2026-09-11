package net.jojoaddison.service.session;

/**
 * What answers on every estate today: nobody hosts a room — {@code decisions.md} D86, backlog WP-17.
 *
 * <p><strong>This is D17's recommended v1 and not a stub.</strong> Its recommendation is that the
 * professional supplies their own meeting link and the platform relays it, so a provider that hosts
 * nothing is the correct answer rather than a placeholder for one. It throws nothing, refuses nothing
 * and needs no configuration.
 *
 * <p>It is the counterpart of {@code PaymentConfiguration.UnconfiguredPaymentProvider}, which reports
 * {@code OFF_PLATFORM} rather than failing — and the shape is copied deliberately, because the mistake
 * it avoids is the same one: if the default threw, every caller would have to special-case the normal
 * case, and the first one to forget would turn "we relay the professional's link" into an error the
 * customer sees.
 */
public class UnconfiguredMeetingRoomProvider implements MeetingRoomProvider {

    /**
     * The name the empty case answers to.
     *
     * <p>{@code "none"} is what it calls itself, and D46's finding one seam along is why that is safe
     * here and was not there: the payments registry had to stop *offering* the literal `none` to
     * customers because it pointed a client at the one name {@code choices()} exists to withhold. There
     * is no equivalent exposure here — nothing publishes a list of session providers and no client
     * chooses one — so the honest name is the plain one.
     */
    @Override
    public String name() {
        return "none";
    }

    @Override
    public MeetingRoom create(String bookingReference) {
        return MeetingRoom.professionalSupplied();
    }
}
