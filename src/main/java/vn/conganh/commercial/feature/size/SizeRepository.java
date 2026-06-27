package vn.conganh.commercial.feature.size;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SizeRepository extends JpaRepository<Size, Long>, JpaSpecificationExecutor<Size> {

    boolean existsByName(String name);
}
