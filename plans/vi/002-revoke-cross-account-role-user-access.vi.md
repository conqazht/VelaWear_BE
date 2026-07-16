# BE-002 — Revoke quyền cross-account của `ROLE_USER`

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Đây là phase contract của rollout expand/migrate/contract: chỉ execute sau khi BE-001 và external FE-001 đã merge và verify. Dùng production role thật trong authorization test. Không gộp redesign endpoint vào công việc này.

## Trạng thái

- State: `TODO`
- Risk: High
- Category: Security / RBAC
- Priority / effort / wave: P1 / M / 1
- Planned against: `c3b1361` ngày 2026-07-16
- Branch: `fix/cross-account-role-user-access`
- Dependencies: BE-001 merge tại `c3b1361`; external FE-001 merge tại `4c2767b`
- Reserved migration: `V22__revoke_legacy_customer_permissions.sql`

## Vì sao

`ROLE_USER` có thể truy cập path customer-resource tổng quát chỉ bằng permission method/path. Khi toàn bộ customer traffic đã dùng API `/me` bind ownership, các grant này vừa không cần vừa nguy hiểm. V22 phải chỉ revoke permission customer legacy, đồng thời giữ operator access và contract self-service mới.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/security/PermissionAuthorizationManager.java`, `authorize(...)`, parse value `method + apiPath` đã lưu và gọi `pathMatcher.match(apiPath, requestPath)` mà không có resource ownership check.
- `src/main/resources/db/migration/V2__seed_rbac.sql:122-137`, `V3__align_rbac_api_paths.sql:59-68`, và `V5__seed_missing_permissions.sql:106-119` cấp USER các path cart/order/address/review/wishlist tổng quát.
- `src/main/java/vn/conganh/commercial/config/SecurityConfig.java`, `filterChain(...)`, authenticate các direct self-service path; các path `/me` khác được biểu diễn trong RBAC migration.
- `src/test/java/vn/conganh/commercial/TestDataFactory.java:33-117` tạo `TEST_ROLE` rộng; đây không phải bằng chứng production `ROLE_USER` an toàn.
- `src/main/resources/db/migration/V21__seed_customer_self_service_permissions.sql:4-37` thêm permission profile/order/address ownership-scoped do BE-001 bàn giao.
- BE-001 đã merge tại `c3b1361`; external FE-001 đã merge tại `4c2767b` và xóa customer call tới các generic route được review ở đây.

Việc review V22 bắt đầu từ revoke set rõ ràng sau cho `ROLE_USER` và phải xác minh từng method/path đã lưu trước khi xóa:

- Grant legacy V2: `VIEW_CART`, `UPDATE_CART`, `DELETE_CART_ITEM`, `CREATE_ORDER`, `VIEW_ORDER`, `CREATE_PAYMENT`, `UPDATE_REVIEW`, `DELETE_REVIEW`, `VIEW_WISHLIST`, `UPDATE_WISHLIST`.
- Grant V3: `CREATE_CART`, `DELETE_CART`, `VIEW_USER_ADDRESSES`, `VIEW_USER_ADDRESS`, `CREATE_USER_ADDRESS`, `UPDATE_USER_ADDRESS`, `DELETE_USER_ADDRESS`.
- Grant V5: `VIEW_CART_BY_ID`, `VIEW_CART_BY_USER`, `VIEW_ORDER_BY_CODE`, `VIEW_ORDERS_BY_USER`, `VIEW_ORDER_STATUS_HISTORIES`, `VIEW_REVIEWS_BY_USER`, `VIEW_REVIEWS_BY_ORDER`, `VIEW_REVIEWS_BY_ORDER_ITEM`, `VIEW_WISHLIST_BY_ID`, `DELETE_WISHLIST`.

Giữ catalog read, toàn bộ grant `/me` của V21, checkout, self-cart/wishlist/coupon và `CREATE_REVIEW`: multipart create endpoint hiện tại lấy user từ JWT, không nhận ownership do client chọn. Grant legacy stale vẫn bị revoke để controller tương lai không vô tình kích hoạt lại.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat c3b1361..HEAD -- src/main/resources/db/migration src/main/java/vn/conganh/commercial/security src/main/java/vn/conganh/commercial/config/SecurityConfig.java src/test docs
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest,CustomerSelfScopeIntegrationTest test` | Exit 0 |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Scope | `git status --short` | Chỉ migration/test/docs trong allowlist xuất hiện |
| Scope diff | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Phạm vi

### Trong phạm vi

- Inventory permission matrix production cuối cùng, thêm V22 để xóa mapping USER nguy hiểm, và test behavior của `ROLE_USER`, ADMIN, MANAGER, STAFF.
- Cập nhật tài liệu RBAC/API/security để đánh dấu route tổng quát chỉ dành cho operator.

### Exact file allowlist

- New: `src/main/resources/db/migration/V22__revoke_legacy_customer_permissions.sql`.
- Existing: `src/test/java/vn/conganh/commercial/SystemSecurityIntegrationTest.java`, `src/test/java/vn/conganh/commercial/CustomerSelfScopeIntegrationTest.java`, `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `src/main/java/vn/conganh/commercial/feature/permission/CONTEXT.md`, và `src/main/java/vn/conganh/commercial/feature/role/CONTEXT.md`.
- Plan mirror đã reconcile: `plans/002-revoke-cross-account-role-user-access.md` và `plans/vi/002-revoke-cross-account-role-user-access.vi.md`.
- Không controller/service/frontend file nào được đổi trong contract PR này; update cả hai plan mirror trước khi expand scope.

### Ngoài phạm vi

- Xóa controller, đổi response DTO, frontend code, ownership implementation đã giao bởi BE-001, hoặc redesign role không liên quan.

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
git switch -c fix/cross-account-role-user-access
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/không fast-forward/diverge, branch đã tồn tại, thiếu bằng chứng BE-001/FE-001, hoặc V22 đã có. Không stash/reset hay rename migration đã merge. Không commit/push trước khi mọi gate pass; sau đó publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Reconcile permission matrix sau BE-001

Liệt kê mọi method/path được gán cho production `ROLE_USER`. Phân loại public, ownership-bound `/me`, hoặc operator-only generic. Cross-check frontend để tìm customer call tổng quát còn lại.

**Verify**: `rg -n "ROLE_USER|/me|/api/v1/(carts|orders|user-addresses|reviews|wishlists)" src/main/resources/db/migration docs/API_SPEC.md` → output đã review support mọi keep/revoke row bằng exact method/path.

### 2. Thêm V22 contract migration

Tạo `V22__revoke_legacy_customer_permissions.sql` với delete có mục tiêu, idempotent khỏi `permission_role` cho USER. Giữ permission `/me` mới và mọi mapping role không phải USER.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest test` → exit 0; fresh/upgrade migration check pass và V22 chỉ đổi targeted USER mapping.

### 3. Test production role

Thêm/mở rộng `SystemSecurityIntegrationTest` và `CustomerSelfScopeIntegrationTest` bằng role seed thật. Assert USER nhận `403` trên path cart/order/address/review/wishlist cross-account tổng quát, trong khi flow `/me` hoạt động. Assert intended ADMIN/MANAGER/STAFF access còn nguyên.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest,CustomerSelfScopeIntegrationTest test` → exit 0; USER generic request là 403, self flow pass, unauthenticated là 401, operator role giữ access.

### 4. Cập nhật tài liệu security

Đồng bộ đúng các document trong allowlist: `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `feature/permission/CONTEXT.md`, và `feature/role/CONTEXT.md`. Ghi customer client không được quay lại generic ID route; race-condition/security note và OpenSpec record khác nằm ngoài scope trừ khi plan được reconcile trước.

**Verify**: `rg -n "401|403|404|BE-001.*FE-001.*BE-002|operator-only" docs/API_SPEC.md docs/PROJECT-STATUS.md src/main/java/vn/conganh/commercial/feature/permission/CONTEXT.md src/main/java/vn/conganh/commercial/feature/role/CONTEXT.md` → có đủ distinction và rollout note bắt buộc.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest,CustomerSelfScopeIntegrationTest test
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

Thực hiện full-stack smoke bằng hai customer account cùng từng operator role trước merge.

## Tiêu chí hoàn thành

- V22 revoke mọi generic customer grant đã review khỏi production `ROLE_USER`, không chạm `/me` hay operator grant.
- Real-role integration test chứng minh cross-account denial và self-service success.
- FE-001 không còn customer dependency vào path bị revoke.
- Full verification pass và docs phản ánh contracted matrix.

## STOP conditions

- BE-001 hoặc FE-001 chưa merge và chưa deploy/test cùng nhau.
- Customer frontend call vẫn target permission sắp revoke.
- V22 đã tồn tại, migration history khác, hoặc mapping permission chính xác còn mơ hồ.
- Thay đổi đòi xóa controller legacy hoặc đổi operator permission ngoài matrix đã review.

## Ghi chú maintenance

Re-audit production role đã seed mỗi khi permission thay đổi. Sau khi merge, không bao giờ sửa V22; thêm migration mới. Reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
