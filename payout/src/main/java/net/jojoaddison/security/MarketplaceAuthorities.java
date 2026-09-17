package net.jojoaddison.security;

/**
 * Authorities this product adds beyond JHipster's.
 *
 * <p>A NEW file rather than a constant added to {@link AuthoritiesConstants}, which is generated and
 * would lose it on the next regeneration — the same reasoning that puts {@code BrokerageTerms}
 * beside the generated {@code BrokerageConfigService} rather than inside it.
 *
 * <p>Booking and catalog each hold their own copy, and the three are deliberately <strong>not</strong>
 * a byte-identical family like {@code MarketCalendar} or {@code SubjectPseudonym}: the string is the
 * contract and the javadoc says what the authority does <em>in this service</em>, which is a
 * different sentence in each. Nothing in CI diffs them, and nothing should — the estate shares one
 * signing key, so what has to match is the value, and that is one literal.
 */
public final class MarketplaceAuthorities {

    /**
     * The brokerage desk — decisions.md D23, and D95 for settlement.
     *
     * <p>Deliberately not {@code ROLE_ADMIN}. In booking it resolves disputes and in catalog it
     * decides who wears the verification badge. Here it is narrower still and more consequential than
     * either: it computes what this platform owes a professional and records that the money was sent.
     * A forged settlement is a statement that somebody has been paid when they have not, in the table
     * that is this platform's own account of what it owes — and it is the one act on the estate that a
     * later reconciliation against a bank statement is the only way to catch.
     *
     * <p>It should be grantable to the people who do that without also handing them everything else,
     * which is the whole argument for a separate authority and is why the generated
     * {@code PayoutResource} was deleted rather than gated at {@code ROLE_ADMIN} (D54).
     */
    public static final String BROKERAGE = "ROLE_BROKERAGE";

    private MarketplaceAuthorities() {}
}
