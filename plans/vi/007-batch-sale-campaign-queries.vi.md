# BE-007 — Batch sale-campaign query

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Giữ pagination total, item order, overlap semantics, optimistic/locking behavior, và response schema. Chứng minh bounded query count trên PostgreSQL. Không dùng collection fetch join với pageable query.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P2 / L / 3
- Risk: High — đổi pageable loading và overlap validation trong campaign workflow nhạy cảm với concurrency
- Category: Performance / data access / validation
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `perf/sale-campaign-query-batching`
- Dependency: BE-003
- Migration: không có

## Vì sao

Sale-campaign listing map lazy item collection theo từng campaign, còn create/update validate schedule overlap một lần cho mỗi requested variant. Cả hai làm query tăng theo page/item size. Two-phase page load và một bulk overlap check có thể bound database work mà giữ public campaign contract.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java:63-74` load page; `439-475` map lazy item/product data.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaign.java:85-87` khai báo items lazy, còn `SaleCampaignResponse.java:64-75` embed full item response.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java:327-345` check overlap trong variant loop.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignItemRepository.java:55-68` expose single-variant overlap query nhưng chưa có bulk equivalent.
- `src/main/java/vn/conganh/commercial/feature/salecampaign/dto/CreateSaleCampaignRequest.java:12-20` và `UpdateSaleCampaignRequest.java:12-21` require non-empty item nhưng chưa có maximum được approve.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/salecampaign src/test/java/vn/conganh/commercial/feature/salecampaign src/test/java/vn/conganh/commercial/feature/checkout docs` | Review mọi upstream change; STOP nếu evidence về paging, lock, phase hoặc overlap đã drift. |
| Focused tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignQueryPerformanceIntegrationTest,SaleCampaignConcurrencyIntegrationTest test` | Exit 0; contract, localization, bounded query và race đúng. |
| Full backend | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Diff hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện path trong allowlist bên dưới. |
| Scope verification | `git diff --name-only -- .` | Mọi path in ra đều thuộc allowlist; nếu không thì STOP và tách/revert phần ngoài phạm vi. |

## Phạm vi

### Trong phạm vi

- Two-phase campaign page/item loading, bulk overlap lookup, deterministic response reconstruction, query-count test và request bound 100 item cho create/update.

### Ngoài phạm vi

- Response-schema change, campaign orchestration refactor (BE-013), pricing redesign, cache/CDN, migration, hoặc business limit khác ngoài safety bound 100 item đã document.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignRepository.java`
- `src/main/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignItemRepository.java`
- `src/main/java/vn/conganh/commercial/feature/salecampaign/dto/CreateSaleCampaignRequest.java`
- `src/main/java/vn/conganh/commercial/feature/salecampaign/dto/UpdateSaleCampaignRequest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignControllerIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignServiceImplLocalizationTest.java`
- `src/test/java/vn/conganh/commercial/feature/checkout/SaleCampaignConcurrencyIntegrationTest.java`
- `docs/API_SPEC.md`

New files được phép tạo:

- `src/test/java/vn/conganh/commercial/feature/salecampaign/SaleCampaignQueryPerformanceIntegrationTest.java`

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
git switch -c perf/sale-campaign-query-batching
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc thiếu BE-003. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Characterize page và overlap behavior

Thêm fixture cho empty/non-empty page, nhiều campaign/item, stable item ordering, localized field, active phase, exclude current campaign khi update, duplicate variant input, và exact boundary overlap.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignConcurrencyIntegrationTest test` → exit 0; payload, total, ordering, locale và conflict outcome đã được characterize.

### 2. Implement two-phase page loading

Page chỉ campaign root/ID với specification và sort hiện có. Bulk-fetch detail/item cho ID trong page, rồi reconstruct response theo original page order. Tránh pageable collection fetch join và giữ count query semantics.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignQueryPerformanceIntegrationTest test` → exit 0; total/order khớp fixture và query family bounded khi page size tăng.

### 3. Batch overlap validation

Normalize distinct requested variant ID và chạy một overlap query cho toàn set cùng requested time range, exclude updated campaign khi cần. Map conflict về stable domain error. Giữ validation trong established transaction/lock ordering.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignConcurrencyIntegrationTest,SaleCampaignQueryPerformanceIntegrationTest test` → exit 0; một bulk overlap query family xử lý N item và mọi phase/boundary outcome khớp.

### 4. Giới hạn campaign mutation payload

Thêm `@Size(max = 100)` vào item collection của cả create và update DTO, rồi document limit trong API contract. Mức này khớp convention public maximum-page hiện tại và giữ validation/query/memory work hữu hạn; không thêm hidden limit khác.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; rg -n "100.*(item|items)|items.*100" docs/API_SPEC.md` → exit 0; create/update nhận 100 item, reject 101 với stable 400 validation envelope, và public API contract ghi cùng bound.

### 5. Thêm query-performance integration test

Tạo `SaleCampaignQueryPerformanceIntegrationTest` với PostgreSQL và query capture/statistics. Assert page và overlap query family bounded giữa fixture nhỏ và lớn hơn.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignQueryPerformanceIntegrationTest,SaleCampaignConcurrencyIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignControllerIntegrationTest test` → exit 0; bounded-query assertion và mọi regression suite pass.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SaleCampaignControllerIntegrationTest,SaleCampaignConcurrencyIntegrationTest,SaleCampaignServiceImplLocalizationTest,SaleCampaignQueryPerformanceIntegrationTest test
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

Smoke pageable filter/sort, empty page, campaign detail, localized response, create/update overlap boundary, duplicate variant, phase transition, và concurrent update.

## Tiêu chí hoàn thành

- Campaign page và overlap validation không còn scale query tuyến tính theo campaign/item.
- Pagination, response/item order, localization, phase, error, và lock behavior không đổi.
- Create/update từ chối quá 100 item qua Bean Validation đã document và boundary test.
- Query-count, regression, concurrency, và full verification pass.

## STOP conditions

- Đề xuất collection fetch join với pagination hoặc làm hỏng count query.
- Bulk validation di chuyển ngoài transaction, đổi lock order, hoặc đổi overlap boundary/error semantics.
- Active documented product requirement cần hơn 100 item mỗi campaign; dừng và revise contract thay vì âm thầm đổi bound.
- Work cần response schema hoặc service decomposition rộng hơn.

## Ghi chú maintenance

Giữ two-phase reconstruction có coverage cho sort/order stability và update bulk query khi response detail đổi. Track query family thay vì exact total statement. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
