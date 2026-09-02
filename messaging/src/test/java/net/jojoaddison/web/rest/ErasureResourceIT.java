package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.Direction;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.domain.PepperWitness;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.NotificationRepository;
import net.jojoaddison.repository.PepperWitnessRepository;
import net.jojoaddison.service.ErasureRegisterGuard;
import net.jojoaddison.service.ErasureWorkflow;
import net.jojoaddison.service.SubjectPseudonym;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure on the messaging service — {@code decisions.md} D24/D31.
 *
 * <p>The empty-thread case is the one this class exists for, and it came from a real erasure on the
 * quality box rather than from reasoning: a booking raises a conversation before anybody writes in
 * it, so a customer can have a thread keyed to their login and no messages at all. The receipt
 * reported the message count alone and answered <strong>zero</strong> — which an operator would file
 * against a data subject request as "messaging held nothing for this person", while a row keyed to
 * their login sat there re-keyed and uncounted.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureResourceIT {

    private static final String URL = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String PRO = "akosua.mensah";

    /**
     * The alias derivation, injected rather than called statically — decisions.md D35. It is peppered
     * from src/test/resources/config/application.yml, and SubjectPseudonymUnitTest pins what it
     * produces; here it is used only so the assertions ask for the same string the service wrote.
     */
    @Autowired
    private SubjectPseudonym pseudonyms;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ErasureWorkflow erasure;

    @Autowired
    private net.jojoaddison.repository.ErasedSubjectRepository register;

    @Autowired
    private PepperWitnessRepository witnesses;

    /** One bell-menu row. {@code deepLink} is {@code /bookings/<ref>} for everything this service raises. */
    private Notification notification(String recipient, String kind, String body, String bookingRef) {
        return notificationLinkedTo(recipient, kind, body, "/bookings/" + bookingRef);
    }

    /**
     * The same, with the deep link given literally — for the rows the invariant does not hold for.
     * {@code null} is one such row and it is not hypothetical: {@code MessagingSeeder} writes
     * notifications with no deep link at all.
     */
    private Notification notificationLinkedTo(String recipient, String kind, String body, String deepLink) {
        return notifications.saveAndFlush(
            new Notification().recipientLogin(recipient).kind(kind).body(body).raisedAt(Instant.now()).deepLink(deepLink)
        );
    }

    /** The thread a repeat booking shares — keyed to the FIRST booking, which is what the consumer writes. */
    private Conversation sharedThread(String bookingReference) {
        return conversations.saveAndFlush(
            new Conversation()
                .reference("t-" + bookingReference)
                .customerLogin(CUSTOMER)
                .professionalRef(PRO)
                .bookingReference(bookingReference)
                .lastMessageAt(Instant.now())
        );
    }

    private Conversation thread(String reference) {
        return conversations.saveAndFlush(
            new Conversation()
                .reference(reference)
                .customerLogin(CUSTOMER)
                .professionalRef(PRO)
                .bookingReference("b-" + reference)
                .lastMessageAt(Instant.now())
        );
    }

    /**
     * The regression. A thread with nothing in it is still a row bearing the customer's login, so it
     * must be re-keyed <em>and</em> counted.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("an empty conversation is pseudonymised and reported, not silently counted as nothing")
    void anEmptyConversationIsStillReported() throws Exception {
        Conversation empty = thread("c-empty");

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1))
            .andExpect(jsonPath("$.messagesErased").value(0))
            .andExpect(jsonPath("$.pseudonym").value(pseudonyms.of(CUSTOMER)));

        assertThat(conversations.findById(empty.getId()).orElseThrow().getCustomerLogin()).isEqualTo(
            pseudonyms.of(CUSTOMER)
        );
    }

    /**
     * Both directions are redacted, not just the customer's own. A professional's reply quotes what
     * it is replying to often enough that leaving one side intact would leave the other's content
     * sitting inside it.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("message bodies go in both directions, and the thread survives")
    void bodiesAreRedactedBothWays() throws Exception {
        Conversation c = thread("c-talkative");
        messages.saveAndFlush(
            new Message()
                .conversation(c)
                .direction(Direction.CUSTOMER_TO_PROFESSIONAL)
                .body("I have been getting headaches after meals")
                .sentAt(Instant.now())
        );
        messages.saveAndFlush(
            new Message()
                .conversation(c)
                .direction(Direction.PROFESSIONAL_TO_CUSTOMER)
                .body("Headaches after meals can mean a few things")
                .sentAt(Instant.now())
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1))
            .andExpect(jsonPath("$.messagesErased").value(2));

        assertThat(messages.findAll().stream().map(Message::getBody)).allSatisfy(body ->
            assertThat(body).doesNotContain("headaches").doesNotContain("Headaches")
        );
        // The thread itself survives, so the professional's list does not develop holes.
        assertThat(conversations.findById(c.getId())).isPresent();
        assertThat(conversations.findById(c.getId()).orElseThrow().getProfessionalRef()).isEqualTo(PRO);
    }


    /**
     * The notification sitting in somebody <em>else's</em> bell menu.
     *
     * <p>{@code booking.requested} raises "Ama Mensah asked for a home visit" addressed to the
     * <strong>professional</strong>, so the customer's name lives in a row keyed to a different
     * person's login and no query by recipient will ever return it. That is why it survived the first
     * implementation untouched. The row stays — it is a real event in the professional's history —
     * and only the body goes.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a notification about the customer, addressed to the professional, is redacted")
    void redactsNotificationsHeldByOtherPeople() throws Exception {
        Conversation c = thread("c-named");
        Notification mine = notifications.saveAndFlush(
            new Notification()
                .recipientLogin(CUSTOMER)
                .kind("Booking confirmed")
                .body("Your home visit on 12 Sep at 10:00 is confirmed.")
                .raisedAt(Instant.now())
                .deepLink("/bookings/" + c.getBookingReference())
        );
        Notification theirs = notifications.saveAndFlush(
            new Notification()
                .recipientLogin(PRO)
                .kind("Booking requested")
                .body("Ama Tobeforgotten asked for a home visit on 12 Sep at 10:00.")
                .raisedAt(Instant.now())
                .deepLink("/bookings/" + c.getBookingReference())
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            .andExpect(jsonPath("$.notificationsRedacted").value(1));

        assertThat(notifications.findById(mine.getId()).orElseThrow().getRecipientLogin()).isEqualTo(
            pseudonyms.of(CUSTOMER)
        );
        Notification after = notifications.findById(theirs.getId()).orElseThrow();
        assertThat(after.getBody()).doesNotContain("Ama").doesNotContain("Tobeforgotten");
        // Still the professional's row, still in their history.
        assertThat(after.getRecipientLogin()).isEqualTo(PRO);
        assertThat(after.getDeepLink()).isEqualTo("/bookings/" + c.getBookingReference());
    }

    /**
     * <strong>The second booking with the same professional — {@code decisions.md} D36.</strong>
     *
     * <p>{@code BookingEventConsumer.openThreadIfNone} dedupes threads <em>by professional</em>, so a
     * customer who books the same person twice has one conversation and it carries the <em>first</em>
     * booking's reference. Deriving the deep links from conversations alone therefore never produced
     * {@code /bookings/b-repeat}, and the professional's "Ama Tobeforgotten asked for a strength
     * session" sat in their bell menu after a receipt had reported a clean erasure. Repeat bookings
     * with one professional are not an exotic case; they are the product working.
     *
     * <p>The customer's own copy of the second booking's event carries the same {@code deepLink}, and
     * that is what now bridges the gap.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a repeat booking shares the thread, and its notification is still found")
    void findsNotificationsForARepeatBookingSharingOneThread() throws Exception {
        // One thread for two bookings, bearing the FIRST booking's reference — what the consumer writes.
        conversations.saveAndFlush(
            new Conversation()
                .reference("t-b-first")
                .customerLogin(CUSTOMER)
                .professionalRef(PRO)
                .bookingReference("b-first")
                .lastMessageAt(Instant.now())
        );

        Notification mineFirst = notification(CUSTOMER, "Booking confirmed", "Your home visit on 12 Sep at 10:00 is confirmed.", "b-first");
        Notification theirsFirst = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a home visit on 12 Sep at 10:00.",
            "b-first"
        );
        Notification mineRepeat = notification(
            CUSTOMER,
            "Booking confirmed",
            "Your strength session on 26 Sep at 09:00 is confirmed.",
            "b-repeat"
        );
        Notification theirsRepeat = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a strength session on 26 Sep at 09:00.",
            "b-repeat"
        );
        // Somebody else's booking with the same professional. A widened sweep would take this too.
        Notification aboutSomeoneElse = notification(
            PRO,
            "Booking requested",
            "Kojo Stillhere asked for a home visit on 30 Sep at 14:00.",
            "b-other"
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1))
            .andExpect(jsonPath("$.notificationsReKeyed").value(2))
            .andExpect(jsonPath("$.notificationsRedacted").value(2));

        assertThat(notifications.findById(mineFirst.getId()).orElseThrow().getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(notifications.findById(mineRepeat.getId()).orElseThrow().getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));

        for (Notification about : List.of(theirsFirst, theirsRepeat)) {
            Notification after = notifications.findById(about.getId()).orElseThrow();
            assertThat(after.getBody()).doesNotContain("Ama").doesNotContain("Tobeforgotten");
            assertThat(after.getRecipientLogin()).isEqualTo(PRO);
        }

        Notification untouched = notifications.findById(aboutSomeoneElse.getId()).orElseThrow();
        assertThat(untouched.getBody()).contains("Kojo Stillhere");
        assertThat(untouched.getRecipientLogin()).isEqualTo(PRO);
    }

    /**
     * <strong>The residual D36 records, pinned — {@code decisions.md} D36.</strong>
     *
     * <p><em>This test asserts what the code does today on purpose. It is not a regression test and
     * it was never seen to fail</em>, because the behaviour it describes is a documented gap rather
     * than a defect that was fixed. A booking still <strong>pending</strong> at the instant of the
     * erasure has raised a notification to the professional and none to the customer, and — being a
     * repeat with that professional — shares its thread with an earlier booking, so no conversation
     * and no customer-keyed notification points at it and the union cannot reach it.
     *
     * <p>It is here because three READY packages touch this mechanism and a residual nobody asserts
     * is indistinguishable from a residual nobody knows about.
     *
     * <p><strong>WP-07 closed it, and this test did not go red — which is the part worth reading.</strong>
     * D36 predicted that the day messaging was given the booking references it holds no thread for,
     * this assertion would fail and force the prose to be corrected in the same commit. What actually
     * happened is that the references arrive in a <em>payload</em>, and this test sends none: it is a
     * direct desk call, and a direct desk call still cannot reach the pending row because nothing has
     * told it the booking exists. So the residual did not disappear, it acquired a boundary — it is
     * now exactly the gap between {@code POST .../erase} and {@code POST .../erase-everywhere}, which
     * is a much more useful thing to know than "one row somewhere". The other side of that boundary is
     * {@link #theResidualIsClosedWhenTheFanOutSuppliesTheReferences}, and the two tests are only
     * meaningful as a pair.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a booking still pending at the instant of erasure is the documented residual, and is not reached")
    void theResidualIsOneRowForAPendingBooking() throws Exception {
        sharedThread("b-first");

        Notification mineFirst = notification(CUSTOMER, "Booking confirmed", "Your home visit on 12 Sep at 10:00 is confirmed.", "b-first");
        Notification theirsFirst = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a home visit on 12 Sep at 10:00.",
            "b-first"
        );
        // The pending one: requested, not yet accepted, so the customer has no copy of any event
        // about it and it reuses the thread b-first opened.
        Notification theirsPending = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a strength session on 26 Sep at 09:00.",
            "b-pending"
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            // One, not two: the pending booking's row is neither found nor counted.
            .andExpect(jsonPath("$.notificationsRedacted").value(1));

        assertThat(notifications.findById(mineFirst.getId()).orElseThrow().getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(notifications.findById(theirsFirst.getId()).orElseThrow().getBody()).doesNotContain("Tobeforgotten");

        // The residual, stated as an assertion so it cannot drift out of the documentation quietly.
        assertThat(notifications.findById(theirsPending.getId()).orElseThrow().getBody()).contains("Ama Tobeforgotten");
    }

    /**
     * <strong>The other half of the residual — {@code decisions.md} D36's design note, built as D38.</strong>
     *
     * <p>Exactly the fixture above, with the one thing this service could never have: the customer's
     * booking references, as booking knows them. Booking is authoritative for the list —
     * {@code booking.customer_login} has been indexed for the question since D34 — and the erasure
     * fan-out hands it over, so {@code /bookings/b-pending} enters the link set even though no
     * conversation and no customer-keyed notification in this service points at it.
     *
     * <p>Confirmed red before the change, against the resource that took no body:
     * {@code notificationsRedacted expected:<2> but was:<1>}, with the pending row still reading
     * "Ama Tobeforgotten asked for a strength session".
     *
     * <p>The bystander is here for the reason every erasure fixture has carried one since D34, and it
     * matters more in this test than in any of the others: the references are <em>supplied by the
     * caller</em>, so this is the one path where a wrong reference could redact somebody else's row.
     * {@code b-other} is not in the payload and must not be touched.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the pending booking's row is reached when the fan-out supplies the references")
    void theResidualIsClosedWhenTheFanOutSuppliesTheReferences() throws Exception {
        sharedThread("b-first");

        Notification mineFirst = notification(CUSTOMER, "Booking confirmed", "Your home visit on 12 Sep at 10:00 is confirmed.", "b-first");
        Notification theirsFirst = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a home visit on 12 Sep at 10:00.",
            "b-first"
        );
        Notification theirsPending = notification(
            PRO,
            "Booking requested",
            "Ama Tobeforgotten asked for a strength session on 26 Sep at 09:00.",
            "b-pending"
        );
        Notification aboutSomeoneElse = notification(
            PRO,
            "Booking requested",
            "Kojo Stillhere asked for a home visit on 30 Sep at 14:00.",
            "b-other"
        );

        mockMvc
            .perform(
                post(URL, CUSTOMER)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingReferences\":[\"b-first\",\"b-pending\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            // Two, where the desk call above reaches one.
            .andExpect(jsonPath("$.notificationsRedacted").value(2));

        assertThat(notifications.findById(mineFirst.getId()).orElseThrow().getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        for (Notification about : List.of(theirsFirst, theirsPending)) {
            Notification after = notifications.findById(about.getId()).orElseThrow();
            assertThat(after.getBody()).doesNotContain("Ama").doesNotContain("Tobeforgotten");
            // The row survives; it is a real event in the professional's history.
            assertThat(after.getRecipientLogin()).isEqualTo(PRO);
        }

        assertThat(notifications.findById(aboutSomeoneElse.getId()).orElseThrow().getBody()).contains("Kojo Stillhere");
    }

    /**
     * A reference for a booking this service has never heard of is a deep link that matches no row.
     *
     * <p>Worth pinning because the payload is the first thing in this feature that a caller supplies,
     * and "unknown reference" is the ordinary case rather than an attack: booking holds bookings that
     * never produced a notification here at all. It must be a no-op, not an error and not a widened
     * sweep.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a booking reference this service holds nothing for changes nothing")
    void anUnknownReferenceIsANoOp() throws Exception {
        sharedThread("b-first");
        Notification aboutSomeoneElse = notification(
            PRO,
            "Booking requested",
            "Kojo Stillhere asked for a home visit on 30 Sep at 14:00.",
            "b-other"
        );

        mockMvc
            .perform(
                post(URL, CUSTOMER)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingReferences\":[\"b-nothing-here\",\"\",\"   \"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsRedacted").value(0));

        assertThat(notifications.findById(aboutSomeoneElse.getId()).orElseThrow().getBody()).contains("Kojo Stillhere");
    }

    /**
     * The rows with no deep link at all, which the filter in {@code ErasureWorkflow} has always had a
     * branch for and nothing exercised.
     *
     * <p>They exist: {@code MessagingSeeder} writes every seeded notification without one. A null
     * link must be dropped rather than passed into the {@code IN} set — SQL would not match it, but
     * the point is that the customer's own row is still re-keyed on its way past, and somebody else's
     * linkless row is not touched by an erasure that has nothing pointing at it.
     *
     * <p>Like the residual above, this pins behaviour rather than a fix.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a notification with no deep link is re-keyed, and reaches nobody else")
    void notificationsWithNoDeepLinkAreHandled() throws Exception {
        sharedThread("b-first");

        Notification mineLinkless = notificationLinkedTo(CUSTOMER, "Welcome", "Welcome to BridgeCare.", null);
        Notification theirsLinkless = notificationLinkedTo(
            PRO,
            "Booking requested",
            "Kojo Stillhere asked for a home visit on 30 Sep at 14:00.",
            null
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            .andExpect(jsonPath("$.notificationsRedacted").value(0));

        Notification mine = notifications.findById(mineLinkless.getId()).orElseThrow();
        assertThat(mine.getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(mine.getDeepLink()).isNull();

        Notification untouched = notifications.findById(theirsLinkless.getId()).orElseThrow();
        assertThat(untouched.getBody()).contains("Kojo Stillhere");
        assertThat(untouched.getRecipientLogin()).isEqualTo(PRO);
    }

    /**
     * <strong>A malformed link must not match every other malformed row — {@code decisions.md} D36.</strong>
     *
     * <p>{@code "/bookings/" + bookingRef} with a blank reference is the literal {@code /bookings/}:
     * non-null and non-blank, so it passed the filter into the {@code IN} set, where it matched every
     * other row built the same way — regardless of whose booking it was — and overwrote their bodies
     * against a receipt reporting a larger and entirely plausible count.
     *
     * <p>The source is closed in {@code BookingEventConsumer.raise}, which now refuses to write a row
     * without a booking reference. This is the other half, for the rows written before it did.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a bare /bookings/ link reaches only its own row, not everybody else's")
    void aMalformedDeepLinkDoesNotOverMatch() throws Exception {
        Notification mineMalformed = notificationLinkedTo(
            CUSTOMER,
            "Booking confirmed",
            "Your strength session on 26 Sep at 09:00 is confirmed.",
            "/bookings/"
        );
        Notification strangerMalformed = notificationLinkedTo(
            PRO,
            "Booking requested",
            "Kojo Stillhere asked for a home visit on 30 Sep at 14:00.",
            "/bookings/"
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            .andExpect(jsonPath("$.notificationsRedacted").value(0));

        assertThat(notifications.findById(mineMalformed.getId()).orElseThrow().getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));

        Notification stranger = notifications.findById(strangerMalformed.getId()).orElseThrow();
        assertThat(stranger.getBody()).contains("Kojo Stillhere");
        assertThat(stranger.getRecipientLogin()).isEqualTo(PRO);
    }

    /**
     * <strong>A customer-facing body that names the customer — {@code decisions.md} D36.</strong>
     *
     * <p>Re-keying a notification to the alias moves who it is addressed to and nothing else, and for
     * as long as no customer-facing template greeted anybody by name that was enough. It coupled the
     * correctness of the erasure to the wording of the templates: the day one says "Hi Ama, your…",
     * the row is re-keyed to an alias with the name still sitting in it, permanently, and every
     * existing test stays green because none of their customer-side fixtures name the customer.
     *
     * <p>This one does. The body is now redacted along with the re-key, so the coupling is gone
     * rather than merely unlikely.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a customer's own notification that greets them by name loses the name too")
    void aCustomerFacingBodyNamingThemIsRedacted() throws Exception {
        sharedThread("b-first");
        Notification greeting = notification(
            CUSTOMER,
            "Booking confirmed",
            "Hi Ama Tobeforgotten, your strength session on 26 Sep at 09:00 is confirmed.",
            "b-first"
        );

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.notificationsReKeyed").value(1));

        Notification after = notifications.findById(greeting.getId()).orElseThrow();
        assertThat(after.getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(after.getBody()).doesNotContain("Ama").doesNotContain("Tobeforgotten");
    }

    /**
     * <strong>Erasure has to outlive the moment it runs in.</strong>
     *
     * <p>Found on the quality box, not here: booking publishes {@code booking.requested} and messaging
     * raises the thread from it seconds later, so a desk that erased in between got a clean receipt
     * and a fresh row under the original login. {@code ErasedSubject} is what makes the erasure a
     * standing fact, and this asserts the register answers for a login it has never stored.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the register remembers, so a late event has something to consult")
    void erasureIsRememberedAfterwards() throws Exception {
        assertThat(erasure.isErased(CUSTOMER)).isFalse();

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        assertThat(erasure.isErased(CUSTOMER)).isTrue();
        assertThat(erasure.isErased("someone.else")).isFalse();
        assertThat(erasure.isErased(null)).isFalse();
        assertThat(erasure.isErased("")).isFalse();
    }


    /**
     * A re-run must not move {@code erasedAt}.
     *
     * <p>Data subject requests get retried — they arrive by email and get forwarded — and
     * {@code save()} on an existing primary key would overwrite the original timestamp with the date
     * of whoever ran it a second time. That timestamp is the one fact an audit of an irreversible
     * action will ask for.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("re-running does not move the erasure timestamp")
    void erasedAtSurvivesARerun() throws Exception {
        thread("c-twice");

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());
        Instant first = register.findById(pseudonyms.of(CUSTOMER)).orElseThrow().getErasedAt();

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(0));

        assertThat(register.findById(pseudonyms.of(CUSTOMER)).orElseThrow().getErasedAt()).isEqualTo(first);
    }

    /**
     * The pepper witness must not become an answer to "has this service erased anybody" —
     * {@code decisions.md} D35.
     *
     * <p>{@code ErasureRegisterGuard} allows an unpeppered service to start while
     * {@code erased_subject} is empty, and that allowance is what lets {@code isErased} answer
     * {@code false} and {@code lockSubject} do nothing instead of throwing and stalling the
     * booking-event consumer. Put the witness row in {@code erased_subject} and {@code count()} is
     * never zero again, so the allowance disappears with nothing saying so — the symptom would be a
     * service refusing to start over a person it never erased.
     *
     * <p>This context started peppered, so the guard has written a witness by now: the row exists in
     * its own table and nothing in the register carries its alias.
     */
    @Test
    @Transactional
    @DisplayName("the witness row is not an erased subject")
    void theWitnessIsNotAnErasedSubject() {
        PepperWitness witness = witnesses.findById(ErasureRegisterGuard.WITNESS_ID).orElseThrow();

        assertThat(witness.getSubjectAlias()).startsWith("erased-");
        assertThat(register.findById(witness.getSubjectAlias())).isEmpty();
        assertThat(register.findAll()).noneMatch(s -> s.getPseudonym().equals(witness.getSubjectAlias()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user cannot erase anyone")
    void ordinaryUserIsRefused() throws Exception {
        Conversation c = thread("c-guarded");
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());
        assertThat(conversations.findById(c.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
    }
}
