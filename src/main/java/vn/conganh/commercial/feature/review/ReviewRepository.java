package vn.conganh.commercial.feature.review;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    Page<Review> findByUserId(Long userId, Pageable pageable);

    Page<Review> findByOrderItemId(Long orderItemId, Pageable pageable);

    Page<Review> findByOrderItemOrderId(Long orderId, Pageable pageable);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);

    @Query("""
            select review.rating as rating, count(review) as total
            from Review review
            where review.orderItem.variantId in (
                select variant.id
                from ProductVariant variant
                where variant.product.id = :productId
            )
            group by review.rating
            order by review.rating
            """)
    List<ReviewRatingCount> summarizeByProductId(@Param("productId") Long productId);

    @Query("select coalesce(avg(cast(r.rating as double)), 0.0) from Review r")
    Double getAverageRating();

    @Query("select count(r) from Review r")
    long countAllReviews();

    @Query("select r from Review r join fetch r.user join fetch r.orderItem order by r.createdAt desc")
    List<Review> findRecentReviews(Pageable pageable);
}
