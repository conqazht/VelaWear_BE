# Kiểm thử race condition phía backend

Tài liệu này mô tả các race condition có rủi ro cao trong VelaWear, cơ chế bảo vệ
ở backend và cách kiểm thử bằng PostgreSQL/Redis thật. Đây là correctness test,
không phải benchmark tải.

## Phạm vi công cụ

- JUnit + Testcontainers kiểm tra transaction, row lock, atomic update và Redis Lua.
- PostgreSQL là nguồn sự thật cho stock, coupon, order và sale allocation.
- Redis là nguồn sự thật cho refresh session đang hoạt động.
- Playwright chỉ nên bổ sung acceptance test từ góc nhìn trình duyệt; không thay thế
  các concurrency integration test phía backend.
- Không dùng H2 hoặc mock repository để kết luận một race condition đã được bảo vệ.

## Ma trận hiện tại

| Race condition | Cơ chế bảo vệ | Test chính |
|---|---|---|
| Hai khách mua sản phẩm cuối cùng | Atomic conditional stock decrement; DB check stock không âm | `CheckoutConcurrencyTest.checkout_concurrentStock_preventsOverselling` |
| Hai checkout tranh coupon giới hạn 1 | Atomic conditional increment `used_count` | `CheckoutConcurrencyTest.checkout_concurrentCoupon_preventsReuse` |
| Hai khách tranh quota Flash cuối | Atomic reserve quota và capacity constraint | `SaleCampaignConcurrencyIntegrationTest.twoBuyersCompetingForLastFlashQuota_onlyOneKeepsFlashReservation` |
| Một khách tranh giới hạn Flash | Atomic per-customer counter | `SaleCampaignConcurrencyIntegrationTest.concurrentPerCustomerCounter_allowsOnlyOneReservationAtTheLimit` |
| Hai checkout cùng user và idempotency key | Pessimistic cart lock, request hash và unique `(user_id, checkout_idempotency_key)` | `SaleCampaignConcurrencyIntegrationTest.concurrentSepayCheckoutWithSameIdempotencyKey_createsOneOrderPaymentAndReservation` |
| Hai SePay IPN giống hệt | Order/payment row lock, transaction-code unique index và idempotent lifecycle | `SaleCampaignConcurrencyIntegrationTest.concurrentExactDuplicateSepayIpn_confirmsPaymentAndResourcesOnce` |
| IPN đến lúc reservation hết hạn | Cùng tranh order row lock; release chỉ chạy một lần | `SaleCampaignConcurrencyIntegrationTest.lateIpnRacingExpiry_releasesResourcesOnceAndMarksRefundPending` |
| Hai request dùng cùng refresh token | Redis Lua compare-and-swap thay old session bằng đúng một successor | `AuthRefreshConcurrencyIntegrationTest.refreshToken_concurrentRequests_onlyOneSucceeds` |
| Hai request OTP cùng scope cùng reserve cooldown | Redis Lua `SET NX PX`; chỉ một reservation thắng | OTP Redis integration/concurrency test |
| Hai verify đúng cùng challenge | Lua verify xóa active challenge và phát proof trong cùng thao tác; chỉ một proof được phát | OTP Redis integration/concurrency test |
| Hai final action dùng cùng proof | Lua consume so scope rồi xóa proof + active pointer atomically; chỉ một consume thắng | OTP Redis integration/concurrency test |
| Refresh chạy đồng thời với reset/change/role-change | PostgreSQL atomic increment `security_version` là nguồn sự thật; refresh successor version cũ vẫn bị từ chối | Auth session revocation integration/concurrency test |

## Quy tắc viết concurrency test

1. Dùng hai worker riêng và transaction thật cho từng worker.
2. Dùng một `ready` latch để chắc chắn cả hai worker đã sẵn sàng.
3. Chỉ mở `start` latch sau khi `ready` về 0.
4. Với checkout trùng idempotency key và IPN trùng, test dùng interceptor chỉ có
   trong test để chặn cả hai worker ngay trước truy vấn repository có
   `PESSIMISTIC_WRITE`. Nhờ vậy test đồng bộ tại điểm tranh chấp, không chỉ đồng
   bộ lúc khởi động thread.
5. `Future.get` phải có timeout để deadlock trở thành lỗi test rõ ràng.
6. Trong `finally`, luôn mở latch còn đóng, hủy future chưa xong, gọi
   `shutdownNow` và chờ executor kết thúc với timeout. Worker test refresh là
   daemon để một lời gọi hạ tầng không phản hồi không giữ JVM sống vô hạn.
7. Kiểm tra cả kết quả API/service và hậu điều kiện trong database/Redis.
8. Luôn assert số lượng mutation: order, payment, transaction, inventory log,
   allocation hoặc refresh successor không được nhân đôi.
9. Test class concurrency không dùng transaction bao quanh test method vì transaction
   đó không truyền sang worker thread.

Barrier repository xác nhận hai request đã cùng tới cửa truy vấn khóa trên
PostgreSQL thật. Nó không đọc `pg_locks`/`pg_stat_activity`, vì vậy đây không phải
test quan sát trạng thái `WAITING` của từng backend PID; tính đúng đắn vẫn được kết
luận bằng các hậu điều kiện transaction và số lượng mutation sau khi cả hai request
hoàn tất.

## Atomic refresh rotation

`RefreshTokenSessionService.rotateIfCurrent` chạy một Lua script trong Redis:

1. Đọc old session và so sánh với snapshot mà request đã xác thực.
2. Tạo replacement bằng `SET ... PX ... NX`.
3. Chỉ khi replacement được tạo mới xóa old session.
4. Toàn bộ ba bước chạy nguyên tử trong một script.

Khi hai request cùng đọc được old session, chỉ một CAS trả về thành công. Request
còn lại nhận `401` với message hiện có `Refresh session is expired or revoked`.
Audit row PostgreSQL của request thua được rollback cùng transaction.

Thiết kế hiện tại dùng một Redis standalone. Lua script chạm hai key JTI khác nhau;
nếu chuyển sang Redis Cluster thì phải thiết kế lại key với cùng hash slot trước khi
bật cluster.

## Atomic OTP challenge và proof

OTP v2 không dùng chuỗi lệnh read/check/delete rời rạc. Bốn Lua script bảo vệ bốn
cửa race:

1. `reserve-request.lua` chỉ cho một request cùng scope đặt cooldown.
2. `publish-challenge.lua` atomically thay challenge/proof cũ sau khi provider nhận
   email; resend không xóa attempts.
3. `verify-and-issue-proof.lua` so code, tăng failed attempts, xóa challenge và tạo
   proof trong một thao tác.
4. `consume-proof.lua` so exact scope rồi xóa proof cùng active pointer; một proof
   không thể finalize hai mutation đồng thời.

Test phải kiểm tra cả kết quả hai worker và trạng thái key sau cùng: chỉ một
challenge/proof/final mutation tồn tại, attempts không bị reset bởi resend và proof
cũ không còn dùng được sau khi challenge mới được publish.

## Revoke-all và refresh race

Reset/change/role-change tăng `users.security_version` bằng atomic update trong
transaction PostgreSQL, bulk revoke refresh audit rows, rồi mới phát event cleanup
Redis sau commit. PostgreSQL là nguồn sự thật: nếu cleanup Redis lỗi, JWT/refresh
session mang version cũ vẫn bị `SESSION_REVOKED`.

Concurrency test phải tạo refresh và sensitive change sát nhau, sau đó chứng minh
không token successor nào có thể gọi API bằng version cũ. Không chỉ assert Redis key
đã xóa vì cleanup là hậu commit và có thể fail an toàn.

## Google OAuth2 callback race và replay

Authorization request không còn nằm trong cookie Java serialization. Browser chỉ
giữ nonce, còn JSON bounded nằm tại Redis key HMAC. Callback gọi `GETDEL` trước khi
Spring Security kiểm tra state và đổi authorization code, vì vậy hai callback cùng
nonce không thể cùng lấy request.

Integration test tạo một flow rồi thả đồng thời nhiều worker gọi
`removeAuthorizationRequest`; invariant là đúng một worker nhận request, các worker
còn lại nhận null và Redis key biến mất. Sau đó phải thử replay lần nữa, cookie Java
cũ, JSON hỏng/quá lớn và Redis unavailable để chứng minh không có nhánh fallback
sang cookie/HTTP session. `loadAuthorizationRequest` được test riêng vì thao tác đọc
không được consume state trước callback thật.

## Chạy test

Docker Desktop phải hoạt động vì integration test tự khởi động PostgreSQL và Redis.
Trên PowerShell, đặt cùng biến môi trường với backend CI:

```powershell
$env:SPRING_PROFILES_ACTIVE='test'
$env:SPRING_DOCKER_COMPOSE_ENABLED='false'
$env:SERVER_PORT='0'
$env:JWT_ACCESS_TOKEN_SECRET_KEY='test-access-secret-key-for-ci-must-be-at-least-64-characters-long'
$env:JWT_REFRESH_TOKEN_SECRET_KEY='test-refresh-secret-key-for-ci-must-be-at-least-64-characters-long'
$env:JWT_ACCESS_TOKEN_EXPIRATION='900'
$env:JWT_REFRESH_TOKEN_EXPIRATION='259200'
$env:RESEND_API_KEY='test-resend-api-key'
$env:SEPAY_ENABLED='false'

.\mvnw.cmd "-Dtest=AuthRefreshConcurrencyIntegrationTest" test
.\mvnw.cmd "-Dtest=*Otp*IntegrationTest,*Session*IntegrationTest" test
.\mvnw.cmd "-Dtest=CheckoutConcurrencyTest,SaleCampaignConcurrencyIntegrationTest" test
```

Trong GitHub Actions, job `backend-ci.yml` đã cung cấp các biến này và
`mvnw clean verify` sẽ chạy toàn bộ suite.

## Khi test thất bại

- Timeout: kiểm tra deadlock/lock order trước khi tăng timeout.
- Hai success ngoài dự kiến: kiểm tra affected-row của atomic update hoặc CAS result.
- Counter đúng nhưng có hai entity: kiểm tra unique constraint và transaction rollback.
- Test chỉ fail ngẫu nhiên: kiểm tra đã có cả `ready` và `start` latch hay chưa.
- Redis test fail trước khi chạy assertion: kiểm tra Docker và Redis Testcontainer,
  không fallback sang mock.

Quyết định refresh token chi tiết nằm tại
[`decisions/refresh-token-strategy.md`](decisions/refresh-token-strategy.md).
