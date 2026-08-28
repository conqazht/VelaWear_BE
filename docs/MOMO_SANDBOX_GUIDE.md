# Hướng Dẫn Kiểm Thử Cổng Thanh Toán MoMo Sandbox

Tài liệu này hướng dẫn cách cấu hình và kiểm thử thanh toán qua **Ví MoMo Sandbox (All-In-One captureWallet)** trên VelaWear.

---

## 1. Thông Tin Cấu Hình Sandbox

Thêm các biến môi trường sau vào file `.env` của Backend (`commercial/.env`):

```properties
MOMO_ENABLED=true
MOMO_PARTNER_CODE=your_momo_partner_code
MOMO_ACCESS_KEY=your_momo_access_key
MOMO_SECRET_KEY=your_momo_secret_key
MOMO_ENDPOINT=https://test-payment.momo.vn/v2/gateway/api/create
MOMO_RETURN_URL=http://localhost:3000/payment/momo/return
MOMO_IPN_URL=http://localhost:8080/api/v1/payments/momo/ipn
```

> [!NOTE]
> Đăng ký tài khoản MoMo Developer tại [MoMo Developers Portal](https://developers.momo.vn/) để nhận bộ `Partner Code`, `Access Key`, và `Secret Key` sandbox.

---

## 2. Kiểm Thử Ứng Dụng MoMo Test

* Cài đặt ứng dụng **MoMo Test** (dành cho Developer) trên Android/iOS hoặc sử dụng giao diện quét QR / Web checkout sandbox của MoMo.
* Đăng nhập với số điện thoại test do MoMo cấp trong portal.
* Số dư tài khoản test được cấp sẵn tiền ảo để thực hiện giao dịch.

---

## 3. Luồng Kiểm Thử

1. **Khởi tạo thanh toán:**
   * Tại trang Checkout, chọn **Ví điện tử MoMo**.
   * Nhấn **Đặt hàng**. Backend ký chuỗi dữ liệu với thuật toán **HMAC-SHA256** và gửi request `POST /v2/gateway/api/create` sang MoMo.
   * Hệ thống nhận về `payUrl` và chuyển hướng khách hàng sang cổng MoMo.
2. **Xác nhận thanh toán:**
   * Quét mã QR hoặc đăng nhập tài khoản MoMo test để hoàn tất.
3. **Webhook IPN:**
   * MoMo gửi webhook ngầm đến `POST /api/v1/payments/momo/ipn`.
   * Backend xác thực chữ ký signature HMAC-SHA256, chuyển trạng thái đơn hàng sang `PAID` và gửi email thông báo.
4. **Dev Simulation (Offline test):**
   * Sử dụng endpoint `POST /api/v1/payments/simulate/success` để test nhanh mà không cần kết nối mạng.
