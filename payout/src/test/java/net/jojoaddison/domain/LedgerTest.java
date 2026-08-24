package net.jojoaddison.domain;

import static net.jojoaddison.domain.LedgerTestSamples.*;
import static net.jojoaddison.domain.PayoutTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LedgerTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Ledger.class);
        Ledger ledger1 = getLedgerSample1();
        Ledger ledger2 = new Ledger();
        assertThat(ledger1).isNotEqualTo(ledger2);

        ledger2.setId(ledger1.getId());
        assertThat(ledger1).isEqualTo(ledger2);

        ledger2 = getLedgerSample2();
        assertThat(ledger1).isNotEqualTo(ledger2);
    }

    @Test
    void payoutTest() {
        Ledger ledger = getLedgerRandomSampleGenerator();
        Payout payoutBack = getPayoutRandomSampleGenerator();

        ledger.setPayout(payoutBack);
        assertThat(ledger.getPayout()).isEqualTo(payoutBack);

        ledger.payout(null);
        assertThat(ledger.getPayout()).isNull();
    }
}
