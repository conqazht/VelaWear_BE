package vn.conganh.commercial.feature.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByUserId(Long userId);

    List<Review> findByOrderItemId(Long orderItemId);

    List<Review> findByOrderItemOrderId(Long orderId);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);
}
