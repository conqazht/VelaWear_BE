package vn.conganh.commercial.feature.category;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    boolean existsBySlug(String slug);

    Page<Category> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id and c.deletedAt is null")
    Optional<Category> findWithLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);

    Optional<Category> findBySlug(String slug);
}
