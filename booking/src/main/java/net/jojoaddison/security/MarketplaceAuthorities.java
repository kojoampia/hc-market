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

    /**
     * Erase one named customer, and nothing else — decisions.md D37, D38.
     *
     * <p>Held by no person and granted by no login. It exists on exactly one credential: the
     * short-lived token booking mints so that a single operator action erases a customer in messaging
     * and catalog as well as here. See {@link ErasureFanoutToken}, which also carries the two
     * narrowings that make the name honest — the token names the customer it may erase, and it lives
     * thirty seconds.
     *
     * <p><strong>Named for what it permits, not for the mechanism.</strong> "Fan-out" would describe
     * how the call arrives; whoever next reads it on an endpoint needs to know what it lets through.
     * And it is deliberately not {@code ROLE_BROKERAGE}, which would have needed no new constant and
     * no new anything: that authority also resolves disputes, which writes compensating ledger
     * entries, and decides who wears the verification badge. A service holding the estate signing key
     * can mint that today and this changes nothing about that — but a fan-out that <em>routinely</em>
     * carried it would turn a capability nobody exercises into an interface, and the difference
     * between those two is most of what security review is about.
     */
    public static final String CUSTOMER_ERASURE = "ROLE_CUSTOMER_ERASURE";

    private MarketplaceAuthorities() {}
}
