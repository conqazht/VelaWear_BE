package vn.conganh.commercial.feature.color;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ColorRepository extends JpaRepository<Color, Long>, JpaSpecificationExecutor<Color> {

    boolean existsByName(String name);
}
