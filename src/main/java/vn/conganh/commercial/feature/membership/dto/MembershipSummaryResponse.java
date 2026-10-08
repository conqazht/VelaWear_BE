package vn.conganh.commercial.feature.membership.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.util.constant.CustomerTier;

public record MembershipSummaryResponse(
        CustomerTier currentTier,
        String tierLabel,
        BigDecimal cycleSpentAmount,
        CustomerTier nextTier,
        String nextTierLabel,
        BigDecimal amountToNextTier,
        int cycleDays,
        Instant cycleStartDate
) {
}
