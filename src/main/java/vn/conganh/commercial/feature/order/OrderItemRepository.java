package vn.conganh.commercial.feature.order;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.conganh.commercial.feature.order.dto.TopSellingProductProjection;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    @Query("""
            select oi.productName as productName,
                   oi.productSlug as productSlug,
                   oi.image as image,
                   sum(oi.quantity) as soldQuantity,
                   sum(oi.subtotal) as totalRevenue
            from OrderItem oi
            where oi.order.status not in ('CANCELLED')
            group by oi.productName, oi.productSlug, oi.image
            order by sum(oi.quantity) desc
            """)
    List<TopSellingProductProjection> findTopSellingProducts(Pageable pageable);
}
