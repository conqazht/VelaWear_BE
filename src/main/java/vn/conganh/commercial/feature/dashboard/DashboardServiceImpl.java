package vn.conganh.commercial.feature.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.dashboard.dto.CashflowChartPoint;
import vn.conganh.commercial.feature.dashboard.dto.CrmDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.CrmKpiSummary;
import vn.conganh.commercial.feature.dashboard.dto.CustomerGrowthChartPoint;
import vn.conganh.commercial.feature.dashboard.dto.CustomerReviewSummary;
import vn.conganh.commercial.feature.dashboard.dto.DashboardKpiSummary;
import vn.conganh.commercial.feature.dashboard.dto.EcommerceDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.FinanceDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.FinanceKpiSummary;
import vn.conganh.commercial.feature.dashboard.dto.InventorySummary;
import vn.conganh.commercial.feature.dashboard.dto.MembershipTierSummary;
import vn.conganh.commercial.feature.dashboard.dto.PaymentMethodSummary;
import vn.conganh.commercial.feature.dashboard.dto.PaymentStatusSummary;
import vn.conganh.commercial.feature.dashboard.dto.RecentOrderSummary;
import vn.conganh.commercial.feature.dashboard.dto.RecentPaymentTransaction;
import vn.conganh.commercial.feature.dashboard.dto.RecentReviewSummary;
import vn.conganh.commercial.feature.dashboard.dto.RevenueChartPoint;
import vn.conganh.commercial.feature.dashboard.dto.TopCustomerSummary;
import vn.conganh.commercial.feature.dashboard.dto.TopProductSummary;
import vn.conganh.commercial.feature.dashboard.dto.TopSpendingCustomerProjection;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.dto.TopSellingProductProjection;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.review.Review;
import vn.conganh.commercial.feature.review.ReviewRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CustomerTier;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Override
    public EcommerceDashboardResponse getEcommerceDashboard(String period) {
        String normalizedPeriod = normalizePeriod(period);
        PeriodRange range = calculatePeriodRange(normalizedPeriod);

        // 1. KPI Calculations
        BigDecimal currentRevenue = orderRepository.sumRevenueBetween(range.currentStart(), range.currentEnd());
        if (currentRevenue == null) currentRevenue = BigDecimal.ZERO;
        BigDecimal previousRevenue = orderRepository.sumRevenueBetween(range.previousStart(), range.previousEnd());
        if (previousRevenue == null) previousRevenue = BigDecimal.ZERO;
        Double revenueChange = calculatePercentageChange(previousRevenue, currentRevenue);

        long currentOrders = orderRepository.countOrdersBetween(range.currentStart(), range.currentEnd());
        long previousOrders = orderRepository.countOrdersBetween(range.previousStart(), range.previousEnd());
        Double ordersChange = calculatePercentageChange(previousOrders, currentOrders);

        BigDecimal currentAov = currentOrders > 0
                ? currentRevenue.divide(BigDecimal.valueOf(currentOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal previousAov = previousOrders > 0
                ? previousRevenue.divide(BigDecimal.valueOf(previousOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Double aovChange = calculatePercentageChange(previousAov, currentAov);

        long currentCancelled = orderRepository.countCancelledOrdersBetween(range.currentStart(), range.currentEnd());
        double cancellationRate = currentOrders > 0
                ? Math.round(((double) currentCancelled / currentOrders) * 1000.0) / 10.0
                : 0.0;

        DashboardKpiSummary kpis = DashboardKpiSummary.builder()
                .grossSales(currentRevenue)
                .grossSalesChangePercentage(revenueChange)
                .totalOrders(currentOrders)
                .totalOrdersChangePercentage(ordersChange)
                .averageOrderValue(currentAov)
                .aovChangePercentage(aovChange)
                .cancellationsCount(currentCancelled)
                .cancellationRate(cancellationRate)
                .build();

        // 2. Revenue Chart
        List<Order> chartOrders = orderRepository.findRevenueOrdersBetween(range.currentStart(), range.currentEnd());
        List<RevenueChartPoint> revenueChart = buildRevenueChart(range, chartOrders);

        // 3. Recent Orders
        List<Order> recentOrdersList = orderRepository.findRecentOrders(PageRequest.of(0, 10));
        List<RecentOrderSummary> recentOrders = recentOrdersList.stream()
                .map(order -> RecentOrderSummary.builder()
                        .id(order.getId())
                        .orderCode(order.getOrderCode())
                        .customerName(order.getReceiverName())
                        .customerEmail(order.getUser() != null ? order.getUser().getEmail() : "")
                        .createdAt(order.getCreatedAt())
                        .status(order.getStatus())
                        .paymentStatus(order.getPaymentStatus())
                        .paymentMethod(order.getPaymentMethod())
                        .totalAmount(order.getFinalAmount())
                        .build())
                .toList();

        // 4. Top Selling Products
        List<TopSellingProductProjection> topSellingProjections =
                orderItemRepository.findTopSellingProducts(PageRequest.of(0, 5));
        List<TopProductSummary> topProducts = topSellingProjections.stream()
                .map(p -> TopProductSummary.builder()
                        .productName(p.getProductName())
                        .productSlug(p.getProductSlug())
                        .image(p.getImage())
                        .soldQuantity(p.getSoldQuantity() != null ? p.getSoldQuantity() : 0L)
                        .totalRevenue(p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO)
                        .build())
                .toList();

        // 5. Inventory Summary
        long totalVariants = productVariantRepository.countByDeletedAtIsNull();
        long inStock = productVariantRepository.countByDeletedAtIsNullAndStockQuantityGreaterThan(5);
        long lowStock = productVariantRepository.countByDeletedAtIsNullAndStockQuantityGreaterThanAndStockQuantityLessThanEqual(0, 5);
        long outOfStock = productVariantRepository.countByDeletedAtIsNullAndStockQuantity(0);

        InventorySummary inventory = InventorySummary.builder()
                .totalVariants(totalVariants)
                .inStockCount(inStock)
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .build();

        // 6. Customer Reviews Summary
        Double avgRating = reviewRepository.getAverageRating();
        long totalReviews = reviewRepository.countAllReviews();
        List<Review> recentReviewList = reviewRepository.findRecentReviews(PageRequest.of(0, 5));
        List<RecentReviewSummary> recentReviews = recentReviewList.stream()
                .map(r -> RecentReviewSummary.builder()
                        .id(r.getId())
                        .customerName(r.getUser() != null ? r.getUser().getFullName() : "Customer")
                        .productName(r.getOrderItem() != null ? r.getOrderItem().getProductName() : "")
                        .rating(r.getRating() != null ? r.getRating().intValue() : 5)
                        .comment(r.getComment())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();

        CustomerReviewSummary customerReviews = CustomerReviewSummary.builder()
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .totalReviews(totalReviews)
                .recentReviews(recentReviews)
                .build();

        return EcommerceDashboardResponse.builder()
                .period(normalizedPeriod)
                .kpis(kpis)
                .revenueChart(revenueChart)
                .recentOrders(recentOrders)
                .topProducts(topProducts)
                .inventory(inventory)
                .customerReviews(customerReviews)
                .build();
    }

    @Override
    public FinanceDashboardResponse getFinanceDashboard(String period) {
        String normalizedPeriod = normalizePeriod(period);
        PeriodRange range = calculatePeriodRange(normalizedPeriod);

        // 1. KPIs
        BigDecimal netCollectedRevenue = orderRepository.sumPaidRevenueBetween(range.currentStart(), range.currentEnd());
        if (netCollectedRevenue == null) netCollectedRevenue = BigDecimal.ZERO;
        BigDecimal previousNetCollected = orderRepository.sumPaidRevenueBetween(range.previousStart(), range.previousEnd());
        if (previousNetCollected == null) previousNetCollected = BigDecimal.ZERO;
        Double netCollectedChange = calculatePercentageChange(previousNetCollected, netCollectedRevenue);

        BigDecimal pendingRevenue = orderRepository.sumPendingRevenueBetween(range.currentStart(), range.currentEnd());
        if (pendingRevenue == null) pendingRevenue = BigDecimal.ZERO;
        BigDecimal refundedRevenue = orderRepository.sumRefundedRevenueBetween(range.currentStart(), range.currentEnd());
        if (refundedRevenue == null) refundedRevenue = BigDecimal.ZERO;
        BigDecimal totalDiscounts = orderRepository.sumDiscountAmountBetween(range.currentStart(), range.currentEnd());
        if (totalDiscounts == null) totalDiscounts = BigDecimal.ZERO;

        long paidOrdersCount = orderRepository.countPaidOrdersBetween(range.currentStart(), range.currentEnd());
        long pendingOrdersCount = orderRepository.countPendingOrdersBetween(range.currentStart(), range.currentEnd());

        FinanceKpiSummary kpis = FinanceKpiSummary.builder()
                .netCollectedRevenue(netCollectedRevenue)
                .netCollectedChangePercentage(netCollectedChange)
                .pendingRevenue(pendingRevenue)
                .refundedRevenue(refundedRevenue)
                .totalDiscounts(totalDiscounts)
                .paidOrdersCount(paidOrdersCount)
                .pendingOrdersCount(pendingOrdersCount)
                .build();

        // 2. Fetch all orders in current period
        List<Order> periodOrders = orderRepository.findAllOrdersBetween(range.currentStart(), range.currentEnd());

        // 3. Daily cashflow chart
        List<CashflowChartPoint> cashflowChart = buildCashflowChart(range.startDate(), range.endDate(), periodOrders);

        // 4. Payment methods breakdown
        List<PaymentMethodSummary> paymentMethods = buildPaymentMethodSummaries(periodOrders);

        // 5. Payment statuses distribution
        List<PaymentStatusSummary> paymentStatuses = buildPaymentStatusSummaries(periodOrders);

        // 6. Recent payment transactions (10 most recent orders)
        List<Order> recentOrders = orderRepository.findRecentOrders(PageRequest.of(0, 10));
        List<RecentPaymentTransaction> recentTransactions = recentOrders.stream()
                .map(order -> RecentPaymentTransaction.builder()
                        .orderId(order.getId())
                        .orderCode(order.getOrderCode())
                        .customerName(order.getUser() != null ? order.getUser().getFullName() : order.getReceiverName())
                        .customerEmail(order.getUser() != null ? order.getUser().getEmail() : "")
                        .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod() : "COD")
                        .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus() : "UNPAID")
                        .finalAmount(order.getFinalAmount())
                        .createdAt(order.getCreatedAt())
                        .build())
                .toList();

        return FinanceDashboardResponse.builder()
                .period(normalizedPeriod)
                .kpis(kpis)
                .cashflowChart(cashflowChart)
                .paymentMethods(paymentMethods)
                .paymentStatuses(paymentStatuses)
                .recentTransactions(recentTransactions)
                .build();
    }

    @Override
    public CrmDashboardResponse getCrmDashboard(String period) {
        String normalizedPeriod = normalizePeriod(period);
        PeriodRange range = calculatePeriodRange(normalizedPeriod);

        // 1. KPIs
        long totalCustomers = userRepository.countByDeletedAtIsNull();
        long newCustomers = userRepository.countUsersCreatedBetween(range.currentStart(), range.currentEnd());
        long prevNewCustomers = userRepository.countUsersCreatedBetween(range.previousStart(), range.previousEnd());
        Double newCustomersChange = calculatePercentageChange(prevNewCustomers, newCustomers);

        long activeBuyers = orderRepository.countDistinctActiveBuyers();
        List<Long> repeatCustomerIds = orderRepository.findRepeatCustomerIds();
        long repeatCustomerCount = repeatCustomerIds != null ? repeatCustomerIds.size() : 0L;
        double repeatPurchaseRate = 0.0;
        if (activeBuyers > 0) {
            repeatPurchaseRate = ((double) repeatCustomerCount / activeBuyers) * 100.0;
            repeatPurchaseRate = Math.round(repeatPurchaseRate * 10.0) / 10.0;
        }

        BigDecimal periodRevenue = orderRepository.sumRevenueBetween(range.currentStart(), range.currentEnd());
        BigDecimal averageSpend = BigDecimal.ZERO;
        if (activeBuyers > 0 && periodRevenue != null && periodRevenue.compareTo(BigDecimal.ZERO) > 0) {
            averageSpend = periodRevenue.divide(BigDecimal.valueOf(activeBuyers), 0, RoundingMode.HALF_UP);
        }

        CrmKpiSummary kpis = CrmKpiSummary.builder()
                .totalCustomers(totalCustomers)
                .newCustomers(newCustomers)
                .newCustomersChangePercentage(newCustomersChange)
                .activeBuyers(activeBuyers)
                .repeatCustomerCount(repeatCustomerCount)
                .repeatPurchaseRate(repeatPurchaseRate)
                .averageCustomerSpend(averageSpend)
                .build();

        // 2. Customer growth chart
        List<User> newUsersInPeriod = userRepository.findUsersCreatedBetween(range.currentStart(), range.currentEnd());
        List<Order> ordersInPeriod = orderRepository.findRevenueOrdersBetween(range.currentStart(), range.currentEnd());
        List<CustomerGrowthChartPoint> growthChart = buildCustomerGrowthChart(
                range.startDate(), range.endDate(), newUsersInPeriod, ordersInPeriod);

        // 3. Membership tier breakdown
        List<TopSpendingCustomerProjection> allBuyerSpends = orderRepository.findTopSpendingCustomers(PageRequest.of(0, 5000));
        List<MembershipTierSummary> membershipTiers = buildMembershipTierSummaries(totalCustomers, allBuyerSpends);

        // 4. Top spending customers
        List<TopSpendingCustomerProjection> top10Buyers = orderRepository.findTopSpendingCustomers(PageRequest.of(0, 10));
        List<TopCustomerSummary> topCustomers = top10Buyers.stream()
                .map(p -> {
                    BigDecimal totalSpent = p.getTotalSpent() != null ? p.getTotalSpent() : BigDecimal.ZERO;
                    CustomerTier tier = CustomerTier.fromSpend(totalSpent);
                    return TopCustomerSummary.builder()
                            .userId(p.getUserId())
                            .fullName(p.getFullName() != null ? p.getFullName() : "Khách hàng #" + p.getUserId())
                            .email(p.getEmail() != null ? p.getEmail() : "")
                            .phone(p.getReceiverPhone() != null ? p.getReceiverPhone() : "")
                            .membershipTier(tier.name())
                            .tierLabel(tier.getLabel())
                            .totalOrders(p.getTotalOrders() != null ? p.getTotalOrders() : 0L)
                            .totalSpent(totalSpent)
                            .lastOrderDate(p.getLastOrderDate())
                            .build();
                })
                .toList();

        return CrmDashboardResponse.builder()
                .period(normalizedPeriod)
                .kpis(kpis)
                .customerGrowthChart(growthChart)
                .membershipTiers(membershipTiers)
                .topCustomers(topCustomers)
                .build();
    }

    private List<CustomerGrowthChartPoint> buildCustomerGrowthChart(
            LocalDate startDate, LocalDate endDate, List<User> users, List<Order> orders) {
        Map<String, Long> dailyNewUsers = new LinkedHashMap<>();
        Map<String, Long> dailyOrders = new LinkedHashMap<>();

        for (LocalDate date : buildDateSeries(startDate, endDate)) {
            String key = date.format(DATE_FORMATTER);
            dailyNewUsers.put(key, 0L);
            dailyOrders.put(key, 0L);
        }

        for (User u : users) {
            if (u.getCreatedAt() == null) continue;
            String key = u.getCreatedAt().atZone(ZONE_VN).toLocalDate().format(DATE_FORMATTER);
            if (dailyNewUsers.containsKey(key)) {
                dailyNewUsers.put(key, dailyNewUsers.get(key) + 1);
            }
        }

        for (Order o : orders) {
            if (o.getCreatedAt() == null) continue;
            String key = o.getCreatedAt().atZone(ZONE_VN).toLocalDate().format(DATE_FORMATTER);
            if (dailyOrders.containsKey(key)) {
                dailyOrders.put(key, dailyOrders.get(key) + 1);
            }
        }

        List<CustomerGrowthChartPoint> points = new ArrayList<>();
        dailyNewUsers.forEach((date, userCount) -> points.add(CustomerGrowthChartPoint.builder()
                .date(date)
                .newCustomersCount(userCount)
                .activeOrdersCount(dailyOrders.getOrDefault(date, 0L))
                .build()));
        return points;
    }

    private List<MembershipTierSummary> buildMembershipTierSummaries(
            long totalCustomers, List<TopSpendingCustomerProjection> allBuyers) {
        Map<CustomerTier, Long> tierCounts = new LinkedHashMap<>();
        for (CustomerTier tier : CustomerTier.values()) {
            tierCounts.put(tier, 0L);
        }

        long activeBuyerCount = 0;
        for (TopSpendingCustomerProjection buyer : allBuyers) {
            BigDecimal spent = buyer.getTotalSpent() != null ? buyer.getTotalSpent() : BigDecimal.ZERO;
            CustomerTier tier = CustomerTier.fromSpend(spent);
            tierCounts.put(tier, tierCounts.get(tier) + 1);
            activeBuyerCount++;
        }

        long nonBuyers = Math.max(0, totalCustomers - activeBuyerCount);
        tierCounts.put(CustomerTier.STANDARD, tierCounts.get(CustomerTier.STANDARD) + nonBuyers);

        long denom = Math.max(totalCustomers, activeBuyerCount);

        List<MembershipTierSummary> summaries = new ArrayList<>();
        for (CustomerTier tier : CustomerTier.values()) {
            long count = tierCounts.get(tier);
            double pct = denom > 0 ? (double) count / denom * 100.0 : 0.0;
            pct = Math.round(pct * 10.0) / 10.0;
            summaries.add(MembershipTierSummary.builder()
                    .tier(tier.name())
                    .label(tier.getLabel())
                    .count(count)
                    .percentage(pct)
                    .build());
        }
        return summaries;
    }

    private List<CashflowChartPoint> buildCashflowChart(LocalDate startDate, LocalDate endDate, List<Order> orders) {
        Map<String, BigDecimal> dailyCollected = new LinkedHashMap<>();
        Map<String, BigDecimal> dailyPending = new LinkedHashMap<>();
        Map<String, BigDecimal> dailyRefunded = new LinkedHashMap<>();

        for (LocalDate date : buildDateSeries(startDate, endDate)) {
            String dateKey = date.format(DATE_FORMATTER);
            dailyCollected.put(dateKey, BigDecimal.ZERO);
            dailyPending.put(dateKey, BigDecimal.ZERO);
            dailyRefunded.put(dateKey, BigDecimal.ZERO);
        }

        for (Order order : orders) {
            if (order.getCreatedAt() == null) continue;
            String dateKey = order.getCreatedAt().atZone(ZONE_VN).toLocalDate().format(DATE_FORMATTER);
            if (!dailyCollected.containsKey(dateKey)) continue;

            BigDecimal amount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;
            String paymentStatus = order.getPaymentStatus() != null ? order.getPaymentStatus() : "UNPAID";
            String status = order.getStatus() != null ? order.getStatus() : "";

            if ("PAID".equalsIgnoreCase(paymentStatus)) {
                dailyCollected.put(dateKey, dailyCollected.get(dateKey).add(amount));
            } else if ("REFUNDED".equalsIgnoreCase(paymentStatus) || "REFUNDED".equalsIgnoreCase(status)) {
                dailyRefunded.put(dateKey, dailyRefunded.get(dateKey).add(amount));
            } else if ("UNPAID".equalsIgnoreCase(paymentStatus) && !"CANCELLED".equalsIgnoreCase(status)) {
                dailyPending.put(dateKey, dailyPending.get(dateKey).add(amount));
            }
        }

        List<CashflowChartPoint> points = new ArrayList<>();
        dailyCollected.forEach((date, collected) -> points.add(CashflowChartPoint.builder()
                .date(date)
                .collectedAmount(collected)
                .pendingAmount(dailyPending.getOrDefault(date, BigDecimal.ZERO))
                .refundedAmount(dailyRefunded.getOrDefault(date, BigDecimal.ZERO))
                .build()));
        return points;
    }

    private List<PaymentMethodSummary> buildPaymentMethodSummaries(List<Order> orders) {
        Map<String, Long> methodCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> methodAmounts = new LinkedHashMap<>();
        BigDecimal totalPaidOrActiveRevenue = BigDecimal.ZERO;

        for (Order order : orders) {
            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) continue;
            String method = order.getPaymentMethod() != null ? order.getPaymentMethod().toUpperCase() : "COD";
            BigDecimal amount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;

            methodCounts.put(method, methodCounts.getOrDefault(method, 0L) + 1);
            methodAmounts.put(method, methodAmounts.getOrDefault(method, BigDecimal.ZERO).add(amount));
            totalPaidOrActiveRevenue = totalPaidOrActiveRevenue.add(amount);
        }

        List<PaymentMethodSummary> list = new ArrayList<>();
        final BigDecimal finalTotalRevenue = totalPaidOrActiveRevenue;

        methodAmounts.forEach((method, amount) -> {
            long count = methodCounts.getOrDefault(method, 0L);
            double pct = 0.0;
            if (finalTotalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                pct = amount.divide(finalTotalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                pct = Math.round(pct * 10.0) / 10.0;
            }
            list.add(PaymentMethodSummary.builder()
                    .method(method)
                    .count(count)
                    .totalAmount(amount)
                    .percentage(pct)
                    .build());
        });

        list.sort((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()));
        return list;
    }

    private List<PaymentStatusSummary> buildPaymentStatusSummaries(List<Order> orders) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> statusAmounts = new LinkedHashMap<>();
        long totalOrders = orders.size();

        for (Order order : orders) {
            String status = order.getPaymentStatus() != null ? order.getPaymentStatus().toUpperCase() : "UNPAID";
            BigDecimal amount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;

            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            statusAmounts.put(status, statusAmounts.getOrDefault(status, BigDecimal.ZERO).add(amount));
        }

        List<PaymentStatusSummary> list = new ArrayList<>();
        statusCounts.forEach((status, count) -> {
            BigDecimal amount = statusAmounts.getOrDefault(status, BigDecimal.ZERO);
            double pct = totalOrders > 0 ? (double) count / totalOrders * 100.0 : 0.0;
            pct = Math.round(pct * 10.0) / 10.0;

            list.add(PaymentStatusSummary.builder()
                    .status(status)
                    .count(count)
                    .totalAmount(amount)
                    .percentage(pct)
                    .build());
        });

        list.sort((a, b) -> b.getCount().compareTo(a.getCount()));
        return list;
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "this-month";
        }
        return switch (period) {
            case "last-month", "last-30-days", "year-to-date" -> period;
            default -> "this-month";
        };
    }

    private PeriodRange calculatePeriodRange(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZONE_VN);

        switch (period) {
            case "last-month" -> {
                ZonedDateTime lastMonthStart = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime thisMonthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime twoMonthsAgoStart = now.minusMonths(2).withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE_VN);
                return new PeriodRange(
                        lastMonthStart.toInstant(),
                        thisMonthStart.toInstant(),
                        twoMonthsAgoStart.toInstant(),
                        lastMonthStart.toInstant(),
                        lastMonthStart.toLocalDate(),
                        thisMonthStart.toLocalDate().minusDays(1));
            }
            case "last-30-days" -> {
                ZonedDateTime thirtyDaysAgo = now.minusDays(30).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime sixtyDaysAgo = now.minusDays(60).toLocalDate().atStartOfDay(ZONE_VN);
                return new PeriodRange(
                        thirtyDaysAgo.toInstant(),
                        now.toInstant(),
                        sixtyDaysAgo.toInstant(),
                        thirtyDaysAgo.toInstant(),
                        thirtyDaysAgo.toLocalDate(),
                        now.toLocalDate());
            }
            case "year-to-date" -> {
                ZonedDateTime yearStart = now.withDayOfYear(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime lastYearStart = now.minusYears(1).withDayOfYear(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime lastYearSamePoint = now.minusYears(1);
                return new PeriodRange(
                        yearStart.toInstant(),
                        now.toInstant(),
                        lastYearStart.toInstant(),
                        lastYearSamePoint.toInstant(),
                        yearStart.toLocalDate(),
                        now.toLocalDate());
            }
            default -> { // "this-month"
                ZonedDateTime thisMonthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime lastMonthStart = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE_VN);
                ZonedDateTime lastMonthSamePoint = now.minusMonths(1);
                return new PeriodRange(
                        thisMonthStart.toInstant(),
                        now.toInstant(),
                        lastMonthStart.toInstant(),
                        lastMonthSamePoint.toInstant(),
                        thisMonthStart.toLocalDate(),
                        now.toLocalDate());
            }
        }
    }

    private List<RevenueChartPoint> buildRevenueChart(PeriodRange range, List<Order> orders) {
        Map<String, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        Map<String, Long> dailyOrderCount = new LinkedHashMap<>();

        // Initialize daily timeline between start and end date
        LocalDate current = range.startDate();
        LocalDate end = range.endDate();
        while (!current.isAfter(end)) {
            String dateKey = current.format(DATE_FORMATTER);
            dailyRevenue.put(dateKey, BigDecimal.ZERO);
            dailyOrderCount.put(dateKey, 0L);
            current = current.plusDays(1);
        }

        // Fill data from orders
        for (Order order : orders) {
            if (order.getCreatedAt() == null) continue;
            String dateKey = order.getCreatedAt().atZone(ZONE_VN).toLocalDate().format(DATE_FORMATTER);
            if (dailyRevenue.containsKey(dateKey)) {
                dailyRevenue.put(dateKey, dailyRevenue.get(dateKey).add(order.getFinalAmount()));
                dailyOrderCount.put(dateKey, dailyOrderCount.get(dateKey) + 1);
            }
        }

        List<RevenueChartPoint> points = new ArrayList<>();
        dailyRevenue.forEach((date, revenue) -> points.add(RevenueChartPoint.builder()
                .date(date)
                .revenue(revenue)
                .orderCount(dailyOrderCount.getOrDefault(date, 0L))
                .build()));
        return points;
    }

    private List<LocalDate> buildDateSeries(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> series = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            series.add(current);
            current = current.plusDays(1);
        }
        return series;
    }

    private Double calculatePercentageChange(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return (curr != null && curr.compareTo(BigDecimal.ZERO) > 0) ? 100.0 : 0.0;
        }
        if (curr == null) {
            return -100.0;
        }
        BigDecimal diff = curr.subtract(prev);
        BigDecimal percentage = diff.divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return Math.round(percentage.doubleValue() * 10.0) / 10.0;
    }

    private Double calculatePercentageChange(long prev, long curr) {
        if (prev == 0) {
            return curr > 0 ? 100.0 : 0.0;
        }
        double percentage = ((double) (curr - prev) / prev) * 100.0;
        return Math.round(percentage * 10.0) / 10.0;
    }

    private record PeriodRange(
            Instant currentStart,
            Instant currentEnd,
            Instant previousStart,
            Instant previousEnd,
            LocalDate startDate,
            LocalDate endDate) {}
}
