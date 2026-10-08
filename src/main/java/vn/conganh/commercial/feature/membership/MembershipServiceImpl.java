package vn.conganh.commercial.feature.membership;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.membership.dto.MembershipSummaryResponse;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.CustomerTier;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    public static final int CYCLE_DAYS = 180;

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerTier getUserTier(User user, Instant now) {
        if (user == null || user.getId() == null) {
            return CustomerTier.STANDARD;
        }
        Instant since = now.minus(CYCLE_DAYS, ChronoUnit.DAYS);
        BigDecimal spent = orderRepository.findCompletedSpendSince(user.getId(), since);
        return CustomerTier.fromSpend(spent);
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipSummaryResponse getMembershipSummary(User user, Instant now) {
        if (user == null || user.getId() == null) {
            return new MembershipSummaryResponse(
                    CustomerTier.STANDARD,
                    CustomerTier.STANDARD.getLabel(),
                    BigDecimal.ZERO,
                    CustomerTier.SILVER,
                    CustomerTier.SILVER.getLabel(),
                    CustomerTier.SILVER.getMinSpend(),
                    CYCLE_DAYS,
                    now.minus(CYCLE_DAYS, ChronoUnit.DAYS)
            );
        }
        Instant since = now.minus(CYCLE_DAYS, ChronoUnit.DAYS);
        BigDecimal spent = orderRepository.findCompletedSpendSince(user.getId(), since);
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }
        CustomerTier currentTier = CustomerTier.fromSpend(spent);
        CustomerTier nextTier = currentTier.getNextTier();
        String nextTierLabel = nextTier != null ? nextTier.getLabel() : null;
        BigDecimal amountToNext = currentTier.getAmountToNextTier(spent);

        return new MembershipSummaryResponse(
                currentTier,
                currentTier.getLabel(),
                spent,
                nextTier,
                nextTierLabel,
                amountToNext,
                CYCLE_DAYS,
                since
        );
    }
}
