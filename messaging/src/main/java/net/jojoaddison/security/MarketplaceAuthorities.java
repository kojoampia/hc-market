package net.jojoaddison.security;

/**
 * Authorities this product adds beyond JHipster's.
 *
 * <p>A NEW file rather than a constant on the generated {@link AuthoritiesConstants}, which
 * regeneration would rewrite. Deliberately identical to booking's and catalog's copies: the estate
 * shares one signing key, so an authority means the same thing in every service or it means nothing.
 */
public final class MarketplaceAuthorities {

    /**
     * The brokerage desk — decisions.md D23, D16, and D24 for erasure.
     *
     * <p>Not {@code ROLE_ADMIN}. Here it redacts a real person's message history irreversibly, which
     * is a narrower and more consequential power than general administration.
     */
    public static final String BROKERAGE = "ROLE_BROKERAGE";

    /**
     * Erase one named customer, and nothing else — decisions.md D37, D38.
     *
     * <p>Held by no person and granted by no login. It arrives on one credential only: the
     * short-lived token booking mints when an operator asks for a complete erasure. See
     * {@link ErasureFanoutToken}.
     *
     * <p>It reaches {@code ErasureResource} and nothing else here. That matters more in this service
     * than in the other two, because the fan-out call carries a payload — the customer's booking
     * references, which booking is authoritative for — and a payload is a thing a caller supplies.
     * The references decide which notifications get redacted, so a wider authority on this endpoint
     * would be a way to redact rows by naming somebody else's bookings.
     */
    public static final String CUSTOMER_ERASURE = "ROLE_CUSTOMER_ERASURE";

    private MarketplaceAuthorities() {}
}
