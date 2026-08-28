# Hardening OTP/Auth v1 — 5 hạng mục, chưa dùng Cloudflare

## Tóm tắt

- Phạm vi gồm: proof token + Redis atomic, limiter đa chiều cho Auth/OTP, trusted client IP, thu hồi toàn bộ session và monitoring.
- Chỉ dùng Redis, Resend và Spring Boot Actuator/Micrometer đã có. Không thêm Cloudflare, CAPTCHA, Prometheus/Grafana hay SaaS mới.
- Backend và frontend thay contract cùng lúc; khi phát hành, token/OTP cũ bị vô hiệu và người dùng phải đăng nhập lại một lần.
- Trước khi sửa, tại cả `commercial` và `commercial-fe`:
  1. `git fetch origin`
  2. `git switch main`
  3. `git pull --ff-only origin main`
  4. xác nhận worktree sạch
  5. `git switch -c feature/otp-security-hardening`
- Nếu main không fast-forward, worktree bẩn hoặc nhánh đã tồn tại thì dừng; không tự stash/reset, commit hay push.

## API và luồng mới

- `POST /api/v1/auth/otp/request`
  - Request giữ `{email, purpose}`.
  - Response: `{challengeId, expiresInSeconds: 300, cooldownSeconds: 60}`.
  - `CHANGE_EMAIL` bắt buộc JWT và challenge được bind với `userId`.
  - Forgot-password với email không tồn tại vẫn trả cùng status/schema bằng decoy challenge và không gửi email.

- `POST /api/v1/auth/otp/verify`
  - Request đổi thành `{challengeId, code}`.
  - Response: `{proofToken, expiresInSeconds: 300}`.
  - Không nhận lại email/purpose để tránh tráo mục đích.

- `POST /auth/register`, `POST /auth/forgot-password/reset`, `PUT /auth/me/email` thêm `otpProofToken`.
  - Backend tự xác định purpose dựa trên endpoint.
  - Proof bind lần lượt với email đăng ký, email reset, hoặc `userId + newEmail`.
  - Proof chỉ dùng một lần và được consume atomically ngay trước thay đổi DB.

- Reset/đổi mật khẩu và đổi email trả:
  - `{allSessionsRevoked: true, reauthenticationRequired: true}`.
  - Backend xóa refresh-token cookie; frontend xóa access token/cache và chuyển về trang đăng nhập.

- Error code ổn định:
  - `OTP_INVALID_OR_EXPIRED`
  - `OTP_ATTEMPTS_EXHAUSTED`
  - `OTP_RATE_LIMITED`
  - `OTP_PROOF_INVALID_OR_EXPIRED`
  - `AUTH_RATE_LIMITED`
  - `SESSION_REVOKED`
  - `OTP_SERVICE_UNAVAILABLE`
  - `OTP_DELIVERY_UNAVAILABLE`
  - Mọi `429` có `Retry-After` và `data.retryAfterSeconds`, nhưng không tiết lộ bucket hoặc số lần thử còn lại.

## Triển khai 5 lớp bảo vệ

### 1. Proof token và Redis atomic

- Sinh `challengeId` và proof token bằng 32 byte `SecureRandom`, Base64URL.
- OTP vẫn 6 số nhưng Redis chỉ lưu HMAC-SHA256; proof, email và IP cũng được HMAC với domain riêng.
- Thêm `SECURITY_HMAC_SECRET` tối thiểu 32 byte, bắt buộc trong production; không tái sử dụng JWT secret.
- Dùng prefix `auth:otp:v2:*`; khóa cũ bị bỏ qua và tự hết TTL.
- Lua script thực hiện atomically:
  - reserve cooldown/quota trước khi gửi;
  - publish challenge sau khi Resend chấp nhận;
  - tăng failed-attempt và khóa sau 5 lần;
  - verify OTP, xóa challenge và phát proof trong một thao tác;
  - consume proof đúng một lần.
- Resend thành công sẽ thay challenge cũ; resend không reset failed-attempt.
- Gửi email thất bại vẫn tính cooldown/quota nhưng giữ challenge cũ, trả `503`.
- Redis lỗi thì fail-closed `503`.
- Validate các lỗi nghiệp vụ dự đoán được trước khi consume proof. Nếu DB lỗi hiếm gặp sau consume, proof vẫn bị đốt và người dùng phải xin OTP mới; không có transaction chung tuyệt đối giữa Redis và PostgreSQL.

### 2. Limiter đa chiều cho Auth/OTP

Dùng Redis sliding-window và Lua atomic. Filter xử lý global/IP trước khi parse body; service bổ sung email, account, user và session sau khi đã normalize.

| Nhóm | Dimension và ngưỡng mặc định |
|---|---|
| OTP request | email+purpose: `1/60s`, `5/15m`, `10/24h`; email tổng: `15/24h`; IP+email: `3/15m`; IP: `20/10m`, `100/24h`; global: `300/min` |
| OTP verify | tối đa 5 mã sai trên cùng scope, khóa 10 phút kể cả đã resend; IP: `30/10m`, `100/h`; global: `600/min` |
| Login | IP+account failure: `5/15m`; account failure: `10/15m`; IP: `30/5m`, `100/h`; global: `600/min`; đăng nhập thành công xóa failure counter |
| Register/reset | IP: `20/h`; global: `300/min`; proof vẫn là hàng rào chính |
| Change email/password | user: `5/h`; IP: `30/h`; global: `300/min` |
| Refresh | refresh JTI: `30/min`; IP: `120/min`; global: `1000/min` |
| OAuth exchange | IP: `30/10m`; global: `300/min` |
| `/auth/me`, logout | user/session: `120/min`; IP: `300/min` |

- Tất cả ngưỡng nằm trong `app.security.rate-limit`, không hardcode.
- Không tạo browser fingerprint hay device cookie trong v1; chúng dễ bị giả và có vấn đề riêng tư. Session dimension dùng refresh-token JTI.
- IP chỉ là dimension phụ để giảm false-positive với mạng NAT.
- Limiter này chống abuse/brute-force ở tầng ứng dụng, không được coi là chống DDoS volumetric. OWASP cũng yêu cầu policy được tinh chỉnh theo từng endpoint. [OWASP API4](https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/)

### 3. Trusted client IP

- “Trusted IP” ở đây là xác định IP client đáng tin, không phải allowlist khách hàng.
- Mặc định hiện tại:
  - `server.forward-headers-strategy=NONE`
  - bỏ qua `Forwarded`, `X-Forwarded-For`, `X-Real-IP`, `X-Forwarded-Proto`;
  - `ClientIpResolver` chỉ đọc `request.getRemoteAddr()`.
- Khi có reverse proxy sau này:
  - đổi sang `NATIVE`;
  - cấu hình Tomcat `RemoteIpValve` với danh sách proxy regex rõ ràng;
  - startup từ chối cấu hình proxy rỗng hoặc dạng trust-all;
  - chỉ sau bước này `getRemoteAddr()` và `request.isSecure()` mới phản ánh forwarded header.
- Xóa logic tự lấy phần tử đầu tiên của `X-Forwarded-For`; cookie `Secure` chỉ dựa trên `request.isSecure()`.
- IPv4 dùng địa chỉ chuẩn hóa; IPv6 gom theo `/64`; khóa limiter/log dùng HMAC thay vì IP thô.
- Cloudflare và `CF-Connecting-IP` để đợt sau.

### 4. Thu hồi toàn bộ session

- Flyway V20 thêm `users.security_version BIGINT NOT NULL DEFAULT 0`.
- Access JWT, refresh JWT và Redis `RefreshTokenSession` thêm claim/field `securityVersion`.
- Mọi request có JWT so sánh claim với version hiện tại trong PostgreSQL; thiếu hoặc sai version trả `401 SESSION_REVOKED`.
- Reset password, change password, change email và thay đổi role:
  - tăng `securityVersion` trong cùng transaction DB;
  - bulk revoke tất cả refresh-token audit rows;
  - sau commit, xóa toàn bộ Redis refresh sessions thông qua index theo `userId`.
- Redis thêm ZSET index session theo user; create/rotate/delete duy trì index atomically.
- Nếu cleanup Redis thất bại, token cũ vẫn bị chặn bởi DB version; khóa Redis cũ chỉ còn tồn tại đến TTL và phát sinh log/metric lỗi.
- Logout bình thường vẫn chỉ thu hồi phiên hiện tại.
- Blacklist access token chuyển sang dùng HMAC digest thay vì đặt raw JWT trong Redis key.
- Frontend không gọi refresh/check-session sau thay đổi nhạy cảm; nó xóa auth state và chuyển về đăng nhập. Việc tự động thu hồi session sau reset password phù hợp hướng dẫn của [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

### 5. Monitoring code-only

- Thêm correlation filter sinh server-side `X-Request-ID`, đưa vào MDC và luôn clear sau request.
- Production dùng structured Logstash JSON có sẵn trong Spring Boot; dev giữ log dễ đọc.
- Security log ghi event/outcome/reason/purpose/userId nội bộ/IP hash; tuyệt đối không ghi OTP, proof, token, password, email hoặc IP thô.
- Micrometer counters/timers:
  - `security.otp.requests`
  - `security.otp.verifications`
  - `security.auth.attempts`
  - `security.rate.limit.rejections`
  - `security.sessions.revoked`
  - `security.otp.delivery.duration`
- Chỉ dùng tag hữu hạn như operation, purpose, outcome, policy, reason; không dùng email, userId, IP, challenge hoặc requestId làm metric tag.
- Mở `/actuator/metrics/**` cho `ROLE_ADMIN` bằng `EndpointRequest`; health/info vẫn public, các actuator khác không được expose. Spring Boot đã hỗ trợ endpoint này qua Actuator/Micrometer hiện có. [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- Metrics hiện tại là in-memory, reset khi restart và chưa tổng hợp nhiều instance; chưa có cảnh báo gửi ra ngoài.

## Kiểm thử, tài liệu và rollout

- Backend dùng Testcontainers Redis/PostgreSQL:
  - request/verify/consume đồng thời chỉ đúng một tác vụ thắng;
  - resend không reset attempts, challenge/proof cũ mất hiệu lực;
  - proof sai scope/email/user, hết hạn hoặc reuse bị từ chối;
  - kiểm tra mọi ngưỡng/dimension, `Retry-After`, Redis fail-closed;
  - forged XFF/XFP bị bỏ qua; IPv6 `/64` hoạt động;
  - reset/change thu hồi access và mọi refresh session, kể cả race với refresh;
  - metrics chỉ ADMIN đọc được; log không chứa secret/PII.
- Frontend Vitest:
  - proof chỉ tồn tại trong biến callback trong memory;
  - resend thay challenge;
  - final action gửi đúng `otpProofToken`;
  - mapping error code/Retry-After;
  - thay đổi nhạy cảm xóa local auth state và redirect đăng nhập.
- Chạy `.\mvnw.cmd test`; frontend chạy `pnpm test:unit`, `pnpm lint`, `pnpm build` và fullstack smoke phù hợp.
- Viết tài liệu chính bằng tiếng Việt tại `docs/OTP_SECURITY_FLOW_VI.md`: thuật ngữ, sequence ba luồng OTP, Redis keys/TTL/Lua invariants, limiter table, trusted proxy, session version, metrics, failure/race cases, cấu hình và troubleshooting.
- Đồng bộ `API_SPEC`, auth `CONTEXT`, tài liệu race-condition, project status, README, ADR và OpenSpec; change cũ dùng verified-marker được đánh dấu superseded.
- Backend/frontend phát hành cùng lúc. JWT không có `securityVersion`, refresh session serialization cũ và verified-marker cũ đều bị từ chối; đây là one-time forced re-login có chủ đích.

## Ngoài phạm vi

- Chưa có Cloudflare/WAF/CDN, adaptive CAPTCHA hay chống DDoS tầng mạng; DDoS lớn phải chặn ở edge/upstream. [CISA DDoS guidance](https://www.cisa.gov/sites/default/files/2023-09/TLP%20CLEAR%20-DDOS%20Mitigations%20Guidance_508c.pdf)
- Chưa rate-limit API nghiệp vụ. Backlog tiếp theo: upload quota, Gemini quota/concurrency, checkout theo user; public reads dựa thêm vào cache/CDN.
- Finding riêng `POST /api/v1/reviews` đang nhận `userId` từ body thay vì bind JWT không được trộn vào nhánh này; cần xử lý bằng security ticket riêng.
