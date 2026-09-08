## Why

OTP v1 dùng verified-marker chưa biểu diễn proof trong API, chưa bind chặt actor đổi
email và chưa đủ invariant nguyên tử cho request/verify/consume đồng thời. Auth cần
thêm abuse control, trusted client IP, revoke-all và quan sát nội bộ trước khi cân
nhắc edge service hoặc adaptive CAPTCHA.

## What Changes

- Thay verified-marker bằng contract `challengeId -> proofToken -> otpProofToken`.
- Dùng HMAC domain-separated và Redis Lua cho cooldown, challenge, attempts, proof.
- Thêm Redis sliding-window limiter đa chiều cho Auth/OTP.
- Chỉ tin servlet remote address; proxy mode fail startup nếu trust config không an
  toàn.
- Thêm `users.security_version` để revoke mọi access/refresh session.
- Thêm correlation ID, structured security event và bounded Micrometer metrics.
- Backend/frontend rollout cùng lúc; token/OTP serialization cũ bị từ chối.

## Capabilities

### New Capabilities

- `otp-auth-hardening`: scoped one-time proof, abuse controls, trusted IP,
  session-version revocation and code-only monitoring.

### Modified Capabilities

- `auth-otp-email`: giữ Resend provider nhưng thay verified-marker/key/API contract.
- `redis-refresh-token-sessions`: thêm `securityVersion` và user JTI index.

## Impact

- API Auth/OTP và frontend auth flow đổi contract đồng thời.
- Flyway V20 thêm `users.security_version`.
- Redis thêm `auth:otp:v2:*`, `security:rate:v1:*` và
  `auth:refresh:user:{userId}`.
- Production cần `SECURITY_HMAC_SECRET` riêng, tối thiểu 32 byte.
- One-time forced re-login khi rollout.
- Không thêm Cloudflare, CAPTCHA, Prometheus/Grafana hoặc SaaS.
