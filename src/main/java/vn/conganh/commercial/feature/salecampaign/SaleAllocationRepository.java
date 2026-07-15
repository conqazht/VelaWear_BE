package vn.conganh.commercial.feature.salecampaign;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleAllocationRepository extends JpaRepository<SaleAllocation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaignItem", "orderItem", "user"})
    @Query("select a from SaleAllocation a where a.orderItem.order.id = :orderId order by a.id asc")
    List<SaleAllocation> findWithLockByOrderId(@Param("orderId") Long orderId);
}
