# BE-012 — Refactor checkout orchestration

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Giữ single checkout transaction, resource-lock order, fingerprint byte, money/image snapshot, coupon behavior và public error. Extract chính xác `CheckoutFingerprintService` và `CheckoutOrderItemAssembler`; giữ response/localization trong facade. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P3 / L / 4
- Risk: High — checkout extraction có thể đổi transaction order, idempotency byte hoặc monetary snapshot
- Category: Refactor / checkout correctness
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `refactor/checkout-orchestration`
- Dependencies: BE-003, BE-006, BE-010
- Migration: không có

## Vì sao

`CheckoutServiceImpl` gộp transaction orchestration với request fingerprinting, order-line snapshot construction, response localization. Pure deterministic work có thể move sang focused collaborator, để service thành explicit transaction script. Move lock hoặc persistence sẽ không an toàn.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java:149-240` là core transaction/lock script.
- Cùng file chứa quote/calculation gần `286`, order-item assembly `395-452`, request hashing/fingerprint `523-575`, response/localization `660-729`.
- `src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md` document checkout transaction, idempotency, resource ordering.
- BE-003 bảo vệ coupon counter race; BE-006 batch image; BE-010 freeze fingerprint, snapshot, money, response, error, concurrency behavior.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/checkout src/test/java/vn/conganh/commercial/feature/checkout docs` | Review drift BE-003/BE-006/BE-010 và refresh plan trước. |
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutFingerprintServiceTest,CheckoutOrderItemAssemblerTest,CheckoutServiceImplTest,CheckoutControllerTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test` | Exit 0; extraction và mọi critical checkout invariant pass. |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện file trong allowlist. |
| Scope verification | `git diff --name-only -- .` | Mọi path thuộc allowlist; nếu không thì STOP. |

## Phạm vi

### Trong phạm vi

- Extract chính xác pure `CheckoutFingerprintService` và `CheckoutOrderItemAssembler`; simplify `CheckoutServiceImpl`; update test/docs.

### Ngoài phạm vi

- Response assembler extraction (defer), move response/localization khỏi `CheckoutServiceImpl`, move transaction/repository lock, đổi query/lock order, DTO/API/schema, price/coupon/image semantics, idempotency format, MapStruct, hoặc performance work ngoài BE-006.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutControllerTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutConcurrencyTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT-STATUS.md`

New files được phép tạo:

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutFingerprintService.java`
- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutOrderItemAssembler.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutFingerprintServiceTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutOrderItemAssemblerTest.java`

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
git switch -c refactor/checkout-orchestration
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc dependency chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

## Các bước implementation

### 1. Đánh dấu transaction và lock boundary

Annotate/review exact sequence user/cart/stock/coupon/campaign/order operation và data mỗi pure collaborator có thể nhận. Không extracted class nào acquire repository/lock nếu chưa review riêng.

**Verify**: `rg -n "@Transactional|PESSIMISTIC|decrementStock|consume|save" src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md` → facade vẫn sở hữu documented transaction, lock và mutation theo cùng order.

### 2. Extract fingerprint logic

Move normalization, canonical serialization, hashing sang `CheckoutFingerprintService` với explicit immutable input. Giữ charset, field order, null/default handling, digest encoding byte-for-byte.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutFingerprintServiceTest,CheckoutServiceImplTest test` → exit 0; golden byte và replay/conflict outcome giống nhau.

### 3. Extract order-item snapshot assembly

Move deterministic construction từ product/variant/image/pricing data đã load vào assembler. Giữ repository và stock/coupon mutation trong orchestration. Làm rõ money rounding và snapshot field.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutOrderItemAssemblerTest,CheckoutServiceImplTest,CheckoutControllerTest test` → exit 0; persisted/API snapshot field-for-field giống nhau.

### 4. Giữ response và localization trong facade

Không tạo response assembler trong PR này. Giữ response/localization path hiện có trong `CheckoutServiceImpl` để wave chỉ có hai extraction axis; document việc defer cho review dựa trên evidence sau.

**Verify**: `rg -n "response|locale|localization" src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java` → response/localization vẫn ở facade, và `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest,CheckoutControllerTest test` exit 0.

### 5. Simplify và document orchestration

Giảm `CheckoutServiceImpl` thành named validation, locking, calculation, persistence, delegation phase không đổi public signature. Update checkout `CONTEXT.md` và architecture note.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test` → exit 0; orchestration, idempotency và race pass lặp lại.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutFingerprintServiceTest,CheckoutOrderItemAssemblerTest,CheckoutServiceImplTest,CheckoutControllerTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test
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

- Fingerprint và snapshot mapping là cohesive tested collaborator; checkout service vẫn là transaction orchestrator.
- Transaction boundary, lock order, fingerprint byte, money/image snapshot, coupon/pricing, error, API không đổi.
- Không new per-item query hay lazy-load leak.
- Focused, concurrency, full verification gate pass.

## STOP conditions

- Extraction move `@Transactional`, repository lock/mutation, hoặc đổi resource order.
- Fingerprint output, money rounding, snapshot data, idempotency, query count, hoặc error code đổi.
- Assembler đề xuất cần repository/lazy entity hoặc work mở rộng sang business redesign.
- Có đề xuất extraction thứ ba, gồm response assembler, trong PR này.

## Ghi chú maintenance

Giữ pure collaborator input explicit và immutable; chỉ update golden fingerprint fixture cho intentional versioned change. Giữ checkout context là source của lock ordering. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này.
