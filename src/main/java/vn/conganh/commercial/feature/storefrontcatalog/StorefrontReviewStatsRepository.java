package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.conganh.commercial.feature.review.Review;

public interface StorefrontReviewStatsRepository extends Repository<Review, Long> {

    @Query("""
            select variant.product.id, avg(review.rating), count(review.id)
            from Review review, ProductVariant variant
            where review.orderItem.variantId = variant.id
              and variant.product.id in :productIds
            group by variant.product.id
            """)
    List<Object[]> findScoresByProductIds(@Param("productIds") Collection<Long> productIds);
}
