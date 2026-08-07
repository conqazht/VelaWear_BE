package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sale campaign phase transitions characterization")
class SaleCampaignPhaseTest {

    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    @DisplayName("Cancelled campaign is always ENDED regardless of start/end dates")
    void cancelledCampaignIsAlwaysEndedEvenBeforeItsStart() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(
                SaleCampaignStatus.CANCELLED,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.ENDED);
    }

    @Test
    @DisplayName("Future start date resolves to UPCOMING phase")
    void futureStartsAt_returnsUpcoming() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(
                SaleCampaignStatus.PUBLISHED,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.UPCOMING);
    }

    @Test
    @DisplayName("Current time between start and end dates resolves to LIVE phase")
    void activeTimeWindow_returnsLive() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(
                SaleCampaignStatus.PUBLISHED,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.LIVE);
    }

    @Test
    @DisplayName("Past end date resolves to ENDED phase")
    void pastEndsAt_returnsEnded() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(
                SaleCampaignStatus.PUBLISHED,
                NOW.minusSeconds(7200),
                NOW.minusSeconds(3600),
                NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.ENDED);
    }

    @Test
    @DisplayName("Boundary check: start date exactly equal to now is LIVE")
    void startsAtEqualsNow_returnsLive() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(NOW, NOW.plusSeconds(3600), NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.LIVE);
    }

    @Test
    @DisplayName("Boundary check: end date exactly equal to now is ENDED")
    void endsAtEqualsNow_returnsEnded() {
        SaleCampaignPhase phase = SaleCampaignPhase.from(NOW.minusSeconds(3600), NOW, NOW);

        assertThat(phase).isEqualTo(SaleCampaignPhase.ENDED);
    }
}
