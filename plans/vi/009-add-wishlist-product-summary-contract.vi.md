# BE-009 — Thêm wishlist product-summary contract

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Trước khi code, lấy contract confirmation từ Wave-0 plan FE-007 đã review hoặc reviewer sign-off explicit; implementation FE-007 không phải prerequisite và chỉ bắt đầu sau khi BE-009 merge. Chỉ enrich self-service wishlist list, batch mọi dependency, và không gọi full product service một lần cho mỗi wishlist row. Sau khi dependency merge, nếu drift chạm in-scope path thì update live citation, scope/test name, `Planned against`, và mirror tiếng Việt trước khi code; không bao giờ execute stale plan.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P2 / M / 3
- Risk: High — đổi paginated customer API contract, visibility total, localization và pricing assembly
- Category: API contract / performance / localization
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `feature/wishlist-product-summary`
- Dependency: BE-002
- Consumed by: external FE-007 sau khi BE-009 merge
- Migration: không có

## Vì sao

`GET /wishlists/me` chỉ trả ID, nên frontend launch một product-detail request cho mỗi favorite. Self list nên gồm product-card summary nhỏ, localized, assemble theo batch, giảm latency và tránh N+1 API pattern mà không expose full admin/product-detail DTO.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistController.java:40-47`, `getMyWishlists(...)`, trả generic paginated wishlist response.
- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistServiceImpl.java:63-66`, `getMyWishlists(...)`, map `WishlistResponse` row.
- `src/main/java/vn/conganh/commercial/feature/wishlist/dto/WishlistResponse.java:6-18` chỉ có `id`, `userId`, `productId`, và `createdAt`.
- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistRepository.java:9-17` page theo user ID không có product summary fetch.
- `src/main/java/vn/conganh/commercial/feature/catalog/i18n/CatalogLocaleResolver.java:20-31`, `resolve(...)`, đã định nghĩa precedence explicit locale → `Accept-Language` → configured default/`vi` fallback.
- Product model đã có bulk-capable image/translation/pricing collaborator; `src/main/java/vn/conganh/commercial/feature/product/dto/ProductResponse.java` cố ý lớn hơn nhu cầu wishlist card.
- Frontend `../commercial-fe/components/shop/favorites-provider.tsx:77-93` hiện tạo một product query theo từng wishlist product ID.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/wishlist src/main/java/vn/conganh/commercial/feature/catalog/i18n/CatalogLocaleResolver.java src/main/java/vn/conganh/commercial/feature/product src/main/java/vn/conganh/commercial/feature/productvariant src/main/java/vn/conganh/commercial/feature/category src/main/java/vn/conganh/commercial/feature/salecampaign src/test docs` | Review mọi upstream contract/locale/catalog change; STOP và refresh plan nếu evidence hoặc test name drift. |
| Focused tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistServiceImplTest,WishlistControllerTest,WishlistProductSummaryIntegrationTest test` | Exit 0; locale precedence, active-product paging, summary contract và bounded query pass. |
| Full backend | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Diff hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện file trong allowlist bên dưới. |
| Scope verification | `git diff --name-only -- .` | Mọi path in ra đều thuộc allowlist; nếu không thì STOP và tách/revert phần ngoài phạm vi. |

## Contract đích

Với `GET /api/v1/wishlists/me`, mỗi visible row được trả về giữ chính xác các wishlist field backward-compatible `id`, `userId`, `productId`, `createdAt`, rồi embed required, non-null `product` summary cho đúng `productId` đó với chính xác: `id`, localized `slug`, localized `name`, localized `description`, `categoryId`, `originalSlug`, localized `shortDescription`, `status`, `image`, `thumbnail`, localized `categoryName`, `categorySlug`, base `price`, và effective `pricing`. Đây là transport shape cần cho `mapBackendProduct` hiện có của FE-007; cố ý loại SEO, material, care, full `images`, và `colorImages`.

Controller inject `CatalogLocaleResolver`, nhận optional `locale` và optional `Accept-Language`, rồi gọi `localeResolver.resolve(locale, acceptLanguage)`. Precedence là query `locale` → header → configured default → hard fallback `vi`; resolved code được truyền vào dedicated method `WishlistService.getMyWishlists(email, localeCode, pageable)`.

Với từng localized field của product/category, lookup theo requested translation → translation `CatalogLocaleResolver.DEFAULT_LOCALE` (`vi`) → base entity field; thiếu một field không làm null toàn summary. Representative pricing chọn variant có `VariantPricing.effectivePrice` nhỏ nhất, tie-break cùng giá bằng `variantId` tăng dần, expose `listPrice` của representative variant thành `price`, và expose effective/sale result thành `pricing`.

Self page chỉ gồm wishlist row có joined product đang `ACTIVE` và `deletedAt IS NULL`. Paginated database query tự áp dụng predicate này để `result`, `totalElements`, và `totalPages` cùng mô tả một visible set. Product join không tồn tại cũng bị loại bởi inner join. Không trả null/partial summary, không fail toàn page và không xóa stored wishlist row; row của inactive product có thể hiện lại khi product active. Generic admin `/wishlists` contract giữ nguyên.

## Phạm vi

### Trong phạm vi

- Dedicated self-list item/product-summary DTO, batch product/image/translation/pricing assembly, locale/fallback documentation, và constant-query integration test.

### Ngoài phạm vi

- Trả full `ProductResponse`, frontend implementation, schema migration, đổi wishlist business rule, storefront catalog redesign, hoặc per-row product service call.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistController.java`
- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistService.java`
- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/wishlist/WishlistRepository.java`
- `src/test/java/vn/conganh/commercial/feature/wishlist/WishlistControllerTest.java`
- `src/test/java/vn/conganh/commercial/feature/wishlist/WishlistServiceImplTest.java`
- `docs/API_SPEC.md`
- `docs/PROJECT-STATUS.md`
- `src/main/java/vn/conganh/commercial/feature/product/CONTEXT.md`

New files được phép tạo:

- `src/main/java/vn/conganh/commercial/feature/wishlist/dto/WishlistSelfItemResponse.java`
- `src/main/java/vn/conganh/commercial/feature/wishlist/dto/WishlistProductSummaryResponse.java`
- `src/test/java/vn/conganh/commercial/feature/wishlist/WishlistProductSummaryIntegrationTest.java`

## Git workflow

**Authorization gate:** Mọi Git/GitHub write (commit, push và tạo PR) chỉ được thực hiện khi operator hiện tại cho phép rõ ràng. Việc pass verification gate là điều kiện cần, không tự tạo quyền thực hiện các thao tác này.

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git switch main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$worktree = git status --porcelain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($worktree) { throw "Worktree is not clean" }
$head = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$originMain = git rev-parse origin/main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($head -ne $originMain) { throw "HEAD does not equal origin/main" }
git switch -c feature/wishlist-product-summary
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, thiếu BE-002, hoặc FE-007 contract chưa có Wave-0 plan approved hay reviewer sign-off explicit. Không cần FE-007 code tồn tại trước. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

## Các bước implementation

### 1. Freeze additive self-list DTO

Freeze field name, nullability, decimal/pricing shape, active-product-only behavior, và locale fallback theo Wave-0 plan FE-007 đã approved hoặc reviewer sign-off explicit. Tạo dedicated DTO thay vì reuse full product detail response; không chờ implementation FE-007.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistControllerTest test` → exit 0; exact self-list envelope và một fully populated summary được assert, còn generic admin response test không đổi.

### 2. Wire locale contract hiện có tại controller

Inject `CatalogLocaleResolver` vào `WishlistController`. Thêm optional `@RequestParam String locale` và optional `Accept-Language` cho `GET /me`, gọi `resolve(locale, acceptLanguage)`, rồi truyền kết quả vào service signature riêng `getMyWishlists(email, localeCode, pageable)`. Không resolve locale độc lập trong service.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistControllerTest test` → exit 0; test chứng minh explicit locale override header, header được dùng khi thiếu locale, và resolver default/fallback được dùng khi cả hai thiếu hoặc unsupported.

### 3. Page chỉ visible wishlist root và collect product ID

Giữ ownership-bound paging theo authenticated user. Thêm pageable query vào `WishlistRepository` dùng inner join product và filter `product.status = 'ACTIVE'` cùng `product.deletedAt IS NULL` trước count/content pagination. Không xóa hidden row. Extract distinct product ID từ page trả về theo stable wishlist order.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistProductSummaryIntegrationTest test` → exit 0; ACTIVE row xuất hiện, INACTIVE/soft-deleted/absent joined product không xuất hiện, stored row còn nguyên, và `result`, `totalElements`, `totalPages` nhất quán với mixed visibility fixture.

### 4. Batch summary dependency

Bulk-load image, requested-locale translation, translation `CatalogLocaleResolver.DEFAULT_LOCALE` (`vi`), base entity data, và pricing cho product/variant set. Resolve từng localized product/category field theo requested translation → `vi` translation → base entity field. Chọn representative variant theo `VariantPricing.effectivePrice` nhỏ nhất rồi `variantId` tăng dần; đặt `price` bằng `listPrice` của variant đó và `pricing` bằng effective/sale result. Build map và assemble summary không có repository/service call trong wishlist loop.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistServiceImplTest,WishlistProductSummaryIntegrationTest test` → exit 0; requested/`vi`/base field-level fallback, missing category translation, thumbnail precedence, no image, minimum-effective-price selection, equal-price `variantId` tie-break, `price = listPrice`, sale `pricing`, và duplicate product ID pass mà không có repository/service call trong row loop.

### 5. Thêm constant-query integration coverage

Mở rộng `WishlistServiceImplTest` và controller test; thêm `WishlistProductSummaryIntegrationTest` dùng PostgreSQL/query capture. So sánh một với nhiều wishlist entry.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistProductSummaryIntegrationTest,WishlistControllerTest test` → exit 0; query family bounded với một và nhiều visible row, generic admin contract không đổi.

### 6. Đồng bộ documentation

Update đúng backend document trong allowlist: `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, và product `CONTEXT.md`. Đặt handoff semantics cho FE-007 trong BE-009 PR description/comment thay vì tạo wishlist context file ngoài allowlist.

**Verify**: `rg -n "locale|Accept-Language|ACTIVE|totalElements|WishlistProductSummary" docs/API_SPEC.md docs/PROJECT-STATUS.md src/main/java/vn/conganh/commercial/feature/product/CONTEXT.md` → từng policy và DTO term xuất hiện trong canonical document phù hợp để FE-007 implement mà không đọc entity.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=WishlistServiceImplTest,WishlistControllerTest,WishlistProductSummaryIntegrationTest test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git status --short
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --name-only -- .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Smoke empty/full page, locale/fallback, active/inactive product, missing image, campaign pricing, duplicate product ID, và self-scope isolation.

## Tiêu chí hoàn thành

- `/wishlists/me` cung cấp agreed product-card summary trong một client request.
- Backend query family bounded khi page size tăng, không có per-row product-service call.
- Paging/order/ownership và generic admin contract còn compatible.
- Locale, image, status, pricing semantics được document/test; full verification pass.

## STOP conditions

- Cả Wave-0 plan FE-007 approved lẫn reviewer sign-off explicit đều chưa confirm required field, pricing shape, và locale/fallback semantics; implementation FE-007 không bắt buộc ở thời điểm này.
- Implementation gọi `ProductService.getProductById` hoặc repository trong wishlist row loop.
- Design expose full product detail/admin field, vô tình đổi generic admin contract, hoặc cần schema migration.
- Visibility filtering xảy ra sau pagination, trả null/partial summary, fail toàn list, hoặc xóa hidden wishlist row.

## Ghi chú maintenance

Giữ summary cố ý nhỏ và version additively khi card cần data mới. Maintain batch loader và query test theo DTO change. Sau khi dependency merge, nếu drift chạm in-scope path thì update live citation, scope/test name, `Planned against`, và mirror tiếng Việt trước khi code; không bao giờ execute stale plan. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
