package vn.conganh.commercial.feature.wishlist;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WishlistRepository extends JpaRepository<Wishlist, Long>, JpaSpecificationExecutor<Wishlist> {

    Page<Wishlist> findByUserId(Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT w FROM Wishlist w JOIN w.product p WHERE w.user.id = :userId AND p.status = 'ACTIVE' AND p.deletedAt IS NULL")
    Page<Wishlist> findActiveWishlistsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    Page<Wishlist> findByProductId(Long productId, Pageable pageable);

    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
