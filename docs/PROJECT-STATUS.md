### BE-012: Refactor checkout orchestration

- **Date/Time**: 2026-08-07 (Asia/Saigon)
- **Summary of Changes**: Tách các tác vụ tính toán thuần túy từ `CheckoutServiceImpl` sang 2 collaborators độc lập: `CheckoutFingerprintService` (chịu trách nhiệm tính toán canonical pricing fingerprint & request hash SHA-256) và `CheckoutOrderItemAssembler` (chịu trách nhiệm dựng đối tượng snapshot `OrderItem`). `CheckoutServiceImpl` giữ nguyên toàn bộ giao dịch thanh toán (`@Transactional`), thứ tự khóa tài nguyên theo ID tăng dần (`PESSIMISTIC_WRITE`), trừ kho, trừ coupon, trừ quota campaign, cũng như các thông báo lỗi và localization.
- **Verification**: Extraction boundary verification (`rg -n`) xác nhận 2 collaborator mới là pure component (0 phụ thuộc HTTP/DB/Redis/Transactional). Focused test suite `CheckoutFingerprintServiceTest,CheckoutOrderItemAssemblerTest,CheckoutServiceImplTest,CheckoutControllerTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest` pass 100% (45/45). Full `mvnw.cmd clean verify` pass 100% (763/763). Tuân thủ 100% `git diff --check` và file allowlist (`git diff --name-only`).

### BE-011: Refactor authentication orchestration

- **Date/Time**: 2026-08-07 (Asia/Saigon)
- **Summary of Changes**: Tách lớp mã hóa/giải mã và kiểm tra tính hợp lệ của JWT token từ `AuthServiceImpl` sang component độc lập `AuthTokenCodec`. `AuthTokenCodec` đảm nhận việc encode access token (chỉ encode) và encode/decode/validate refresh token (`type=refresh`, `jti`, `securityVersion`), sử dụng `@Qualifier("refreshJwtDecoder")` mà không can thiệp hay duplicate `jwtDecoder` của Spring Security Resource Server. `AuthServiceImpl` giữ nguyên toàn bộ luồng nghiệp vụ xác thực, quản lý DB audit, và Redis session CAS rotation.
- **Verification**: Extraction boundary verification (`rg -n`) xác nhận `AuthTokenCodec` là pure component (0 phụ thuộc HTTP/DB/Redis). Focused test suite `AuthTokenCodecTest,AuthServiceImplTest,AuthControllerTest,AuthRefreshConcurrencyIntegrationTest,RefreshTokenSessionServiceIntegrationTest,RedisSecurityAndCleanupIntegrationTest` pass (55/55). Full `mvnw.cmd clean verify` pass 100% (757/757). Tuân thủ 100% `git diff --check` và allowlist scope (`git diff --name-only`).

### BE-010: Characterize large orchestration services

- **Date/Time**: 2026-08-07 (Asia/Saigon)
- **Summary of Changes**: Triển khai bộ characterization test củng cố cho 4 Orchestration Services lớn (Auth, Checkout, Sale Campaign, Product) để bảo vệ các bất biến về public outputs, stable error codes, lock ordering (ASC variant IDs), transaction boundaries, request pricing fingerprint, money/image snapshots, session revocation invariants, localization fallback (vi/en), và campaign phase transitions (`UPCOMING`, `LIVE`, `ENDED`). Không làm thay đổi bất kỳ mã nguồn production (`src/main/java`) hay public contract API nào.
- **Verification**: Focused characterization test gate `Dtest=AuthServiceImplTest,AuthControllerTest,CheckoutServiceImplTest,SaleCampaignServiceImplLocalizationTest,ProductServiceImplTest,ProductResponseTest` pass (75/75). Full verification `mvnw.cmd clean verify` pass 100% (756/756). Complete compliance with `git diff --check` and allowlist scope (`git diff --name-only`).

### BE-009: Add a wishlist product-summary contract

- **Date/Time**: 2026-07-25 (Asia/Saigon)
- **Summary of Changes**: Implement `GET /api/v1/wishlists/me` trả về wishlist của current user kèm product summary (`WishlistProductSummary`). Batch load translations, images và pricing để giải quyết vấn đề N+1 queries khi hiển thị danh sách wishlist. Chỉ trả về những sản phẩm `ACTIVE` và chưa bị xoá, `totalElements` của page được đồng bộ với danh sách products hiển thị. Áp dụng locale fallback theo thứ tự: query param `locale` -> header `Accept-Language` -> mặc định `vi`.
- **Verification**: Thêm integration test `WishlistProductSummaryIntegrationTest` chứng minh query count không đổi kể cả 1 hay nhiều items. Update `WishlistControllerTest` và `WishlistServiceImplTest` để bao phủ locale fallbacks, thiếu image/translation, và logic tie-break của giá (minimum effective price -> lowest variant ID). Full test suite passed (751/751). Contract documented trong `API_SPEC.md` và `feature/product/CONTEXT.md`.

### BE-008: Eliminate admin-list N+1 queries
- **Date/Time**: 2026-07-25 (Asia/Saigon)
- **Summary of Changes**: Tối ưu hoá list endpoints của Order, Payment, và Cart trong admin (những endpoint dùng chung `JpaSpecificationExecutor` và trả về `ResultPaginationDTO`) bằng cách thêm phương thức `findAll` override với `@EntityGraph`. Các methods này fetch eagerly các associations to-one (`Order.user`, `Payment.order`, `Cart.user`), loại bỏ vấn đề N+1 lazy loading queries khi mapping sang các DTOs.
- **Verification**: Viết thêm `AdminCommerceListFetchIntegrationTest` dùng Hibernate Statistics trên PostgreSQL Testcontainers để so sánh query count giữa single-row page và 10-row page. Số lượng query constant (chênh lệch 1) chứng minh N+1 đã được giải quyết. Focused tests và full `mvnw.cmd clean verify` pass.

### BE-007: Batch sale-campaign queries

- **Date/Time**: 2026-07-24 (Asia/Saigon)
- **Summary of Changes**: Tối ưu hoá page loading của sale-campaign bằng cách lấy ID trước và load detailed items sau (two-phase page loading), loại bỏ fetch join trên pageable query và duy trì pagination order. Tối ưu hoá overlap validation khi publish campaign bằng cách gộp tất cả variants thành 1 câu query duy nhất với `in clause` (`findOverlappingForVariants`). Áp dụng max 100 items per campaign trong API payload để bảo vệ memory.
- **Verification**: Focused tests `SaleCampaignControllerIntegrationTest,SaleCampaignConcurrencyIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignQueryPerformanceIntegrationTest` passed. `SaleCampaignQueryPerformanceIntegrationTest` sử dụng Hibernate Statistics để chứng minh số lượng query không đổi khi tạo/publish campaign có 1 hay 5 items. Full `mvnw.cmd clean verify` passed.

### BE-006: Batch checkout image loading

- **Date/Time**: 2026-07-24 (Asia/Saigon)
- **Summary of Changes**: Thay thế N+1 image query per line bằng một single bulk `findByProductIdIn(...)` call cho mỗi checkout. Build in-memory index theo variant ID và product ID, giữ nguyên deterministic selection logic (variant-first → product fallback, thumbnail → sortOrder → ID). Centralize image selection comparator thành `IMAGE_SELECTION_ORDER` static constant.
- **Verification**: Image characterization tests cover variant-specific, product fallback, thumbnail preference, sortOrder/ID tie-break, missing images, và duplicate product lines. `CheckoutImageQueryPerformanceIntegrationTest` dùng Hibernate statistics chứng minh image query count constant (1 vs 5 items). Focused suite pass `40/40` (16 unit + 24 integration); full `mvnw.cmd clean verify` pass `740/740` và build JAR thành công.

### BE-005: Stabilize API error contract

- **Date/Time**: 2026-07-22 (Asia/Saigon)
- **Summary of Changes**: Updated `GlobalExceptionHandler` and `SecurityConfig` to return stable `code` values for all framework and security exceptions (`REQUEST_BODY_INVALID`, `INVALID_REQUEST`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `INTERNAL_SERVER_ERROR`). Exceptions now use explicit handlers instead of falling through to generic 500s or leaking internal stack traces/messages to the client response.
- **Verification**: `GlobalExceptionHandlerTest` explicitly tests all new handled exceptions. `ApiErrorContractIntegrationTest` validates `INTERNAL_SERVER_ERROR` without details on generic failures. Full `mvnw.cmd clean verify` pass and JAR build successful. Contract changes documented in `API_SPEC.md` and `feature/auth/CONTEXT.md`.

### BE-004: Govern public upload lifecycle cho customer avatar

- **Date/Time**: 2026-07-18 (Asia/Saigon)
- **Summary of Changes**: Thêm `PUT /api/v1/files/avatar` self-scoped, chỉ nhận
  `multipart file`; backend lấy user từ JWT, lock row bằng `PESSIMISTIC_WRITE`,
  lưu avatar vào `/uploads/avatars/*`, cập nhật `users.avatar`, rollback thì xóa
  file mới và after-commit thì xóa previous managed avatar. Generic
  `POST /api/v1/files` giữ cho operator/admin flow nhưng V23 thu hồi `UPLOAD_FILE`
  khỏi `ROLE_USER` và seed permission `UPDATE_MY_AVATAR`.
- **Security/Correctness**: Avatar upload dùng Redis policy `avatar-upload`
  (`user 5/1h`, `IP 30/1h`, `global 300/1m`) và trả `AUTH_RATE_LIMITED`.
  File name dùng UUID, ghi `.tmp` rồi atomic move. Managed deletion chỉ áp dụng
  `/uploads/avatars/*`; external URL, product image và review image không bị xóa.
  Background reconciliation scan avatar namespace mỗi 6h, grace 24h, recheck DB
  trước khi delete orphan và emit metric `security.avatar.cleanup`.
- **Verification**: Focused Testcontainers suite pass `71/71`:
  `FileControllerTest,FileServiceImplTest,AvatarUploadIntegrationTest,
  AvatarReconciliationJobTest,SystemSecurityIntegrationTest`. Full
  `mvnw.cmd clean verify` pass `723/723` và build JAR thành công.

### BE-003: Serialize coupon counter updates

- **Date/Time**: 2026-07-16 (Asia/Saigon)
- **Summary of Changes**: Admin update/delete coupon dùng dedicated
  `PESSIMISTIC_WRITE` lookup trong transaction trước khi mutation toàn entity.
  Checkout và order lifecycle tiếp tục dùng guarded atomic SQL cho
  `consumeUsage`/`releaseUsage`; không đổi entity, schema hoặc lock order checkout.
- **Correctness**: PostgreSQL serialize admin whole-row mutation với coupon counter
  update trên cùng row, đóng lost-update window có thể làm `usedCount` bị ghi đè.
- **Verification**: Focused suite pass `12/12`; concurrency pair
  `CouponConcurrencyIntegrationTest,CheckoutConcurrencyTest` pass 3 vòng liên tiếp;
  full `mvnw.cmd clean verify` pass `712/712` và build JAR thành công.

### BE-002: Thu hồi legacy cross-account permissions của ROLE_USER

- **Date/Time**: 2026-07-16 (Asia/Saigon)
- **Summary of Changes**: Thêm Flyway V22 để xóa đúng 27 mapping
  `permission_role` của production role `USER` trên các generic
  cart/order/address/payment/review/wishlist routes. Migration match bằng
  `api_path + method` để chịu được permission name từng bị repeatable dev seed
  đổi tên; không xóa permission, controller hoặc mapping của role khác.
- **Security/Compatibility**: Customer tiếp tục dùng principal-bound `/me`, self
  cart/wishlist/coupon, checkout và verified review creation. Generic route trả
  `403` cho `ROLE_USER`; `ADMIN`, `MANAGER`, `STAFF` giữ đúng mapping riêng đã
  seed, không mặc định có quyền như nhau.
- **Rollout**: Chuỗi contract **BE-001 `c3b1361` → FE-001 `4c2767b` → BE-002**
  đã hoàn tất ở source. Frontend không còn customer caller phụ thuộc route bị
  thu hồi.
- **Verification**: Focused Testcontainers suite pass `56/56`; full
  `mvnw.cmd clean verify` pass `709/709` và build JAR thành công. Fresh database
  apply đủ 29 versioned/repeatable migrations tới V22; long-lived dev database
  upgrade từ V19 lên V22 thành công. Playwright full-stack pass `7/7`, gồm hai
  customer account không thể đọc chéo self-service resource; production
  `ADMIN`/`MANAGER`/`STAFF` matrix được xác nhận trong integration suite.

### BE-001: Customer self-service contract theo principal

- **Date/Time**: 2026-07-16 (Asia/Saigon)
- **Summary of Changes**: Thêm `PUT /api/v1/users/me`, bốn route
  `/api/v1/orders/me/**` và năm route `/api/v1/user-addresses/me/**`. Identity lấy
  từ JWT subject trong service; order/address lookup theo cả resource identifier và
  owner ID. Self-create address không nhận `userId`; self-profile DTO chỉ có
  `fullName`, `birthDate`, `gender` và không thể thay đổi `avatar`.
- **Security/Compatibility**: Foreign và missing order ID/code/history hoặc address
  ID cùng trả `404`. V21 seed đủ 10 method/path permissions cho `ADMIN`,
  `MANAGER`, `STAFF`, `USER`. Generic mappings từng được giữ tạm trong expand
  phase và đã bị V22 supersede đối với `USER`. Default address cũ được flush trước
  khi persist/promote default mới để giữ partial unique-index invariant.
- **Rollout**: Đây là expand phase lịch sử của chuỗi **BE-001 → FE-001 → BE-002**;
  contract phase BE-002 đã thu hồi generic `ROLE_USER` permissions sau khi FE-001
  chuyển customer caller sang `/me`.
- **Verification**: Focused Testcontainers suite pass `116/116`; Flyway áp dụng đủ
  28 versioned/repeatable migrations và schema đạt V21. `mvnw.cmd clean verify`
  pass `703/703`, không failure/error/skipped và đóng gói JAR thành công.
  `git diff --check`, exact allowlist, contract scan và ba vòng cold review đều đạt.

### Hardening OTP/Auth v1: proof một lần, limiter và revoke-all

- **Date/Time**: 2026-07-16 (Asia/Saigon)
- **Summary of Changes**: Thay verified-marker bằng contract `challengeId -> proofToken`
  dùng Redis Lua atomic; thêm limiter đa chiều Auth/OTP, client IP resolver không tin
  forwarded header, `users.security_version` để thu hồi mọi access/refresh session,
  correlation/security log và Micrometer metrics giới hạn cardinality. Google OAuth2
  không còn deserialize Java object từ cookie: browser chỉ giữ nonce, request JSON
  nằm trong Redis và callback consume một lần bằng `GETDEL`.
- **Security/Compatibility**: `CHANGE_EMAIL` bind proof với user đang đăng nhập;
  forgot-password email không tồn tại dùng decoy challenge. JWT/refresh session cũ
  thiếu `securityVersion` và OTP key v1 bị từ chối, tạo một lần forced re-login có
  chủ đích. Cookie OAuth2 kiểu Java serialization cũ cũng bị từ chối và flow đang
  dở phải bắt đầu lại. `SECURITY_HMAC_SECRET` tối thiểu 32 byte và tách khỏi JWT
  secret.
- **Scope**: Chỉ dùng Redis, Resend, Spring Boot Actuator/Micrometer hiện có. Chưa có
  Cloudflare/WAF, adaptive CAPTCHA, Prometheus/Grafana hoặc SaaS mới. Xem
  `docs/OTP_SECURITY_FLOW_VI.md` để biết contract, Lua invariant, proxy config và
  troubleshooting.
- **Verification Performed**: OAuth2 focused suite pass 14/14, gồm Redis thật,
  callback concurrency/replay, legacy cookie, JSON invalid/oversized, Redis
  fail-closed và nonce collision. Toàn bộ `mvnw.cmd test` pass 664/664; OpenSpec
  strict validation và `git diff --check` pass.

### Dev catalog 100 Product xác định, gallery đúng theo màu

- **Date/Time**: 2026-07-16 (Asia/Saigon)
- **Summary of Changes**: `R__3_dev_catalog_products.sql` quản lý đúng 100 design family; màu nằm ở Variant nên không làm tăng số Product. Toàn bộ ảnh cũ trong `uploads/products` được phân loại lại theo hình thực tế và ảnh còn thiếu được tạo thành bộ front/back/detail, bảo đảm mỗi product-color có tối thiểu 3 ảnh đúng thiết kế.
- **Compatibility**: Product/Variant seed dùng slug, natural key và SKU ổn định. Seed cũ ngoài manifest được `INACTIVE`/`DISCONTINUED` kèm soft-delete để endpoint Collection không trả lại; sáu SKU core vẫn được giữ cho Order/Sale fixture. Apparel, giày và phụ kiện dùng riêng hệ size phù hợp.
- **Verification Coverage**: `DevSeedDataIntegrationTest` kiểm tra đúng 100 Product visible/ACTIVE, tên và slug không trùng, đủ VI/EN, gallery theo màu, không dùng chéo ảnh, dùng hết asset vật lý và chạy lại repeatable seed không làm phình dữ liệu.

### Gợi ý nội dung English bằng Gemini

- **Date/Time**: 2026-07-15 (Asia/Saigon)
- **Summary of Changes**: Thêm ba endpoint không persistence để Admin tạo bản nháp English cho Product, Category và Sale Campaign từ field VI. Provider dùng Gemini `v1beta generateContent`, structured JSON, model whitelist và API key chỉ ở backend; response không chứa slug và Admin vẫn phải xem lại/lưu bằng API translation hiện có.
- **Security/Configuration**: Tính năng mặc định tắt; hỗ trợ `gemini-3.1-flash-lite`, `gemini-3.5-flash`, `gemini-3.1-pro-preview`. Flyway V19 cấp quyền tạo suggestion cho `ADMIN`/`MANAGER`; request không có token nhận 401, role thiếu quyền nhận 403.
- **Verification Performed**: Targeted service/provider/controller suite pass 15/15. Toàn bộ `mvnw.cmd -q test` pass 622/622, không failure/error/skipped. Integration test chạy PostgreSQL/Redis Testcontainers, apply đủ 25 migration gồm V19, kiểm tra ba endpoint typed và chứng minh số row Product/Category/Sale translation không đổi sau khi gọi suggestion. Gemini live test xác nhận `responseMimeType` + `responseJsonSchema` hoạt động; `responseFormat.text` trả `400 INVALID_ARGUMENT` nên không được dùng cho adapter này.

### Nội dung VI/EN và Sale seed hoàn chỉnh

- **Date/Time**: 2026-07-15
- **Summary of Changes**: Bật locale `en`; bổ sung quản trị bản dịch Product, Category và Sale Campaign; tạo repeatable seed bảo đảm toàn bộ 6 Product/14 Category có đủ VI/EN; thêm bốn campaign demo STANDARD/FLASH/DRAFT/ENDED; chuyển 7 order item có giá sale cũ sang snapshot campaign-backed đúng semantics.
- **Documentation**: Thêm `I18N_CATALOG_SALE_VI.md` và đồng bộ DATABASE, API_SPEC, FLYWAY, SALE_CAMPAIGN_BACKEND bằng tiếng Việt.
- **Verification Performed**: `DevSeedDataIntegrationTest` có 11 test kiểm tra coverage locale, localized slug, Sale fixture, historical order, mock order accounting, phục hồi core slug/default locale, optimistic version và chạy lại repeatable seed không sinh bản ghi trùng. Toàn bộ backend có 601 test pass trên PostgreSQL Testcontainers, gồm kiểm tra slug VI/EN hoạt động hai chiều khi đổi locale; Flyway chạy qua V18 và Hibernate schema validation thành công.

### Atomic refresh-token và bổ sung coverage race condition

- **Date/Time**: 2026-07-15 (Asia/Saigon)
- **Summary of Changes**: Thay rotation refresh-token kiểu xóa rồi tạo bằng Redis Lua compare-and-swap nguyên tử, vẫn giữ response unauthorized hiện có cho token cũ/replay. Bổ sung integration test cho hai request refresh đồng thời, hai checkout SePay cùng user/idempotency key và hai IPN giống hệt. Checkout/IPN test dùng barrier chỉ tồn tại trong test, đặt ngay trước repository pessimistic-lock query để hai worker cùng tới đúng cửa tranh chấp; executor luôn có timeout, cancel và `shutdownNow` để không treo CI. Thêm tài liệu tiếng Việt về ma trận invariant, cơ chế bảo vệ, cách chạy và giới hạn quan sát.
- **Verification Performed**: Targeted concurrency suite `AuthRefreshConcurrencyIntegrationTest,SaleCampaignConcurrencyIntegrationTest` pass 22/22 trên PostgreSQL và Redis Testcontainers thật. Sau khi rebase lên `main` có Flyway V18/i18n mới, chạy lại đúng môi trường `backend-ci.yml`: `mvnw.cmd clean verify` pass 607/607, 0 failure, 0 error, 0 skipped. `git diff --check` pass; `backend-ci.yml` không cần thay đổi vì Maven tự phát hiện các test mới.

### Sale Campaign end-to-end

- **Date/Time**: 2026-07-15
- **Summary of Changes**: Added scheduled `STANDARD` and quota-limited `FLASH` campaigns, admin/public APIs, campaign-backed pricing, checkout preview/idempotency, atomic Flash quota/customer limits, payment reservation lifecycle, timeout/late-IPN handling, RBAC, tests and Vietnamese technical documentation. Legacy `product_variants.sale_price` is removed by V15; prices now come from base price or a published campaign.
- **Verification Performed**: Flyway V1–V18 cùng repeatable development seeds và Hibernate schema validation đã pass trên PostgreSQL Testcontainers. V16–V17 bổ sung nội dung VI/EN và quyền quản trị; V18 thêm snapshot localized product slug cho order item.

### Fix Product Price Mismatch between Catalog and Detail Page

- **Date/Time**: 2026-07-08T19:49:00+07:00
- **Summary of Changes**: Historical implementation used the minimum legacy `salePrice`/base price. This behavior is superseded by the Sale Campaign pricing engine introduced on 2026-07-15.
- **Verification Performed**: mvnw clean compile (Backend) passed.
- **Known Follow-ups**: None.

# Project Status

## 2026-07-16 — Storefront Catalog UX và verified review

### Completed

- Thêm public `GET /api/v1/storefront/products` cho Collection/Search với `q`,
  multi-category/color/size, effective-price range, bốn storefront sort, page
  1-based và category/color/size/price facets.
- Giữ invariant color + size + effective price trên cùng ACTIVE variant; OR trong
  một facet, AND giữa các facet. Result, filter và sort dùng
  `VariantPricingService` cho Base/Standard/Flash, quota và customer limit.
- Thêm review summary, public filtered/sorted review page, principal-scoped
  `/reviews/me` và `POST /reviews` multipart gắn JWT.
- Tạo review chỉ cho owned OrderItem của Order `COMPLETED`; public DTO không lộ ID
  user/order/order item; duplicate race được đổi thành
  `409 REVIEW_ALREADY_EXISTS`.
- Ảnh review dùng UUID, validate extension/MIME/signature, ghi tạm + atomic move,
  rollback cleanup và scheduled orphan cleanup sau 24 giờ. Generic `/files` chặn
  upload trực tiếp vào folder `reviews`.
- Không thay đổi schema và không thêm Flyway migration.

### Documentation

- Thêm [STOREFRONT_CATALOG_UX_BACKEND_VI.md](./STOREFRONT_CATALOG_UX_BACKEND_VI.md)
  và đồng bộ `API_SPEC.md`, Product/Review context, filter strategy và README.
- OpenSpec change: `complete-storefront-catalog-ux`.

### Verification coverage

- Catalog coverage: CSV/list binding, anonymous access, page 1-based, same-variant,
  Base/Standard/Flash effective price, featured/newest/price sort, facet counts và
  seed integration.
- Review coverage: JWT principal, privacy DTO, summary/filter/sort,
  `COMPLETED`/foreign/duplicate race, UUID atomic storage, rollback, MIME/signature
  và orphan cleanup.
- Bàn giao ngày 2026-07-16: targeted catalog/review suite pass `50/50`;
  `mvnw.cmd clean verify` pass `648/648` và đóng gói JAR thành công; strict OpenSpec,
  `git diff --check`, quét link Markdown nội bộ và UTF-8 đều đạt.

## 2026-06-30

### Completed

- Implemented Redis-backed OTP generation, validation, and verification markers.
  Verified-marker contract này đã được Hardening OTP/Auth v1 ngày 2026-07-16 thay
  bằng challenge + single-use proof token; chỉ giữ mục này làm lịch sử.
- Integrated Resend email provider with custom, responsive HTML templates for OTP delivery.
- Enforced OTP verification for storefront user registration (`REGISTER` purpose).
- Implemented forgot-password reset flow (`FORGOT_PASSWORD` purpose) and authenticated email changes (`CHANGE_EMAIL` purpose), originally consumed via OTP verified markers and now superseded by scoped proof tokens.
- Added full suite of unit and integration tests covering OTP services, controllers, and protected auth endpoints.
- Updated `API_SPEC.md` documentation to reflect the new public and private OTP endpoints.
## 2026-07-06

### Completed

- **Database Seed Data Redesign**: Bộ sinh `RANDOM()` cũ đã được thay bằng manifest đúng 100 Product. `R__3_dev_catalog_products.sql` quản lý product/color/size/gallery; `R__3_dev_large_mock_data.sql` chỉ tạo dữ liệu feature dựa trên `SeedOwner`; `R__5` reconcile VI/EN và `R__6` tạo Sale fixture.
- **UTF-8 Encoding for Flyway**: Added `encoding: UTF-8` to `spring.flyway` in `application.yaml` to prevent Vietnamese character corruption on Windows systems during db migration/seeding.
- **Enhanced Product DTO Mappings**: Updated `ProductResponse` and `ProductServiceImpl` to batch-load and include `image`, `thumbnail`, `images` list, `categoryName`, and `categorySlug` in product detail/list API responses.
- Verified backend compilability via `./mvnw compile`.

## 2026-06-29

### Completed

- Separated the symmetric HMAC HS512 JWT configuration into two distinct keys: `JWT_ACCESS_TOKEN_SECRET_KEY` and `JWT_REFRESH_TOKEN_SECRET_KEY`.
- Created separate primary and qualified `JwtEncoder` and `JwtDecoder` Spring beans for both access and refresh tokens.
- Updated `AuthServiceImpl` to use specific encoders/decoders for generating/verifying tokens, and configured unit tests to run with separate secrets.
- Updated documentation (`docs/PROJECT-RULES.md` and `docs/API_SPEC.md`), environment variable templates (`.env.example` and `.env`), and integration test configurations.

## 2026-06-27

### Completed

- Added dynamic filtering for all existing paginated `GET` list APIs that return `ResultPaginationDTO`.
- Implemented per-feature filter request records and `PredicateSpecification` builders following `docs/decisions/filter-strategy.md`.
- Extended filtered repositories with `JpaSpecificationExecutor` and kept Spring-resolved `Pageable` for pagination/sorting.
- Enforced null/blank ignore behavior, case-insensitive string contains, exact ID/enum/boolean matching, inclusive ranges, invalid range `400`, soft-delete exclusion, and scoped path/query ID conflict `400`.
- Added practical PostgreSQL filter indexes in `V6__add_filter_indexes.sql`.
- Updated API and database docs with filter query params and index coverage.

## 2026-06-26

### Completed

- Fixed local Swagger auth refresh behavior by allowing the refresh-token cookie on HTTP localhost while still
  adding `Secure` for HTTPS requests, and configured Swagger UI requests to include cookies.
- Updated `/api/v1/auth/me` to include role summaries (`id`, `name`) in the user response.
- Added explicit RBAC join queries for admin user-role, user effective-permission, and role-permission detail views.
- Added focused service tests for auth `/me` roles, admin user RBAC detail, and role permission detail.
- Updated user list/detail responses to include role summaries consistently with `/api/v1/auth/me`.
- Added dev seed `example.com` accounts for MANAGER, STAFF, and USER role testing.
- Refactored Auth to implement `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, and `/api/v1/auth/logout`.
- Register now creates a user and assigns the default `USER` role.
- Login and refresh issue HS512 JWT access tokens with 15-minute expiry.
- Refresh tokens are HS512 JWTs stored with SHA-512 hashing, revoked on logout, and rotated on refresh with a 3-day expiry.
- Production JWT configuration now requires `JWT_SECRET_KEY` from environment variables, with token TTLs configurable through env.
- Added Auth context documentation and aligned API spec with implemented Auth endpoints.
- Implemented JWT-protected file upload via `POST /api/v1/files`.
- Added config-driven local upload storage with environment-backed `app.upload` properties.
- Added static `/uploads/**` resource serving and RBAC seed permission `UPLOAD_FILE`.
- Added upload validation for folder, extension, size, file name, and path traversal.
- Updated `docs/API_SPEC.md` with the file upload endpoint and added focused service tests.

## 2026-06-23

### Completed

- Updated `docs/PROJECT-RULES.md` with missing coding convention rules from the reference checklist:
  - Formatting, Java style, immutability, early returns, comment limits, and annotation guidance.
  - Lombok rules for `@RequiredArgsConstructor`, `@Slf4j`, `@Builder(setterPrefix = "with")`, and avoiding `@Data`.
  - Mapper guidance for static mappers and optional MapStruct usage.
  - Structured logging guidance and consistent error response notes.
- Converted remaining Vietnamese guidance in `docs/PROJECT-RULES.md` to English.
- Updated code examples in `docs/PROJECT-RULES.md` to prefer Lombok `@RequiredArgsConstructor` and `@Slf4j` where appropriate.
- Removed mandatory `final` usage rules for method parameters and local variables to keep the project style lighter.

## 2026-06-22

### Completed

- Implemented full CRUD features for all remaining database tables across 17 feature modules:
  - **Catalog**: Brand (soft delete), Color, Size, ProductVariant (soft delete), ProductImage, ProductAttribute
  - **Cart & Checkout**: Cart, CartItem, Order, OrderItem, OrderStatusHistory (append-only)
  - **Payment & Coupon**: Payment, PaymentTransaction, Coupon, CouponUsage
  - **User-related**: UserAddress, RefreshToken, Wishlist
  - **RBAC Joins**: UserRole, PermissionRole (composite key entities)
  - **Review & Inventory**: Review (append-only), ReviewImage, InventoryLog (append-only)
- Resolved duplicate entity conflicts (OrderItem, ProductVariant) caused by parallel agent overlap.
- Fixed cross-feature imports (Review → OrderItem, InventoryLog → ProductVariant).
- Verified compilation: `mvnw compile` passed with 235 source files.

### Previously Completed

- Aligned user CRUD with the current VelaWear `users` schema:
  - Switched user IDs to identity-backed `BIGINT`/`Long`.
  - Replaced stale username/phone/status fields with full name, email, password, birth date, avatar, gender, and soft-delete timestamps.
  - Added email normalization on create, active-only reads, and soft delete via `deleted_at`.
- Updated user DTOs, repository methods, service behavior, controller path IDs, feature context, and API examples.

## 2026-06-14

### Completed

- Added PostgreSQL configuration with Flyway migrations.
- Added commercial base schema:
  - RBAC: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`
  - Customer: `customers`, `addresses`
  - Catalog: `categories`, `products`, `product_images`, `product_variants`, `inventory_items`
  - Sales: `carts`, `cart_items`, `orders`, `order_items`, `payments`, `shipments`
- Seeded base RBAC roles and permissions.
- Rebuilt Java package structure according to `docs/PROJECT-RULES.md`.
- Added top-level `config`, `security`, `exception`, and shared `dto` packages.
- Re-enabled Lombok for boilerplate according to the updated project rules.
- Reworked `docs/API_SPEC.md` and `docs/DATABASE.md` into detailed commercial documentation while keeping the original documentation structure:
  - API docs include auth plan, implemented CRUD endpoints, request/response examples, errors, planned commercial endpoints, and endpoint summary.
  - Database docs include PostgreSQL engine notes, ERD, table-by-table columns, relationships, JPA mapping notes, seed data, migration notes, and future schema candidates.
- Added full Swagger/OpenAPI customization:
  - API metadata, contact, license, local/production servers.
  - JWT bearer security scheme for Swagger UI `Authorize`.
  - Swagger UI path and sorting/display settings.
- Split environment configuration into `dev`, `test`, and `prod` profiles:
  - `application.yaml` contains shared defaults and activates `dev` by default.
  - `application-dev.yml` uses local PostgreSQL defaults and verbose SQL logging.
  - `application-test.yml` is intended for Testcontainers-driven PostgreSQL tests and Flyway.
  - `application-prod.yml` requires env-provided PostgreSQL/JWT config and disables Swagger UI by default.
- Added CRUD features:
  - `feature/user`
  - `feature/role`
  - `feature/permission`
  - `feature/category`
  - `feature/product`

### Notes

- Controllers return `ResponseEntity<ApiResponse<T>>`.
- Services use interface + implementation pattern.
- DTOs are Java records with Jakarta validation.
- Entities and Spring components may use Lombok for boilerplate, but entities must not use `@Data`.
- H2 test dependency is not used; Testcontainers is the expected approach for integration tests.
- Test database target is PostgreSQL via Testcontainers, not H2.

### Verification

- `pom.xml` was validated as XML.
- `mvnw -DskipTests compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-25` after enabling Lombok annotation processing.
- `mvnw -DskipTests test-compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-25`.
- Full tests were not run because the required `test` profile database environment variables are not configured.
- PostgreSQL migration runtime validation was not completed because Docker Desktop daemon was not running.

## 2026-06-19

### Completed

- Redesigned `docs/DATABASE.md` for the current VelaWear e-commerce schema while preserving the existing documentation format:
  - PostgreSQL engine notes and naming conventions.
  - ERD, table-by-table definitions, constraints, indexes, notes, relationships, JPA mapping examples, sample data, migration notes, and design review notes.
  - RBAC join tables use composite primary keys with `ON DELETE CASCADE` on both foreign keys.
  - Soft-delete tables include indexed `deleted_at` guidance and cleanup-job notes.
- Replaced outdated HR/company schema references with VelaWear commercial tables.

### Notes

- PostgreSQL requires triggers or Hibernate timestamp handling for `updated_at`; there is no direct automatic column clause.
- Current schema design intentionally keeps order/payment/review/inventory history append-friendly and avoids hard deletes in normal workflows.

## 2026-06-19

### Completed

- Updated database documentation to require cleanup/anonymization job policies for every indexed `deleted_at` soft-delete column.
- Updated RBAC JPA guidance to avoid `@ManyToMany`; join tables should be modeled as explicit entities using `@ManyToOne`, `@OneToMany`, `@EmbeddedId`, and `@MapsId`.
- Updated project rules to forbid `@ManyToMany` mappings in JPA entities.

## 2026-06-19

### Completed

- Updated `product_variants` database design with `status`, indexed `deleted_at`, and cleanup retention notes.
- Updated `payment_transactions.transaction_code` to use a unique partial index when the transaction code is not null.
- Clarified product status meanings, especially `DRAFT`.
- Removed resolved items from `Design Review Notes`.

## 2026-06-19

### Completed

- Resolved remaining database design review notes:
  - Added `orders.updated_at`.
  - Clarified immutable order item snapshot fields.
  - Added transaction and row-locking guidance for coupon usage.
  - Kept `colors` and `sizes` as simple master data without audit timestamps.
  - Removed persistent `notifications` table from the current schema; important order/payment events should use email and short-lived UI messages can stay outside database state.

## 2026-06-19

### Completed

- Added project-local Codex skill definitions under `.agents/skills`:
  - `spring-crud` for VelaWear CRUD feature creation.
  - `testing` for unit and integration test workflows.
  - `review-pr` for code review checklists.
  - `flyway` for PostgreSQL/Flyway migration conventions.
- Split detailed skill checklists into `references/` files to keep each `SKILL.md` concise.
- Refined `review-pr` checklist around practical VelaWear architecture fit, security, naming consistency, performance risks, tests, docs, and project-specific rules without overloading reviews with unnecessary patterns.
- Added optional SOLID and Clean Architecture review lenses that should be applied only when changed code is complex enough to need them.
- Updated Flyway skill conventions to prefer identity-backed `BIGINT` primary keys, use `uidx_` for unique indexes, and reuse existing trigger functions/triggers.
- Simplified `docs/DATABASE.md` index strategy:
  - Removed low-value status/type/date indexes requested for cleanup.
  - Stopped listing indexes that PostgreSQL already creates for primary key and unique constraints.
  - Moved unique business rules into table constraints instead of the index list.

## 2026-06-20

### Completed

- Rebuilt Flyway base migrations to match the current `docs/DATABASE.md` VelaWear schema:
  - Replaced old UUID/customer/shipment schema with 28-table identity-backed `BIGINT` schema.
  - Added named primary key, foreign key, unique, and check constraints.
  - Added explicit indexes only for FK lookup, cleanup, and selected range/history queries.
  - Added partial unique indexes with `uidx_` for conditional uniqueness.
  - Added reusable `set_updated_at()` trigger function and table triggers.
- Replaced RBAC seed migration with roles, permissions, and `permission_role` mappings for the current schema.
- Added dev-only repeatable Flyway mock data under `db/dev`.
- Configured `application-dev.yml` to load `classpath:db/migration,classpath:db/dev` while shared/test/prod Flyway locations remain schema-only.
- Updated `docs/DATABASE.md` to document identity-backed `BIGINT` primary keys.

### Notes

- Existing local databases that already applied the old `V1/V2` migrations need to be dropped, cleaned, or repaired before rerunning Flyway.
- Dev mock accounts use raw password `Password123!` represented by a BCrypt hash in SQL.

## 2026-06-20

### Completed

- Updated `docs/DATABASE.md` ERD from a simplified diagram to a fuller box-style diagram that shows the current 28-table schema by domain area.
- Kept high-level `N:M` relationships for readability and listed physical join tables separately where appropriate.
- Corrected review relationships in the ERD: users, products, and orders each relate to reviews as `1:N`; order-product sales history is represented through `order_items`.
- Added nullable `reference_type` and `reference_id` to `inventory_logs` for source tracing, with a paired-null check and reference lookup index.
- Changed reviews to reference `order_items` through `order_item_id` instead of referencing only `orders`, so each review is tied to the exact purchased item.
- Changed business history foreign keys from cascade delete to restrict delete for `order_items -> orders`, `payment_transactions -> payments`, and `order_status_histories -> orders`.

## 2026-06-27

### Completed

- Refactored `/api/v1/auth/refresh` and `/api/v1/auth/logout` to run on cookie-based refresh tokens.
- Restored optional request body for `/refresh` to support manual token entry in Swagger UI, while keeping `/logout` parameterless.
- Custom-configured Swagger UI response interceptor to clear credentials from `localStorage` and programmatically log out of Swagger UI without page reload.
- Handled Spring Data `PropertyReferenceException` and database-level `InvalidDataAccessApiUsageException` in `GlobalExceptionHandler` to gracefully return `400 Bad Request` instead of `500 Internal Server Error` on invalid query sorts.
- Added Flyway migration `V5__seed_missing_permissions.sql` to seed all 23 missing REST endpoints permissions, correct invalid HTTP methods, and assign full permissions to the `ADMIN` role and logical sub-permissions to `MANAGER`, `STAFF`, and `USER` roles.

