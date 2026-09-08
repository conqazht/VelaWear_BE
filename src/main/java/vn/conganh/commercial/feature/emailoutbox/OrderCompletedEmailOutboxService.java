package vn.conganh.commercial.feature.emailoutbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.order.Order;

@Service
@RequiredArgsConstructor
public class OrderCompletedEmailOutboxService {

    private final CommerceEmailOutboxService commerceEmailOutboxService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Order order) {
        commerceEmailOutboxService.enqueueOrderCompleted(order);
    }
}

