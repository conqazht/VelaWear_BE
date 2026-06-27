package vn.conganh.commercial.feature.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    Page<Review> findByUserId(Long userId, Pageable pageable);

    Page<Review> findByOrderItemId(Long orderItemId, Pageable pageable);

    Page<Review> findByOrderItemOrderId(Long orderId, Pageable pageable);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);
}
