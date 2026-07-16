# BE-014 — Refactor product orchestration

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Chỉ extract product response assembler sau query/characterization dependency. Giữ admin/storefront boundary, batched data loading, localization/fallback, pricing, image/category, ordering, response field. Không dùng MapStruct cho repository/pricing/locale policy.

## Trạng thái

- State: `TODO`
- Risk: Medium — behavior-preserving refactor trên localized/priced paginated response
- Category: Architecture / Maintainability
- Priority / effort / wave: P3 / L / 4
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `refactor/product-orchestration`
- Dependencies: BE-007, BE-009, BE-010, BE-013
- Migration: không có

## Vì sao

`ProductServiceImpl` sở hữu CRUD/query orchestration và assemble contextual response sâu bằng translation, category, image, variant, effective pricing. Explicit assembler có thể isolate deterministic presentation policy, nhưng mapping này không phải field copying đơn giản và không được ẩn I/O hoặc thành per-product N+1.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/product/ProductServiceImpl.java:60-106` build paged list response bằng batch context; `117-175` build detail response.
- Cùng file thực hiện pricing, translation, category, image, response assembly `301-499`.
- `src/main/java/vn/conganh/commercial/feature/product/dto/ProductResponse.java` gồm locale-aware và image/pricing field với custom selection rule.
- `src/main/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceImpl.java` sở hữu storefront visibility/catalog behavior riêng phải giữ tách biệt.
- `docs/PROJECT-RULES.md:345-379` ưu tiên explicit/static mapping và chỉ cho MapStruct khi justified; contextual mapping này không phù hợp MapStruct boundary.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/product src/main/java/vn/conganh/commercial/feature/storefrontcatalog src/test/java/vn/conganh/commercial/feature/product src/test/java/vn/conganh/commercial/feature/storefrontcatalog docs
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductResponseAssemblerTest,ProductServiceImplTest,ProductResponseTest,ProductControllerTest,ProductTranslationServiceImplTest,StorefrontCatalogServiceImplTest,StorefrontCatalogServiceIntegrationTest test` | Exit 0; field equivalence và bounded loading pass |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Worktree | `git status --short` | Chỉ exact allowlist file xuất hiện |
| Scope | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Phạm vi

### Trong phạm vi

- Define complete batch mapping context, extract `ProductResponseAssembler`, simplify product service orchestration, giữ separate storefront rule, update test/docs.

### Exact file allowlist

- Existing source: `src/main/java/vn/conganh/commercial/feature/product/ProductServiceImpl.java`, `src/main/java/vn/conganh/commercial/feature/product/dto/ProductResponse.java`, `src/main/java/vn/conganh/commercial/feature/product/CONTEXT.md`, `src/main/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceImpl.java`.
- Existing tests: `src/test/java/vn/conganh/commercial/feature/product/ProductServiceImplTest.java`, `src/test/java/vn/conganh/commercial/feature/product/dto/ProductResponseTest.java`, `src/test/java/vn/conganh/commercial/feature/product/ProductControllerTest.java`, `src/test/java/vn/conganh/commercial/feature/product/ProductTranslationServiceImplTest.java`, `src/test/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceImplTest.java`, `src/test/java/vn/conganh/commercial/feature/storefrontcatalog/StorefrontCatalogServiceIntegrationTest.java`.
- Existing docs: `docs/ARCHITECTURE.md`, `docs/PROJECT-RULES.md`, `docs/PROJECT-STATUS.md`.
- New: `src/main/java/vn/conganh/commercial/feature/product/ProductResponseAssembler.java`, `src/test/java/vn/conganh/commercial/feature/product/ProductResponseAssemblerTest.java`.
- Không repository, DTO contract, storefront policy, migration, hay file khác được đổi nếu chưa reconcile plan.

### Ngoài phạm vi

- MapStruct, API/DTO/schema change, product CRUD/lock/campaign rule change, storefront merge, cache mới, per-product repository call, hoặc pricing/locale redesign.

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
git switch -c refactor/product-orchestration
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc dependency chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Freeze list/detail/storefront contract

Dùng fixture BE-010/BE-009 để enumerate field, locale fallback, category name/slug, image/thumbnail/color ordering, base/effective price, campaign pricing, status visibility, paging order. Mark ownership admin/product và storefront policy.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductServiceImplTest,ProductResponseTest,StorefrontCatalogServiceImplTest test` → exit 0; behavior matrix list/detail/storefront được cover trước extraction.

### 2. Define complete batch mapping context

Tạo immutable context structure keyed theo product ID cho translation/locale, image, category data, base price, effective pricing. Product service/repository load bulk trước mapping.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductServiceImplTest test` → exit 0; fixture một/nhiều product giữ bounded bulk collaborator call và context immutable.

### 3. Extract `ProductResponseAssembler`

Move deterministic list/detail DTO construction và custom fallback/selection rule vào assembler. Reuse existing response helper semantics khi an toàn; giữ explicit code cho contextual field. Không introduce MapStruct.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductResponseAssemblerTest,ProductResponseTest test` → exit 0; output locale/category/image/variant/base-sale-pricing/status field-equivalent.

### 4. Simplify product service orchestration

Giữ CRUD, transaction, lock, campaign guard, repository, batch-loading decision trong `ProductServiceImpl`; chỉ delegate mapping từ complete context. Giữ `StorefrontCatalogServiceImpl` tách biệt.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductServiceImplTest,ProductControllerTest,StorefrontCatalogServiceImplTest,StorefrontCatalogServiceIntegrationTest test` → exit 0; không per-product I/O và CRUD/guard/storefront behavior không đổi.

### 5. Update documentation

Update product `CONTEXT.md` trong allowlist, architecture boundary, project rule/status, và DTO mapping note để giải thích vì sao dùng explicit assembler thay MapStruct. Storefront behavior được cover bằng test và shared docs; PR này không tạo storefront `CONTEXT.md` riêng.

**Verify**: `rg -n "ProductResponseAssembler|batch context|repository-free|MapStruct|StorefrontCatalogServiceImpl" docs/ARCHITECTURE.md docs/PROJECT-RULES.md docs/PROJECT-STATUS.md src/main/java/vn/conganh/commercial/feature/product/CONTEXT.md` → document exact dependency direction và explicit-mapper rationale.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ProductResponseAssemblerTest,ProductServiceImplTest,ProductResponseTest,ProductControllerTest,ProductTranslationServiceImplTest,StorefrontCatalogServiceImplTest,StorefrontCatalogServiceIntegrationTest test
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

- Product response mapping isolated sau repository-free explicit assembler với complete batch context.
- List/detail/storefront contract, locale, pricing, category/image order, query bound, CRUD/lock/guard không đổi.
- Không introduce MapStruct và không có per-product I/O.
- Focused, storefront, query, full verification gate pass.

## STOP conditions

- Assembler cần repository/service, tạo per-product I/O, hoặc đổi query bound.
- Phải merge product/storefront policy, hoặc response/locale/pricing/image/status behavior đổi.
- Solution đề xuất dựa MapStruct cho contextual policy hoặc cần API/schema/business redesign.

## Ghi chú maintenance

Giữ context loader và assembler tách biệt: I/O trước, pure mapping sau. Thêm response field mới vào batch context có chủ đích và bảo vệ bằng query test. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
