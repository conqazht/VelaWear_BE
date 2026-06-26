package vn.conganh.commercial.feature.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByUserId(Long userId, Pageable pageable);

    Page<Review> findByOrderItemId(Long orderItemId, Pageable pageable);

    Page<Review> findByOrderItemOrderId(Long orderId, Pageable pageable);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);
}
