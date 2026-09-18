package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ServiceOfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the two "from" prices look like on the wire — NEW-50, {@code decisions.md} D100.
 *
 * <p><strong>On the serialised body rather than on the DTO, deliberately.</strong> The sibling unit
 * test {@code AFreeServiceIsNotAPriceUnitTest} settles the semantics against hand-built records and
 * cannot see whether either field reaches a client: a record component that is never serialised, or
 * one renamed on the way out, would leave every assertion there green while NEW-48's card has
 * nothing to render. That is D47's finding on the verification desk — the field's presence on the
 * wire was the thing that mattered — applied to a field being <em>added</em> rather than withheld.
 *
 * <p>Rows are planted rather than seeded. The seed does not load under test, and in any case it
 * contains no professional whose services are all free and none with nothing published, which are
 * the two cases most likely to be got wrong. The prices are the measured figures of {@code p12},
 * {@code p13} and {@code p1} so that this file and the item describe the same numbers.
 *
 * <p>Membership is asserted by reference and never by a total, because {@code browse} reads the whole
 * professional table and this file must not depend on what else is in it.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TheFromPriceOnTheWireIT {

    private static final String BROWSE = "/api/professionals";
    private static final String PROFILE = "/api/professionals/{ref}";
    private static final String FACETS = "/api/professionals/facets";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private ServiceOfferingRepository offerings;

    @Autowired
    private EntityManager em;

    @Autowired
    private ObjectMapper json;

    /** The unique suffix keeps these five apart from anything else the table holds. */
    private String suffix;

    /**
     * A city nothing else uses, so the facet assertion can be scoped to these five rows.
     *
     * <p>The facet range is a minimum and a maximum over whatever matched, which is the one
     * assertion in this file that cannot be made by reference — so it is made against a filter only
     * these rows satisfy instead of against the whole table.
     */
    private String city;

    private String freeAndPaid;
    private String freeAndDearer;
    private String control;
    private String allFree;
    private String nothingPublished;

    @BeforeEach
    void plantFourListings() {
        suffix = "-new50-" + System.nanoTime();
        city = "Testville" + suffix;
        // p12 as measured: a free discovery session, then ₵280 and ₵1,560.
        freeAndPaid = plant("free-and-paid", 0L, 28000L, 156000L);
        // p13 as measured: a free doula consultation, then ₵420 and ₵3,200.
        freeAndDearer = plant("free-and-dearer", 0L, 42000L, 320000L);
        // p1, the control: sixteen of the eighteen look like this — no free service at all.
        control = plant("control", 15000L, 28000L, 34000L);
        // Neither of these exists in either estate.
        allFree = plant("all-free", 0L);
        nothingPublished = plant("nothing-published");
    }

    // ------------------------------------------------------------------ the card body --

    /**
     * The headline a card renders is the cheapest service somebody can buy; the literal minimum is
     * still published beside it, and {@code 0} is still the honest answer to what the least this
     * listing charges is.
     */
    @Test
    @Transactional
    void aFreeServiceIsMarkedAndDoesNotBecomeTheHeadlinePrice() throws Exception {
        mockMvc
            .perform(get(PROFILE, freeAndPaid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.fromPriceMinor").value(0))
            .andExpect(jsonPath("$.card.fromPaidPriceMinor").value(28000))
            .andExpect(jsonPath("$.card.hasFreeService").value(true));

        mockMvc
            .perform(get(PROFILE, freeAndDearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.fromPriceMinor").value(0))
            .andExpect(jsonPath("$.card.fromPaidPriceMinor").value(42000))
            .andExpect(jsonPath("$.card.hasFreeService").value(true));
    }

    /** The control carries one number twice and says there is nothing free. */
    @Test
    @Transactional
    void theControlIsUnmovedAndUnmarked() throws Exception {
        mockMvc
            .perform(get(PROFILE, control))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.fromPriceMinor").value(15000))
            .andExpect(jsonPath("$.card.fromPaidPriceMinor").value(15000))
            .andExpect(jsonPath("$.card.hasFreeService").value(false));
    }

    /**
     * A listing with nothing to sell has no paid minimum — null, never zero, because a zero here is
     * the "from ₵0" headline this item exists to stop. It is still distinguishable from an empty
     * listing by the literal minimum, which is what keeps "free" and "unpriced" two facts.
     */
    @Test
    @Transactional
    void anAllFreeListingHasNoPaidPriceAndIsStillMarkedFree() throws Exception {
        mockMvc
            .perform(get(PROFILE, allFree))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.fromPriceMinor").value(0))
            .andExpect(jsonPath("$.card.fromPaidPriceMinor").isEmpty())
            .andExpect(jsonPath("$.card.hasFreeService").value(true));
    }

    /** Unchanged behaviour, and the case the repository javadoc has always named. */
    @Test
    @Transactional
    void aListingWithNothingPublishedCarriesNeitherPrice() throws Exception {
        mockMvc
            .perform(get(PROFILE, nothingPublished))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.fromPriceMinor").isEmpty())
            .andExpect(jsonPath("$.card.fromPaidPriceMinor").isEmpty())
            .andExpect(jsonPath("$.card.hasFreeService").value(false));
    }

    /** Browse serves the same three fields as the profile — one {@code toCard} for both paths. */
    @Test
    @Transactional
    void browseCarriesBothPricesToo() throws Exception {
        JsonNode card = browseCardFor(freeAndPaid, BROWSE + "?size=500");
        assertThat(card.get("fromPriceMinor").asLong()).isZero();
        assertThat(card.get("fromPaidPriceMinor").asLong()).isEqualTo(28000L);
        assertThat(card.get("hasFreeService").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------- filtering and sorting --

    /**
     * The measured defect, end to end. ₵90 is the floor of the prototype's price slider; before D100
     * both free-service listings answered it, and the dearer of the two sells nothing under ₵420.
     */
    @Test
    @Transactional
    void aBudgetFilterDoesNotOfferAListingItCannotBuyFrom() throws Exception {
        List<String> refs = refsFrom(BROWSE + "?maxPriceMinor=9000&size=500");
        assertThat(refs).doesNotContain(freeAndPaid, freeAndDearer);
    }

    @Test
    @Transactional
    void thatSameListingIsOfferedOnceTheBudgetReachesItsCheapestPaidService() throws Exception {
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=28000&size=500")).contains(freeAndPaid);
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=27999&size=500")).doesNotContain(freeAndPaid);
    }

    /** The control's own boundary, so a filter change that moved it would be red here. */
    @Test
    @Transactional
    void theControlsBudgetBoundaryIsUnmoved() throws Exception {
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=15000&size=500")).contains(control);
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=14999&size=500")).doesNotContain(control);
    }

    /**
     * Excluded at every budget, following {@code minRating}'s refusal to read an unrated professional
     * as 0.0. Asserted at an absurd ceiling as well as a tight one, because an implementation that
     * treated a missing price as zero would pass the tight case for the wrong reason.
     */
    @Test
    @Transactional
    void aListingWithNoPriceIsNotMatchedByAPriceFilterAtAnyBudget() throws Exception {
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=9000&size=500")).doesNotContain(allFree, nothingPublished);
        assertThat(refsFrom(BROWSE + "?maxPriceMinor=100000000&size=500")).doesNotContain(allFree, nothingPublished);
    }

    /** No budget named is no price question asked. All five listings are still in the set. */
    @Test
    @Transactional
    void anUnfilteredBrowseStillCarriesEveryListing() throws Exception {
        assertThat(refsFrom(BROWSE + "?size=500"))
            .contains(freeAndPaid, freeAndDearer, control, allFree, nothingPublished);
    }

    /** Cheapest first orders by what a listing sells for, so the free consultations do not lead. */
    @Test
    @Transactional
    void cheapestFirstDoesNotLeadWithAFreeConsultation() throws Exception {
        List<String> refs = refsFrom(BROWSE + "?sort=price-asc&size=500");
        assertThat(indexOf(refs, control)).isLessThan(indexOf(refs, freeAndPaid));
        assertThat(indexOf(refs, freeAndPaid)).isLessThan(indexOf(refs, freeAndDearer));
    }

    @Test
    @Transactional
    void dearestFirstIsTheSameQuantityReversed() throws Exception {
        List<String> refs = refsFrom(BROWSE + "?sort=price-desc&size=500");
        assertThat(indexOf(refs, freeAndDearer)).isLessThan(indexOf(refs, freeAndPaid));
        assertThat(indexOf(refs, freeAndPaid)).isLessThan(indexOf(refs, control));
    }

    /**
     * Last in both directions. An unpriced listing is neither the cheapest nor the dearest, and
     * asserting only the ascending half would leave a {@code nullsFirst} on the other comparator
     * unnoticed.
     */
    @Test
    @Transactional
    void anUnpricedListingSortsLastWhicheverWayTheOrderRuns() throws Exception {
        for (String sort : List.of("price-asc", "price-desc")) {
            List<String> refs = refsFrom(BROWSE + "?sort=" + sort + "&size=500");
            assertThat(indexOf(refs, control)).isLessThan(indexOf(refs, allFree));
            assertThat(indexOf(refs, control)).isLessThan(indexOf(refs, nothingPublished));
        }
    }

    /**
     * The fourth site, which NEW-50 did not name: the facet range is what a client draws the price
     * slider from, so a floor of {@code 0} gives the slider a bracket that matches nothing. It is the
     * same quantity the filter reads, or the slider and the filter disagree about their own scale.
     */
    @Test
    @Transactional
    void theFacetPriceRangeIsTheSameQuantityTheFilterReads() throws Exception {
        JsonNode facets = json.readTree(
            mockMvc
                .perform(get(FACETS + "?city={city}", city))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        );
        // Five rows matched, of which three have a paid price: 15000, 28000 and 42000.
        assertThat(facets.get("total").asLong()).isEqualTo(5L);
        assertThat(facets.get("minPriceMinor").asLong()).isEqualTo(15000L);
        assertThat(facets.get("maxPriceMinor").asLong()).isEqualTo(42000L);
    }

    // ------------------------------------------------------------------------- helpers --

    private JsonNode browseCardFor(String ref, String url) throws Exception {
        JsonNode body = json.readTree(
            mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
        );
        for (JsonNode card : body.get("content")) {
            if (ref.equals(card.get("ref").asText())) {
                return card;
            }
        }
        throw new AssertionError(ref + " is not in the browse response");
    }

    private List<String> refsFrom(String url) throws Exception {
        JsonNode body = json.readTree(
            mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
        );
        List<String> refs = new ArrayList<>();
        body.get("content").forEach(card -> refs.add(card.get("ref").asText()));
        return refs;
    }

    /** Fails loudly rather than comparing a -1, which would read as "sorted correctly". */
    private static int indexOf(List<String> refs, String ref) {
        int at = refs.indexOf(ref);
        if (at < 0) {
            throw new AssertionError(ref + " is not in the sorted response at all");
        }
        return at;
    }

    private String plant(String role, long... activePricesMinor) {
        Professional professional = ProfessionalResourceIT.createEntity(em);
        String reference = role + suffix;
        professional.reference(reference).userLogin(role + ".owner" + suffix).city(city);
        professionals.saveAndFlush(professional);

        int order = 1;
        for (long priceMinor : activePricesMinor) {
            offerings.saveAndFlush(
                new ServiceOffering()
                    .reference("svc-" + order + "-" + reference)
                    .name(priceMinor == 0L ? "Discovery session" : "Session at " + priceMinor)
                    .durationMinutes(60)
                    .priceMinor(priceMinor)
                    .currency("GHS")
                    .active(true)
                    .sortOrder(order)
                    .professional(professional)
            );
            order++;
        }
        return reference;
    }
}
