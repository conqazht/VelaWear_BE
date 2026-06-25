package vn.conganh.commercial.feature.color;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorRepository extends JpaRepository<Color, Long> {

    boolean existsByName(String name);
}
