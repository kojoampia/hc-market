package net.jojoaddison.domain.enumeration;

/**
 * Where a professional sits in the trust chain. The prototype had only a boolean `verified`;
 * four states exist because suspension has to be distinguishable from never-verified.
 */
public enum VerificationState {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    SUSPENDED,
}
