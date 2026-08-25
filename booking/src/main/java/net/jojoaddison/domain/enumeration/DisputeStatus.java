package net.jojoaddison.domain.enumeration;

/**
 * D23. Deliberately a SEPARATE lifecycle from BookingStatus rather than more values on it: a
 * booking can be disputed and still be completed, so folding disputes into that enum would force
 * one of the two facts to be discarded. BookingStatus stays the clean seven-state machine its
 * sealed BookingTransition set is built around.
 *
 * OPEN ──review──▶ UNDER_REVIEW ──uphold───▶ RESOLVED   (reverses the ledger row)
 * └───────reject────▶ REJECTED   (the ledger is untouched)
 */
public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    REJECTED,
}
