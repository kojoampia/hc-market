package net.jojoaddison.security;

/**
 * Authorities this product adds beyond JHipster's.
 *
 * <p>A NEW file rather than a constant added to {@link AuthoritiesConstants}, which is generated and
 * would lose it on the next regeneration — the same reasoning that puts
 * {@code MarketplacePublicSecurityConfiguration} beside the generated {@code SecurityConfiguration}
 * rather than inside it. Deliberately identical to booking's copy: the estate shares one signing
 * key, so an authority means the same thing in every service or it means nothing.
 */
public final class MarketplaceAuthorities {

    /**
     * The brokerage desk — decisions.md D23, and D16/D29 for verification.
     *
     * <p>Deliberately not {@code ROLE_ADMIN}. In booking it resolves disputes, which moves money.
     * Here it decides whether a professional carries the VERIFIED badge shown publicly beside their
     * name — a trust signal customers act on when choosing who comes into their home. Both are
     * narrower and more consequential than general administration, and both should be grantable to
     * the people who do them without also handing them everything else.
     */
    public static final String BROKERAGE = "ROLE_BROKERAGE";

    private MarketplaceAuthorities() {}
}
