# Checkout Module Context

> Tài liệu ngắn dành cho developer sửa module checkout. Nghiệp vụ Sale Campaign đầy đủ nằm tại [`docs/SALE_CAMPAIGN_BACKEND.md`](../../../../../../../../docs/SALE_CAMPAIGN_BACKEND.md).

## Nguồn sự thật

- Item và quantity được đọc từ cart của user trong database.
- `product_variants.price` là giá niêm yết; giá hiệu lực phải đi qua pricing service.
- Client không phải nguồn sự thật của giá, subtotal, quota, stock, coupon discount hoặc shipping fee.
- Shipping fee lấy từ `app.checkout.shipping-fee` (mặc định 30.000); field `shippingFee` cũ trong request chỉ để tương thích và bị bỏ qua.
- Add-to-cart không giữ stock/quota Flash. Chỉ checkout thành công mới tạo allocation.
- PostgreSQL là nguồn sự thật; Redis chỉ được dùng cache/throttle.
- `availableQuantity` chỉ giúp UI giới hạn input: min(stock, quota còn lại, lượt khách còn lại). Checkout vẫn phải atomic-check lại.

## Transaction checkout

Checkout chạy trong một `@Transactional` duy nhất và theo thứ tự cố định:

1. Khóa cart của user.
2. Kiểm tra `Idempotency-Key` và request hash.
3. Resolve lại pricing; client bắt buộc gửi `pricingFingerprint` từ preview và giá trị phải khớp.
4. Sắp xếp variant/campaign item theo ID tăng dần.
5. Bảo đảm row `sale_customer_usages` tồn tại bằng insert-if-absent.
6. Atomic reserve quota trong `sale_campaign_items`.
7. Atomic reserve counter trong `sale_customer_usages`.
8. Atomic decrement stock.
9. Atomic consume coupon trên eligible subtotal; dòng `FLASH_SALE` bị loại.
10. Lưu order, order item snapshot, payment và sale allocation; xóa cart và commit.

Một bước thất bại phải rollback toàn transaction. Ví dụ reserve được quota nhưng
stock thực tế chỉ còn 1 trong khi khách mua 2 thì quota/customer usage cũng quay
về trạng thái trước checkout; không gọi "bù" bằng một transaction rời.

## Chống race condition

- Không dùng luồng `SELECT counter -> kiểm tra Java -> save counter`.
- Stock/quota/customer limit/coupon phải dùng atomic conditional update và kiểm tra affected row.
- Admin update/delete coupon phải lấy `PESSIMISTIC_WRITE` lock trên row coupon trong
  transaction trước khi ghi toàn entity. Atomic `consumeUsage`/`releaseUsage` của
  checkout và lifecycle giữ nguyên; PostgreSQL row lock serialize hai loại mutation,
  tránh admin save ghi đè `usedCount` vừa tăng/giảm.
- Counter quota luôn thỏa `reserved + sold <= quota`.
- Customer usage luôn thỏa `reserved + purchased <= maxPerCustomer` khi có limit.
- Khóa nhiều row theo ID tăng dần để giảm deadlock.
- Hai admin publish campaign overlap cũng phải được serialize/khóa trong transaction.

Các conflict nghiệp vụ trả HTTP `409` với code ổn định như
`INSUFFICIENT_STOCK`, `FLASH_SALE_SOLD_OUT`,
`FLASH_SALE_LIMIT_EXCEEDED`, `FLASH_SALE_ENDED` và `PRICE_CHANGED`.

## Idempotency checkout

`POST /api/v1/checkout` bắt buộc header `Idempotency-Key` (tối đa 100 ký tự).
Unique `(user_id, checkout_idempotency_key)` bảo vệ double-click/retry:

- Cùng key và cùng request hash: trả lại order đã tạo.
- Cùng key nhưng payload khác: `IDEMPOTENCY_KEY_REUSED`.

Không tạo key mới trong backend khi retry cùng một ý định. Client chịu trách
nhiệm giữ key cho đến khi biết kết quả cuối cùng, rồi tạo key mới cho lần đặt
đơn khác.

## Order resource lifecycle

Mọi nhánh cancel, payment failure, SePay IPN và timeout scheduler phải gọi cùng
một service quản lý tài nguyên, khóa order trước khi transition và kiểm tra
`orders.resources_released_at`.

```text
Online: RESERVED -> CONFIRMED (payment success)
Online: RESERVED -> RELEASED  (cancel/failure/timeout)
COD:    tạo trực tiếp CONFIRMED
COD:    CONFIRMED -> REVERSED  (cancel hợp lệ)
```

Transition allocation phải là compare-and-set theo status cũ. Webhook/cancel
lặp không được tăng hoặc hoàn stock/coupon/quota/customer usage lần hai.

## Payment và timeout

- Online payment có `payment_due_at = created_at + 15 phút`.
- `reservation_expires_at = payment_due_at + 30 giây` để chờ IPN đang đến.
- Scheduler chỉ xử lý order chưa release và đã qua `reservation_expires_at`.
- IPN và scheduler tranh cùng row lock; chỉ một nhánh được transition.
- Nếu tiền đến sau khi tài nguyên đã release, payment chuyển
  `REFUND_PENDING`; không tự phục hồi order.
- Nếu gateway gửi transaction thứ hai cho order đã PAID, giữ mã transaction gốc,
  lưu transaction mới ở `REFUND_PENDING` và không confirm allocation lần hai.
- Reservation tạo trước khi campaign kết thúc vẫn được thanh toán đến hạn.
- Kết thúc campaign sớm chỉ chặn checkout mới.

## Snapshot order item

Order item phải lưu `listPrice`, `price`, `priceSource`, campaign item và snapshot
code/tên campaign. Không truy ngược catalog để sửa lịch sử đơn khi campaign hoặc
giá sản phẩm thay đổi.

## Test tối thiểu khi sửa module

- Quota 1, hai checkout đồng thời: chỉ một thành công.
- Một user checkout đồng thời: không vượt customer limit.
- Reserve quota rồi stock fail: tất cả counter rollback.
- Retry checkout cùng idempotency key: chỉ một order.
- Hai cancel đồng thời: tài nguyên chỉ hoàn một lần.
- IPN lặp và IPN tranh timeout: chỉ một transition thắng.
- Mixed cart BASE/STANDARD/FLASH: coupon chỉ tính dòng đủ điều kiện.

Test concurrency phải dùng PostgreSQL Testcontainers; mock/H2 không chứng minh
được row lock và atomic update của production.
