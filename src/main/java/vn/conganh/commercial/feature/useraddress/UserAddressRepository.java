package vn.conganh.commercial.feature.useraddress;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    Page<UserAddress> findByUserId(Long userId, Pageable pageable);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    boolean existsByUserIdAndIsDefaultTrue(Long userId);
}
