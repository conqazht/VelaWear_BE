# Hướng Dẫn Kiểm Thử Cổng Thanh Toán VNPay Sandbox

Tài liệu này hướng dẫn cách cấu hình và thực hiện kiểm thử thanh toán qua **VNPay Sandbox** trên VelaWear.

---

## 1. Thông Tin Cấu Hình Sandbox

Thêm các biến môi trường sau vào file `.env` của Backend (`commercial/.env`):

```properties
VNPAY_ENABLED=true
VNPAY_TMN_CODE=your_tmn_code_from_vnpay
VNPAY_HASH_SECRET=your_hash_secret_from_vnpay
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:3000/payment/vnpay/return
VNPAY_EXPIRE_MINUTES=15
```

> [!NOTE]
> Nếu chưa đăng ký tài khoản VNPay Sandbox riêng, bạn có thể đăng ký miễn phí tại [VNPay Sandbox Merchant Portal](https://sandbox.vnpayment.vn/devreg/).

---

## 2. Thông Tin Thẻ Test (NCB Bank Test)

Khi thanh toán trên giao diện VNPay Sandbox, chọn **Thẻ nội địa & tài khoản ngân hàng** -> chọn Ngân hàng **NCB** và nhập thông tin sau:

| Trường thông tin | Giá trị mẫu |
| :--- | :--- |
| **Ngân hàng** | **NCB (Ngân hàng Quốc Dân)** |
| **Số thẻ** | `9704198526191432198` |
| **Tên chủ thẻ** | `NGUYEN VAN A` |
| **Ngày phát hành** | `07/15` |
| **Mật khẩu OTP** | `123456` |

---

## 3. Luồng Kiểm Thử

1. **Khởi tạo đơn hàng:**
   * Trên Frontend (`/checkout`), chọn phương thức **Cổng thanh toán VNPay**.
   * Nhấn **Đặt hàng**. Hệ thống Backend tạo URL thanh toán được ký mã hóa HMAC-SHA512 và chuyển hướng bạn sang cổng VNPay Sandbox.
2. **Xác nhận thanh toán:**
   * Nhập thông tin thẻ test NCB ở trên, nhập OTP `123456`.
   * VNPay xử lý giao dịch thành công và chuyển hướng người dùng về `VNPAY_RETURN_URL`.
3. **Xử lý IPN ngầm (Webhook):**
   * Trong môi trường local, nếu muốn nhận IPN thực tế từ server VNPay, mở tunnel bằng Ngrok:
     ```bash
     ngrok http 8080
     ```
   * Cập nhật URL IPN trên trang quản trị VNPay Sandbox: `https://<ngrok-id>.ngrok-free.app/api/v1/payments/vnpay/ipn`.
   * Backend xác thực chữ ký `vnp_SecureHash`, cập nhật đơn hàng thành `PAID` và gửi email xác nhận.
4. **Dev Simulation (Kiểm thử nhanh offline):**
   * Nếu không có kết nối internet hoặc ngrok, bạn có thể gọi API giả lập:
     ```http
     POST /api/v1/payments/simulate/success
     Content-Type: application/json

     {
       "orderCode": "VELA-XXXXXX",
       "amount": 250000
     }
     ```
