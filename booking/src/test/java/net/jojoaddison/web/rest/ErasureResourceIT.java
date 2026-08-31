package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingRepository;
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

    private Booking booking;

    @BeforeEach
    void aBookingWithEverythingPersonalOnIt() {
        booking = bookings.saveAndFlush(
            BookingResourceIT.createEntity(em)
                .customerLogin(CUSTOMER)
                .customerName("Ama To-Be-Forgotten")
                .visitAddress("14 Nii Boi Ave, Accra")
                .customerNote("Please call before arriving, my mother is resting")
                .onBehalfOf("My mother")
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
