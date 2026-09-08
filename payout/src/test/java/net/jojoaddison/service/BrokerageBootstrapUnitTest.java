package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * An empty {@code brokerage_config} is founded exactly once, and a populated one is never touched —
 * {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>What these are standing on</h2>
 *
 * <p>Nothing could create a {@code BrokerageConfig} on an estate that does not seed, and production does
 * not seed. {@code BookingEventConsumer.configInForce} then threw and the consumer retried for ever, and
 * {@code BrokerageResource.inForce} answered 503, so every receipt was a 503 — both after the customer's
 * money had moved.
 *
 * <h2>Each guarded thing is mutated on its own</h2>
 *
 * <p>Deliberate, and the reason is D53's review: a battery that fires on one field while reading as
 * covering two proves less than it looks. There are six things guarded here — the emptiness of the
 * table, the founding instant, and the four validated inputs — and each has a case that goes red when
 * only that thing is broken, rather than one case whose failure could be caused by any of them.
 *
 * <p>Every asserted instant is anchored in 1970 or 2019–2021. A test whose asserted moment could equal a
 * real clock's passes against the defect on some days, which is the trap D51 recorded and every test in
 * this family has followed since.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerageBootstrapUnitTest {

    /** The oldest moment this estate could plausibly be asked to price. Long before any seeded session. */
    private static final Instant AN_OLD_BOOKING = Instant.parse("2019-01-01T00:00:00Z");

    @Mock
    private BrokerageConfigRepository configs;

    /** Terms with nothing set — the estate every deployment gets unless it says otherwise. */
    private static FoundingTerms unset() {
        return new FoundingTerms();
    }

    private BrokerageBootstrap bootstrap(FoundingTerms terms) {
        when(configs.save(any(BrokerageConfig.class))).thenAnswer(saved -> saved.getArgument(0));
        return new BrokerageBootstrap(configs, terms);
    }

    private BrokerageConfig founded() {
        ArgumentCaptor<BrokerageConfig> written = ArgumentCaptor.forClass(BrokerageConfig.class);
        verify(configs).save(written.capture());
        return written.getValue();
    }

    // ------------------------------------------------------------------ the emptiness guard --

    @Test
    @DisplayName("an empty table is founded, once")
    void anEmptyTableIsFounded() {
        when(configs.count()).thenReturn(0L);

        bootstrap(unset()).bootstrap();

        verify(configs, times(1)).save(any(BrokerageConfig.class));
    }

    @Test
    @DisplayName("a second startup writes no duplicate")
    void aSecondStartupWritesNothing() {
        // The first sees an empty table and writes; the second sees the row the first wrote. This is
        // the restart, and it is the case a bootstrap that merely "runs on startup" gets wrong.
        when(configs.count()).thenReturn(0L, 1L);
        BrokerageBootstrap first = bootstrap(unset());

        first.bootstrap();
        first.bootstrap();

        verify(configs, times(1)).save(any(BrokerageConfig.class));
    }

    @Test
    @DisplayName("terms that already exist are left exactly as they are")
    void aPopulatedTableIsNeverTouched() {
        // Mutating ONLY the emptiness guard: everything else about this case is a healthy estate. An
        // estate that has decided its own terms — by migration, by hand, or by a rate change later —
        // must not acquire a second founding row beside them, and must certainly not be overwritten.
        when(configs.count()).thenReturn(1L);

        // Through the same stubbed save() the writing cases use, so removing the guard fails HERE with
        // "wanted 0 invocations" rather than somewhere downstream with a NullPointerException. A red
        // that names the wrong thing is a red somebody fixes the wrong way.
        bootstrap(unset()).bootstrap();

        verify(configs, never()).save(any(BrokerageConfig.class));
    }

    @Test
    @DisplayName("a table holding only a FUTURE-dated row still counts as decided")
    void aFutureDatedRowIsStillADecision() {
        // count(), not "is anything in force" — see BrokerageBootstrap.alreadyConfigured. Somebody who
        // scheduled next quarter's terms and nothing else has an estate that cannot price today, which
        // BrokerageTermsHealthIndicator reports; writing a founding row beside theirs would be this
        // class inventing an answer to a question they had already answered.
        when(configs.count()).thenReturn(1L);

        bootstrap(unset()).bootstrap();

        verify(configs, never()).save(any(BrokerageConfig.class));
    }

    // ------------------------------------------------------------------- the founding instant --

    @Test
    @DisplayName("the founding row predates every booking this estate could ever price")
    void theFoundingRowIsDatedToTheBeginning() {
        when(configs.count()).thenReturn(0L);

        bootstrap(unset()).bootstrap();

        // Mutating ONLY the instant: date the founding row later than a completion and D53's and D56's
        // selectors find nothing in force, which is NEW-18 arriving through its own fix. Asserted twice
        // on purpose — the exact value, and the property the value exists for.
        assertThat(founded().getEffectiveFrom()).isEqualTo(Instant.EPOCH);
        assertThat(founded().getEffectiveFrom()).isBefore(AN_OLD_BOOKING);
    }

    @Test
    @DisplayName("and BrokerageTerms selects it for a booking completed in 2019")
    void theFoundingRowIsSelectableByTheRealSelector() {
        // Through the real selector rather than by comparing two Instants: what has to be true is that
        // the two callers D57 exists for can find this row, and inForceAt is the one thing both use.
        BrokerageConfig founding = unset().founding();
        when(configs.findAll()).thenReturn(java.util.List.of(founding));

        assertThat(new BrokerageTerms(configs).inForceAt(AN_OLD_BOOKING)).containsSame(founding);
    }

    // ---------------------------------------------------------------- the values, one at a time --

    @Test
    @DisplayName("an estate that sets nothing is founded on the prototype's terms")
    void theDefaultsAreThePrototypes() {
        when(configs.count()).thenReturn(0L);

        bootstrap(unset()).bootstrap();

        BrokerageConfig founding = founded();
        assertThat(founding.getCommissionRate()).isEqualByComparingTo("0.12");
        assertThat(founding.getPayoutLagDays()).isEqualTo(3);
        assertThat(founding.getFreeCancellationHours()).isEqualTo(24);
        assertThat(founding.getLateCancellationPct()).isEqualByComparingTo("0.50");
        assertThat(founding.getCurrency()).isEqualTo("GHS");
    }

    @Test
    @DisplayName("blank is absent, exactly as it is for a payment provider's secret")
    void blankIsAbsent() {
        // Compose's `${X:-}` sets an EMPTY variable rather than leaving it unset, so this is what every
        // estate that overrides nothing actually presents. Typed fields with Spring placeholder
        // defaults would fail to bind here, on every estate, which is why these are Strings.
        FoundingTerms blank = new FoundingTerms();
        blank.setCommissionRate("");
        blank.setPayoutLagDays("  ");
        blank.setFreeCancellationHours("");
        blank.setLateCancellationPct("");
        blank.setCurrency("");

        assertThat(blank.founding().getCommissionRate()).isEqualByComparingTo("0.12");
        assertThat(blank.founding().getPayoutLagDays()).isEqualTo(3);
        assertThat(blank.founding().getCurrency()).isEqualTo("GHS");
    }

    @Test
    @DisplayName("each value is overridable on its own, and overriding one moves nothing else")
    void eachValueIsOverridableSeparately() {
        FoundingTerms rateOnly = new FoundingTerms();
        rateOnly.setCommissionRate("0.15");
        assertThat(rateOnly.founding().getCommissionRate()).isEqualByComparingTo("0.15");
        assertThat(rateOnly.founding().getPayoutLagDays()).isEqualTo(3);
        assertThat(rateOnly.founding().getCurrency()).isEqualTo("GHS");

        FoundingTerms lagOnly = new FoundingTerms();
        lagOnly.setPayoutLagDays("7");
        assertThat(lagOnly.founding().getPayoutLagDays()).isEqualTo(7);
        assertThat(lagOnly.founding().getCommissionRate()).isEqualByComparingTo("0.12");

        FoundingTerms hoursOnly = new FoundingTerms();
        hoursOnly.setFreeCancellationHours("48");
        assertThat(hoursOnly.founding().getFreeCancellationHours()).isEqualTo(48);
        assertThat(hoursOnly.founding().getLateCancellationPct()).isEqualByComparingTo("0.50");

        FoundingTerms lateOnly = new FoundingTerms();
        lateOnly.setLateCancellationPct("0.25");
        assertThat(lateOnly.founding().getLateCancellationPct()).isEqualByComparingTo("0.25");
        assertThat(lateOnly.founding().getFreeCancellationHours()).isEqualTo(24);

        FoundingTerms currencyOnly = new FoundingTerms();
        currencyOnly.setCurrency("NGN");
        assertThat(currencyOnly.founding().getCurrency()).isEqualTo("NGN");
        assertThat(currencyOnly.founding().getCommissionRate()).isEqualByComparingTo("0.12");
    }

    // ------------------------------------------------------------------------ the four refusals --

    @Test
    @DisplayName("a percentage written as a percentage is refused, not silently charged")
    void aRateAboveOneIsRefused() {
        // The mistake this bound is here for: 12 for "12%". Unrefused it is a commission of 1200% on
        // every booking, and it prices in silence — Commission.on multiplies, and nothing downstream
        // has an opinion about the size of the number it is handed.
        FoundingTerms twelvePercentAsTwelve = new FoundingTerms();
        twelvePercentAsTwelve.setCommissionRate("12");

        assertThatThrownBy(twelvePercentAsTwelve::founding)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("commission-rate")
            .hasMessageContaining("12% is 0.12");
    }

    @Test
    @DisplayName("a rate that is not a number refuses the context, on every estate")
    void anUnparseableRateIsRefused() {
        FoundingTerms nonsense = new FoundingTerms();
        nonsense.setCommissionRate("twelve percent");

        assertThatThrownBy(nonsense::founding).isInstanceOf(IllegalStateException.class).hasMessageContaining("not a decimal number");
    }

    @Test
    @DisplayName("a negative lag is refused")
    void aNegativeCountIsRefused() {
        FoundingTerms backwards = new FoundingTerms();
        backwards.setPayoutLagDays("-3");

        assertThatThrownBy(backwards::founding)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("payout-lag-days")
            .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("a currency that is not three upper-case letters is refused")
    void aMalformedCurrencyIsRefused() {
        FoundingTerms cedis = new FoundingTerms();
        cedis.setCurrency("Ghana cedi");

        assertThatThrownBy(cedis::founding).isInstanceOf(IllegalStateException.class).hasMessageContaining("currency");
    }

    @Test
    @DisplayName("a malformed value refuses at CONSTRUCTION, so it fails the context even where it would never be written")
    void aMalformedValueFailsTheContextEvenOnAPopulatedEstate() {
        // The distinction that matters. An estate whose table already holds terms would never write
        // these values, so a check made inside bootstrap() would pass — and the typo would sit in the
        // environment doing nothing until the day the table is next empty, which is the day it prices
        // every booking. Refusing in the constructor puts the failure on the deploy that introduced it.
        when(configs.count()).thenReturn(1L);
        FoundingTerms nonsense = new FoundingTerms();
        nonsense.setCommissionRate("not a rate");

        assertThatThrownBy(() -> new BrokerageBootstrap(configs, nonsense)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the founding row is a fresh entity every time it is asked for")
    void everyCallBuildsANewRow() {
        // FoundingTerms is a singleton and BrokerageConfig is an entity. Caching one and handing the
        // same instance to two savers would be handing out a managed row.
        FoundingTerms terms = unset();
        assertThat(terms.founding()).isNotSameAs(terms.founding());
        assertThat(terms.founding().getCommissionRate()).isEqualByComparingTo(new BigDecimal("0.12"));
    }
}
