## Context

Backend đã có PostgreSQL, Redis, Resend, JWT, Actuator và Micrometer. Thiết kế phải
chống replay/check-then-act ở nhiều instance, không lộ account qua forgot-password,
không tin header proxy từ client và vẫn revoke token khi Redis cleanup lỗi.

## Goals / Non-Goals

**Goals:** opaque scoped proof dùng một lần; limiter theo endpoint/dimension; trusted
client IP; revoke-all có nguồn sự thật DB; log/metric không secret/PII.

**Non-Goals:** Cloudflare/WAF/CDN, CAPTCHA, volumetric DDoS mitigation, browser
fingerprint, business API quota, external metric collector/alert.

## Decisions

### OTP challenge/proof

Request trả random 32-byte `challengeId`; verify chỉ nhận challenge/code và trả random
32-byte proof. Scope là HMAC của purpose, normalized email và actor. Final endpoint
tự xác định purpose, validate lỗi dự đoán được rồi consume proof bằng Lua ngay trước
DB mutation. Forgot-password unknown email tạo decoy challenge, không gửi mail.

### Atomic Redis

Tách bốn Lua script reserve/publish/verify-consume. Challenge mới chỉ publish sau
provider acceptance. Resend lỗi giữ challenge cũ nhưng cooldown/quota vẫn tính.
Attempts không reset khi resend. Redis outage fail closed.

### Multi-dimensional limiter

Sliding-window Lua đánh giá mọi bucket cùng lúc. Filter chạy global/IP trước body;
service thêm recipient/account/user/session sau normalize. Subject key dùng HMAC và
threshold nằm hoàn toàn trong config.

### Trusted IP

Direct mode dùng `NONE`, đọc `getRemoteAddr()` và bỏ qua forwarded header. Proxy mode
dùng Tomcat `NATIVE` với explicit trusted-proxy regex; empty/wildcard config làm
startup thất bại. IPv6 bucket theo `/64`.

### Session version

PostgreSQL `users.security_version` là nguồn sự thật. JWT và Redis session mang
version. Sensitive/role change tăng version + revoke audit rows trong transaction;
Redis user-index cleanup chạy after commit. Stale token bị DB comparison chặn kể cả
cleanup lỗi.

### Monitoring

Server tạo request ID, production log JSON, security fields/tag hữu hạn và không
chứa secret/PII. Metrics chỉ ADMIN đọc và hiện là in-memory.

## Trade-offs

- Authenticated request đọc DB để có immediate revoke-all.
- Không có transaction chung Redis/PostgreSQL; proof đã consume không được restore
  sau DB failure.
- Limiter tầng ứng dụng không chống lưu lượng làm nghẽn upstream.
- Contract/token cũ bị vô hiệu để tránh compatibility path làm yếu invariant mới.

## Migration

1. Phát hành backend/frontend cùng lúc.
2. Cấu hình `SECURITY_HMAC_SECRET` và giữ forward strategy `NONE` nếu không có proxy.
3. Apply Flyway V20.
4. Chấp nhận forced re-login và OTP flow đang mở phải bắt đầu lại.
5. Quan sát metric/log nội bộ; tinh chỉnh threshold bằng config.

Chi tiết key/TTL/error/troubleshooting nằm tại `docs/OTP_SECURITY_FLOW_VI.md`.
