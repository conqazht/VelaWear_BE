package vn.conganh.commercial.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.brand.Brand;
import vn.conganh.commercial.feature.brand.BrandRepository;
import vn.conganh.commercial.feature.brand.BrandSpecification;
import vn.conganh.commercial.feature.brand.dto.BrandFilterRequest;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderSpecification;
import vn.conganh.commercial.feature.order.dto.OrderFilterRequest;
import vn.conganh.commercial.feature.permission.Permission;
import vn.conganh.commercial.feature.permission.PermissionRepository;
import vn.conganh.commercial.feature.permission.PermissionSpecification;
import vn.conganh.commercial.feature.permission.dto.PermissionFilterRequest;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.role.RoleSpecification;
import vn.conganh.commercial.feature.role.dto.RoleFilterRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.user.UserSpecification;
import vn.conganh.commercial.feature.user.dto.UserFilterRequest;
import vn.conganh.commercial.util.constant.UserGender;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Dynamic filter specifications")
class DynamicFilterSpecificationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("catalog filters - no filter excludes soft-delete, string contains ignores case, pagination sort works")
    void catalogFilters_coverSoftDeleteContainsAndPageableSort() {
        String key = uniqueKey();
        Brand alpha = brand("DF " + key + " Alpha", "df-" + key + "-alpha", "ACTIVE", null);
        Brand zebra = brand("DF " + key + " Zebra", "df-" + key + "-zebra", "ACTIVE", null);
        Brand deleted = brand("DF " + key + " Deleted", "df-" + key + "-deleted", "ACTIVE", Instant.now());
        brandRepository.saveAllAndFlush(List.of(alpha, zebra, deleted));

        var noFilterBrands = brandRepository.findAll(Specification.where(BrandSpecification.build(null)));

        assertThat(noFilterBrands)
                .extracting(Brand::getSlug)
                .contains(alpha.getSlug(), zebra.getSlug())
                .doesNotContain(deleted.getSlug());

        var filter = new BrandFilterRequest(
                " " + key.toUpperCase() + " ",
                null,
                "ACTIVE",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        var filteredPage = brandRepository.findAll(
                Specification.where(BrandSpecification.build(filter)),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(filteredPage.getTotalElements()).isEqualTo(2);
        assertThat(filteredPage.getContent()).extracting(Brand::getName).containsExactly(zebra.getName());
    }

    @Test
    @DisplayName("user and RBAC filters - exact enum plus role/permission fields")
    void userAndRbacFilters_coverEnumAndExactFields() {
        String key = uniqueKey();
        User alice = user("DF " + key + " Alice", "df-" + key + "-alice@example.com", UserGender.FEMALE,
                LocalDate.of(2001, 1, 2), null);
        User bob = user("DF " + key + " Bob", "df-" + key + "-bob@example.com", UserGender.MALE,
                LocalDate.of(1990, 1, 2), null);
        User deleted = user("DF " + key + " Deleted", "df-" + key + "-deleted@example.com", UserGender.FEMALE,
                LocalDate.of(2001, 1, 2), Instant.now());
        userRepository.saveAllAndFlush(List.of(alice, bob, deleted));

        var userFilter = new UserFilterRequest(
                key.toLowerCase() + " ali",
                null,
                UserGender.FEMALE,
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2002, 12, 31),
                null,
                null,
                null,
                null);

        assertThat(userRepository.findAll(Specification.where(UserSpecification.build(userFilter))))
                .extracting(User::getEmail)
                .containsExactly(alice.getEmail());

        Role role = role("DF_ROLE_" + key, "Warehouse operations");
        roleRepository.saveAndFlush(role);

        var roleFilter = new RoleFilterRequest("df_role_" + key.toLowerCase(), "warehouse", null, null, null, null);
        assertThat(roleRepository.findAll(Specification.where(RoleSpecification.build(roleFilter))))
                .extracting(Role::getName)
                .containsExactly(role.getName());

        Permission permission = permission(
                "DF_PERMISSION_" + key,
                "/api/v1/df/" + key,
                "GET",
                "DF_FILTER");
        permissionRepository.saveAndFlush(permission);

        var permissionFilter = new PermissionFilterRequest("permission", "/df/" + key, "GET", "DF_FILTER",
                null, null, null, null);

        assertThat(permissionRepository.findAll(Specification.where(PermissionSpecification.build(permissionFilter))))
                .extracting(Permission::getApiPath)
                .containsExactly(permission.getApiPath());
    }

    @Test
    @DisplayName("commerce filters - ID/exact/range filters are inclusive and invalid ranges fail")
    void commerceFilters_coverInclusiveRangeAndInvalidRange() {
        String key = uniqueKey();
        User user = user("DF " + key + " Buyer", "df-" + key + "-buyer@example.com", UserGender.OTHER,
                LocalDate.of(1995, 5, 5), null);
        userRepository.saveAndFlush(user);

        Order match = order(user, "DF-" + key + "-001", "CONFIRMED", "COD", "PAID", new BigDecimal("150.00"));
        Order outsideRange = order(user, "DF-" + key + "-002", "CONFIRMED", "COD", "PAID", new BigDecimal("300.00"));
        orderRepository.saveAllAndFlush(List.of(match, outsideRange));

        var filter = new OrderFilterRequest(
                user.getId(),
                null,
                "CONFIRMED",
                "COD",
                "PAID",
                "buyer",
                null,
                new BigDecimal("150.00"),
                new BigDecimal("150.00"),
                null,
                null,
                null,
                null);

        assertThat(orderRepository.findAll(Specification.where(OrderSpecification.build(filter))))
                .extracting(Order::getOrderCode)
                .containsExactly(match.getOrderCode());

        var invalidRange = new OrderFilterRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> orderRepository.findAll(Specification.where(OrderSpecification.build(invalidRange))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("finalAmount");
    }

    private Brand brand(String name, String slug, String status, Instant deletedAt) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setDescription(name);
        brand.setStatus(status);
        brand.setDeletedAt(deletedAt);
        return brand;
    }

    private User user(String fullName, String email, UserGender gender, LocalDate birthDate, Instant deletedAt) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(birthDate);
        user.setGender(gender);
        user.setDeletedAt(deletedAt);
        return user;
    }

    private Role role(String name, String description) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        return role;
    }

    private Permission permission(String name, String apiPath, String method, String module) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setApiPath(apiPath);
        permission.setMethod(method);
        permission.setModule(module);
        return permission;
    }

    private Order order(
            User user,
            String orderCode,
            String status,
            String paymentMethod,
            String paymentStatus,
            BigDecimal finalAmount) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(orderCode);
        order.setStatus(status);
        order.setSubtotal(finalAmount);
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(finalAmount);
        order.setReceiverName("Dynamic Filter Buyer");
        order.setReceiverPhone("0900000000");
        order.setReceiverAddress("1 Filter Street");
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private String uniqueKey() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
