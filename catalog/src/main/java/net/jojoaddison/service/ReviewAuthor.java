package net.jojoaddison.service;

/**
 * What a review's public author is called — NEW-81, {@code decisions.md} D104.
 *
 * <p>A {@code Review} carries {@code authorName} and {@code authorInitials}, and both are served to
 * anybody at all: {@code MarketplaceDtos.ReviewView} is read through a {@code permitAll} endpoint on a
 * profile page that needs no account. {@code customerLogin} sits beside them and is deliberately never
 * serialised. This class is the one place the first two are composed, so that the third cannot reach
 * them by another road.
 *
 * <h2>Why "the booking supplied a name" is not the same as "customerName is present"</h2>
 *
 * <p>Booking's {@code CustomerBookingResource} stores
 * {@code request.customerName() == null || isBlank() ? login : request.customerName()}, which is
 * correct where it is — a professional's inbox has to say who is asking, and the inbox is an
 * authenticated counterparty to that very booking. The consequence here is that a booking made without
 * a display name arrives at catalog with {@code customerName} <strong>non-null and equal to the
 * login</strong>. So a presence check is not a check: it reads "a name was supplied" for a value that
 * is an account identifier, and publishes it on a page with no account behind it.
 *
 * <p>Hence {@code isLogin}, and hence the comparison being <strong>trimmed and
 * case-folded</strong>. A display name that differs from its login only in case or in surrounding
 * whitespace renders as that login to a reader, so the two are the same disclosure; and nothing is
 * lost in the one case where a person's real display name happens to equal their login, because
 * publishing it would be publishing their login either way. The comparison is EQUALITY and not
 * resemblance — a rule refusing anything "login-shaped", or anything containing the login, would
 * reject {@code Ama Mensah} for a customer logging in as {@code ama}.
 *
 * <h2>Why a stored label, and why these two values</h2>
 *
 * <p>{@code authorName} is {@code required} in {@code jdl/catalog.jdl} and {@code @NotNull} on the
 * entity, so null is not available without regenerating {@code Review}'s changelog and invalidating
 * the checksum every existing database recorded. {@link #ANONYMOUS_NAME} is therefore a stored value,
 * and it is prose rather than a sentinel because nothing in the client maps it: {@code professional.html}
 * renders {@code authorName} verbatim, so a marker would be read by a member of the public until some
 * other package taught a screen about it — and there is <strong>no endpoint that could ever correct a
 * review</strong>, so "until" means "for the life of every row written in between".
 *
 * <p>{@code authorInitials} is nullable and gets {@code null}, which is the opposite call for the
 * opposite reason: there are no initials of a name that was never given, the column can say so, and
 * {@code initialsOf(login)} produced a fragment of an identifier rather than a monogram —
 * {@code kojo.ampia.addison} yields {@code KA} and {@code jdoe123} yields {@code J}. This is the same
 * line as a professional with no reviews having {@code rating} null rather than {@code 0.0}: absent and
 * known are not the same fact.
 *
 * <p><strong>Neither value may be the erasure's.</strong> {@code ErasureWorkflow} writes
 * {@code [erased]} and {@code ··} into the same two columns, and that is a different fact about a
 * review — a person asked to be forgotten, rather than a booking made without a name. Converging them
 * would make an irreversible act indistinguishable from an ordinary one.
 */
public final class ReviewAuthor {

    /**
     * What a reviewer is called when the booking named nobody.
     *
     * <p>Pinned by {@code TheReviewAuthorIsNeverALoginTest} as a literal, deliberately: re-wording it
     * is red, which is the only moment anyone is going to notice that the rows already written keep
     * the old wording for ever. It is English in a data column on a client where translation is on —
     * the cost of the constraint above, and backlog NEW-87.
     */
    public static final String ANONYMOUS_NAME = "A BridgeCare customer";

    private ReviewAuthor() {}

    /**
     * The name to publish for a review, given what the booking service said and every identifier of
     * that customer this service is holding beside it.
     *
     * <p><strong>Both logins are compared, and that is not belt-and-braces.</strong> They are the same
     * string today, by an invariant that lives in another service: booking answers 404 for a booking
     * that is not the caller's, so {@code mineOr404} guarantees the summary's customer is the JWT's
     * subject. That is a cross-service invariant, and the cost of not relying on it is one
     * {@code ||} — so the published name is refused if it matches <em>either</em>, and the invariant
     * no longer has to be argued here for the rule to be sound.
     *
     * @param customerName the booking's {@code customerName} — which may be the login, see the class
     *     javadoc
     * @param callerLogin the JWT subject, which is the key the review is stored under. Never published
     * @param bookingCustomerLogin the summary's own {@code customerLogin} — the identifier booking
     *     laundered in. Never published
     */
    public static String displayName(String customerName, String callerLogin, String bookingCustomerLogin) {
        return hasName(customerName, callerLogin, bookingCustomerLogin) ? customerName : ANONYMOUS_NAME;
    }

    /**
     * The monogram to publish, or null when there is no name to take one from.
     *
     * <p>"Kojo Ampia-Addison" -&gt; "KA". Splitting on spaces and dots is what the seeded 63 are
     * consistent with; it is only ever applied to a value this class has established is a name.
     *
     * <p>⚠ It takes the first <strong>UTF-16 unit</strong> of each part, carried over verbatim from the
     * {@code initialsOf} this replaced — <strong>backlog NEW-88</strong>, deliberately not fixed here
     * because it is unchanged from the code this moved out of. Measured: {@code "𝒜nna Mensah"} yields
     * {@code U+D835 U+004D}, a <em>lone high surrogate</em> and so not well-formed UTF-16;
     * {@code "!!!"} yields {@code "!"}; and {@code "..."} yields <strong>null</strong>, colliding with
     * the no-name state. What the driver and PostgreSQL do with an unpaired surrogate is <em>not</em>
     * measured — the two units fit {@code @Size(max = 4)}, so this is not a length problem.
     */
    public static String initials(String customerName, String callerLogin, String bookingCustomerLogin) {
        if (!hasName(customerName, callerLogin, bookingCustomerLogin)) {
            return null;
        }
        String[] parts = customerName.trim().split("[ .]+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && out.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                out.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** Whether the booking supplied something that is a name rather than an identifier. */
    private static boolean hasName(String customerName, String callerLogin, String bookingCustomerLogin) {
        return (
            customerName != null &&
            !customerName.isBlank() &&
            !isLogin(customerName, callerLogin) &&
            !isLogin(customerName, bookingCustomerLogin)
        );
    }

    /**
     * Whether this "display name" is that login wearing different whitespace or case.
     *
     * <p>A null login cannot be matched against, and is not a reason to publish: it can only mean the
     * booking service answered without one, and a name that might be an unknown login is refused in
     * the safe direction. An identifier this service cannot read is one it cannot rule out.
     */
    private static boolean isLogin(String customerName, String login) {
        return login == null || customerName.trim().equalsIgnoreCase(login.trim());
    }
}
