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
 *
 * <h2>Why the rule also answers WHY, and why it still has no logger — NEW-89, D110</h2>
 *
 * <p>{@link #authorship} exists so that the <em>caller</em> can record a suppression without
 * re-deriving the condition. D97's rule puts the level with the code that knows which of two facts it
 * is, and this class knows neither: it is a pure function with no idea whether it is being asked on a
 * write path, and a static utility cannot tell a caller's ordinary state from an estate fault. So it
 * reports the reason and {@code ReviewWriteResource} — which knows it is about to write an
 * uncorrectable public row — decides what that is worth saying.
 *
 * <p><strong>{@link #authorship} is the one place the question is answered, and {@link #displayName}
 * and {@link #initials} both go through it.</strong> A second copy of the rule at the call site is
 * exactly what this class was created to remove, and it would be the worse kind of copy: the log line
 * would name a reason while the stored value came from somewhere else, so the two could disagree with
 * nothing going red.
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

    /**
     * How many letters a monogram is made of. Two, and it is the bound that makes the length safe —
     * see {@link #initials} on why four UTF-16 units is exactly saturated rather than comfortable.
     */
    private static final int MONOGRAM_LETTERS = 2;

    private ReviewAuthor() {}

    /**
     * Why a review's public author is the stand-in, or that it is not — NEW-89, {@code decisions.md}
     * D110.
     *
     * <p>Three states and not two, because {@link #NOT_SUPPLIED} and {@link #IDENTIFIER_UNREADABLE} are
     * different facts about the estate and D104 §5's own rule is that two facts must not collapse into
     * one value. The first is an ordinary caller state. The second says a name <em>was</em> supplied and
     * was refused in the safe direction because an identifier to compare it against could not be read —
     * which, if {@code booking} ever stopped sending {@code customerLogin}, would be <strong>every
     * review from that moment on</strong>, correct by the rule and wrong about the world.
     */
    public enum Authorship {
        /** The booking supplied a display name that is nobody's identifier. It is published verbatim. */
        SUPPLIED,
        /**
         * The booking named nobody. Absent, blank, or — the live shape — the login booking laundered
         * into {@code customerName}, which is one fact rather than two: in all three the booking
         * supplied no display name, and the caller cannot tell which spelling it used.
         */
        NOT_SUPPLIED,
        /**
         * An identifier this service holds could not be read, so a supplied name could not be ruled out
         * and was refused. Unreachable through {@code POST /api/reviews} from the JWT subject, which the
         * resource answers 401 without; it is the booking summary's {@code customerLogin} that can be
         * null.
         */
        IDENTIFIER_UNREADABLE
    }

    /**
     * Whether the booking supplied something publishable as a name, and if not, which fact that is.
     *
     * <p>The order of the two guards is the decision inside this method, and it decides exactly one
     * combination: a name that is absent <em>and</em> an identifier that could not be read. A blank
     * name wins, because "nobody gave a name" is then the whole of what happened and there was nothing
     * to rule out — reversing the guards would report an estate fault about a row where no name was at
     * risk. ⚠ It changes nothing for any other input: with both identifiers present the first guard is
     * the only one that can fire, so the ordering is <strong>not</strong> what keeps the ordinary
     * no-name case out of the second arm. {@code theNamelessBookingWithNoIdentifierIsTheNamelessFact}
     * is what drives it.
     *
     * @param customerName the booking's {@code customerName} — which may be the login, see the class
     *     javadoc
     * @param callerLogin the JWT subject. Never published, and never logged
     * @param bookingCustomerLogin the summary's own {@code customerLogin}. Never published, never logged
     */
    public static Authorship authorship(String customerName, String callerLogin, String bookingCustomerLogin) {
        if (customerName == null || customerName.isBlank()) {
            return Authorship.NOT_SUPPLIED;
        }
        if (callerLogin == null || bookingCustomerLogin == null) {
            return Authorship.IDENTIFIER_UNREADABLE;
        }
        return isLogin(customerName, callerLogin) || isLogin(customerName, bookingCustomerLogin)
            ? Authorship.NOT_SUPPLIED
            : Authorship.SUPPLIED;
    }

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
     * The monogram to publish, or null when the name has no letters to take one from.
     *
     * <p>"Kojo Ampia-Addison" -&gt; "KA". Splitting on spaces and dots is what the seeded 63 are
     * consistent with; it is only ever applied to a value this class has established is a name.
     *
     * <h2>A monogram is made of LETTERS, and of whole code points — NEW-88, D107</h2>
     *
     * <p>Two rules, and <strong>each covers a case the other does not</strong>, which is why neither is
     * a tidying of the other. <em>Letters</em> answers {@code "😀 Smiley"}: an emoji is
     * {@code Character.isLetter == false}, so it contributes nothing and the monogram is {@code S}.
     * <em>Whole code points</em> answers {@code "𞤢ɗo Diallo"}: Adlam is a living West African script
     * for Fulani, it is <strong>astral</strong>, it <em>is</em> a letter and it has a case mapping
     * (U+1E922 -&gt; U+1E900) — so it must be taken as one code point or not at all.
     *
     * <p>⚠ It took the first <strong>UTF-16 unit</strong> until D107, carried over verbatim from the
     * {@code initialsOf} this replaced. Measured through a real PostgreSQL 17 and pgjdbc 42.7.11: an
     * unpaired surrogate is <strong>accepted, not refused</strong> — no SQLSTATE — and comes back as
     * {@code U+003F}, so the row published a permanent {@code ?} on a page needing no account, in a
     * column no endpoint can correct. Cosmetic, and still not fixable afterwards.
     *
     * <p>⚠ <strong>The loop counts CODE POINTS, and {@code out.length() < 2} is the trap inside the
     * fix.</strong> Swapping only {@code charAt} for {@code codePointAt} leaves the bound counting
     * UTF-16 units, and an astral first letter is two of them — so the monogram silently stops at one
     * letter, {@code 𞤢} rather than {@code 𞤢D}, with no surrogate anywhere for a test to catch. The
     * count is derived from the buffer rather than kept beside it for the usual reason: a second
     * {@code appendCodePoint} added in some later branch cannot make the two disagree.
     *
     * <p><strong>The length is safe with zero headroom, and the two constraints count different
     * things.</strong> {@code @Size(max = 4)} on {@code Review.authorInitials} counts UTF-16 units;
     * {@code varchar(4)} counts code points. Two letters is at most 4 units and exactly 2 code points,
     * so both hold — and {@code @Size} is <em>saturated</em>, not spare. <strong>That is
     * unconditional, and it depends on nothing about Unicode</strong>: {@code Character.toUpperCase(int)}
     * returns a single code point, so {@code Character.charCount} of its result is at most 2 whatever any
     * mapping does now or in a later JDK, and {@link #MONOGRAM_LETTERS} letters is therefore at most
     * {@code 2 × 2} units. So the constant is the <em>only</em> thing bounding it: raise it to three and
     * a name in three astral scripts is 6 units against a limit of 4 — refused by bean validation at
     * persist time, on the write path of a review somebody has earned, while {@code varchar(4)} would
     * accept 3 code points quite happily so the database would not object either.
     * {@code noMonogramCanOutgrowTheColumnHoweverManyPartsTheNameHas} is red rather than that.
     *
     * <p><strong>A name with no letters yields null, and that is the same value the anonymous path
     * writes — argued, not overlooked.</strong> {@code "..."} and {@code "!!!"} are both null now,
     * where {@code "!!!"} used to be {@code "!"}. The two facts stay distinguishable from the row
     * rather than from this column: {@code authorName} is {@code @NotNull}, so an anonymous reviewer is
     * {@code A BridgeCare customer} with a null monogram and a letterless name is {@code "..."} with
     * one. D107 §4 argues why a third stored value loses.
     */
    public static String initials(String customerName, String callerLogin, String bookingCustomerLogin) {
        if (!hasName(customerName, callerLogin, bookingCustomerLogin)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : customerName.trim().split("[ .]+")) {
            if (out.codePointCount(0, out.length()) == MONOGRAM_LETTERS) {
                break;
            }
            int letter = firstLetterIn(part);
            if (letter >= 0) {
                out.appendCodePoint(Character.toUpperCase(letter));
            }
        }
        return out.isEmpty() ? null : out.toString();
    }

    /**
     * The first letter in one part of a name as a whole code point, or {@code -1} if it holds none.
     *
     * <p>It scans <em>into</em> the part rather than looking only at its first code point, so
     * {@code "'Ama Mensah"} is {@code AM} rather than {@code M}: a leading apostrophe would otherwise
     * make the part contribute nothing and the monogram would be one letter for a two-word name.
     */
    private static int firstLetterIn(String part) {
        int i = 0;
        while (i < part.length()) {
            int codePoint = part.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                return codePoint;
            }
            i += Character.charCount(codePoint);
        }
        return -1;
    }

    /**
     * Whether the booking supplied something that is a name rather than an identifier.
     *
     * <p>It asks {@link #authorship} rather than restating the condition, so the value stored and the
     * reason {@code ReviewWriteResource} logs are the same derivation and cannot disagree (D110).
     */
    private static boolean hasName(String customerName, String callerLogin, String bookingCustomerLogin) {
        return authorship(customerName, callerLogin, bookingCustomerLogin) == Authorship.SUPPLIED;
    }

    /**
     * Whether this "display name" is that login wearing different whitespace or case.
     *
     * <p><strong>The login may not be null here, and that is a precondition rather than an omission.</strong>
     * A null login cannot be matched against, and is not a reason to publish: it can only mean the
     * booking service answered without one, and a name that might be an unknown login is refused in
     * the safe direction. An identifier this service cannot read is one it cannot rule out — and since
     * D110 that refusal is {@link Authorship#IDENTIFIER_UNREADABLE}, decided in {@link #authorship}
     * above this call so the <em>reason</em> survives to the caller. It was a {@code login == null ||}
     * here until then, which refused identically and told nobody which of the two facts it was.
     */
    private static boolean isLogin(String customerName, String login) {
        return customerName.trim().equalsIgnoreCase(login.trim());
    }
}
