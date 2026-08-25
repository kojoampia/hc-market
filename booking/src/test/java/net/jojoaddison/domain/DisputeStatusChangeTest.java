package net.jojoaddison.domain;

import static net.jojoaddison.domain.DisputeStatusChangeTestSamples.*;
import static net.jojoaddison.domain.DisputeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DisputeStatusChangeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DisputeStatusChange.class);
        DisputeStatusChange disputeStatusChange1 = getDisputeStatusChangeSample1();
        DisputeStatusChange disputeStatusChange2 = new DisputeStatusChange();
        assertThat(disputeStatusChange1).isNotEqualTo(disputeStatusChange2);

        disputeStatusChange2.setId(disputeStatusChange1.getId());
        assertThat(disputeStatusChange1).isEqualTo(disputeStatusChange2);

        disputeStatusChange2 = getDisputeStatusChangeSample2();
        assertThat(disputeStatusChange1).isNotEqualTo(disputeStatusChange2);
    }

    @Test
    void disputeTest() {
        DisputeStatusChange disputeStatusChange = getDisputeStatusChangeRandomSampleGenerator();
        Dispute disputeBack = getDisputeRandomSampleGenerator();

        disputeStatusChange.setDispute(disputeBack);
        assertThat(disputeStatusChange.getDispute()).isEqualTo(disputeBack);

        disputeStatusChange.dispute(null);
        assertThat(disputeStatusChange.getDispute()).isNull();
    }
}
