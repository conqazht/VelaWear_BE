package vn.conganh.commercial.feature.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsBySlug(String slug);
}
