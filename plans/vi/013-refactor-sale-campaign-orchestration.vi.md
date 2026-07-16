# BE-013 — Refactor sale-campaign orchestration

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Giữ lifecycle transaction, overlap/version/lock semantics, query batching từ BE-007, response order, localization, phase và error. Chỉ extract validator và response assembler sau characterization BE-010. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P3 / L / 4
- Risk: High — validation/assembly extraction có thể regress transactional overlap check hoặc localized response semantics
- Category: Refactor / sale-campaign correctness
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `refactor/sale-campaign-orchestration`
- Dependencies: BE-007 và BE-010
- Migration: không có

## Vì sao

`SaleCampaignServiceImpl` gộp lifecycle transaction script với reusable validation và response/localization assembly. Move các concern cohesive này làm rõ ownership/testing, nhưng database-aware validation phải ở trong transaction và giữ batched query/lock behavior đã thiết lập.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java:57-270` chứa list/detail/create/update/delete/lifecycle orchestration.
- Cùng file có validation và overlap logic `278-422`, rồi response/localization/pricing assembly `431-609`.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignItemRepository.java:55-68` là overlap-query seam được cải thiện trong BE-007.
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java` bảo vệ version/lock/phase race; localization/controller test bảo vệ response behavior.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/salecampaign src/test/java/vn/conganh/commercial/feature/salecampaign src/test/java/vn/conganh/commercial/feature/checkout docs` | Review drift BE-007/BE-010 và refresh plan trước extraction. |
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignValidatorTest,SaleCampaignResponseAssemblerTest,SaleCampaignControllerIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignConcurrencyIntegrationTest,SaleCampaignQueryPerformanceIntegrationTest test` | Exit 0; validation, response, batching và race pass. |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện file trong allowlist. |
| Scope verification | `git diff --name-only -- .` | Mọi path thuộc allowlist; nếu không thì STOP. |

## Phạm vi

### Trong phạm vi

- Extract `SaleCampaignValidator` cho transaction-scoped request/entity policy và `SaleCampaignResponseAssembler` cho already-loaded response context; simplify service orchestration; update test/docs.

### Ngoài phạm vi

- API/DTO/schema change, campaign rule mới, move transaction boundary, undo BE-007 batching, đổi pricing/locale/phase semantics, MapStruct, hoặc cache work.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignControllerIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImplLocalizationTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignQueryPerformanceIntegrationTest.java` (do BE-007 tạo; STOP nếu thiếu sau dependency merge)
- `docs/ARCHITECTURE.md`
- `docs/PROJECT-STATUS.md`

New files được phép tạo:

- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignValidator.java`
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignResponseAssembler.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignValidatorTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignResponseAssemblerTest.java`

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
git switch -c refactor/sale-campaign-orchestration
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc dependency chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

## Các bước implementation

### 1. Định nghĩa collaborator contract

Phân loại validation thành pure shape/time rule và database-aware overlap/entity rule. Define complete response context gồm loaded campaign item, product/variant data, requested locale/fallback, pricing, phase clock. Giữ repository-aware validation được invoke trong service transaction.

**Verify**: chạy negative search dưới đây. Exit 0 và không output chứng minh assembler repository-free, còn validator transaction requirement explicit tại facade call site.

```powershell
rg -n "Repository|EntityManager|@Transactional" src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignResponseAssembler.java
if ($LASTEXITCODE -eq 0) { exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
exit 0
```

### 2. Extract `SaleCampaignValidator`

Move request normalization, duplicate check, time/phase rule, entity consistency, batched overlap interpretation mà không đổi query count hay error code. Service vẫn control thời điểm lock/query.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignValidatorTest,SaleCampaignQueryPerformanceIntegrationTest test` → exit 0; mọi characterization row pass và overlap còn một query family với update self-exclusion.

### 3. Extract `SaleCampaignResponseAssembler`

Move deterministic DTO construction, item order, locale fallback, pricing, phase representation. Truyền prefetched map/context; không gọi repository trong item loop.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignResponseAssemblerTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignControllerIntegrationTest,SaleCampaignQueryPerformanceIntegrationTest test` → exit 0; response field-equivalent và query bounded.

### 4. Giảm service thành lifecycle orchestration

Giữ public method và `@Transactional` boundary trong `SaleCampaignServiceImpl`; làm rõ validation, lock/load, mutation, persistence, assembly phase. Giữ save/flush/version behavior và after-effect.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignConcurrencyIntegrationTest test` → exit 0; lifecycle transaction/version/lock behavior không đổi.

### 5. Update architecture documentation

Update architecture/service-boundary note và project status với responsibility transaction owner, validator, assembler, batch loader. Không invent feature `CONTEXT.md` mới trong PR này.

**Verify**: `rg -n "SaleCampaignValidator|SaleCampaignResponseAssembler|transaction|repository-free" docs/ARCHITECTURE.md docs/PROJECT-STATUS.md` → canonical docs nói database-aware validation chạy trong facade transaction và assembler repository-free.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignValidatorTest,SaleCampaignResponseAssemblerTest,SaleCampaignControllerIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignConcurrencyIntegrationTest,SaleCampaignQueryPerformanceIntegrationTest test
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

- Validator và response assembly là focused tested collaborator; lifecycle service vẫn transaction owner.
- Overlap batching, lock/version, query bound, item order, phase, pricing, locale, error, API không đổi.
- Không repository call từ assembler item loop.
- Focused, concurrency, performance, full verification pass.

## STOP conditions

- Validation move ngoài required transaction, lock/version order đổi, hoặc overlap batching regress.
- Response field/order/locale/pricing/phase/error đổi hoặc lazy load thoát transaction.
- Extraction cần API/schema/business-rule change; tách và re-plan.

## Ghi chú maintenance

Giữ validator DB requirement explicit và assembler input complete. Extend characterization/query test trước khi thêm campaign rule/field. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này.
