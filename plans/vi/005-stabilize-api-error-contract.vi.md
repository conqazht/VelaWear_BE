# BE-005 — Ổn định API error contract

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Giữ established business/OTP error code, loại server exception text khỏi client response, và coordinate frontend behavior đang phụ thuộc message trước merge. Không log request body hay secret khi diagnose malformed input.

## Trạng thái

- State: `TODO`
- Risk: Medium
- Category: API contract / Security
- Priority / effort / wave: P1 / M / 2
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `fix/api-error-contract`
- Dependency: BE-002
- Migration: không có

## Vì sao

Một số framework và security failure hiện trả raw exception message, còn malformed JSON và unsupported method/media có thể rơi vào handler không nhất quán. Client phải branch theo finite documented error code; internal cause chỉ thuộc sanitized server log liên kết bằng request ID.

## Hiện trạng và bằng chứng

- `src/main/java/vn/conganh/commercial/exception/GlobalExceptionHandler.java:131-141` trả root-cause hoặc text `IllegalArgumentException`; `144-148` xử lý error chưa classify thành generic 500.
- `src/main/java/vn/conganh/commercial/config/SecurityConfig.java:201-232` ghi authentication/access-denied exception message vào API response.
- `src/main/java/vn/conganh/commercial/dto/ApiResponse.java:9-35` đã hỗ trợ stable field `code`.
- OTP/auth hardening đã có business code như `OTP_RATE_LIMITED`, `OTP_PROOF_INVALID_OR_EXPIRED`, `AUTH_RATE_LIMITED`, và `SESSION_REVOKED`; phải giữ nguyên.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat 2be2362..HEAD -- src/main/java/vn/conganh/commercial/exception src/main/java/vn/conganh/commercial/dto/ApiResponse.java src/main/java/vn/conganh/commercial/config/SecurityConfig.java src/main/java/vn/conganh/commercial/feature/auth src/test docs
```

| Gate | Exact command | Expected result |
|---|---|---|
| Focused | `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=GlobalExceptionHandlerTest,ApiErrorContractIntegrationTest,SystemSecurityIntegrationTest,AuthControllerTest test` | Exit 0 |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Scope | `git status --short` | Chỉ exact allowlist handler/security/test/docs xuất hiện |
| Scope diff | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Contract đích

Thêm/document finite framework/security code: `REQUEST_BODY_INVALID`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `INVALID_REQUEST`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, và `INTERNAL_SERVER_ERROR`. Map malformed JSON thành 400, unsupported method 405, unsupported media type 415, unauthenticated 401, forbidden 403, và unexpected server failure 500. Message phải an toàn, chỉ localized nếu architecture hiện tại hỗ trợ, và không dùng làm machine identifier.

## Phạm vi

### Trong phạm vi

- Explicit MVC exception handler, security entry-point/denied-handler response, safe logging, stable-code documentation, và contract test.

### Exact file allowlist

- Existing: `src/main/java/vn/conganh/commercial/exception/GlobalExceptionHandler.java`, `src/main/java/vn/conganh/commercial/config/SecurityConfig.java`, `src/main/java/vn/conganh/commercial/dto/ApiResponse.java`, `src/test/java/vn/conganh/commercial/SystemSecurityIntegrationTest.java`, `src/test/java/vn/conganh/commercial/feature/auth/AuthControllerTest.java`, `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, `src/main/java/vn/conganh/commercial/feature/auth/CONTEXT.md`.
- New: `src/test/java/vn/conganh/commercial/exception/GlobalExceptionHandlerTest.java`, `src/test/java/vn/conganh/commercial/ApiErrorContractIntegrationTest.java`.
- Không response DTO, domain exception, controller, hay frontend file nào được đổi nếu chưa reconcile plan.

### Ngoài phạm vi

- Thay `ApiResponse`, đổi success response, redesign domain exception taxonomy, frontend retry UX, hoặc expose stack trace/debug cause.

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
git switch -c fix/api-error-contract
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc frontend dependency vào raw message chưa coordinate. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, reconcile in-scope drift bằng cách update citation, allowlist, test, `Planned against` SHA, và English mirror trước khi code. Không execute stale plan.

## Các bước implementation

### 1. Inventory và freeze code hiện có

Liệt kê mọi `ApiResponse.code`, status, và consumer. Đánh dấu domain/OTP code được preserve và thêm framework/security matrix trên vào `docs/API_SPEC.md`.

**Verify**: `rg -n "REQUEST_BODY_INVALID|METHOD_NOT_ALLOWED|UNSUPPORTED_MEDIA_TYPE|INVALID_REQUEST|AUTHENTICATION_REQUIRED|ACCESS_DENIED|INTERNAL_SERVER_ERROR" src/main/java docs/API_SPEC.md` → mỗi target code có một documented status/meaning và preserved business code còn hiện diện.

### 2. Thêm explicit framework handler

Handle rõ `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, và `HttpMediaTypeNotSupportedException`. Convert case `IllegalArgumentException` dự đoán được thành `INVALID_REQUEST` mà không trả raw text. Chỉ giữ validation field detail nếu đã an toàn và documented.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=GlobalExceptionHandlerTest test` → exit 0; envelope 400/405/415/500 stable và không parser/root-cause text.

### 3. Ổn định security failure

Update authentication entry point và access-denied handler để emit `AUTHENTICATION_REQUIRED` và `ACCESS_DENIED` trong cùng envelope. Giữ `SESSION_REVOKED` khi JWT/session validation layer cố ý emit.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SystemSecurityIntegrationTest test` → exit 0; unauthenticated/invalid/revoked 401 và authenticated forbidden 403 giữ stable code khác nhau.

### 4. Sanitize unexpected-failure logging

Trả `INTERNAL_SERVER_ERROR` với generic message và không có cause/stack detail trong client envelope. Sanitized server log được giữ exception stack trace cùng correlation context, operation, finite reason để diagnose, nhưng phải loại request body, credential, OTP, proof, token, password, raw email/IP, uploaded content, và unsafe exception-message value.

**Verify**: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApiErrorContractIntegrationTest test` → exit 0; client response không có cause/stack detail, còn sanitized server-log capture giữ correlation/diagnostic stack behavior nhưng không có body, credential, OTP/proof, token, password, raw email/IP, uploaded content, hay unsafe exception-message value.

### 5. Đồng bộ consumer và documentation

Update đúng các contract document trong allowlist: `docs/API_SPEC.md`, `docs/PROJECT-STATUS.md`, và `feature/auth/CONTEXT.md`. Ghi trong PR handoff cho external FE-005 rằng consumer branch theo `code` và `Retry-After`, không theo message text; OpenSpec nằm ngoài PR này trừ khi plan được reconcile trước.

**Verify**: `$unsafe = rg -n "exception\.getMessage\(\)|rootCause|getRootCause" src/main/java/vn/conganh/commercial/exception src/main/java/vn/conganh/commercial/config/SecurityConfig.java docs; if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }; if ($unsafe) { $unsafe; throw "Vẫn còn raw exception/root-cause response path" }` → exit 0 không có match; `rg` exit 1 chính là zero-match result bắt buộc.

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=GlobalExceptionHandlerTest,ApiErrorContractIntegrationTest,SystemSecurityIntegrationTest,AuthControllerTest test
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

Smoke malformed/truncated JSON, sai content type, sai method, validation failure, thiếu auth, forbidden role, revoked session, OTP failure, và unexpected exception.

## Tiêu chí hoàn thành

- Mỗi target framework/security failure có deterministic HTTP status và stable code.
- Raw exception/root-cause text và secret không tới response hay unsafe log.
- Existing business/OTP/session code còn compatible.
- Contract/security test và full verification pass; documentation dùng được cho FE-005.

## STOP conditions

- Frontend flow phụ thuộc raw message và chưa có coordinated migration được approve.
- Handling đề xuất collapse deliberate business code thành generic code.
- Test yêu cầu log request body, credential, token, OTP/proof, hoặc PII.
- Thay đổi cần wholesale response-envelope migration; tách và re-plan.

## Ghi chú maintenance

Xem public error code mới là API surface: document và test, không âm thầm repurpose. Giữ metric/log reason value finite. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
