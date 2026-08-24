package net.jojoaddison.domain;

import static net.jojoaddison.domain.LedgerTestSamples.*;
import static net.jojoaddison.domain.PayoutTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PayoutTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Payout.class);
        Payout payout1 = getPayoutSample1();
        Payout payout2 = new Payout();
        assertThat(payout1).isNotEqualTo(payout2);

        payout2.setId(payout1.getId());
        assertThat(payout1).isEqualTo(payout2);

        payout2 = getPayoutSample2();
        assertThat(payout1).isNotEqualTo(payout2);
    }

    @Test
    void entriesTest() {
        Payout payout = getPayoutRandomSampleGenerator();
        Ledger ledgerBack = getLedgerRandomSampleGenerator();

        payout.addEntries(ledgerBack);
        assertThat(payout.getEntrieses()).containsOnly(ledgerBack);
        assertThat(ledgerBack.getPayout()).isEqualTo(payout);

        payout.removeEntries(ledgerBack);
        assertThat(payout.getEntrieses()).doesNotContain(ledgerBack);
        assertThat(ledgerBack.getPayout()).isNull();

        payout.entrieses(new HashSet<>(Set.of(ledgerBack)));
        assertThat(payout.getEntrieses()).containsOnly(ledgerBack);
        assertThat(ledgerBack.getPayout()).isEqualTo(payout);

        payout.setEntrieses(new HashSet<>());
        assertThat(payout.getEntrieses()).doesNotContain(ledgerBack);
        assertThat(ledgerBack.getPayout()).isNull();
    }
}
