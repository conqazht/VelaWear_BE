package vn.conganh.commercial.feature.size;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeRepository extends JpaRepository<Size, Long> {

    boolean existsByName(String name);
}
