package net.jojoaddison.service.session;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hosted-room provider nobody has chosen yet — {@code decisions.md} D86, backlog WP-17.
 *
 * <p>The sessions equivalent of {@code ProviderAwaitingIntegration} in the payments seam, and it exists
 * for the same reason: an adapter can be <em>selected and dispatched to exactly as a real one would
 * be</em> while every call that would have to know how the provider speaks fails closed. D17 names
 * Daily.co and self-hosted Jitsi as the two candidates; neither has an account, a price or a chosen
 * API, so writing their calls would be invention.
 *
 * <p><strong>It refuses rather than falling back to the professional's link</strong>, and that is the
 * one decision in this class. Falling back would mean an estate that had deliberately enabled a hosted
 * provider silently behaved as though it had not — the customer relayed a link the professional may not
 * have supplied, with nothing anywhere saying the provider was never built. Refusing is loud, and
 * {@link UnconfiguredMeetingRoomProvider} is what an estate uses when it wants the fallback.
 *
 * <p>It says which of two things it is at startup, from {@link #isSelected()}: WARN for a provider that
 * is enabled and unwritten, nothing at all for one that is off. That is D45's
 * {@code integratedCalls()} idea reduced to the one bit that matters here — an estate that has turned
 * on a provider nobody wrote should find that out at boot rather than at the first session.
 */
public abstract class ProviderAwaitingSelection implements MeetingRoomProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderAwaitingSelection.class);

    private final String name;
    private final boolean selected;

    protected ProviderAwaitingSelection(String name, boolean selected) {
        this.name = name;
        this.selected = selected;
    }

    @Override
    public final String name() {
        return name;
    }

    /** Whether an estate has turned this provider on. Read by the announcement and by nothing else. */
    protected final boolean isSelected() {
        return selected;
    }

    /**
     * What this provider would need before it could be written. Subclasses answer; the list goes in the
     * refusal so the log names the missing thing rather than just the missing code.
     */
    public abstract List<String> needs();

    @jakarta.annotation.PostConstruct
    void announce() {
        if (selected) {
            LOG.warn(
                "sessions: the {} meeting-room provider is ENABLED and is NOT IMPLEMENTED — every online session will be refused. It needs: {}. See decisions.md D86 and backlog WP-17.",
                name,
                String.join(", ", needs())
            );
        }
    }

    @Override
    public MeetingRoom create(String bookingReference) {
        throw new UnsupportedOperationException(
            "the %s meeting-room provider is selected but not implemented; it needs %s (decisions.md D86, backlog WP-17)".formatted(
                    name,
                    String.join(", ", needs())
                )
        );
    }
}
