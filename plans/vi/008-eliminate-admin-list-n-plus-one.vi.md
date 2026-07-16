# BE-008 — Loại bỏ N+1 query ở admin list

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Chỉ optimize to-one association cần cho pageable admin order/payment/cart response. Giữ dynamic filter và count query, giữ entity mapping LAZY, và verify bounded query count trên PostgreSQL.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P2 / L / 3
- Risk: Medium — pageable repository fetch change có thể ảnh hưởng count query, filter và root cardinality
- Category: Performance / persistence
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `perf/admin-list-fetching`
- Dependency: BE-002
- Migration: không có

## Vì sao

Admin list service page root entity rồi map lazy to-one association cho từng row. Điều này tạo N+1 query trên page thường dùng. Precise entity graph hoặc explicit projection có thể fetch đúng to-one data cần thiết mà không làm global mapping eager hoặc phá pageable count.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/order/OrderServiceImpl.java:37-41` map pageable order result; `Order.java:34-36` giữ `user` lazy; `dto/OrderResponse.java:34-40` đọc user data.
- `src/main/java/vn/conganh/commercial/feature/payment/PaymentServiceImpl.java:24-28` map payment page; `Payment.java:38-40` giữ `order` lazy; `dto/PaymentResponse.java:22-27` đọc nó.
- `src/main/java/vn/conganh/commercial/feature/cart/CartServiceImpl.java:48-52` map cart page; `Cart.java:32-34` giữ `user` lazy; `dto/CartResponse.java:17-24` đọc nó.
- Các repository này cũng hỗ trợ dynamic `Specification` cộng `Pageable`, nên fetch strategy phải giữ specification và count query hợp lệ.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/order src/main/java/vn/conganh/commercial/feature/payment src/main/java/vn/conganh/commercial/feature/cart src/test` | Review upstream repository/list change; STOP nếu evidence về response hoặc pagination đã drift. |
| Focused tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=OrderServiceImplTest,PaymentServiceImplTest,CartServiceImplTest,AdminCommerceListFetchIntegrationTest,DynamicFilterSpecificationIntegrationTest test` | Exit 0; response, filter, total và bounded query count pass. |
| Full backend | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Diff hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện file trong allowlist bên dưới. |
| Scope verification | `git diff --name-only -- .` | Mọi path in ra đều thuộc allowlist; nếu không thì STOP và tách/revert phần ngoài phạm vi. |

## Phạm vi

### Trong phạm vi

- Fetch `Order.user`, `Payment.order`, và `Cart.user` cho pageable admin list qua precise repository method/entity graph hoặc projection; thêm response-equivalence và query-count test.

### Ngoài phạm vi

- Global `EAGER` mapping, fetch child collection, response redesign, database migration, caching, hoặc optimize detail/storefront endpoint không liên quan.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/order/OrderRepository.java`
- `src/main/java/vn/conganh/commercial/feature/order/OrderServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/payment/PaymentRepository.java`
- `src/main/java/vn/conganh/commercial/feature/payment/PaymentServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/cart/CartRepository.java`
- `src/main/java/vn/conganh/commercial/feature/cart/CartServiceImpl.java`
- `src/test/java/vn/conganh/commercial/feature/order/OrderServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/payment/PaymentServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/cart/CartServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/filter/DynamicFilterSpecificationIntegrationTest.java`

New files được phép tạo:

- `src/test/java/vn/conganh/commercial/feature/commerce/AdminCommerceListFetchIntegrationTest.java`

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
git switch -c perf/admin-list-fetching
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc thiếu BE-002. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Characterize list contract

Freeze filter, sort, pagination metadata, null-association behavior, và response field cho order, payment, cart admin list. Bao gồm multi-row fixture trigger lazy load hiện tại.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=OrderServiceImplTest,PaymentServiceImplTest,CartServiceImplTest test` → exit 0; response fixture hiện có giữ field-for-field equivalence.

### 2. Introduce precise fetch plan

Ưu tiên repository method compatible với `JpaSpecificationExecutor` và `Pageable`, annotate bằng `@EntityGraph` chỉ chứa required to-one association. Nếu provider ignore/override graph khi chạy specification, dùng explicit two-phase ID query hoặc projection.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AdminCommerceListFetchIntegrationTest test` → exit 0; required to-one association được initialize, total hợp lệ, root unique và không collection nào bị fetch.

### 3. Route list service qua optimized method

Chỉ update admin list service path. Giữ entity association annotation LAZY và existing mapper, error behavior, transactional read semantics.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=OrderServiceImplTest,PaymentServiceImplTest,CartServiceImplTest test` → exit 0; cả ba list method dùng optimized repository path và non-list flow không đổi.

### 4. Thêm bounded-query regression test

Tạo `AdminCommerceListFetchIntegrationTest` và mở rộng `DynamicFilterSpecificationIntegrationTest`. So sánh page nhỏ và lớn hơn bằng Hibernate statistics/query capture cho từng resource.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AdminCommerceListFetchIntegrationTest,DynamicFilterSpecificationIntegrationTest test` → exit 0; association-query count bounded với một và nhiều row, mọi filter/sort/count case pass trên PostgreSQL.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AdminCommerceListFetchIntegrationTest,DynamicFilterSpecificationIntegrationTest,OrderServiceImplTest,PaymentServiceImplTest,CartServiceImplTest test
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

Smoke empty page, nhiều row, null relation nếu được phép, từng supported filter/sort, page boundary, và concurrent read/write visibility.

## Tiêu chí hoàn thành

- Order, payment, cart admin page query family bounded thay vì per-row.
- Dynamic specification, sorting, total, response schema, và null semantics không đổi.
- Không association nào thành globally EAGER và không pageable collection fetch join.
- Focused query-count và full verification gate pass.

## STOP conditions

- Entity graph bị ignore/override cho specification paging và không có alternate repository shape đã verify.
- Fix đề xuất fetch collection, duplicate root, phá count query, hoặc đổi global fetch type.
- Query-count proof chỉ dùng mock/H2 hoặc phụ thuộc brittle absolute total SQL count.

## Ghi chú maintenance

Giữ admin list fetch plan gần repository method và thêm response field vào graph/projection có chủ đích. Chạy lại query test sau mapper change. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
