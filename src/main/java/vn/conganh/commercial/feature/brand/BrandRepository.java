package vn.conganh.commercial.feature.brand;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long>, JpaSpecificationExecutor<Brand> {

    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Brand b where b.id = :id and b.deletedAt is null")
    Optional<Brand> findWithLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    Page<Brand> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySlug(String slug);
}
