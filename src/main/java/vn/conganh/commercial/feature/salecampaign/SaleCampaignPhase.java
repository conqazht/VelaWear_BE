package vn.conganh.commercial.feature.salecampaign;

import java.time.Instant;

public enum SaleCampaignPhase {
    UPCOMING,
    LIVE,
    ENDED;

    public static SaleCampaignPhase from(
            SaleCampaignStatus status,
            Instant startsAt,
            Instant endsAt,
            Instant now) {
        if (status == SaleCampaignStatus.CANCELLED) {
            return ENDED;
        }
        return from(startsAt, endsAt, now);
    }

    public static SaleCampaignPhase from(Instant startsAt, Instant endsAt, Instant now) {
        if (now.isBefore(startsAt)) {
            return UPCOMING;
        }
        return now.isBefore(endsAt) ? LIVE : ENDED;
    }
}
