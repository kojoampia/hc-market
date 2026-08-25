package net.jojoaddison.security;

/**
 * Authorities this product adds beyond JHipster's.
 *
 * <p>A NEW file rather than a constant added to {@link AuthoritiesConstants}, which is generated and
 * would lose it on the next regeneration — the same reasoning that puts
 * {@code MarketplacePublicSecurityConfiguration} beside the generated {@code SecurityConfiguration}
 * rather than inside it.
 */
public final class MarketplaceAuthorities {

    /**
     * The brokerage desk — decisions.md D23.
     *
     * <p>Deliberately not {@code ROLE_ADMIN}. Resolving a dispute moves money: an upheld dispute
     * writes a compensating entry against a professional's earnings. That is a narrower and more
     * consequential power than general administration, and it should be grantable to the people who
     * do it without also handing them everything else.
     */
    public static final String BROKERAGE = "ROLE_BROKERAGE";

    private MarketplaceAuthorities() {}
}
