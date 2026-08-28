package vn.conganh.commercial.feature.checkout;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.order.OrderFulfillmentService;
import vn.conganh.commercial.feature.order.OrderRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReservationExpiryJob {

    private final OrderRepository orderRepository;
    private final OrderFulfillmentService lifecycleService;

    @Scheduled(fixedDelayString = "${app.checkout.expiry-scan-ms:30000}")
    public void expireReservations() {
        for (Long orderId : orderRepository.findExpiredReservationIds(Instant.now(), PageRequest.of(0, 100))) {
            try {
                lifecycleService.expireOrder(orderId);
            } catch (RuntimeException exception) {
                log.error("Cannot expire checkout reservation for order {}", orderId, exception);
            }
        }
    }
}
