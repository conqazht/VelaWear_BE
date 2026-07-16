# Decision Analysis: Dynamic Filter với Java Spring

> **Context:** Dynamic filter · < 100K records · Simple WHERE conditions · Java Spring

---

## 1. Các lựa chọn & So sánh

| Tiêu chí | JPA Specification | QueryDSL | RSQL Parser |
|---|---|---|---|
| **Độ phức tạp setup** | Thấp | Trung bình | Thấp |
| **Type-safe** | Có (verbose) | Có (fluent) | Không |
| **Dynamic filter** | ✅ Tốt | ✅ Rất tốt | ✅ Rất tốt |
| **Boilerplate** | Nhiều | Ít | Ít nhất |
| **Kiểm soát query** | Cao | Cao | Thấp |
| **Learning curve** | Thấp | Trung bình | Thấp |
| **Phù hợp < 100K** | ✅ | ✅ | ✅ |

---

## 2. Recommendation

### ✅ JPA Specification + Spring Data

Với use case hiện tại, đây là lựa chọn tối ưu vì:

- **Native** trong Spring Data — không cần thêm dependency nặng
- **Đủ mạnh** cho dynamic WHERE filter với mọi kiểu điều kiện
- **Dễ maintain** — team mới tiếp cận được ngay
- **Type-safe** — compile-time error thay vì runtime

> Chỉ nên chuyển sang **QueryDSL** nếu query phức tạp hơn (join nhiều bảng, subquery).  
> Chỉ nên dùng **RSQL** nếu muốn expose filter syntax ra cho client tự build (API gateway style).

---

## 3. Triển khai

### 3.1 Request DTO

```java
// Chỉ chứa filter fields — KHÔNG có page, size, sort
// Pageable được Spring tự resolve từ query params
public record ProductFilterRequest(
    String name,
    String status,
    BigDecimal priceFrom,
    BigDecimal priceTo,
    LocalDate createdFrom,
    LocalDate createdTo
) {}
```

### 3.2 Specification Builder

> ℹ️ **Spring Data JPA 4.0:** Dùng `PredicateSpecification<T>` với `List<Predicate>` + `cb.and()`
> bên trong một lambda duy nhất — gọn nhất cho dynamic filter đơn giản, không cần reuse từng predicate riêng lẻ.

```java
public class ProductSpecification {

    public static PredicateSpecification<Product> build(ProductFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.name() != null && !filter.name().isBlank()) {
                predicates.add(
                    cb.like(cb.lower(from.get("name")), "%" + filter.name().toLowerCase() + "%")
                );
            }

            if (filter.status() != null) {
                predicates.add(
                    cb.equal(from.get("status"), filter.status())
                );
            }

            if (filter.priceFrom() != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(from.get("price"), filter.priceFrom())
                );
            }

            if (filter.priceTo() != null) {
                predicates.add(
                    cb.lessThanOrEqualTo(from.get("price"), filter.priceTo())
                );
            }

            if (filter.createdFrom() != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(from.get("createdAt").as(LocalDate.class), filter.createdFrom())
                );
            }

            if (filter.createdTo() != null) {
                predicates.add(
                    cb.lessThanOrEqualTo(from.get("createdAt").as(LocalDate.class), filter.createdTo())
                );
            }

            // Nếu không có filter nào → cb.and() rỗng = match all (không có WHERE clause)
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

### 3.3 Repository

```java
// Chỉ cần extends thêm JpaSpecificationExecutor — không cần thêm gì khác
public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {}
```

### 3.4 Service

```java
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ResultPaginationDTO filter(ProductFilterRequest filter, Pageable pageable) {
        PredicateSpecification<Product> spec = ProductSpecification.build(filter);
        Page<ProductResponse> page = productRepository.findAll(spec, pageable)
            .map(ProductResponse::fromEntity);
        return ResultPaginationDTO.fromPage(page);
    }
}
```

### 3.5 Controller

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> filter(
        ProductFilterRequest filter,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        ResultPaginationDTO result = productService.filter(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
```

---

## 4. Ví dụ API Request

```
# Filter đơn giản
GET /api/v1/products?name=iphone&status=ACTIVE

# Range filter
GET /api/v1/product-variants?priceFrom=100&priceTo=500&createdFrom=2024-01-01

# Kết hợp + phân trang
GET /api/v1/products?name=samsung&status=ACTIVE&page=1&size=10&sort=createdAt,desc
```

---

## 5. Index cần thiết (PostgreSQL/MySQL)

```sql
-- Index các cột thường xuyên filter
CREATE INDEX idx_product_status ON products(status);
CREATE INDEX idx_product_created_at ON products(created_at);
CREATE INDEX idx_product_price ON products(price);

-- Full-text nếu cần sau này
CREATE INDEX idx_product_name_text ON products USING gin(to_tsvector('english', name));
```

> Với < 100K records thì query performance ổn ngay cả không có index, nhưng nên tạo sẵn để scale.

---

## 6. Khi nào cần nâng cấp?

| Tình huống | Giải pháp |
|---|---|
| Data > 1M records | Thêm Elasticsearch cho full-text |
| Join phức tạp (3+ bảng) | Chuyển sang QueryDSL |
| Client tự build filter | RSQL Parser |
| Auto-suggest / typeahead | Redis cache + prefix index |
| Report/analytics query | Native query hoặc jOOQ |

---

## 7. Ngoại lệ có chủ đích: Storefront Catalog

Ngày cập nhật: **2026-07-16**<br>
OpenSpec change: **`complete-storefront-catalog-ux`**

`GET /api/v1/storefront/products` không dùng `ProductSpecification` + entity
`Pageable` như list quản trị. Lý do là result, facets và sort đều phụ thuộc giá
hiệu lực của **cùng Product Variant** sau khi chạy `VariantPricingService`, gồm
Base, Standard Sale, Flash Sale, quota và giới hạn theo khách hàng.

Thiết kế hiện tại:

1. `StorefrontCatalogLoader` nạp snapshot chỉ gồm Product/Category/Variant
   `ACTIVE`, chưa soft-delete, rồi resolve pricing theo batch.
2. `StorefrontCatalogFilterEngine` áp dụng `q`, OR trong cùng facet và AND giữa
   category/color/size/price.
3. Color, size và effective price được kiểm tra trên cùng `Offer`; không ghép các
   variant khác nhau.
4. Khi tính count cho một facet, engine bỏ qua chính nhóm facet đó nhưng giữ các
   nhóm còn lại. Count là Product distinct.
5. Sort dùng whitelist `featured|newest|price-asc|price-desc`, không chuyển chuỗi
   storefront thành property của entity.
6. Sort toàn bộ candidate trước, sau đó cắt page 1-based; mặc định 12, tối đa 60.

Ví dụ đúng contract:

```http
GET /api/v1/storefront/products?categorySlugs=ao,ao-khoac&colorIds=1,2&sizeIds=3,4&minPrice=300000&maxPrice=1800000&sort=price-asc&page=1&size=12
```

Không gửi `sort=price`; Product không có property đại diện cho effective price.
Giá hiển thị/filter/sort phải cùng lấy từ `VariantPricingService`.

Snapshot in-memory phù hợp seed hiện tại khoảng 100 Product. Khi dữ liệu tăng,
phải đo P95 và heap trước; nếu cần đẩy query xuống PostgreSQL thì vẫn phải giữ test
same-variant, cross-facet count và effective-price sort.

Chi tiết đầy đủ:
[Storefront Catalog UX — Backend](../STOREFRONT_CATALOG_UX_BACKEND_VI.md).
