package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.repository.BookingStatusChangeEraseRepository;
import net.jojoaddison.repository.DisputeEraseRepository;
import net.jojoaddison.repository.OutboxEraseRepository;
import net.jojoaddison.service.ErasureWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure on the booking service — {@code decisions.md} D24/D31.
 *
 * <p>The assertions come in two halves, and the second is the one that matters. Redacting the person
 * is easy to get right; leaving everything the rest of the estate depends on <em>intact</em> is what
 * makes pseudonymisation viable instead of deletion. {@code Ledger} rows in payout are keyed by
 * {@code bookingReference}, reviews are keyed by booking, and a professional's earnings are
 * aggregates over exactly these rows — so a booking that lost its reference or its money fields
 * would take a payout with it.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureResourceIT {

    private static final String URL = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private EntityManager em;

    @Autowired
    private DisputeEraseRepository disputes;

    @Autowired
    private BookingStatusChangeEraseRepository history;

    @Autowired
    private OutboxEraseRepository outbox;

    private Booking booking;

    /**
     * Every table the workflow is supposed to reach, populated.
     *
     * <p>This used to seed the booking alone, which is precisely why D34's four missing tables shipped
     * green: a fixture that touches one table can only ever prove one table is erased. A new column
     * holding personal data should fail a test here rather than reach production.
     */
    @BeforeEach
    void aCustomerWithSomethingInEveryTable() {
        booking = bookings.saveAndFlush(
            BookingResourceIT.createEntity(em)
                .customerLogin(CUSTOMER)
                .customerName("Ama To-Be-Forgotten")
                .visitAddress("14 Nii Boi Ave, Accra")
                .customerNote("Please call before arriving, my mother is resting")
                .onBehalfOf("My mother")
                .cancellationReason("my mother was taken to Korle Bu on Tuesday")
        );
        history.saveAndFlush(
            new BookingStatusChange()
                .fromStatus(BookingStatus.REQUESTED)
                .toStatus(BookingStatus.CANCELLED)
                .actor(CUSTOMER)
                .occurredAt(Instant.now())
                .note("cancel")
                .booking(booking)
        );
        disputes.saveAndFlush(
            new Dispute()
                .reference("d-erase-1")
                .bookingReference(booking.getReference())
                .raisedBy(CancelledBy.CUSTOMER)
                .raisedByLogin(CUSTOMER)
                .professionalRef(booking.getProfessionalRef())
                .reason("She never arrived and I was left waiting at 14 Nii Boi Ave with my mother")
                .status(DisputeStatus.OPEN)
                .raisedAt(Instant.now())
                .dueBy(Instant.now().plusSeconds(172800))
        );
        outbox.saveAndFlush(
            new OutboxEvent()
                .eventId("evt-erase-1")
                .type("healthconnect.booking.requested")
                .topic("healthconnect.booking.requested")
                .aggregateRef(booking.getReference())
                .actor(CUSTOMER)
                .payload(
                    "{\"bookingRef\":\"" +
                    booking.getReference() +
                    "\",\"customerLogin\":\"" +
                    CUSTOMER +
                    "\",\"customerName\":\"Ama To-Be-Forgotten\",\"priceMinor\":28000,\"currency\":\"GHS\"}"
                )
                .occurredAt(Instant.now())
        );
    }

    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the person is redacted and the record survives")
    void erasesThePersonAndKeepsTheRecord() throws Exception {
        long priceBefore = booking.getPriceMinor();
        String referenceBefore = booking.getReference();
        String professionalBefore = booking.getProfessionalRef();

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bookingsErased").value(1))
            .andExpect(jsonPath("$.pseudonym").value(ErasureWorkflow.pseudonym(CUSTOMER)));

        Booking after = bookings.findById(booking.getId()).orElseThrow();
        assertThat(after.getCancellationReason()).isNull();

        // gone
        assertThat(after.getCustomerLogin()).isEqualTo(ErasureWorkflow.pseudonym(CUSTOMER)).doesNotContain("ama");
        assertThat(after.getCustomerName()).isEqualTo("[erased]");
        assertThat(after.getVisitAddress()).isNull();
        assertThat(after.getCustomerNote()).isNull();
        assertThat(after.getOnBehalfOf()).isNull();

        // still there — this is the half that makes pseudonymisation work at all
        assertThat(after.getReference()).isEqualTo(referenceBefore);
        assertThat(after.getPriceMinor()).isEqualTo(priceBefore);
        assertThat(after.getCurrency()).isNotNull();
        assertThat(after.getProfessionalRef()).isEqualTo(professionalBefore);
        assertThat(after.getStatus()).isNotNull();
        assertThat(after.getScheduledDate()).isNotNull();
    }

    /**
     * Erasure requests arrive by email and get forwarded, so they get retried. A second pass must be
     * a no-op rather than an error or a second redaction of somebody else.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("running it twice is a no-op the second time")
    void isIdempotent() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(jsonPath("$.bookingsErased").value(1));
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.bookingsErased").value(0));
    }


    /**
     * <strong>The outbox was the worst of D34's four.</strong>
     *
     * <p>Every event this service publishes carries {@code customerLogin} and {@code customerName} in
     * its payload, and no purge of sent rows exists anywhere in the service — {@code sent_at} is only
     * ever set. So an erasure that reported success left the person's login and display name in one
     * row per event ever published about them, indefinitely, in a table nothing reads.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the published events stop naming the customer, and stay valid events")
    void redactsTheOutboxPayload() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.outboxPayloadsRedacted").value(1));

        OutboxEvent after = outbox.findByAggregateRefIn(List.of(booking.getReference())).get(0);
        assertThat(after.getPayload()).doesNotContain(CUSTOMER).doesNotContain("Ama To-Be-Forgotten");
        assertThat(after.getActor()).isEqualTo(ErasureWorkflow.pseudonym(CUSTOMER));
        // Still an event a consumer could act on — only the identity fields moved.
        assertThat(after.getPayload())
            .contains(ErasureWorkflow.pseudonym(CUSTOMER))
            .contains(booking.getReference())
            .contains("28000")
            .contains("GHS");
    }

    /** The dispute's free text is the customer's own account of what went wrong. */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a dispute the customer raised is re-keyed and its reason redacted")
    void redactsDisputes() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.disputesRedacted").value(1));

        Dispute after = disputes.findByRaisedByLogin(ErasureWorkflow.pseudonym(CUSTOMER)).get(0);
        assertThat(after.getReason()).doesNotContain("Nii Boi").doesNotContain("mother");
        assertThat(after.getBookingReference()).isEqualTo(booking.getReference());
        assertThat(disputes.findByRaisedByLogin(CUSTOMER)).isEmpty();
    }

    /**
     * The status history is the thing the first test asserts <em>survives</em>. It survived with the
     * customer's login in {@code actor} on every row they caused — and requesting and cancelling are
     * both customer actions, so that was every booking they ever made.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the audit trail keeps its shape and loses the login")
    void reKeysTheStatusHistory() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.historyRowsReKeyed").value(1));

        assertThat(history.findByActor(CUSTOMER)).isEmpty();
        BookingStatusChange after = history.findByActor(ErasureWorkflow.pseudonym(CUSTOMER)).get(0);
        // The transition itself is untouched: what happened, and when, is not personal data.
        assertThat(after.getToStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(after.getNote()).isEqualTo("cancel");
        assertThat(after.getOccurredAt()).isNotNull();
    }

    /**
     * Nobody else's rows move.
     *
     * <p>Every other test here seeds one customer, so a regression that widened the sweep — a dropped
     * {@code where}, a query matching on the wrong column — would pass all of them while erasing the
     * estate.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a second customer is left completely alone")
    void doesNotTouchAnybodyElse() throws Exception {
        Booking theirs = bookings.saveAndFlush(
            BookingResourceIT.createEntity(em).customerLogin("kwame.stillhere").customerName("Kwame Still-Here").visitAddress("2 Oxford Street")
        );

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        Booking after = bookings.findById(theirs.getId()).orElseThrow();
        assertThat(after.getCustomerLogin()).isEqualTo("kwame.stillhere");
        assertThat(after.getCustomerName()).isEqualTo("Kwame Still-Here");
        assertThat(after.getVisitAddress()).isEqualTo("2 Oxford Street");
    }

    /** The same person must carry the same alias in booking, messaging and catalog. */
    @Test
    @DisplayName("the pseudonym is deterministic and does not contain the login")
    void pseudonymIsStable() {
        String once = ErasureWorkflow.pseudonym(CUSTOMER);
        assertThat(ErasureWorkflow.pseudonym(CUSTOMER)).isEqualTo(once);
        assertThat(once).startsWith("erased-").doesNotContain(CUSTOMER);
        assertThat(ErasureWorkflow.pseudonym("someone.else")).isNotEqualTo(once);
    }

    /**
     * Erasure is irreversible and it is a decision about a real person's record. An ordinary
     * authenticated user must not be able to destroy somebody's booking history.
     */
    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user cannot erase anyone")
    void ordinaryUserIsRefused() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());
        assertThat(bookings.findById(booking.getId()).orElseThrow().getCustomerName()).isEqualTo("Ama To-Be-Forgotten");
    }
}
