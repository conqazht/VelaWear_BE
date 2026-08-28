# Hướng Dẫn Kiểm Thử Thanh Toán Thẻ Quốc Tế Stripe Test Mode

Tài liệu này hướng dẫn cách cấu hình và kiểm thử thanh toán thẻ quốc tế (Visa, Mastercard) qua **Stripe Test Mode** trên VelaWear.

---

## 1. Thông Tin Cấu Hình Test Mode

Thêm các biến môi trường sau vào file `.env` của Backend (`commercial/.env`):

```properties
STRIPE_ENABLED=true
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
STRIPE_WEBHOOK_SECRET=whsec_your_stripe_webhook_secret
STRIPE_SUCCESS_URL=http://localhost:3000/payment/stripe/success?session_id={CHECKOUT_SESSION_ID}&order_code={ORDER_CODE}
STRIPE_CANCEL_URL=http://localhost:3000/payment/stripe/cancel?order_code={ORDER_CODE}
STRIPE_CURRENCY=vnd
```

> [!NOTE]
> Lấy `sk_test_...` tại [Stripe Dashboard (Test Mode)](https://dashboard.stripe.com/test/apikeys).

---

## 2. Thông Tin Thẻ Test Quốc Tế

| Loại thẻ | Số thẻ test | Ngày hết hạn | CVC / CVV | Mã bưu chính (ZIP) |
| :--- | :--- | :--- | :--- | :--- |
| **Visa (Thành công)** | `4242 4242 4242 4242` | Bất kỳ ngày tương lai nào (vd: `12/28`) | `123` | `70000` |
| **Mastercard** | `5555 5555 5555 4444` | Bất kỳ ngày tương lai nào | `123` | `70000` |
| **Thẻ bị từ chối** | `4000 0000 0000 0002` | Bất kỳ ngày tương lai nào | `123` | `70000` |

---

## 3. Chuyển Tiếp Webhook Bằng Stripe CLI

Để kiểm thử Webhook thực tế từ Stripe về máy local:

1. **Cài đặt Stripe CLI:**
   ```bash
   stripe login
   ```
2. **Lắng nghe và chuyển tiếp Webhook đến cổng local:**
   ```bash
   stripe listen --forward-to localhost:8080/api/v1/payments/stripe/webhook
   ```
3. Stripe CLI sẽ in ra `whsec_...` (Webhook Signing Secret). Sao chép key này vào `STRIPE_WEBHOOK_SECRET` trong file `.env`.
4. Khi khách hàng hoàn tất thanh toán trên Stripe Checkout Session, Stripe sẽ gửi sự kiện `checkout.session.completed` về backend. Backend dùng SDK xác thực chữ ký và cập nhật đơn hàng thành công.
