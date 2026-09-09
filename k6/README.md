# VelaWear Flash-Sale & High-Concurrency Checkout Stress Test (k6)

Kịch bản kiểm thử tải cao (Stress / Concurrency Testing) cho tính năng Checkout & Flash Sale của **VelaWear Backend**.

## Mục tiêu kiểm thử:
1. **Zero Overselling (Không âm kho):** Khi có nhiều request đồng thời mua cùng 1 sản phẩm có số lượng tồn kho giới hạn, số đơn tạo thành công không bao giờ vượt quá tồn kho ban đầu.
2. **Zero Deadlocks (Không khóa chết):** Nhờ cơ chế **Ordered Row-level Pessimistic Locking** (`order by id asc`) và **Atomic Conditional SQL Updates**, 100% giao dịch đồng thời không bao giờ phát sinh lỗi deadlock ở tầng PostgreSQL.
3. **Hiệu năng & Đo lường thực tế:** Đo lường **Throughput (Requests/sec)** và **Độ trễ (Latency p95, p99)**.

---

## Hướng dẫn chạy Stress Test

### Bước 1: Khởi động Database & Redis
```bash
sudo systemctl start docker
docker compose up -d
```

### Bước 2: Khởi động Backend Spring Boot
```bash
./mvnw spring-boot:run
```
*(Đợi ứng dụng khởi động thành công trên cổng `8080`).*

### Bước 3: (Tùy chọn) Chuẩn bị kho hàng Flash Sale
Chạy câu lệnh SQL để set số lượng kho của variant id = 1 về 10 chiếc:
```bash
docker exec -i commercial-postgres psql -U postgres -d commercial < k6/seed-flash-sale.sql
```

### Bước 4: Chạy k6 Stress Test
Chạy k6 mặc định (100 concurrent requests):
```bash
k6 run k6/checkout-stress-test.js
```

Hoặc tùy chỉnh số lượng người dùng ảo (VUs) và số lần chạy:
```bash
# Test với 500 concurrent requests
k6 run --vus 100 --iterations 500 k6/checkout-stress-test.js
```

---

## Báo cáo đầu ra của k6:
- `checkout_success`: Số đơn hàng thanh toán thành công (bằng chính xác số lượng kho ban đầu).
- `checkout_insufficient_stock`: Số request nhận lỗi `INSUFFICIENT_STOCK` (hết hàng có kiểm soát).
- `checkout_deadlock_detected`: Luôn bằng **0** (chứng minh hệ thống không bị Deadlock).
- `http_req_duration`: Bảng thống kê độ trễ `avg`, `min`, `med`, `p(90)`, `p(95)`, `p(99)`.
