package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaleCampaignPhaseTest {

    @Test
    void cancelledCampaignIsAlwaysEndedEvenBeforeItsStart() {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");

        SaleCampaignPhase phase = SaleCampaignPhase.from(
                SaleCampaignStatus.CANCELLED,
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                now);

        assertThat(phase).isEqualTo(SaleCampaignPhase.ENDED);
    }
}
