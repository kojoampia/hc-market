package net.jojoaddison.service;

import java.util.Set;
import net.jojoaddison.domain.enumeration.DisputeStatus;

/**
 * The dispute state machine, sealed for the same reason {@link BookingTransition} is: every legal
 * move is a permitted subtype, so adding one is a compile error at every switch that has not been
 * updated rather than a silently missing side effect.
 *
 * <pre>
 *   OPEN ──review──▶ UNDER_REVIEW ──uphold──▶ RESOLVED   (reverses the ledger entry)
 *                          └───────reject──▶ REJECTED   (the ledger is untouched)
 * </pre>
 *
 * <p><strong>This is deliberately a separate lifecycle from {@code BookingStatus}</strong>
 * (decisions.md D23). A booking can be disputed and still be completed — those are two facts about
 * the same booking, and folding them into one enum would force one of them to be discarded.
 *
 * <p>There is no transition back from {@code RESOLVED} or {@code REJECTED}. Reopening a resolved
 * dispute would mean either double-reversing a ledger entry or un-reversing one, and both are worse
 * than raising the question again with a fresh record.
 */
public sealed interface DisputeTransition {
    /** The states this transition may be applied from. */
    Set<DisputeStatus> from();

    /** The state the dispute ends in. */
    DisputeStatus to();

    /** What the audit row records. */
    String action();

    default boolean legalFrom(DisputeStatus status) {
        return status != null && from().contains(status);
    }

    /** The desk picks the dispute up. Nothing moves yet; this records that someone is looking. */
    record Review() implements DisputeTransition {
        @Override
        public Set<DisputeStatus> from() {
            return Set.of(DisputeStatus.OPEN);
        }

        @Override
        public DisputeStatus to() {
            return DisputeStatus.UNDER_REVIEW;
        }

        @Override
        public String action() {
            return "review";
        }
    }

    /**
     * The desk finds for the customer. This is the only transition that moves money.
     *
     * <p>{@code refundMinor} is the amount to reverse, and it is not negative here — the sign is
     * applied by payout when it writes the compensating entry, so this column reads the same way as
     * every other money column in the estate. A null means the whole earning.
     *
     * <p>Upholding is legal from {@code OPEN} as well as {@code UNDER_REVIEW}: an obviously correct
     * complaint should not need a ceremonial state change first.
     */
    record Uphold(String resolution, Long refundMinor) implements DisputeTransition {
        @Override
        public Set<DisputeStatus> from() {
            return Set.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);
        }

        @Override
        public DisputeStatus to() {
            return DisputeStatus.RESOLVED;
        }

        @Override
        public String action() {
            return "uphold";
        }
    }

    /** The desk finds for the professional. The ledger is not touched. */
    record Reject(String resolution) implements DisputeTransition {
        @Override
        public Set<DisputeStatus> from() {
            return Set.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);
        }

        @Override
        public DisputeStatus to() {
            return DisputeStatus.REJECTED;
        }

        @Override
        public String action() {
            return "reject";
        }
    }
}
