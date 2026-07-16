# ADR: Hardening OTP/Auth v1 bằng Redis atomic và security version

- **Trạng thái**: Accepted
- **Ngày**: 2026-07-16
- **Phạm vi**: Backend và frontend Auth/OTP
- **Thay thế**: verified-marker theo email/purpose của change `add-auth-otp-email`

## Bối cảnh

Luồng OTP ban đầu đánh dấu một email/purpose đã verify trong Redis. Cách đó chưa có
opaque proof trong contract, khó bind an toàn với actor đổi email và dễ tạo
check-then-act race nếu verify/consume dùng nhiều lệnh Redis. Auth endpoint cũng chưa
có limiter đồng nhất, forwarded header có nguy cơ bị client forge và việc đổi thông
tin nhạy cảm chưa có một nguồn sự thật chung để vô hiệu mọi access/refresh token.

Hệ thống đã có Redis, Resend, PostgreSQL, Spring Boot Actuator và Micrometer. V1 cần
hardening bằng các thành phần này, không đưa thêm Cloudflare/CAPTCHA/SaaS.

## Quyết định

### Challenge và proof

OTP request trả `challengeId`; verify chỉ nhận `{challengeId, code}` và trả proof
token năm phút. Challenge/proof là 32 byte `SecureRandom`, Base64URL. Redis chỉ giữ
HMAC-SHA256 có domain riêng. Proof bind với purpose/email; `CHANGE_EMAIL` bind thêm
`userId`. Endpoint cuối tự xác định purpose và consume proof đúng một lần bằng Lua.

Forgot-password với email không tồn tại trả decoy challenge cùng schema, không gửi
email. Challenge mới chỉ publish sau khi Resend chấp nhận; delivery failure giữ
challenge trước nhưng vẫn giữ cooldown/quota.

### Redis atomic và fail-closed

Bốn Lua script chịu trách nhiệm reserve, publish, verify/issue và consume. Failed
attempts sống theo scope qua resend và khóa 10 phút sau năm lần sai. Redis lỗi trả
503, không bypass OTP/limiter. Redis và PostgreSQL không có distributed transaction;
proof đã consume không được phục hồi nếu DB lỗi hiếm gặp sau đó.

### Limiter đa chiều

Redis sliding-window Lua đánh giá toàn bộ bucket cùng lúc. Filter áp dụng global/IP
trước body parsing; service bổ sung email/account/user/refresh JTI sau normalize.
Ngưỡng nằm trong `app.security.rate-limit`. Không dùng browser fingerprint; IP là
dimension phụ và limiter không được coi là chống DDoS volumetric.

### Trusted client IP

Mặc định `server.forward-headers-strategy=NONE`; resolver chỉ đọc
`request.getRemoteAddr()`. Không parse `Forwarded`/`X-Forwarded-*` trong application.
Khi có proxy, chỉ dùng `NATIVE` với Tomcat trusted-proxy regex cụ thể; application
fail startup nếu regex rỗng/trust-all. IPv6 limiter gom theo `/64`.

### Revoke-all

`users.security_version` là nguồn sự thật. Access/refresh JWT và Redis refresh
session mang version; request JWT so với PostgreSQL. Reset/change password, đổi email
và đổi role tăng version và revoke audit rows trong transaction; Redis cleanup chạy
sau commit qua ZSET index theo user. Cleanup Redis lỗi không làm token cũ hợp lệ lại.

### Monitoring

Server sinh `X-Request-ID`, security log dùng field hữu hạn và không ghi secret/PII.
Micrometer cung cấp counter/timer Auth/OTP/limiter/revocation/delivery. Metrics chỉ
ADMIN đọc; dữ liệu hiện in-memory, chưa có collector hoặc alert ngoài.

## Hệ quả

Tích cực:

- proof không thể dùng sai purpose/email/user và không thể replay đồng thời;
- abuse control có nhiều dimension nhưng không phụ thuộc fingerprint;
- forged forwarded header không né limiter;
- một atomic DB version vô hiệu mọi token, kể cả Redis cleanup lỗi;
- có request correlation và metric hữu hạn để tinh chỉnh policy.

Đánh đổi:

- mỗi authenticated JWT request phải đọc security version từ PostgreSQL;
- Redis trở thành dependency fail-closed cho OTP/limiter;
- không có transaction nguyên tử xuyên Redis/PostgreSQL;
- backend/frontend phải rollout cùng lúc và token/OTP v1 bị vô hiệu;
- metric mất khi process restart và chưa tổng hợp multi-instance.

## Phương án không chọn

- **Final mutation ngay trong `/otp/verify`**: ghép OTP với từng use case, buộc
  verify nhận password/profile và khó tái sử dụng.
- **Verified marker không opaque**: contract không biểu diễn quyền hạn ngắn hạn rõ
  ràng và khó bind actor/use-once an toàn.
- **Process-local counter/lock**: sai khi chạy nhiều instance hoặc restart.
- **Tin phần tử đầu XFF**: client có thể forge nếu chain proxy không được container
  xác thực.
- **Blacklist từng access JWT để revoke-all**: không biết hết mọi token đã phát và
  tốn key; version counter đơn giản hơn, có nguồn sự thật DB.
- **Thêm Cloudflare/CAPTCHA ngay v1**: mở rộng vận hành và contract ngoài phạm vi;
  để đợt edge/adaptive protection sau.

## Rollout

Backend/frontend phát hành đồng thời. OTP prefix chuyển sang `auth:otp:v2:*`; JWT
thiếu `securityVersion` và refresh serialization cũ bị từ chối. Đây là forced
re-login một lần có chủ đích. Chi tiết vận hành nằm tại
[`../OTP_SECURITY_FLOW_VI.md`](../OTP_SECURITY_FLOW_VI.md).
