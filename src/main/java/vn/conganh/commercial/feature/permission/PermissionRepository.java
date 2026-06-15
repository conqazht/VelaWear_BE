package vn.conganh.commercial.feature.permission;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    boolean existsByCode(String code);
}
