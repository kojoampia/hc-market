package net.jojoaddison.domain;

import static net.jojoaddison.domain.DisputeStatusChangeTestSamples.*;
import static net.jojoaddison.domain.DisputeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DisputeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Dispute.class);
        Dispute dispute1 = getDisputeSample1();
        Dispute dispute2 = new Dispute();
        assertThat(dispute1).isNotEqualTo(dispute2);

        dispute2.setId(dispute1.getId());
        assertThat(dispute1).isEqualTo(dispute2);

        dispute2 = getDisputeSample2();
        assertThat(dispute1).isNotEqualTo(dispute2);
    }

    @Test
    void historyTest() {
        Dispute dispute = getDisputeRandomSampleGenerator();
        DisputeStatusChange disputeStatusChangeBack = getDisputeStatusChangeRandomSampleGenerator();

        dispute.addHistory(disputeStatusChangeBack);
        assertThat(dispute.getHistories()).containsOnly(disputeStatusChangeBack);
        assertThat(disputeStatusChangeBack.getDispute()).isEqualTo(dispute);

        dispute.removeHistory(disputeStatusChangeBack);
        assertThat(dispute.getHistories()).doesNotContain(disputeStatusChangeBack);
        assertThat(disputeStatusChangeBack.getDispute()).isNull();

        dispute.histories(new HashSet<>(Set.of(disputeStatusChangeBack)));
        assertThat(dispute.getHistories()).containsOnly(disputeStatusChangeBack);
        assertThat(disputeStatusChangeBack.getDispute()).isEqualTo(dispute);

        dispute.setHistories(new HashSet<>());
        assertThat(dispute.getHistories()).doesNotContain(disputeStatusChangeBack);
        assertThat(disputeStatusChangeBack.getDispute()).isNull();
    }
}
