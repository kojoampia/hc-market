package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link CapturedZone} — {@code decisions.md} D60, backlog NEW-20.
 *
 * <p>The derivation on its own, without a Spring context. {@code TheZoneACatalogueOffersIsParsed
 * AtCaptureIT} is the same subject through {@code POST /api/bookings}, and the two are separate for
 * D58's reason: the endpoint can be wrong while the derivation is right, and the mutation table in
 * D60 shows exactly that.
 *
 * <p><strong>The zones are not Accra.</strong> A fixture in {@code Africa/Accra} cannot tell a
 * capture that parsed from one that passed a string through, because both produce the same five
 * characters. {@code Pacific/Kiritimati} (+14) and {@code Pacific/Honolulu} (-10) are D58's pair,
 * kept here so the two halves of the same column are pinned by the same fixtures.
 */
class TheZoneACatalogueOffersIsParsedAtCaptureTest {

    private static final String REF = "p1";

    /**
     * The case that must not change: a professional genuinely outside Ghana keeps their calendar,
     * spelled exactly as the catalogue spelled it. This is the whole reason {@code Booking.zoneId}
     * exists (D21/D55), so a parse that "corrected" it would be worse than no parse at all.
     */
    @ParameterizedTest
    @ValueSource(strings = { "Pacific/Kiritimati", "Pacific/Honolulu", "Europe/London", "Africa/Accra", "Africa/Lagos" })
    void aReadableZonePassesThroughUnchanged(String offered) {
        assertThat(CapturedZone.of(offered, REF)).isEqualTo(offered);
    }

    /**
     * Absent is a deployment state, not a wrong answer: a catalogue one release behind sends no
     * zone at all, and refusing there would fail every booking in the estate over an empty field.
     * Ghana is UTC+0 all year, so the marketplace's calendar cannot make the time wrong today.
     *
     * <p>Asserted against {@link MarketCalendar#MARKET_ZONE} <em>and</em> against the literal, so
     * that moving the marketplace's calendar is red here rather than silently redefining what an
     * absent zone means.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t" })
    void anAbsentZoneIsTheMarketplacesOwnCalendar(String offered) {
        assertThat(CapturedZone.of(offered, REF)).isEqualTo("Africa/Accra").isEqualTo(MarketCalendar.MARKET_ZONE.getId());
    }

    /**
     * <strong>The defect NEW-20 names.</strong> Each of these was stored verbatim before D60 and
     * afterwards read as Africa/Accra for ever by {@code BookingWorkflow.scheduledAt}, at WARN, on
     * the boundary that decides a 50% late-cancellation fee.
     *
     * <p>The last three are the ones worth having: a region that does not exist but looks like one,
     * the right name in the wrong case, and the right name with a space on the end. None of them is
     * garbage a reviewer would notice in a database, and all three are what a hand-typed row looks
     * like.
     */
    @ParameterizedTest
    @ValueSource(strings = { "Mars/Olympus", "not a zone", "Europe/Accra", "Africa/Kumasi", "africa/accra", "Africa/Accra " })
    void aZoneTzdbCannotReadIsRefused(String offered) {
        assertThatThrownBy(() -> CapturedZone.of(offered, REF))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    /**
     * The refusal names neither the value nor the reference — D44 for the first (a string off
     * another service's wire does not reach a response body) and D45 for the second (a refusal does
     * not echo what was asked for). Both are in the ERROR log instead, which is where the person
     * who can edit the row is looking.
     *
     * <p>{@code getMessage()} as well as {@code getReason()}, because they are not the same string
     * and it is the message a stray {@code log.error(e)} or a serialiser reaches for.
     */
    @Test
    void theRefusalNamesNeitherTheOfferedValueNorTheReference() {
        String offered = "Mars/Olympus";
        Throwable thrown = catchThrowable(() -> CapturedZone.of(offered, REF));

        // Asserted before the cast: without it, a capture that stopped refusing fails this as a
        // NullPointerException three lines down, which reads as a broken test rather than as the
        // subject regressing.
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException refusal = (ResponseStatusException) thrown;

        assertThat(refusal.getReason()).doesNotContain(offered).doesNotContain(REF);
        assertThat(refusal.getMessage()).doesNotContain(offered).doesNotContain(REF);
    }

    /**
     * The stored value is the parsed zone's own id, not the string that was offered — and that is
     * <strong>observable</strong>, which is why it gets a case of its own rather than a sentence in
     * a javadoc.
     *
     * <p>Measured on JDK 25: all <strong>604</strong> tzdb region ids are their own {@code getId()},
     * so no real professional's calendar is ever rewritten by this. The offset spellings are what
     * moves, and moving them is the point — one row saying {@code GMT+0} and another saying
     * {@code GMT} are the same zone written two ways, in a column that is compared as text.
     */
    @Test
    void anOffsetZoneIsStoredInItsCanonicalSpelling() {
        assertThat(CapturedZone.of("GMT+0", REF)).isEqualTo("GMT");
        assertThat(CapturedZone.of("UTC+0", REF)).isEqualTo("UTC");
        assertThat(CapturedZone.of("UT+1", REF)).isEqualTo("UT+01:00");
    }

    /**
     * The ERROR log is the <strong>only</strong> place the offending value exists, so it has to be
     * legible there — D60's review.
     *
     * <p>Undelimited, {@code "Africa/Accra "} renders as <em>carries a zoneId Africa/Accra that is
     * not a readable zone</em>: identical to the zone that *is* readable, in the one place the
     * operator was sent to look, for the fixture this class's own javadoc calls the most realistic
     * of them. The quotes are what make a trailing space, a leading space or a tab visible.
     *
     * <p>Asserted on the <em>formatted</em> message rather than on the pattern, because a pattern
     * with quotes and an argument that has already been trimmed somewhere would pass a check on the
     * pattern alone.
     */
    @Test
    void theLogDelimitsTheValueSoWhitespaceIsVisible() {
        String trailingSpace = "Africa/Accra ";

        List<ILoggingEvent> heard = whileListening(() -> {
            try {
                CapturedZone.of(trailingSpace, REF);
            } catch (ResponseStatusException expected) {
                // The refusal is this test's precondition, not its subject.
            }
        });

        assertThat(heard).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("'" + trailingSpace + "'").contains(REF);
            // The whole point: the readable spelling must not be a prefix-match of what is logged.
            assertThat(event.getFormattedMessage()).doesNotContain("'Africa/Accra'");
        });
    }

    /**
     * Collects what {@link CapturedZone} logs while the given work runs — the shape
     * {@code PaymentConfigurationUnitTest} established. The appender is attached to that one logger
     * and detached in a {@code finally}, so a failure inside the block cannot leave it attached for
     * the rest of the suite.
     */
    private static List<ILoggingEvent> whileListening(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(CapturedZone.class);
        ListAppender<ILoggingEvent> heard = new ListAppender<>();
        heard.start();
        logger.addAppender(heard);
        try {
            work.run();
        } finally {
            logger.detachAppender(heard);
            heard.stop();
        }
        return List.copyOf(heard.list);
    }

    /**
     * What is stored round-trips. The stored value is the parsed zone's own id rather than the
     * string that was offered, so "parsed at capture" is a property of the row and not only of the
     * moment — {@code BookingWorkflow.scheduledAt} can read anything this method has written.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "Pacific/Kiritimati", "Pacific/Honolulu", "Africa/Accra", "UTC", "  " })
    void whateverIsStoredCanBeReadBack(String offered) {
        String stored = CapturedZone.of(offered, REF);

        assertThatCode(() -> ZoneId.of(stored)).doesNotThrowAnyException();
        assertThat(stored).isNotBlank().hasSizeLessThanOrEqualTo(64);
    }
}
