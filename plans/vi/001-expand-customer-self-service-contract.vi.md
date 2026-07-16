# BE-001 — Mở rộng contract customer self-service

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Đây là execution context tiếng Việt đối chiếu với bản English canonical. Thực thi trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc toàn bộ plan trước khi sửa. Chỉ làm từ `main` sạch và mới nhất, giữ đúng thứ tự rollout additive, và dừng tại mọi STOP condition. Không nới authorization để làm test pass. Chỉ đồng bộ mirror này khi plan canonical thay đổi; phần implementation thuộc source files, không thuộc plan này.

## Trạng thái

- State: `TODO`
- Risk: High
- Category: Security / API contract
- Priority / effort / wave: P1 / L / 1
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `feature/customer-self-service-contract`
- Dependencies: không có.
- Unblocks: external FE-001, sau đó FE-001 phải merge trước BE-002.
- Reserved migration: `V21__seed_customer_self_service_permissions.sql`

## Vì sao

Các trang customer vẫn gọi resource dùng user ID tổng quát. Vì vậy `ROLE_USER` đang giữ permission mà path match không chứng minh resource ownership, tạo rủi ro IDOR/cross-account. Chuỗi expand/migrate/contract an toàn là: thêm API `/me` bind ownership tại đây, migrate frontend trong FE-001, rồi revoke permission legacy trong BE-002.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/security/PermissionAuthorizationManager.java`, `authorize(...)`: authorization parse từng value `method + apiPath` đã lưu rồi gọi `pathMatcher.match(apiPath, requestPath)`. Không có entity ownership check.
- `src/main/resources/db/migration/V3__align_rbac_api_paths.sql:59-68`, `V5__seed_missing_permissions.sql:106-119`, và `V2__seed_rbac.sql:122-137` cấp cho `ROLE_USER` các permission cart, order, address, review và wishlist tổng quát.
- `src/main/java/vn/conganh/commercial/feature/order/OrderController.java`, `getOrdersByUser`, `getOrderById`, và `getOrderStatusHistories` expose `/orders/user/{userId}`, `/orders/{id}`, và `/orders/{id}/status-histories`.
- `src/main/java/vn/conganh/commercial/feature/useraddress/UserAddressController.java` chỉ expose collection tổng quát và CRUD `/{id}`. `src/main/java/vn/conganh/commercial/feature/useraddress/dto/CreateUserAddressRequest.java` nhận `Long userId` từ client.
- `src/main/java/vn/conganh/commercial/feature/user/UserController.java`, `updateUser(...)` expose `PUT /users/{id}` cho thay đổi profile.
- Precedent an toàn đã có: `CartController` có `/me` và `/me/items`; `WishlistController` có `/me`; `ReviewController` có `/me`; `AuthController` có `GET /auth/me`.

Trước implementation, chạy drift check và xem từng hunk thay đổi:

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/order src/main/java/vn/conganh/commercial/feature/user src/main/java/vn/conganh/commercial/feature/useraddress src/main/resources/db/migration src/test docs
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=UserControllerTest,OrderControllerTest,UserAddressControllerTest,CustomerSelfScopeIntegrationTest test` | Exit 0 |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Scope | `git status --short` | Chỉ file trong exact allowlist bên dưới xuất hiện |
| Scope diff | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Contract đích

- `PUT /api/v1/users/me` dùng dedicated self-profile DTO chỉ gồm `fullName`, `birthDate`, và `gender`; lấy user từ JWT đã authenticate. Route này không thể mutate `User.avatar`. Generic operator `PUT /users/{id}` giữ `UpdateUserRequest` hiện có, gồm `avatar`, tới khi BE-004 biến `PUT /api/v1/files/avatar` thành customer avatar mutation duy nhất.
- `GET /api/v1/orders/me`, `GET /api/v1/orders/me/code/{orderCode}`, `GET /api/v1/orders/me/{id}`, và `GET /api/v1/orders/me/{id}/status-histories` chỉ trả dữ liệu của user đang authenticate. Route by-code giữ URL frontend `/profile/orders/[code]` hiện có mà không cần client-side list lookup.
- `GET /api/v1/user-addresses/me`, `GET /api/v1/user-addresses/me/{id}`, `POST /api/v1/user-addresses/me`, `PUT /api/v1/user-addresses/me/{id}`, và `DELETE /api/v1/user-addresses/me/{id}` chỉ thao tác address của user đang authenticate.
- Tạo address self-service dùng request DTO riêng không có field `userId`.
- Identifier cross-account hoặc không thuộc owner trả `404` vô điều kiện cho `GET /orders/me/code/{orderCode}`, `GET /orders/me/{id}`, `GET /orders/me/{id}/status-histories`, và address `GET`/`PUT`/`DELETE /user-addresses/me/{id}`; tránh cả lộ dữ liệu lẫn ownership enumeration.
- Mọi route legacy giữ behavior compatible trong phase expand này.

## Phạm vi

### Trong phạm vi

- Thêm controller, DTO/service method, owner-scoped repository query, production-role test, V21 permission seed và tài liệu API/security đồng bộ cho contract đích.
- Tái sử dụng response envelope và pagination semantics hiện có.

### Exact file allowlist

- Existing user files: `src/main/java/vn/conganh/commercial/feature/user/UserController.java`, `src/main/java/vn/conganh/commercial/feature/user/UserService.java`, `src/main/java/vn/conganh/commercial/feature/user/UserServiceImpl.java`, `src/main/java/vn/conganh/commercial/feature/user/UserRepository.java`, `src/main/java/vn/conganh/commercial/feature/user/dto/UpdateUserRequest.java`.
- Existing order files: `src/main/java/vn/conganh/commercial/feature/order/OrderController.java`, `src/main/java/vn/conganh/commercial/feature/order/OrderService.java`, `src/main/java/vn/conganh/commercial/feature/order/OrderServiceImpl.java`, `src/main/java/vn/conganh/commercial/feature/order/OrderRepository.java`, `src/main/java/vn/conganh/commercial/feature/order/OrderStatusHistoryRepository.java`.
- Existing address files: `src/main/java/vn/conganh/commercial/feature/useraddress/UserAddressController.java`, `src/main/java/vn/conganh/commercial/feature/useraddress/UserAddressService.java`, `src/main/java/vn/conganh/commercial/feature/useraddress/UserAddressServiceImpl.java`, `src/main/java/vn/conganh/commercial/feature/useraddress/UserAddressRepository.java`, `src/main/java/vn/conganh/commercial/feature/useraddress/dto/CreateUserAddressRequest.java`, `src/main/java/vn/conganh/commercial/feature/useraddress/dto/UpdateUserAddressRequest.java`.
- Existing test/docs: `src/test/java/vn/conganh/commercial/AuthenticatedIntegrationTest.java`, `src/test/java/vn/conganh/commercial/SystemSecurityIntegrationTest.java`, `src/test/java/vn/conganh/commercial/feature/user/UserControllerTest.java`, `src/test/java/vn/conganh/commercial/feature/order/OrderControllerTest.java`, `src/test/java/vn/conganh/commercial/feature/useraddress/UserAddressControllerTest.java`, `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `src/main/java/vn/conganh/commercial/feature/user/CONTEXT.md`.
- New: `src/main/java/vn/conganh/commercial/feature/user/dto/UpdateMyProfileRequest.java`, `src/main/resources/db/migration/V21__seed_customer_self_service_permissions.sql`, và `src/test/java/vn/conganh/commercial/CustomerSelfScopeIntegrationTest.java`.
- Không file nào khác được thay đổi nếu chưa update plan này và mirror English trước.

### Ngoài phạm vi

- Revoke permission `ROLE_USER` tổng quát (BE-002), frontend migration (FE-001), avatar-file governance (BE-004), hoặc redesign admin API.
- Rename hoặc xóa endpoint legacy.

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
git switch -c feature/customer-self-service-contract
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng nếu worktree không sạch, pull không fast-forward, `HEAD` khác `origin/main`, branch đã tồn tại, hoặc V21 đã có. Không stash, reset, hay rename migration đã merge. Không commit/push trước khi mọi gate pass; sau đó commit, push và mở PR theo operator workflow.

Nếu drift chạm in-scope path, update live citation, allowlist, test name, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Chốt contract API self-service

Thêm các route đích vào `UserController`, `OrderController`, và `UserAddressController`. Tạo DTO self-create address không có `userId`; giữ nguyên DTO và endpoint tổng quát.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=UserControllerTest,OrderControllerTest,UserAddressControllerTest test` → exit 0; route/request/envelope test pass, gồm test submitted `avatar` không đổi `User.avatar`.

### 2. Bind mọi operation vào account đã authenticate

Resolve user một lần từ JWT subject/email qua service/repository path đã có. Thêm read/write qualify bằng owner, như order/address ID cộng owner ID; không load chỉ bằng ID rồi authorize ở controller. Giữ transaction boundary trong service và default-address invariant.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CustomerSelfScopeIntegrationTest test` → exit 0; self operation thành công; account thứ hai nhận `404` cho foreign order ID, foreign order code, status-history của order đó, và address GET/PUT/DELETE theo ID.

### 3. Seed permission additive trong V21

Tạo `src/main/resources/db/migration/V21__seed_customer_self_service_permissions.sql`. Seed permission method/path chính xác cho route `/me` mới và gán idempotently cho `ADMIN`, `MANAGER`, `STAFF`, `USER`, theo convention của self-cart/wishlist hiện tại. Không xóa permission tổng quát tại đây.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest test` → exit 0; V21 migrate và real `ROLE_USER` nhận đúng additive self permission của phase này.

### 4. Thêm production-role ownership test

Tạo `CustomerSelfScopeIntegrationTest` dùng `ROLE_USER` thật, hai user, order/address riêng, và authenticated request. Ownership matrix phải cover foreign order ID, foreign order code, status history của foreign order ID, và foreign address GET/PUT/DELETE; tất cả trả `404`. Mở rộng `OrderControllerTest`, `UserAddressControllerTest`, và user test liên quan. Không chỉ dựa vào `TEST_ROLE` từ `TestDataFactory`.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=CustomerSelfScopeIntegrationTest,SystemSecurityIntegrationTest test` → exit 0; two-user isolation và temporary legacy compatibility cùng pass.

### 5. Đồng bộ tài liệu contract

Cập nhật đúng các contract document trong allowlist: `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, và `src/main/java/vn/conganh/commercial/feature/user/CONTEXT.md`. Ghi rõ thứ tự bắt buộc BE-001 → FE-001 → BE-002; security/race-condition note và OpenSpec nằm ngoài PR này trừ khi plan được reconcile trước.

**Verify**: `rg -n "users/me|orders/me|user-addresses/me|UpdateMyProfileRequest|BE-001.*FE-001.*BE-002" docs src/main/java/vn/conganh/commercial/feature/user/CONTEXT.md` → match document exact route, self DTO không avatar, và rollout order.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=OrderControllerTest,UserAddressControllerTest,CustomerSelfScopeIntegrationTest test
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

Smoke-test thêm profile update, paginated order list/detail/history, address CRUD/default selection, unauthenticated `401`, foreign order ID/code/history và address ID not-found response, cùng admin legacy access.

## Tiêu chí hoàn thành

- Mọi route `/me` đích đã implement, document, và ownership-bound bên dưới controller layer.
- Request self-service không thể chọn `userId`; foreign ID không lộ customer data.
- `PUT /users/me` không thể mutate `User.avatar`; generic operator DTO còn compatible.
- V21 additive/idempotent và permission tổng quát còn giữ trong migration window.
- Focused test và full backend verification pass.
- Implementation PR ghi FE-001 phải merge tiếp và BE-002 phải chờ.

## STOP conditions

- `V21__seed_customer_self_service_permissions.sql` đã tồn tại hoặc migration history diverge.
- Frontend không thể adopt contract đích, hoặc quyết định route/DTO khác FE-001 mà chưa coordinate rõ ràng.
- Ownership chỉ có thể enforce trong controller sau unscoped entity load.
- Body self-service đề xuất vẫn nhận `userId`, hoặc implementation cần revoke legacy access trong PR này.
- Self-profile DTO nhận `avatar` hoặc test có thể mutate `User.avatar` qua `/users/me`.

## Ghi chú maintenance

Khi source path hoặc symbol drift, chạy lại drift command và cập nhật plan trước execution. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ `plans/vi/001-expand-customer-self-service-contract.vi.md` giống cấu trúc bản canonical và giữ V21 vĩnh viễn sau khi merge.
