package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.StringJoiner;
import net.jojoaddison.domain.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A review's monogram is well-formed UTF-16 and is made of letters — NEW-88, {@code decisions.md} D107.
 *
 * <p><strong>Why this is a sibling of {@code TheReviewAuthorIsNeverALoginTest} rather than more cases
 * in it.</strong> That class's subject is the login disclosure, and its own javadoc argues that every
 * case there must drive {@code ReviewWriteResource} because the live path is the one where
 * {@code customerName} is <em>present</em> — a property of the call site, not of the composer. The
 * subject here is a property of {@link ReviewAuthor#initials} itself, so driving a resource through
 * Mockito and a security context would add a fixture and see nothing extra. The call site is held
 * elsewhere and in two places: {@code aGenuineDisplayNameSurvivesADifferentBookingLogin} asserts
 * {@code authorInitials} on the {@code Review} handed to the repository, and {@code build.yml}'s
 * <em>"A review's public author may not be composed from an identifier"</em> greps for the
 * {@code ReviewAuthor.initials(} call by name. Adding cases there would also have invalidated every row
 * of D104 §9's published mutation table, which is keyed on that class holding 12.
 *
 * <p><strong>Every expected value is built from CODE POINTS, never pasted as a literal.</strong> The
 * subject is what this file's own bytes become after decoding, so a test asserting
 * {@code isEqualTo("𝒜M")} is asserting over the encoding of the source file as much as over the code —
 * and this repository has twice been caught by a probe that answered a question next to the one it was
 * pointed at. {@link #hex} is what every failure message reads out, for the same reason NEW-88's own
 * table printed hex rather than glyphs.
 *
 * <p><strong>The two rules are separately mutable and neither subsumes the other.</strong> Reverting
 * {@code codePointAt} to {@code charAt} reddens the two astral cases and the surrogate sweep; reverting
 * {@code Character.isLetter} to "the part's first code point, whatever it is" reddens the emoji case,
 * the punctuation cases and the digit case. Restoring {@code out.length() < 2} beside a correct
 * {@code appendCodePoint} reddens {@link #theMonogramTakesTwoLettersCountedAsCodePoints()} alone, and
 * that mutation produces no surrogate at all — which is why a sweep for surrogates cannot be the only
 * assertion here.
 */
class TheMonogramIsWellFormedUnitTest {

    /** The login the composer must refuse, matching {@code TheReviewAuthorIsNeverALoginTest}'s. */
    private static final String LOGIN = "kojo.ampia.addison";

    /** U+1D49C MATHEMATICAL SCRIPT CAPITAL A — astral, a letter, and its own uppercase. */
    private static final int SCRIPT_CAPITAL_A = 0x1D49C;

    /**
     * U+1E922 ADLAM SMALL LETTER ALIF — astral, a letter, and it HAS a case mapping (U+1E900).
     *
     * <p>Adlam is a living script for Fulani, written across West Africa including Ghana's northern
     * neighbours, so the astral-letter case is a real name on this platform rather than a mathematical
     * curiosity. It is the case that also exercises {@code Character.toUpperCase} on a supplementary
     * code point: a fix that pairs the surrogates but upper-cases the {@code char} leaves it lower.
     */
    private static final int ADLAM_SMALL_ALIF = 0x1E922;

    /** U+1E900 ADLAM CAPITAL LETTER ALIF — what the above must become. */
    private static final int ADLAM_CAPITAL_ALIF = 0x1E900;

    /** U+1F600 GRINNING FACE — astral, and {@code isLetter == false}. */
    private static final int GRINNING_FACE = 0x1F600;

    /** U+00C4 LATIN CAPITAL LETTER A WITH DIAERESIS — one unit, one code point, never broken. */
    private static final int A_DIAERESIS = 0x00C4;

    // ------------------------------------------------------------------------------ the controls --

    @Test
    @DisplayName("a plain Latin name still yields its own two initials")
    void aPlainLatinNameYieldsItsOwnInitials() {
        assertThat(monogramOf("Selina Amoah")).isEqualTo("SA");
    }

    /**
     * The control that catches a "fix" which mangles non-ASCII generally.
     *
     * <p>A BMP accent is one UTF-16 unit and one code point, so it was never broken and must stay
     * unbroken: a repair reaching for bytes, or for {@code US-ASCII}, would redden here and nowhere
     * else among the controls.
     */
    @Test
    @DisplayName("a BMP accent is one code point and survives untouched")
    void aBmpAccentSurvives() {
        String monogram = monogramOf(startingWith(A_DIAERESIS, "kua Boateng"));

        assertThat(codePointsOf(monogram)).as("hex %s", hex(monogram)).containsExactly(A_DIAERESIS, 'B');
    }

    // ------------------------------------------------------------- whole code points, not units --

    /**
     * Red against {@code main}: {@code charAt(0)} of an astral part is the high surrogate alone, so
     * this answered {@code U+D835 U+004D} — two UTF-16 units that are not a character.
     */
    @Test
    @DisplayName("a mathematical script letter is taken as one code point, not as half of one")
    void aMathematicalScriptLetterIsTakenAsOneCodePoint() {
        String monogram = monogramOf(startingWith(SCRIPT_CAPITAL_A, "nna Mensah"));

        assertThat(codePointsOf(monogram)).as("hex %s", hex(monogram)).containsExactly(SCRIPT_CAPITAL_A, 'M');
        assertThat(isWellFormed(monogram)).as("hex %s", hex(monogram)).isTrue();
    }

    /**
     * The same shape in a script somebody is actually called in, and it asserts the uppercasing too.
     */
    @Test
    @DisplayName("an Adlam letter is taken whole AND upper-cased as a code point")
    void anAdlamLetterIsTakenWholeAndUpperCased() {
        String monogram = monogramOf(startingWith(ADLAM_SMALL_ALIF, "do Diallo"));

        assertThat(codePointsOf(monogram)).as("hex %s", hex(monogram)).containsExactly(ADLAM_CAPITAL_ALIF, 'D');
    }

    /**
     * The trap inside the fix, and <strong>no surrogate assertion can see it</strong>.
     *
     * <p>Swap {@code charAt} for {@code codePointAt} and leave the loop bound as {@code out.length() <
     * 2} and an astral first letter is two units, so the monogram stops at one letter — well-formed,
     * silent, and one letter short. The bound has to count code points.
     */
    @Test
    @DisplayName("the monogram takes two letters, counted as code points rather than as units")
    void theMonogramTakesTwoLettersCountedAsCodePoints() {
        String monogram = monogramOf(startingWith(SCRIPT_CAPITAL_A, "nna Mensah Boateng"));

        assertThat(monogram.codePointCount(0, monogram.length())).as("hex %s", hex(monogram)).isEqualTo(2);
    }

    /**
     * The property, so a name shape nobody enumerated is covered.
     *
     * <p>Every case below is a name this composer could be handed; what is asserted is not what each
     * one produces but that none of them produces half a character. That is the one assertion that does
     * not have to be rewritten when somebody adds a script to the list.
     */
    @Test
    @DisplayName("no monogram ever contains an unpaired surrogate")
    void noMonogramEverContainsAnUnpairedSurrogate() {
        List<String> names = List.of(
            "Selina Amoah",
            startingWith(A_DIAERESIS, "kua Boateng"),
            startingWith(SCRIPT_CAPITAL_A, "nna Mensah"),
            startingWith(ADLAM_SMALL_ALIF, "do Diallo"),
            startingWith(GRINNING_FACE, " Smiley"),
            startingWith(GRINNING_FACE, startingWith(GRINNING_FACE, "")),
            startingWith(SCRIPT_CAPITAL_A, startingWith(SCRIPT_CAPITAL_A, "")),
            "Kojo Ampia-Addison",
            "!!!",
            "..."
        );

        for (String name : names) {
            String monogram = monogramOf(name);
            assertThat(isWellFormed(monogram)).as("%s -> %s", hex(name), hex(monogram)).isTrue();
        }
    }

    // ------------------------------------------------------------- a monogram is made of letters --

    /**
     * Red against {@code main} for the opposite reason to the astral cases: the emoji's high surrogate
     * was taken as the initial, giving {@code U+D83D U+0053}. Refusing a non-letter fixes it without
     * any surrogate reasoning at all — and taking the whole code point would not have fixed it, it
     * would have published a grinning face as somebody's monogram.
     */
    @Test
    @DisplayName("an emoji is not a letter, so it contributes no initial")
    void anEmojiIsNotALetterSoItContributesNothing() {
        String monogram = monogramOf(startingWith(GRINNING_FACE, " Smiley"));

        assertThat(codePointsOf(monogram)).as("hex %s", hex(monogram)).containsExactly('S');
    }

    /**
     * The decision D107 §4 takes, asserted: {@code "!!!"} was {@code "!"} and is now null.
     *
     * <p>A single exclamation mark is not a monogram, and the reason the old behaviour was not even a
     * rule is the asymmetry below it: {@code "."} is in the split pattern and {@code "!"} is not, so
     * dots yielded null and bangs yielded a glyph for no reason anybody could state.
     */
    @Test
    @DisplayName("a name with no letters in it has no monogram")
    void aNameOfOnlyPunctuationHasNoMonogram() {
        assertThat(monogramOf("!!!")).isNull();
        assertThat(monogramOf("...")).isNull();
        assertThat(monogramOf("-")).isNull();
    }

    /**
     * And the collapse D107 §4 accepts is bounded: null in this column is two facts, told apart by the
     * column beside it.
     *
     * <p>{@code authorName} is {@code @NotNull}, so it is always there to discriminate — an anonymous
     * reviewer is {@link ReviewAuthor#ANONYMOUS_NAME} and a letterless one is the name they supplied.
     * Were this to fail, the two states really would be indistinguishable in the row and D107 §4 would
     * owe the third value it declined to spend.
     */
    @Test
    @DisplayName("a letterless name and an anonymous reviewer share a null monogram and differ by NAME")
    void aLetterlessNameIsStillDistinguishableFromAnAnonymousOne() {
        assertThat(monogramOf("!!!")).isNull();
        assertThat(monogramOf(LOGIN)).isNull();

        assertThat(ReviewAuthor.displayName("!!!", LOGIN, LOGIN)).isEqualTo("!!!").isNotEqualTo(ReviewAuthor.ANONYMOUS_NAME);
        assertThat(ReviewAuthor.displayName(LOGIN, LOGIN, LOGIN)).isEqualTo(ReviewAuthor.ANONYMOUS_NAME);
    }

    /**
     * {@code firstLetterIn} scans into the part rather than inspecting only its first code point.
     *
     * <p>Without that, a leading apostrophe makes the whole part contribute nothing and a two-word name
     * publishes a one-letter monogram.
     */
    @Test
    @DisplayName("a leading apostrophe does not cost the part its initial")
    void aLeadingApostropheDoesNotCostTheFirstInitial() {
        assertThat(monogramOf("'Ama Mensah")).isEqualTo("AM");
    }

    /**
     * Why the rule is {@code isLetter} and not {@code isLetterOrDigit} — D107 §4's loser, asserted.
     *
     * <p>Because the scan goes into the part, a digit-leading name still gets a monogram made of its
     * letters. {@code isLetterOrDigit} would publish {@code 4F} here, which is worse than {@code RF}
     * and is the whole case the wider rule was supposed to serve.
     */
    @Test
    @DisplayName("a digit is skipped rather than taken as an initial")
    void aDigitIsSkippedRatherThanTakenAsAnInitial() {
        assertThat(monogramOf("4Real Fitness")).isEqualTo("RF");
    }

    // ------------------------------------------------------------------------------- the length --

    /**
     * The worst case that can be constructed, asked of the <strong>column's own constraint</strong>
     * rather than of a number restated here.
     *
     * <p>Two astral letters is 4 UTF-16 units and 2 code points. {@code @Size(max = 4)} counts units
     * and {@code varchar(4)} counts code points, so both hold — and {@code @Size} is saturated rather
     * than spare. Validating the real annotation means narrowing it to {@code max = 2}, which would
     * reject this row at persist time, is red here instead of at run time on a row nothing can correct.
     *
     * <p>What PostgreSQL does with it is <em>measured elsewhere and not here</em>: a throwaway
     * {@code postgres:17} with pgjdbc 42.7.11 accepted the 4-unit value and read it back unchanged,
     * while refusing 5 code points with SQLSTATE {@code 22001} — which is the control that makes the
     * acceptance mean something. D107 §3 carries the transcript. A Testcontainers IT would spend two
     * minutes re-establishing a property about a column type.
     */
    @Test
    @DisplayName("two astral letters satisfy the column's own @Size, with no headroom left")
    void twoAstralLettersStillSatisfyTheColumnsOwnConstraint() {
        String monogram = monogramOf(startingWith(SCRIPT_CAPITAL_A, "nna " + startingWith(SCRIPT_CAPITAL_A, "moah")));

        assertThat(monogram.length()).as("UTF-16 units of %s", hex(monogram)).isEqualTo(4);
        assertThat(monogram.codePointCount(0, monogram.length())).isEqualTo(2);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Review row = new Review();
            row.setAuthorInitials(monogram);
            assertThat(validator.validateProperty(row, "authorInitials")).as("hex %s", hex(monogram)).isEmpty();
        }
    }

    /**
     * Why {@link #twoAstralLettersStillSatisfyTheColumnsOwnConstraint()} is a <em>bound</em> and not an
     * example — derived over the whole code point range rather than assumed.
     *
     * <p>If any code point's uppercase mapping were longer in UTF-16 than the code point itself, two
     * letters could exceed four units and the monogram could be refused at persist time by a column
     * nobody had reason to look at. None is, so the bound is {@code MONOGRAM_LETTERS}: raise that
     * constant to three and two astral letters plus one is 6 units against a limit of 4, which
     * {@code varchar(4)} would accept quite happily.
     */
    @Test
    @DisplayName("no code point's uppercase mapping is longer in UTF-16 than itself")
    void noUppercaseMappingGrowsAMonogram() {
        for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            int upper = Character.toUpperCase(codePoint);
            assertThat(Character.charCount(upper))
                .as("U+%04X upper-cases to U+%04X", codePoint, upper)
                .isLessThanOrEqualTo(Character.charCount(codePoint));
        }
    }

    // ------------------------------------------------------------------------- D104's own property --

    /**
     * D104 §5 must not regress through this change: no name means no monogram, and in particular not a
     * monogram taken from the login.
     *
     * <p>{@code TheReviewAuthorIsNeverALoginTest} holds this through the resource; it is restated at
     * the composer because every case above calls the composer directly, so a change confined to
     * {@code hasName} would otherwise be invisible from this file.
     */
    @Test
    @DisplayName("a reviewer the booking named nobody for still has no initials")
    void anAnonymousReviewerStillHasNoInitials() {
        assertThat(monogramOf(LOGIN)).isNull();
        assertThat(monogramOf(null)).isNull();
        assertThat(monogramOf("   ")).isNull();
        assertThat(ReviewAuthor.initials("Selina Amoah", LOGIN, null)).isNull();
    }

    // ------------------------------------------------------------------------------------ helpers --

    /** The composer, for a booking whose identifiers are both {@link #LOGIN}. */
    private static String monogramOf(String customerName) {
        return ReviewAuthor.initials(customerName, LOGIN, LOGIN);
    }

    /**
     * A name beginning with one code point, built rather than written.
     *
     * <p>Pasting {@code "𝒜nna Mensah"} into this file would make the assertion depend on the source
     * file's own encoding surviving every editor and every tool that reads it, which is a second
     * subject nobody meant to test.
     */
    private static String startingWith(int codePoint, String rest) {
        return new StringBuilder().appendCodePoint(codePoint).append(rest).toString();
    }

    private static int[] codePointsOf(String value) {
        return value.codePoints().toArray();
    }

    /** Whether every surrogate in the value is half of a pair — the honest reading of well-formed. */
    private static boolean isWellFormed(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char unit = value.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(unit)) {
                return false;
            }
        }
        return true;
    }

    /** What every failure message above reads out, so a diff of glyphs is never what anybody debugs. */
    private static String hex(String value) {
        if (value == null) {
            return "null";
        }
        StringJoiner units = new StringJoiner(" ");
        for (int i = 0; i < value.length(); i++) {
            units.add("U+%04X".formatted((int) value.charAt(i)));
        }
        return "[" + units + "]";
    }
}
