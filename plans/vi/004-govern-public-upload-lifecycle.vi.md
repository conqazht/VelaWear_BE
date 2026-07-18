# BE-004 — Quản trị public upload lifecycle

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết trước khi sửa. Ưu tiên concrete owner-scoped avatar lifecycle bên dưới. Làm rõ xử lý failure giữa filesystem và database, không bao giờ xóa external URL, và không thêm S3/Cloudinary/Cloudflare. V23 là permission contract; nó không tạo upload-governance table.

## Trạng thái

- State: `TODO`
- Risk: High
- Category: Security / Abuse prevention / File lifecycle
- Priority / effort / wave: P1 / L / 2
- Planned against: `b4658c2` ngày 2026-07-17
- Branch: `feature/upload-governance`
- Dependency: BE-002 đã merge trong PR #23; BE-003 đã merge trong PR #24 tại `b4658c2`
- Reserved migration: `V23__scope_customer_file_upload_permissions.sql`

## Vì sao

Authenticated user có thể gọi generic public upload endpoint và tự chọn folder. Server ghi file trước khi owning entity được update, nên file không dùng không có durable owner, quota, hay cleanup lifecycle. Với product need hiện tại, self-avatar endpoint có thể bind identity, serialize replacement, và coordinate rollback/commit cleanup mà không đưa cloud storage vào.

## Hiện trạng và bằng chứng

- `src/main/resources/db/migration/V4__seed_upload_file_permission.sql:8-13` gán `UPLOAD_FILE` cho USER.
- `src/main/resources/application.yaml:195-206` cấu hình public avatar/product directory.
- `src/main/java/vn/conganh/commercial/feature/file/FileController.java:25-30`, `upload(...)`, nhận `folder` do caller chọn.
- `src/main/java/vn/conganh/commercial/feature/file/FileServiceImpl.java:31-51` ghi ngay; `174-200` thao tác local filesystem trực tiếp mà không có owning-resource lifecycle.
- `src/main/java/vn/conganh/commercial/feature/user/UserRepository.java:23-25` có email lookup nhưng chưa có locked self lookup.
- `src/main/java/vn/conganh/commercial/feature/review/ReviewImageStorage.java:176-187` cho thấy UUID temp name và atomic move; `src/main/java/vn/conganh/commercial/feature/review/ReviewServiceImpl.java:233-244` cho thấy transactional cleanup pattern.
- `docs/decisions/file-upload-strategy.md:140-149` cố ý tách upload khỏi entity mutation; quyết định đó hiện để public upload không owner và phải được amend.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat b4658c2..HEAD -- src/main/java/vn/conganh/commercial/feature/file src/main/java/vn/conganh/commercial/feature/user src/main/java/vn/conganh/commercial/feature/review src/main/resources src/test docs
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=FileControllerTest,FileServiceImplTest,AvatarUploadIntegrationTest,AvatarReconciliationJobTest,SystemSecurityIntegrationTest test` | Exit 0 |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Scope | `git status --short` | Chỉ exact allowlist file xuất hiện; không table/cloud/product/review change |
| Scope diff | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Thiết kế đích

- Thêm chính xác `PUT /api/v1/files/avatar`; multipart body chỉ có file, user lấy từ JWT, và không bao giờ nhận folder/user ID. Đây là customer avatar mutation duy nhất; `/users/me` từ BE-001 không đổi được `avatar`.
- Lấy `PESSIMISTIC_WRITE` lock trên user row, lưu new local file an toàn, update `User.avatar`, xóa new file khi transaction rollback, và chỉ xóa prior local managed avatar sau commit.
- Managed-delete predicate chỉ giới hạn trong configured local avatar namespace. Không bao giờ xóa product/review path hay external avatar `http://`/`https://`. Reuse `UploadProperties.maxSizeBytes` (hiện là 5 MiB / `5242880`) và `allowedExtensions` (hiện là `jpg`, `jpeg`, `png`, `webp`), rồi yêu cầu signature/MIME validation khớp allowed type. Reject traversal, unsupported/mismatched content và file quá lớn; không duplicate limit thành avatar-only constant.
- Reuse Redis limiter với policy `avatar-upload`: user `5/1h`, IP `30/1h`, global `300/1m`, response code `AUTH_RATE_LIMITED`. Enforce cả ba bucket trước user lock, file storage hoặc DB mutation. Redis/unconfigured-policy failure fail-closed đúng như auth limiter hiện có; mọi 429 có retry contract đã thiết lập, và cả 429 lẫn fail-closed 503 không được tạo file hay đổi `User.avatar`.
- Chạy reconciliation mỗi 6 giờ với grace 24 giờ: chỉ scan managed avatar namespace, batch current `User.avatar` reference, và recheck candidate ngay trước delete. Việc này repair orphan do crash hoặc after-commit cleanup; failure retry ở run sau và emit sanitized event cùng `security.avatar.cleanup` với finite tag `outcome`/`reason`.
- Revoke generic `UPLOAD_FILE` khỏi `ROLE_USER`; chỉ giữ generic product upload cho operator role được authorize.
- Tạo V23 chỉ làm permission contract: seed precise self-avatar permission cho USER và revoke generic `UPLOAD_FILE` khỏi USER. Thiết kế preferred một-current-avatar không tạo governance table.

## Phạm vi

### Trong phạm vi

- Self-avatar controller/service/storage flow, user-row lock, local commit/rollback cleanup, permission contraction, test, và upload/ADR/API documentation.

### Exact file allowlist

- Existing: `src/main/java/vn/conganh/commercial/feature/file/FileController.java`, `src/main/java/vn/conganh/commercial/feature/file/FileService.java`, `src/main/java/vn/conganh/commercial/feature/file/FileServiceImpl.java`, `src/main/java/vn/conganh/commercial/feature/file/CONTEXT.md`, `src/main/java/vn/conganh/commercial/feature/user/UserRepository.java`, `src/main/java/vn/conganh/commercial/security/ratelimit/AuthRateLimitService.java`, `src/main/java/vn/conganh/commercial/security/ratelimit/RateLimitProperties.java`, `src/main/java/vn/conganh/commercial/security/ClientIpResolver.java`, `src/main/java/vn/conganh/commercial/security/monitoring/SecurityMetrics.java`, `src/main/java/vn/conganh/commercial/security/monitoring/SecurityEventLogger.java`, `src/main/resources/application.yaml`, `src/test/java/vn/conganh/commercial/feature/file/FileControllerTest.java`, `src/test/java/vn/conganh/commercial/feature/file/FileServiceImplTest.java`, `src/test/java/vn/conganh/commercial/SystemSecurityIntegrationTest.java`, `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `docs/decisions/file-upload-strategy.md`, `plans/004-govern-public-upload-lifecycle.md`, `plans/vi/004-govern-public-upload-lifecycle.vi.md`.
- New: `src/main/java/vn/conganh/commercial/feature/file/AvatarUploadService.java`, `src/main/java/vn/conganh/commercial/feature/file/AvatarReconciliationJob.java`, `src/main/resources/db/migration/V23__scope_customer_file_upload_permissions.sql`, `src/test/java/vn/conganh/commercial/feature/file/AvatarUploadIntegrationTest.java`, `src/test/java/vn/conganh/commercial/feature/file/AvatarReconciliationJobTest.java`.
- Không table, cloud adapter, product/review storage file, hay source khác được đổi nếu chưa reconcile plan.

### Ngoài phạm vi

- Cloud/object storage, CDN/WAF, image transformation, generic media library, redesign review image, redesign product-admin, malware SaaS, hoặc distributed file transaction.

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
git switch -c feature/upload-governance
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, dependency BE-002 chưa đạt, hoặc V23 đã có. Không stash/reset. Nếu V23 đã merge, không rename hay reuse. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, reconcile in-scope drift bằng cách update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Xác nhận lifecycle và persistence need

Xác nhận v1 chỉ cần immediate self-avatar replacement, không cần preview upload tồn tại không có user reference. Ghi managed-local-path predicate và shared `UploadProperties` policy: 5 MiB (`5242880`), `jpg`/`jpeg`/`png`/`webp`, cùng signature/MIME agreement. Xác nhận V23 còn trống; nếu live upload property đã đổi thì reconcile plan thay vì hardcode stale value.

**Verify**: `rg -n "PUT /api/v1/files/avatar|avatar-upload|5242880|jpg.*jpeg.*png.*webp|signature|MIME|6 giờ|24 giờ|V23__scope_customer_file_upload_permissions" plans/vi/004-govern-public-upload-lifecycle.vi.md docs/decisions/file-upload-strategy.md` → record exact endpoint, shared size/type policy, reconciliation và permission-only migration.

### 2. Xây safe local avatar storage primitive

Extract/reuse filename generation, normalized path containment, temp write, atomic move khi hỗ trợ, `UploadProperties.maxSizeBytes`/`allowedExtensions`, signature/MIME validation và idempotent delete. Trả managed storage reference thay vì nhận folder từ client.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=FileServiceImplTest test` → exit 0; validation/path containment/atomic-write/delete test pass và managed delete reject product, review, external path.

### 3. Implement ownership-bound replacement

Thêm locked user lookup và transactional service method. Register synchronization để rollback xóa new file và after-commit cleanup chỉ xóa former managed local avatar. Giữ old DB value khi storage failure và new DB value nếu chỉ old-file cleanup fail; log cleanup failure không có PII.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AvatarUploadIntegrationTest test` → exit 0; concurrent replacement, rollback, old local cleanup, external/product/review preservation, `/users/me` avatar bypass denial pass.

### 4. Enforce Redis limiter hiện có

Thêm `avatar-upload` dưới `app.security.rate-limit.policies` với user `5/1h`, IP `30/1h`, global `300/1m`. Resolve trusted client IP bằng `ClientIpResolver`, HMAC subject qua `AuthRateLimitService`, và evaluate mọi bucket trước storage, user lock hoặc DB mutation. Trả `AUTH_RATE_LIMITED`, giữ fail-closed Redis behavior.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AvatarUploadIntegrationTest test` → exit 0; mỗi dimension reject đúng boundary với 429/retry metadata, Redis failure trả fail-closed 503, và cả hai path chứng minh zero new managed file, zero storage invocation, `User.avatar` không đổi.

### 5. Reconcile crash và cleanup orphan

Schedule `AvatarReconciliationJob` mỗi 6 giờ. Ignore file trẻ hơn 24 giờ; batch current DB reference, chỉ scan managed avatar, recheck từng candidate trước delete, và để failed delete cho run sau. Emit `security.avatar.cleanup` với finite tag `outcome`/`reason` và sanitized event log.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AvatarReconciliationJobTest test` → exit 0; crash orphan, grace period, cleanup retry, concurrent re-reference, referenced-file preservation, namespace isolation pass.

### 6. Contract generic USER upload access

Tạo `src/main/resources/db/migration/V23__scope_customer_file_upload_permissions.sql`. Idempotently seed self-avatar method/path permission cho `ROLE_USER`, xóa generic `UPLOAD_FILE` mapping của role này, và giữ operator grant. Không tạo upload-governance table hay sửa migration cũ.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest,AvatarUploadIntegrationTest test` → exit 0; USER chỉ gọi exact self-avatar route và operator generic product upload còn chạy.

### 7. Cập nhật tài liệu API và architecture

Amend đúng các document trong allowlist: `docs/decisions/file-upload-strategy.md`, `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `feature/file/CONTEXT.md`, và comment/configuration trong `application.yaml` khi cần. Giải thích local-storage single-instance limitation, threat/failure behavior, và cleanup troubleshooting; không đổi documentation file khác nếu chưa reconcile plan.

**Verify**: `rg -n "files/avatar|avatar-upload|24 giờ|6 giờ|managed avatar|AUTH_RATE_LIMITED|V23__scope" docs/API_SPEC.md docs/PROJECT-STATUS.md docs/decisions/file-upload-strategy.md src/main/java/vn/conganh/commercial/feature/file/CONTEXT.md` → docs có exact route, limiter, reconciliation, namespace, migration contract và không cloud claim.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=FileControllerTest,FileServiceImplTest,AvatarUploadIntegrationTest,AvatarReconciliationJobTest,SystemSecurityIntegrationTest test
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

Manual smoke valid avatar replacement, invalid content, concurrent replacement, rollback, external OAuth avatar, USER generic denial, và operator upload.

## Tiêu chí hoàn thành

- Customer avatar upload self-scoped, serialized, validated, và có rollback/after-commit cleanup rõ ràng.
- Redis limit và reconciliation 6 giờ/grace 24 giờ đóng gap churn và crash/cleanup orphan mà không có table.
- `ROLE_USER` không thể chọn arbitrary public upload folder; operator behavior còn nguyên.
- External URL không bao giờ bị xóa và local path containment có test.
- V23 chỉ scope permission; không thêm upload-governance table hay cloud dependency.
- Focused/full verification pass; ADR/API/config docs khớp implementation.

## STOP conditions

- Product cần durable preview/unreferenced upload; việc đó cần ownership/TTL/quota design và plan riêng.
- V23 đã tồn tại hoặc migration history diverge.
- Design xóa external URL, filesystem delete trước DB commit, nhận client folder/user ID, hoặc yêu cầu cloud migration.
- Managed deletion có thể ra ngoài configured avatar namespace, limiter fail-open, hoặc reconciliation delete không final DB-reference recheck.
- User-row lock ordering conflict với transaction đã thiết lập; re-plan ordering trước.

## Ghi chú maintenance

Giữ storage reference opaque và managed-path check tập trung. Xem after-commit cleanup failure là observable repair work. Khi migration merge, không bao giờ sửa. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt giống cấu trúc.
