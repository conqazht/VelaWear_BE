# BE-010 — Characterize các orchestration service lớn

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Đây là characterization-test plan, không phải production refactor. Test behavior có ý nghĩa bên ngoài và critical collaborator/transaction invariant mà không freeze private implementation detail. Hoàn thành trước BE-011–BE-014. Sau khi dependency merge, nếu drift chạm in-scope path thì update live citation, scope/test name, `Planned against`, và mirror tiếng Việt trước khi code; không execute stale plan.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P2 / L / 4
- Risk: Medium — characterization kém có thể freeze incidental detail hoặc bỏ sót security/concurrency invariant
- Category: Test coverage / refactor safety
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `test/orchestration-characterization`
- Dependencies: BE-003 và BE-005 đến BE-009
- Migration: không có

## Vì sao

Auth, checkout, sale-campaign, và product service gộp orchestration, persistence, policy, mapping, localization, token/pricing logic. Extract collaborator mà chưa freeze observable behavior có thể tạo regression tinh vi về security, concurrency, money, locale. Focused characterization tạo safety net cho bốn refactor PR sau.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImpl.java` khoảng 747 dòng với 19 collaborator: transaction flow `149-240`, quote gần `286`, item assembly `395-452`, fingerprint/hash `523-575`, response/localization `660-729`.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java` khoảng 627 dòng: lifecycle `57-270`, validation `278-422`, response assembly `431-609`.
- `src/main/java/vn/conganh/commercial/feature/auth/AuthServiceImpl.java` khoảng 524 dòng với 18 collaborator: public flow `133-309`, token operation `323-449`, sensitive change `452-524`.
- `src/main/java/vn/conganh/commercial/feature/product/ProductServiceImpl.java` khoảng 540 dòng: list/detail `54-175`, mapping/pricing/translation `301-499`.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/auth src/main/java/vn/conganh/commercial/feature/checkout src/main/java/vn/conganh/commercial/feature/salecampaign src/main/java/vn/conganh/commercial/feature/product src/test` | Review dependency-wave drift và refresh plan trước khi test. |
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthServiceImplTest,AuthControllerTest,CheckoutServiceImplTest,SaleCampaignServiceImplLocalizationTest,ProductServiceImplTest,ProductResponseTest test` | Exit 0; cả bốn characterization group pass. |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện test file trong allowlist. |
| Scope verification | `git diff --name-only -- .` | Mọi path thuộc allowlist; có production change thì STOP. |

## Phạm vi

### Trong phạm vi

- Thêm/tăng characterization test cho public output, stable error, transaction-sensitive collaborator ordering, lock/session/fingerprint/pricing/locale invariant, và realistic integration seam.
- Chỉ thêm test fixture/helper khi cần.

### Ngoài phạm vi

- Production service extraction, API/schema change, dependency change, performance rewrite, hoặc test assert private method structure.

### Exact file allowlist

Existing files được phép sửa:

- `src/test/java/vn/conganh/commercial/TestDataFactory.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthControllerTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthRefreshConcurrencyIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/RedisSecurityAndCleanupIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/refreshtoken/RefreshTokenSessionServiceIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/CheckoutConcurrencyTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignControllerIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImplLocalizationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignPhaseTest.java`
- `src/test/java/vn/conganh/commercial/feature/product/ProductServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/product/dto/ProductResponseTest.java`
- `src/test/java/vn/conganh/commercial/feature/product/ProductTranslationServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceIntegrationTest.java`

New files được phép tạo: không có; giữ behavior matrix trong PR description.

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
git switch -c test/orchestration-characterization
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc dependency chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

## Các bước implementation

### 1. Xây behavior matrix

Với từng service, list public method, success output, stable error, side effect, transaction/lock requirement, và collaborator boundary định extract sau. Link từng row tới existing/new test.

**Verify**: `rg -n "securityVersion|fingerprint|locale|pricing|overlap" src/test/java/vn/conganh/commercial/feature` → mọi extraction-boundary invariant có behavior test và PR matrix row.

### 2. Characterize authentication orchestration

Mở rộng `AuthServiceImplTest`, `AuthControllerTest`, refresh concurrency/session integration test cho claim, cookie-independent service result, refresh rotate/CAS ordering, audit/session creation, sensitive-change revocation, stable error code.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthServiceImplTest,AuthControllerTest,AuthRefreshConcurrencyIntegrationTest,RefreshTokenSessionServiceIntegrationTest,RedisSecurityAndCleanupIntegrationTest test` → exit 0; claim, session version, rotation và revocation được characterize không dùng raw secret.

### 3. Characterize checkout orchestration

Cover normalized request fingerprint, idempotent replay/conflict, resource lock order, money snapshot, image snapshot, coupon consume/release, pricing/campaign selection, localized response mapping trong `CheckoutServiceImplTest` và PostgreSQL concurrency test.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CheckoutServiceImplTest,CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest test` → exit 0; fingerprint và race fixture pass deterministic.

### 4. Characterize campaign và product orchestration

Mở rộng campaign controller/localization/concurrency/phase test và product service/response/translation/storefront test. Freeze validation boundary, response order, locale fallback, pricing summary, category/image assembly, status visibility.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignPhaseTest,ProductServiceImplTest,ProductResponseTest,ProductTranslationServiceImplTest,StorefrontCatalogServiceImplTest,StorefrontCatalogServiceIntegrationTest test` → exit 0; contract, locale, pricing và visibility invariant pass.

### 5. Review test quality và runtime

Loại duplicate assertion, centralize safe factory, label PostgreSQL/Redis integration need, và dùng deterministic clock/latch thay sleep.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` → exit 0 hai lần không sleep/flaky timing; mutation check được ghi trong PR.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthServiceImplTest,AuthControllerTest,CheckoutServiceImplTest,SaleCampaignServiceImplLocalizationTest,ProductServiceImplTest,ProductResponseTest test
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

Chạy explicit affected Redis/PostgreSQL concurrency integration test nếu naming/exclusion khiến focused command không include.

## Tiêu chí hoàn thành

- Mọi extraction boundary trong BE-011–BE-014 có meaningful behavior và invariant coverage.
- Test cover auth/session security, checkout money/concurrency, campaign phase/overlap, product locale/pricing behavior.
- Không production behavior hay public contract đổi trong PR này.
- Focused và full verification deterministic và pass.

## STOP conditions

- Test chỉ pass khi assert private method/field hoặc incidental mock order không liên quan real invariant.
- Characterization phát hiện active correctness/security defect; tách fix plan/PR trước refactor.
- Cần production code change ngoài tiny testability seam; review và re-scope trước.

## Ghi chú maintenance

Xem test này là refactor safety, không immutable implementation snapshot. Update behavior matrix khi contract đổi và chỉ xóa redundant coverage với evidence. Sau khi dependency merge, refresh live citation/scope/test, `Planned against`, và mirror tiếng Việt trước khi code; không execute stale plan. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này.
