# BE-003 — Serialize cập nhật coupon counter

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan, giữ checkout lock ordering, và chứng minh concurrency trên PostgreSQL bằng coordination deterministic. Không thay atomic database mutation bằng Java read/check/save.

## Trạng thái

- State: `TODO`
- Risk: High
- Category: Correctness / Concurrency
- Priority / effort / wave: P1 / M / 2
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `fix/coupon-counter-concurrency`
- Dependency: BE-002
- Migration: dự kiến không có

## Vì sao

Checkout dùng conditional atomic update cho `usedCount`, nhưng admin coupon edit load và save toàn entity mà không có row lock hay `@Version`. Admin save concurrent có thể overwrite counter increment thành công của checkout, vi phạm usage limit. Serialize admin mutation bằng cùng database row loại bỏ lost-update window mà không redesign checkout.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/coupon/Coupon.java:48-52` lưu `usageLimit` và `usedCount` trên cùng entity và không có `@Version`.
- `src/main/java/vn/conganh/commercial/feature/coupon/CouponRepository.java:23-42`, `consumeUsage(...)` và `releaseUsage(...)`, thực hiện guarded bulk counter update.
- `src/main/java/vn/conganh/commercial/feature/coupon/CouponServiceImpl.java:85-100`, `updateCoupon(...)`, gọi `findById(...)`, mutate field, rồi `save(...)`, cho phép ghi `usedCount` stale.
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutConcurrencyTest.java:229-307` cover concurrent single-use behavior. `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java:776-943` có latch/gate coordination phù hợp cho deterministic race test.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/coupon src/main/java/vn/conganh/commercial/feature/checkout src/test
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CouponServiceImplTest,CouponConcurrencyIntegrationTest,CheckoutConcurrencyTest test` | Exit 0 |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Scope | `git status --short` | Chỉ coupon/test/docs trong allowlist xuất hiện; không migration/entity change |
| Scope diff | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Phạm vi

### Trong phạm vi

- Thêm pessimistic-write coupon lookup cho admin mutation, dùng nó trong transaction hiện có, và thêm deterministic concurrency regression test.
- Document invariant coupon lock/counter.

### Exact file allowlist

- Existing: `src/main/java/vn/conganh/commercial/feature/coupon/CouponRepository.java`, `src/main/java/vn/conganh/commercial/feature/coupon/CouponServiceImpl.java`, `src/test/java/vn/conganh/commercial/feature/coupon/CouponServiceImplTest.java`, `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutConcurrencyTest.java`, `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`, `src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md`, `docs/PROJECT-STATUS.md`.
- New: `src/test/java/vn/conganh/commercial/feature/coupon/CouponConcurrencyIntegrationTest.java`.
- Không entity, migration, checkout production, hay file khác được đổi nếu chưa reconcile plan.

### Ngoài phạm vi

- Thay conditional counter SQL, đưa Redis counter vào, đổi coupon semantics, refactor checkout rộng, hoặc thêm optimistic-lock migration.

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
git switch -c fix/coupon-counter-concurrency
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge hoặc branch đã tồn tại. Xác nhận BE-002 đã merge. Không stash/reset và không commit/push trước khi mọi gate pass; sau đó publish PR theo operator workflow.

Sau khi dependency merge, reconcile in-scope drift bằng cách update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Định nghĩa locking invariant

Document rằng admin edit lấy PostgreSQL `PESSIMISTIC_WRITE` lock trên coupon row trước mutation, trong khi checkout giữ guarded atomic `consumeUsage`/`releaseUsage`. Xác nhận transaction và resource lock order với checkout code.

**Verify**: `rg -n "usedCount|consumeUsage|releaseUsage|PESSIMISTIC_WRITE|findWithLockById" src/main/java/vn/conganh/commercial/feature/coupon src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md` → mọi whole-row mutation được cover bởi documented coupon-row lock và atomic counter còn nguyên.

### 2. Thêm locked repository query

Thêm method riêng như `findWithLockById` vào `CouponRepository` dùng `@Lock(PESSIMISTIC_WRITE)` và explicit query nếu cần. Chỉ dùng cho admin update/delete path có thể overwrite counter state.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CouponConcurrencyIntegrationTest test` → exit 0; deterministic test chứng minh transaction thứ hai chờ coupon row lock.

### 3. Route admin mutation qua lock

Đổi `CouponServiceImpl.updateCoupon(...)` và whole-row destructive mutation tương đương để load qua locked method trong transaction hiện có. Giữ validation, exception code, và mapping.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CouponServiceImplTest test` → exit 0; admin mutation dùng `findWithLockById` và giữ validation/error mapping.

### 4. Thêm deterministic PostgreSQL race test

Tạo `CouponConcurrencyIntegrationTest` với latch/barrier: pause admin mutation sau locked load, race checkout consumption, release, rồi assert `usedCount` đã commit và limit invariant. Cover release/rollback khi liên quan; giữ checkout concurrency test hiện có.

**Verify**: chạy fail-fast loop dưới đây; ba deterministic PostgreSQL run exit 0, không sleep-only coordination.

```powershell
1..3 | ForEach-Object {
  .\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CouponConcurrencyIntegrationTest,CheckoutConcurrencyTest test
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
exit 0
```

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CouponServiceImplTest,CouponConcurrencyIntegrationTest,CheckoutConcurrencyTest test
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

## Tiêu chí hoàn thành

- Admin whole-row mutation không thể overwrite counter update concurrent.
- Checkout giữ conditional atomic consume/release behavior và usage-limit semantics.
- Deterministic PostgreSQL test chứng minh interleaving trước đây bị lỗi.
- Không thêm migration trừ khi schema requirement mới được approve rõ ràng.

## STOP conditions

- Fix đề xuất chỉ dùng H2, mock, timing sleep, hoặc in-memory synchronization để chứng minh concurrency.
- Nó thay guarded SQL bằng read/check/save, đổi checkout lock order, hoặc tạo deadlock cycle mới.
- Cần schema migration hoặc semantic change; re-plan trước khi tiếp tục.

## Ghi chú maintenance

Giữ counter mutation tập trung và document mọi code path mới ghi `usedCount`. Chạy lại race suite sau thay đổi checkout/coupon. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
