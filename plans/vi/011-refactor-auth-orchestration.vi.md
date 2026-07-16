# BE-011 — Refactor authentication orchestration

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Đây là behavior-preserving extraction sau characterization. `AuthTokenCodec` chỉ encode access token, và encode/decode refresh token kèm validate refresh type, JTI và `securityVersion`; không decode access token hoặc move/duplicate resource-server access decoder của Spring Security. Giữ DB audit, Redis refresh-session rotation/CAS, cookie, transaction, account-version comparison và public contract ở layer hiện có. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan.

## Trạng thái

- State: `TODO`
- Priority / effort / wave: P3 / L / 4
- Risk: High — extraction error có thể làm yếu token-type, replay hoặc session-revocation guarantee
- Category: Security refactor / authentication
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `refactor/auth-orchestration`
- Dependencies: BE-001, BE-002, BE-005, BE-010
- Migration: không có

## Vì sao

`AuthServiceImpl` coordinate credential/OAuth flow, user, audit, JWT claim, refresh session, rotation, và sensitive-change revocation. Token codec logic cohesive và testable, nhưng move persistence/session orchestration sẽ rủi ro atomic Redis và DB security invariant từ auth hardening.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/feature/auth/AuthServiceImpl.java:133-309` implement public auth flow; `323-449` tạo/parse access và refresh token; `452-524` xử lý sensitive account change.
- `src/main/java/vn/conganh/commercial/feature/auth/CONTEXT.md:42-56` yêu cầu Redis refresh rotation/CAS giữ atomic.
- `src/main/java/vn/conganh/commercial/feature/refreshtoken/RefreshTokenSessionService.java` và implementation sở hữu Redis session lifecycle; token claim và session record có security-version/JTI invariant.
- `src/main/java/vn/conganh/commercial/config/JwtConfig.java:42-45`, `jwtDecoder()`, là resource-server access-token decoder duy nhất; `refreshJwtDecoder()` tại `56-59` tách riêng và là decoder duy nhất codec mới được dùng.
- BE-010 cung cấp characterization test cho claim shape, rotation, revocation, cookie/controller result, stable error.

## Lệnh

| Gate | Lệnh chính xác | Kết quả mong đợi |
|---|---|---|
| Drift | `git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/feature/auth src/main/java/vn/conganh/commercial/config/JwtConfig.java src/main/java/vn/conganh/commercial/security src/test/java/vn/conganh/commercial/feature/auth src/test/java/vn/conganh/commercial/feature/refreshtoken/RefreshTokenSessionServiceIntegrationTest.java docs` | Review mọi auth-hardening drift và refresh plan trước extraction. |
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthTokenCodecTest,AuthServiceImplTest,AuthControllerTest,AuthRefreshConcurrencyIntegrationTest,RefreshTokenSessionServiceIntegrationTest,RedisSecurityAndCleanupIntegrationTest test` | Exit 0; codec, orchestration, CAS và revocation pass. |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0. |
| Hygiene | `git diff --check` | Exit 0. |
| Worktree | `git status --short` | Chỉ xuất hiện file trong allowlist. |
| Scope verification | `git diff --name-only -- .` | Mọi path thuộc allowlist; có thay đổi `JwtConfig.java` thì STOP. |

## Phạm vi

### Trong phạm vi

- Introduce `AuthTokenCodec` cho access-token encode-only và refresh-token encode/decode kèm required type/JTI/`securityVersion` claim validation; inject vào auth orchestration; giữ behavior và update test/docs.

### Ngoài phạm vi

- Access-token decode/validation, sửa hoặc duplicate resource-server `JwtDecoder`, move Redis CAS/session hoặc DB audit logic, đổi token claim/TTL/key/cookie/code, thay JWT library, API change, MapStruct, hoặc auth feature mới.

### Exact file allowlist

Existing files được phép sửa:

- `src/main/java/vn/conganh/commercial/feature/auth/AuthServiceImpl.java`
- `src/main/java/vn/conganh/commercial/feature/auth/CONTEXT.md`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthServiceImplTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthControllerTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthRefreshConcurrencyIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/auth/RedisSecurityAndCleanupIntegrationTest.java`
- `src/test/java/vn/conganh/commercial/feature/refreshtoken/RefreshTokenSessionServiceIntegrationTest.java`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT-STATUS.md`

New files được phép tạo:

- `src/main/java/vn/conganh/commercial/feature/auth/AuthTokenCodec.java`
- `src/test/java/vn/conganh/commercial/feature/auth/AuthTokenCodecTest.java`

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
git switch -c refactor/auth-orchestration
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc dependency chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

## Các bước implementation

### 1. Freeze extraction boundary

Dùng test BE-010 để freeze hai asymmetric contract: access encode-only; refresh encode/decode với subject, refresh token type, nonblank JTI, numeric `securityVersion`, issued/expiry time và stable validation failure. Để resource-server access decoding, account-version lookup/comparison, audit và session persistence ngoài codec.

**Verify**: chạy riêng hai block. Negative search exit 0 và không output, chứng minh codec không có stateful/HTTP dependency hay access-decoder path; positive search phải tìm thấy qualified refresh decoder.

```powershell
rg -n 'Repository|Redis|Cookie|HttpServlet|@Transactional|@Qualifier\("jwtDecoder"\)|accessJwtDecoder|decodeAccess' src/main/java/vn/conganh/commercial/feature/auth/AuthTokenCodec.java
if ($LASTEXITCODE -eq 0) { exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
exit 0
```

```powershell
rg -n '@Qualifier\("refreshJwtDecoder"\)' src/main/java/vn/conganh/commercial/feature/auth/AuthTokenCodec.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

### 2. Implement và unit-test `AuthTokenCodec`

Move access construction/encoding và refresh construction/encoding/decoding. Refresh decode phải reject wrong/missing type, blank/missing JTI, missing/malformed `securityVersion` bằng stable error hiện có. Inject decoder rõ ràng bằng `@Qualifier("refreshJwtDecoder")`; không thêm access decode hoặc copy resource-server validation.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthTokenCodecTest test` → exit 0; access encoding và refresh encode/decode/type/JTI/`securityVersion` validation khớp fixture, không log raw token.

### 3. Delegate từ auth orchestration

Thay private JWT implementation trong `AuthServiceImpl` bằng codec call. Giữ exact order: validate request/user, create/update DB audit như established, create/rotate Redis session atomically, issue response, xử lý rollback/failure nhất quán.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthServiceImplTest,AuthControllerTest test` → exit 0; public contract và security-relevant collaborator order không đổi.

### 4. Verify refresh và revocation race

Chạy refresh CAS, replay, concurrent rotation, logout, session-version mismatch, password/email reset, Redis cleanup failure test với refactored code.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthRefreshConcurrencyIntegrationTest,RefreshTokenSessionServiceIntegrationTest,RedisSecurityAndCleanupIntegrationTest test` → exit 0; một refresh thắng và revoked/session-version-mismatched token vẫn bị reject.

### 5. Update documentation

Update auth `CONTEXT.md`, `docs/ARCHITECTURE.md`, và `docs/PROJECT-STATUS.md`; chỉ đặt security-boundary note trong đúng các file allowlisted này để phân trách nhiệm codec và session/orchestration.

**Verify**: `rg -n "AuthTokenCodec|resource-server|refresh|Redis|CAS" src/main/java/vn/conganh/commercial/feature/auth/CONTEXT.md docs/ARCHITECTURE.md docs/PROJECT-STATUS.md` → docs nêu rõ access encode-only, refresh decode ownership và resource-server/session boundary riêng.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=AuthTokenCodecTest,AuthServiceImplTest,AuthControllerTest,AuthRefreshConcurrencyIntegrationTest,RefreshTokenSessionServiceIntegrationTest,RedisSecurityAndCleanupIntegrationTest test
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

- Token codec responsibility isolated, không HTTP/DB/Redis dependency.
- Claim, TTL, validation, error, audit, CAS/session, revocation, cookie behavior-identical.
- Concurrent refresh và sensitive-change security test pass.
- Full verification/documentation gate pass, không public API change.

## STOP conditions

- Extraction đổi claim, algorithm, TTL, session serialization/key, CAS order, transaction, cookie, hoặc stable error.
- Codec cần repository/Redis/HTTP state, hoặc orchestration move vào codec.
- Codec decode access token, đổi `JwtConfig.jwtDecoder()`, hoặc duplicate resource-server access validation.
- Characterization/security test lộ defect hiện có; tách fix trước khi tiếp tục refactor.

## Ghi chú maintenance

Giữ token format change explicit và versioned; không bao giờ log token. Session persistence và resource-server access decoding là boundary riêng. Sau dependency merge, refresh live citation/scope/test, `Planned against`, mirror tiếng Việt trước khi code; không execute stale plan. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này.
