package net.jojoaddison.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jojoaddison.config.SessionConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The online-session seam — {@code decisions.md} D86, backlog WP-17.
 *
 * <p>What is pinned is the <strong>difference between the two answers</strong>, because the whole value
 * of this seam is that "nobody hosts a room" and "a provider is selected but unwritten" are different
 * facts. A test asserting only that something came back would pass for a seam that silently fell back.
 */
class MeetingRoomProviderUnitTest {

    private static final String BOOKING = "b-1a2b3c4d";

    /**
     * THE DEFAULT IS D17'S V1 AND MUST NOT THROW.
     *
     * <p>Its recommendation is that the professional supplies the link and the platform relays it, so an
     * unconfigured estate is doing the recommended thing rather than waiting for an implementation. If
     * this ever threw, every caller would have to special-case the normal case — the mistake
     * {@code PaymentState.OFF_PLATFORM} exists to avoid one seam along.
     */
    @Test
    @DisplayName("unconfigured: the professional supplies the link, and it is not a failure")
    void unconfiguredRelaysTheProfessionalsLink() {
        MeetingRoom room = new UnconfiguredMeetingRoomProvider().create(BOOKING);

        assertThat(room.hosted()).isFalse();
        assertThat(room.url()).isNull();
        assertThat(room.provider()).isNull();
        assertThat(new UnconfiguredMeetingRoomProvider().name()).isEqualTo("none");
    }

    /**
     * A SELECTED-BUT-UNWRITTEN PROVIDER REFUSES AND DOES NOT FALL BACK.
     *
     * <p>The one decision in {@code ProviderAwaitingSelection}. Falling back would mean an estate that
     * had deliberately enabled a hosted provider behaved as though it had not — the customer relayed a
     * link the professional may never have supplied, with nothing saying the provider was never built.
     *
     * <p>The refusal must <strong>name what it needs</strong>, not merely refuse: "not implemented" tells
     * an operator to wait for code, while "needs a paid account and its API key" tells them it is waiting
     * on them. Asserting the type alone would pass for the uninformative version.
     */
    @Test
    @DisplayName("dailyco: refuses, names what it needs, and never falls back to the professional's link")
    void dailyCoRefusesAndSaysWhatItNeeds() {
        MeetingRoomProvider selected = new DailyCoMeetingRoomProvider(true);

        assertThat(selected.name()).isEqualTo("dailyco");
        assertThatThrownBy(() -> selected.create(BOOKING))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("dailyco")
            .hasMessageContaining("paid account")
            .hasMessageContaining("WP-17");
    }

    /**
     * RECORDING IS ABSENT FROM THE SEAM ITSELF, and this asserts it stays that way.
     *
     * <p>D17 recommends no recording in v1 <em>because</em> it drags in retention and consent — three
     * problems the platform does not otherwise have. So there is no method that could start one, and
     * nothing in what the seam says it needs asks for one: whoever implements it decides recording as a
     * separate question with counsel, rather than finding a field already waiting.
     *
     * <p>Asserted over the interface's own methods, so adding a {@code startRecording} to
     * {@code MeetingRoomProvider} goes red here rather than being noticed in review.
     */
    @Test
    @DisplayName("the seam has no recording, no identity and no waiting-room vocabulary")
    void theSeamModelsNothingItHasNotChosen() {
        var methods = java.util.Arrays.stream(MeetingRoomProvider.class.getMethods()).map(java.lang.reflect.Method::getName).toList();

        assertThat(methods).containsExactlyInAnyOrder("name", "create");
        // WHAT `needs()` MUST SAY, and my first version of this assertion had it backwards. I wrote
        // `noneMatch(contains("consent"))`, meaning "do not pre-empt the recording decision" — and the
        // needs list mentions consent precisely BECAUSE that is D17's reason for recommending against
        // recording. Naming the reason is the right thing; the test was wrong, not the code.
        assertThat(new DailyCoMeetingRoomProvider(true).needs())
            .as("recording must appear as an OPEN decision, with D17's reason for its default")
            .anyMatch(need -> need.contains("recording") && need.contains("consent"));
    }

    /**
     * A hosted room with no URL is refused, because it would read at a call site as somewhere to send
     * the customer.
     */
    @Test
    @DisplayName("a hosted room must have a url")
    void aHostedRoomMustHaveAUrl() {
        assertThat(MeetingRoom.hostedAt("https://example.test/room/abc", "dailyco").hosted()).isTrue();
        assertThatThrownBy(() -> MeetingRoom.hostedAt("  ", "dailyco")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The configuration picks ONE bean rather than relying on condition order — D44's hazard, which D45
     * deleted from the payments configuration rather than reasoning about.
     */
    @Test
    @DisplayName("the configuration supplies the relay by default and the seam when selected")
    void theConfigurationChoosesWithoutConditionOrdering() {
        SessionConfiguration config = new SessionConfiguration();

        assertThat(config.meetingRoomProvider(false)).isInstanceOf(UnconfiguredMeetingRoomProvider.class);
        assertThat(config.meetingRoomProvider(true)).isInstanceOf(DailyCoMeetingRoomProvider.class);
    }
}
