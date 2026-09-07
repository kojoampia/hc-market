package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.service.BrokerageClient;
import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the receipt asks payout for — {@code decisions.md} D56, backlog NEW-16.
 *
 * <p>{@code TheReceiptAsksForAMomentAndADayUnitTest} pins the query string once the resource has
 * decided what to ask; this pins the deciding. They are separable failures and neither test can see
 * the other's: passing {@code null} for the moment here leaves every assertion in that class true —
 * it has its own null case — and the receipt would go back to being struck at the start of a day
 * with both suites green. That is precisely the shape of defect NEW-16 is, so it gets its own test.
 *
 * <p>{@link BrokerageClient} is mocked rather than stubbed over HTTP, the choice
 * {@code CustomerBookingCreateIT} makes about {@code CatalogClient} and for the same reason: what is
 * under test is the arguments this resource composes, and standing up a second service to observe
 * them would test Spring's client instead.
 *
 * <p>The completion instant is in 2020 and at 14:00 — the hour NEW-16's defect turns on, and a year
 * no clock will be in again, so nothing here can agree with a fallback by coincidence.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = TheReceiptIsStruckAtTheCompletionIT.CUSTOMER)
class TheReceiptIsStruckAtTheCompletionIT {

    static final String CUSTOMER = "ama.receipt";

    /** 14:00 in the marketplace's calendar, which is the side of noon that used to be lost. */
    private static final Instant COMPLETED_AT = LocalDateTime.of(2020, 6, 1, 14, 0)
        .atZone(MarketCalendar.MARKET_ZONE)
        .toInstant();

    private static final LocalDate SCHEDULED_ON = LocalDate.of(2020, 6, 1);

    /**
     * The value of the {@code Authorization} header the receipt forwards to payout.
     *
     * <p>Deliberately <strong>not</strong> a {@code Bearer …} string. The authentication under test
     * is {@link WithMockUser}'s, and the JWT filter takes over the moment the header carries that
     * scheme — so a plausible-looking token here made every request 401 before it reached the
     * resource, which reads as a broken fixture rather than as the header doing its job. What the
     * resource does with the value is forward it opaquely, so its shape is not the point.
     */
    private static final String HELD_TOKEN = "a-customers-token-forwarded-verbatim";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookings;

    @MockitoBean
    private BrokerageClient brokerage;

    @BeforeEach
    void payoutAnswersNormally() {
        when(brokerage.splitFor(anyLong(), any(), any(), anyString())).thenReturn(
            new BrokerageClient.Split(28_000L, 8_400L, 19_600L, "0.30", "GHS", 24, "0.50")
        );
    }

    private Booking booking(String reference, BookingStatus status, Instant completedAt) {
        return bookings.save(
            new Booking()
                .reference(reference)
                .customerLogin(CUSTOMER)
                .customerName("Ama Receipt")
                .professionalRef("p1")
                .professionalLogin("akosua.mensah")
                .serviceRef("s1a")
                .serviceName("Nutrition assessment")
                .priceMinor(28_000L)
                .currency("GHS")
                .scheduledDate(SCHEDULED_ON)
                .scheduledTime(LocalTime.of(13, 0))
                .zoneId("Africa/Accra")
                .deliveryMode(DeliveryMode.ONLINE)
                .status(status)
                .careSummaryShared(false)
                .raisedAt(Instant.parse("2020-05-20T08:00:00Z"))
                .completedAt(completedAt)
                .reviewed(false)
        );
    }

    private void fetchReceipt(String reference) throws Exception {
        mockMvc
            .perform(get("/api/bookings/" + reference + "/receipt").header(HttpHeaders.AUTHORIZATION, HELD_TOKEN))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @DisplayName("a completed booking is priced at the instant it completed, and the day goes beside it")
    void theCompletionInstantIsWhatIsAsked() throws Exception {
        booking("b-receipt-completed", BookingStatus.COMPLETED, COMPLETED_AT);

        fetchReceipt("b-receipt-completed");

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<LocalDate> on = ArgumentCaptor.forClass(LocalDate.class);
        verify(brokerage).splitFor(anyLong(), at.capture(), on.capture(), anyString());

        assertThat(at.getValue()).as("the moment the session completed, to the second").isEqualTo(COMPLETED_AT);
        // The day is that instant's day in the marketplace's calendar — decisions.md D51, and now
        // named at both ends of the round trip rather than only at payout's.
        assertThat(on.getValue()).isEqualTo(LocalDate.ofInstant(COMPLETED_AT, MarketCalendar.MARKET_ZONE));
    }

    @Test
    @Transactional
    @DisplayName("a booking that has not completed has no moment, and none is manufactured")
    void noCompletionMeansNoInstant() throws Exception {
        booking("b-receipt-confirmed", BookingStatus.CONFIRMED, null);

        fetchReceipt("b-receipt-confirmed");

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<LocalDate> on = ArgumentCaptor.forClass(LocalDate.class);
        verify(brokerage).splitFor(anyLong(), at.capture(), on.capture(), anyString());

        // Not the scheduled day's midnight, and not a clock reading: there is no moment, and payout
        // is told so rather than handed one this service invented.
        assertThat(at.getValue()).isNull();
        assertThat(on.getValue()).isEqualTo(SCHEDULED_ON);
    }

    @Test
    @Transactional
    @DisplayName("a payout that cannot be asked is 503, never a guessed receipt")
    void anUnavailablePayoutIsNotAReceipt() throws Exception {
        booking("b-receipt-unavailable", BookingStatus.COMPLETED, COMPLETED_AT);
        when(brokerage.splitFor(anyLong(), any(), any(), anyString())).thenThrow(
            new BrokerageClient.PayoutUnavailable("could not reach the payout service to price this receipt")
        );

        mockMvc
            .perform(
                get("/api/bookings/b-receipt-unavailable/receipt").header(HttpHeaders.AUTHORIZATION, HELD_TOKEN)
            )
            .andExpect(status().isServiceUnavailable());
    }
}
