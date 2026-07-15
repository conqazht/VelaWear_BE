package vn.conganh.commercial.feature.salecampaign;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleCustomerUsageRepository extends JpaRepository<SaleCustomerUsage, Long> {

    Optional<SaleCustomerUsage> findByCampaignItemIdAndUserId(Long campaignItemId, Long userId);

    List<SaleCustomerUsage> findByCampaignItemIdInAndUserId(List<Long> itemIds, Long userId);

    @Modifying
    @Query(value = """
            insert into sale_customer_usages
                (campaign_item_id, user_id, reserved_quantity, purchased_quantity)
            values (:itemId, :userId, 0, 0)
            on conflict (campaign_item_id, user_id) do nothing
            """, nativeQuery = true)
    int createCounterIfAbsent(@Param("itemId") Long itemId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCustomerUsage u
               set u.reservedQuantity = u.reservedQuantity + :quantity
             where u.campaignItem.id = :itemId
               and u.user.id = :userId
               and u.reservedQuantity + u.purchasedQuantity + :quantity <= :maxPerCustomer
            """)
    int reserveWithinLimit(
            @Param("itemId") Long itemId,
            @Param("userId") Long userId,
            @Param("quantity") int quantity,
            @Param("maxPerCustomer") int maxPerCustomer);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCustomerUsage u
               set u.reservedQuantity = u.reservedQuantity - :quantity,
                   u.purchasedQuantity = u.purchasedQuantity + :quantity
             where u.campaignItem.id = :itemId and u.user.id = :userId
               and u.reservedQuantity >= :quantity
            """)
    int confirm(@Param("itemId") Long itemId, @Param("userId") Long userId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCustomerUsage u
               set u.reservedQuantity = u.reservedQuantity - :quantity
             where u.campaignItem.id = :itemId and u.user.id = :userId
               and u.reservedQuantity >= :quantity
            """)
    int release(@Param("itemId") Long itemId, @Param("userId") Long userId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCustomerUsage u
               set u.purchasedQuantity = u.purchasedQuantity - :quantity
             where u.campaignItem.id = :itemId and u.user.id = :userId
               and u.purchasedQuantity >= :quantity
            """)
    int reverse(@Param("itemId") Long itemId, @Param("userId") Long userId, @Param("quantity") int quantity);
}
