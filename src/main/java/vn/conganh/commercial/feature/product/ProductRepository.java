package vn.conganh.commercial.feature.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySlug(String slug);

    @Query(value = "SELECT name FROM products WHERE id = :id", nativeQuery = true)
    String findNameById(@Param("id") Long id);
}
