# BE-006 — Batch checkout image loading

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Chỉ thực hiện image-fetching optimization mô tả tại đây, giữ checkout transaction/lock/fingerprint semantics, và chứng minh cả query count lẫn response equivalence. Không trộn orchestration refactor sau này vào PR này.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P2 / M / 3
- Risk: Medium — performance change trong checkout path nhạy cảm với transaction và lock
- Category: Performance / data access
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `perf/checkout-image-batching`
- Dependency: BE-003
- Migration: không có

## Vì sao

Checkout assemble từng order line bằng query product image riêng. Work tăng theo cart size bên trong transaction nhạy cảm. Repository đã hỗ trợ bulk product-ID query, nên có thể prefetch image một lần rồi select từ in-memory index mà không đổi order snapshot.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java:148-240`, `checkout(...)`, sở hữu transaction và lock ordering.
- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java:395-428` loop checkout item; `617-620` gọi variant/product image query theo từng line.
- `src/main/java/vn/conganh/commercial/feature/product/ProductImageRepository.java:9-16`, `findByProductIdIn(...)`, đã bulk-load image kèm variant data.
- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java:622-632` encode selection invariant: variant image trước product fallback, rồi thumbnail, `sortOrder`, ID.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/checkout src/main/java/vn/conganh/commercial/feature/product/ProductImageRepository.java src/test/java/vn/conganh/commercial/feature/checkout` | Review mọi upstream change trong các path; STOP nếu evidence/invariant không còn đúng. |
| Focused tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest,CheckoutImageQueryPerformanceIntegrationTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test` | Exit 0; selection, bounded query và concurrency đúng. |
| Full backend | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Diff hygiene | `git diff --check` | Exit 0, không có whitespace error. |
| Worktree | `git status --short` | Chỉ báo các file trong allowlist bên dưới. |
| Scope verification | `git diff --name-only -- .` | Mọi path in ra đều thuộc allowlist; nếu không thì STOP và tách/revert phần ngoài phạm vi. |

## Phạm vi

### Trong phạm vi

- Một bulk image query mỗi checkout, deterministic in-memory index/selection, response-equivalence test, và query-count regression coverage.

### Ngoài phạm vi

- Checkout decomposition (BE-012), đổi order snapshot hoặc fingerprint bytes, lazy-loading change, schema/index change, hoặc pricing/campaign optimization không liên quan.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/product/ProductImageRepository.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutConcurrencyTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`

New files được phép tạo:

- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutImageQueryPerformanceIntegrationTest.java`

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
git switch -c perf/checkout-image-batching
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc thiếu BE-003. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Characterize image selection

Thêm focused test cho variant-specific image, product fallback, thumbnail preference, tie-break `sortOrder`/ID, missing image, duplicate product line, và stable snapshot URL.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest test` → exit 0; characterization matrix cố định externally visible URL mà không phụ thuộc incidental collection iteration.

### 2. Prefetch image một lần

Collect distinct product ID từ checkout line đã validate/lock và gọi `ProductImageRepository.findByProductIdIn(...)` một lần. Build product và product+variant index trước order-line assembly.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest test` → exit 0; mock assert đúng một bulk image call cho checkout không rỗng và không có per-line image call.

### 3. Giữ deterministic selection

Move hoặc reuse comparator hiện có để indexed selection giữ variant-first rồi product fallback, thumbnail-first, `sortOrder`, rồi ID. Giữ selected URL được copy vào cùng order-item snapshot field.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest test` → exit 0; response và persisted order-item image fixture không đổi.

### 4. Thêm query-count integration coverage

Tạo bắt buộc allowlisted `CheckoutImageQueryPerformanceIntegrationTest` làm focused PostgreSQL integration test dùng Hibernate statistics/query capture. So sánh một item với nhiều item và assert image-query count không đổi; chỉ mở rộng concurrency test hiện có như coverage bổ sung khi cần.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutImageQueryPerformanceIntegrationTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test` → exit 0; image-query count constant với một và nhiều line, cả hai concurrency suite pass.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest,CheckoutImageQueryPerformanceIntegrationTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test
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

Smoke empty/invalid cart, repeated variant, mixed product, no image, campaign pricing, coupon use, idempotent retry, và concurrent checkout.

## Tiêu chí hoàn thành

- Image repository work constant theo checkout thay vì theo line.
- Selection order và persisted/API image snapshot không đổi.
- Transaction boundary, resource lock order, pricing, coupon, và fingerprint behavior không đổi.
- Query-count, focused, concurrency, và full verification gate pass.

## STOP conditions

- Batching cần lazy association sau transaction, đổi lock order, hoặc đổi snapshot/fingerprint data.
- Đề xuất collection fetch join trên paginated/locked path.
- Work mở rộng thành general checkout refactor hoặc response-schema change.

## Ghi chú maintenance

Giữ selection comparator tập trung và có coverage khi image precedence đổi. Query-count test nên assert bounded behavior, không assert brittle total application query count. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
