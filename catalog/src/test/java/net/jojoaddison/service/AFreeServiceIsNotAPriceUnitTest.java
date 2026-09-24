package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.CategoryRepository;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ProfessionalRatingRepository;
import net.jojoaddison.repository.VerificationReviewQueryRepository;
import net.jojoaddison.service.MarketplaceService.BrowseFilter;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A free service is a fact about a listing, not its price — NEW-50, {@code decisions.md} D100.
 *
 * <p><strong>What this file is about is the SECOND quantity, not the headline.</strong> D100 keeps
 * {@code fromPriceMinor} as the literal minimum — {@code 0} for a professional with a free service,
 * which is the honest answer to "what is the least this listing charges" — and adds
 * {@code fromPaidPriceMinor}, the cheapest service somebody can actually buy. The card renders the
 * second one; a customer's price <em>filter</em> and the two price <em>sorts</em> are questions about
 * the same quantity the card renders, so they move onto it here.
 *
 * <p>Measured on the quality estate at {@code a88c790}, before the change, and this is why the filter
 * is the sharper half of NEW-50: {@code GET /api/professionals?maxPriceMinor=9000} — "up to ₵90", the
 * floor of the prototype's own slider — answered with <strong>three</strong> professionals. One of
 * them, {@code p3}, genuinely charges ₵90. The other two were {@code p12} and {@code p13}, whose
 * cheapest <em>paid</em> services are ₵280 and ₵420 and whose dearest are ₵1,560 and ₵3,200. A
 * customer on the tightest budget the interface can express was shown two listings with nothing in
 * them they could afford. {@code sort=price-asc} led with the same two, ahead of {@code p3}.
 *
 * <p><strong>The null is two different facts and the filter must not flatten them.</strong>
 * {@code fromPaidPriceMinor} is null both for a professional with nothing published and for one whose
 * every service is free, and neither has a price — so neither is matched by a price filter, exactly
 * as {@code minRating} declines to treat an unrated professional as 0.0. That is the precedent this
 * follows rather than a new rule: "has no price" and "is free" must not collapse into one number, for
 * the same reason "unrated" and "rated badly" must not. The two cases are told apart by
 * {@code fromPriceMinor} — {@code 0} for the all-free professional, null for the empty one — which is
 * why the literal minimum is kept rather than replaced.
 *
 * <p><strong>The comparators put a null LAST in both directions, which is not an oversight in one of
 * them.</strong> A professional with no price is not the cheapest and is not the dearest; they are
 * unpriced, and an ordering by price has nowhere to put them but the end. Sorting them first under
 * {@code price-asc} is precisely the defect this item is about, arrived at by a different route.
 *
 * <p>Cards are built here rather than read from the seed on purpose: the seed has no professional
 * whose services are all free and none with no services at all, so the two edges that matter most are
 * the two nothing in either estate has ever exercised.
 */
class AFreeServiceIsNotAPriceUnitTest {

    /**
     * The three seeded professionals NEW-50 names, at their measured figures, plus the two edges the
     * seed does not contain. {@code p1} is the control: literal and paid minimum are the same number,
     * as they are for sixteen of the eighteen, so anything that moves it is a regression rather than
     * a fix.
     */
    private static final ProfessionalCard P1_CONTROL = card("p1", 15000L, 15000L, false);

    private static final ProfessionalCard P12 = card("p12", 0L, 28000L, true);
    private static final ProfessionalCard P13 = card("p13", 0L, 42000L, true);

    /** Nothing to sell but a free consultation — no paid minimum at all. */
    private static final ProfessionalCard ALL_FREE = card("p-all-free", 0L, null, true);

    /** Nothing published. The case the repository's javadoc has always named. */
    private static final ProfessionalCard NOTHING_PUBLISHED = card("p-empty", null, null, false);

    /**
     * The mapping from the repository's two-column row onto the card's three fields.
     *
     * <p><strong>Against the service with a mocked repository, not against a hand-built record.</strong>
     * Asserting {@code card(…).fromPaidPriceMinor()} on a record this file constructed would assert
     * that a record constructor assigns its arguments — D93's finding about three tests that asserted
     * nothing, which is easy to reproduce in exactly this shape. What is worth testing here is the
     * {@code Object[]} unwrapping and the {@code hasFreeService} derivation; the SQL that produces
     * the row is the integration test's subject, because no mock can evaluate a {@code case}
     * expression.
     */
    @Nested
    @DisplayName("the row becomes three fields on the card")
    class TheDerivation {

        private final MarketplaceQueryRepository repository = mock(MarketplaceQueryRepository.class);
        private final ProfessionalRatingRepository ratings = mock(ProfessionalRatingRepository.class);

        private final MarketplaceService service = new MarketplaceService(
            repository,
            mock(CategoryRepository.class),
            ratings,
            mock(VerificationReviewQueryRepository.class)
        );

        @Test
        void aFreeServiceDoesNotBecomeTheHeadlinePrice() {
            ProfessionalCard card = cardFrom(0L, 28000L);
            assertThat(card.fromPriceMinor()).isZero();
            assertThat(card.fromPaidPriceMinor()).isEqualTo(28000L);
            assertThat(card.hasFreeService()).isTrue();
        }

        @Test
        void theControlCarriesOneNumberTwiceAndNoMarker() {
            ProfessionalCard card = cardFrom(15000L, 15000L);
            assertThat(card.fromPriceMinor()).isEqualTo(15000L);
            assertThat(card.fromPaidPriceMinor()).isEqualTo(15000L);
            assertThat(card.hasFreeService()).isFalse();
        }

        /**
         * The two nulls are distinguishable, and that is the whole reason {@code fromPriceMinor} was
         * kept rather than redefined. Collapse them and a professional offering a free consultation
         * becomes indistinguishable from one offering nothing.
         */
        @Test
        void anAllFreeListingIsNotAnEmptyOne() {
            ProfessionalCard allFree = cardFrom(0L, null);
            assertThat(allFree.fromPriceMinor()).isZero();
            assertThat(allFree.fromPaidPriceMinor()).isNull();
            assertThat(allFree.hasFreeService()).isTrue();

            ProfessionalCard empty = cardFrom(null, null);
            assertThat(empty.fromPriceMinor()).isNull();
            assertThat(empty.fromPaidPriceMinor()).isNull();
            assertThat(empty.hasFreeService()).isFalse();
        }

        /**
         * Hibernate returns {@code Integer} for some dialects and expressions and {@code Long} for
         * others, and a straight cast to {@code Long} throws a {@link ClassCastException} from a
         * public read on the one it does not expect.
         */
        @Test
        void aRowOfIntegersIsReadAsMinorUnitsJustTheSame() {
            ProfessionalCard card = cardFrom(Integer.valueOf(0), Integer.valueOf(28000));
            assertThat(card.fromPriceMinor()).isZero();
            assertThat(card.fromPaidPriceMinor()).isEqualTo(28000L);
            assertThat(card.hasFreeService()).isTrue();
        }

        /**
         * An aggregate with no {@code group by} always returns one row, so this cannot happen today.
         * It is asserted because the alternative is an {@link IndexOutOfBoundsException} on a public
         * read the day it can.
         */
        @Test
        void noRowAtAllIsReadAsNothingPublished() {
            ProfessionalCard card = cardFromRows(List.of());
            assertThat(card.fromPriceMinor()).isNull();
            assertThat(card.fromPaidPriceMinor()).isNull();
            assertThat(card.hasFreeService()).isFalse();
        }

        /**
         * Built by hand rather than with {@code List.of} — a lone {@code Object[]} binds to the
         * varargs overload, so {@code List.of(new Object[]{a, b})} is a list of {@code a} and
         * {@code b} rather than a list of one row, and rejects a null besides. {@code modernizer}
         * asks for {@code List.of(Object)} in place of {@code Collections.singletonList} and is
         * wrong about this one call for both of those reasons, so neither spelling is used.
         */
        private ProfessionalCard cardFrom(Object minor, Object paidMinor) {
            List<Object[]> oneRow = new ArrayList<>();
            oneRow.add(new Object[] { minor, paidMinor });
            return cardFromRows(oneRow);
        }

        private ProfessionalCard cardFromRows(List<Object[]> rows) {
            Professional professional = new Professional().reference("p-under-test").zoneId("Africa/Accra");
            when(repository.findAll()).thenReturn(List.of(professional));
            when(repository.findPriceFloors("p-under-test")).thenReturn(rows);
            when(ratings.findByProfessionalIdIn(any())).thenReturn(List.of());
            return service.browse(filter(null, null)).get(0);
        }
    }

    @Nested
    @DisplayName("maxPriceMinor is a question about prices")
    class TheFilter {

        /**
         * The measured defect. ₵90 is the floor of the prototype's slider; p12 and p13 cannot sell
         * anything at that price and must not be offered as though they could.
         */
        @Test
        void aBudgetFilterDoesNotMatchAListingWhoseCheapestPaidServiceIsDearerThanIt() {
            assertThat(matches(9000L, P12)).isFalse();
            assertThat(matches(9000L, P13)).isFalse();
        }

        @Test
        void itStillMatchesOnceTheBudgetReachesTheCheapestPaidService() {
            assertThat(matches(28000L, P12)).isTrue();
            assertThat(matches(27999L, P12)).isFalse();
            assertThat(matches(42000L, P13)).isTrue();
            assertThat(matches(41999L, P13)).isFalse();
        }

        /** The control must not move: one number twice, so the filter behaves as it always has. */
        @Test
        void theControlIsUnchanged() {
            assertThat(matches(15000L, P1_CONTROL)).isTrue();
            assertThat(matches(14999L, P1_CONTROL)).isFalse();
            assertThat(matches(9000L, P1_CONTROL)).isFalse();
        }

        /**
         * Excluded, not matched-at-any-budget. {@code minRating}'s treatment of an unrated
         * professional is the precedent and the argument: a filter over a quantity somebody does not
         * have cannot answer for them, and inventing a zero is how "free" and "unpriced" become the
         * same row. Finding these listings is a different filter, which does not exist yet.
         */
        @Test
        void aListingWithNoPriceIsNotMatchedByAPriceFilter() {
            assertThat(matches(9000L, ALL_FREE)).isFalse();
            assertThat(matches(1_000_000L, ALL_FREE)).isFalse();
            assertThat(matches(9000L, NOTHING_PUBLISHED)).isFalse();
            assertThat(matches(1_000_000L, NOTHING_PUBLISHED)).isFalse();
        }

        /** No budget named is no price question asked, so every listing is still in the set. */
        @Test
        void noBudgetFiltersNothing() {
            assertThat(matches(null, P12)).isTrue();
            assertThat(matches(null, ALL_FREE)).isTrue();
            assertThat(matches(null, NOTHING_PUBLISHED)).isTrue();
        }
    }

    @Nested
    @DisplayName("the price sorts order by what a listing sells for")
    class TheComparators {

        @Test
        void cheapestFirstDoesNotLeadWithAFreeConsultation() {
            assertThat(sorted("price-asc", P12, P13, P1_CONTROL))
                .containsExactly("p1", "p12", "p13");
        }

        @Test
        void dearestFirstIsTheSameQuantityReversed() {
            assertThat(sorted("price-desc", P12, P13, P1_CONTROL))
                .containsExactly("p13", "p12", "p1");
        }

        /**
         * Last in BOTH directions, and asserted in both because {@code nullsLast} on one comparator
         * and {@code nullsFirst} on the other reads as symmetry and is not. An unpriced listing is
         * neither end of a price ordering.
         */
        @Test
        void anUnpricedListingSortsLastWhicheverWayTheOrderRuns() {
            assertThat(sorted("price-asc", ALL_FREE, NOTHING_PUBLISHED, P1_CONTROL))
                .startsWith("p1")
                .endsWith("p-all-free", "p-empty");
            assertThat(sorted("price-desc", ALL_FREE, NOTHING_PUBLISHED, P1_CONTROL))
                .startsWith("p1")
                .endsWith("p-all-free", "p-empty");
        }
    }

    // ------------------------------------------------------------------------- helpers --

    private static boolean matches(Long maxPriceMinor, ProfessionalCard card) {
        return filter(maxPriceMinor, null).matches(card);
    }

    private static List<String> sorted(String sort, ProfessionalCard... cards) {
        List<ProfessionalCard> list = new ArrayList<>(List.of(cards));
        list.sort(filter(null, sort).comparator());
        return list.stream().map(ProfessionalCard::ref).toList();
    }

    private static BrowseFilter filter(Long maxPriceMinor, String sort) {
        return new BrowseFilter(null, null, null, null, maxPriceMinor, null, false, null, sort);
    }

    /** Only the four components any assertion here reads are given real values. */
    private static ProfessionalCard card(String ref, Long fromPriceMinor, Long fromPaidPriceMinor, boolean hasFreeService) {
        return new ProfessionalCard(
            ref,
            "Display " + ref,
            "DR",
            "headline",
            "Nutritionist",
            "NUTRITION",
            "Accra",
            List.of("ONLINE"),
            "VERIFIED",
            true,
            true,
            5,
            30,
            50,
            List.of("English"),
            "#0D3058",
            "#256ABF",
            BigDecimal.valueOf(4.5),
            10L,
            fromPriceMinor,
            fromPaidPriceMinor,
            hasFreeService,
            "GHS",
            "Africa/Accra"
        );
    }
}
