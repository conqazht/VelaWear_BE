package vn.conganh.commercial.feature.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.membership.dto.MembershipSummaryResponse;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.CustomerTier;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Membership - MembershipServiceImpl")
class MembershipServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    private MembershipServiceImpl membershipService;

    private User user;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipServiceImpl(orderRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 100L);
    }

    @Test
    @DisplayName("getUserTier - trả về STANDARD khi chi tiêu = 0 hoặc null")
    void getUserTier_zeroSpend_returnsStandard() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        CustomerTier tier = membershipService.getUserTier(user, Instant.now());

        assertThat(tier).isEqualTo(CustomerTier.STANDARD);
    }

    @Test
    @DisplayName("getUserTier - trả về SILVER khi chi tiêu đạt từ 2 triệu đến dưới 5 triệu")
    void getUserTier_twoMillionSpend_returnsSilver() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(new BigDecimal("2500000"));

        CustomerTier tier = membershipService.getUserTier(user, Instant.now());

        assertThat(tier).isEqualTo(CustomerTier.SILVER);
    }

    @Test
    @DisplayName("getUserTier - trả về GOLD khi chi tiêu đạt từ 5 triệu đến dưới 10 triệu")
    void getUserTier_fiveMillionSpend_returnsGold() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(new BigDecimal("6500000"));

        CustomerTier tier = membershipService.getUserTier(user, Instant.now());

        assertThat(tier).isEqualTo(CustomerTier.GOLD);
    }

    @Test
    @DisplayName("getUserTier - trả về DIAMOND khi chi tiêu từ 10 triệu trở lên")
    void getUserTier_tenMillionSpend_returnsDiamond() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(new BigDecimal("15000000"));

        CustomerTier tier = membershipService.getUserTier(user, Instant.now());

        assertThat(tier).isEqualTo(CustomerTier.DIAMOND);
    }

    @Test
    @DisplayName("getMembershipSummary - tính toán đúng chu kỳ 180 ngày và số tiền cần để lên hạng tiếp theo")
    void getMembershipSummary_silverUser_calculatesRemainingForGold() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(new BigDecimal("3000000"));

        MembershipSummaryResponse summary = membershipService.getMembershipSummary(user, Instant.now());

        assertThat(summary.currentTier()).isEqualTo(CustomerTier.SILVER);
        assertThat(summary.nextTier()).isEqualTo(CustomerTier.GOLD);
        assertThat(summary.amountToNextTier()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(summary.cycleDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("getMembershipSummary - người dùng DIAMOND không còn hạng kế tiếp")
    void getMembershipSummary_diamondUser_noNextTier() {
        when(orderRepository.findCompletedSpendSince(eq(100L), any(Instant.class)))
                .thenReturn(new BigDecimal("12000000"));

        MembershipSummaryResponse summary = membershipService.getMembershipSummary(user, Instant.now());

        assertThat(summary.currentTier()).isEqualTo(CustomerTier.DIAMOND);
        assertThat(summary.nextTier()).isNull();
        assertThat(summary.amountToNextTier()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
