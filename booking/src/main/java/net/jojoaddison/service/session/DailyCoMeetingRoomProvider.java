package net.jojoaddison.service.session;

import java.util.List;

/**
 * Daily.co, the upgrade D17 named — {@code decisions.md} D86, backlog WP-17. A seam.
 *
 * <p>D17: <em>"Daily.co is the upgrade if a no-account, in-browser room with a waiting room is wanted
 * later; self-hosted Jitsi trades a licence cost for an operational one."</em> So this is one of two
 * candidates for a decision nobody has taken, and what it needs is money and a choice before it needs
 * code.
 *
 * <p><strong>Why a seam and not an implementation.</strong> The same argument D50 made for Hubtel and
 * MTN MoMo: with no account, no credentials and no documentation to hand, a signature scheme, a field
 * path or a room-lifetime written here would be invention that passes the mocks written to match it.
 * The difference from payments is that nothing here holds money — but an invented video call is a
 * customer sent to a URL that does not work at the moment of their appointment, which is its own kind
 * of expensive.
 *
 * <p><strong>Recording stays absent even as a seam.</strong> D17 recommends none in v1 precisely
 * because it drags in retention and consent, so this class has no method that could start one and
 * {@link #needs()} does not ask for one. Whoever implements it decides recording as a separate
 * question, with counsel, and not as a field they found already waiting.
 */
public class DailyCoMeetingRoomProvider extends ProviderAwaitingSelection {

    public static final String NAME = "dailyco";

    public DailyCoMeetingRoomProvider(boolean selected) {
        super(NAME, selected);
    }

    @Override
    public List<String> needs() {
        return List.of(
            "a paid account and its API key",
            "a decision on room lifetime and whether a waiting room is wanted",
            "a decision on recording, which D17 recommends against in v1 because of retention and consent",
            "a field on Booking to store the room in, which does not exist yet"
        );
    }
}
