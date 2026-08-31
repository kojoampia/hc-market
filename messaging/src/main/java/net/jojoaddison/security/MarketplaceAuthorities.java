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

    private MarketplaceAuthorities() {}
}
