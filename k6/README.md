# VelaWear Flash-Sale & High-Concurrency Checkout Stress Test (k6)

Bộ kịch bản kiểm thử tải cao (Stress / Performance Benchmark) cho tính năng Checkout & Flash Sale của **VelaWear Backend**.

## 🎯 Mục tiêu kiểm thử:
1. **Zero Overselling (Không âm kho):** Khi **200 người dùng ảo đồng thời** tranh mua sản phẩm giới hạn kho (20 chiếc), số đơn tạo thành công chính xác bằng 20, kho giảm về đúng 0 mà không bao giờ bị bán lố.
2. **Zero Deadlocks (Không khóa chết):** Nhờ cơ chế **Ordered Row-level Pessimistic Locking** (`order by id asc`) và **Atomic Conditional SQL Updates**, 100% giao dịch đồng thời không bao giờ phát sinh lỗi deadlock ở tầng PostgreSQL.
3. **Đo lường hiệu năng:** Kiểm tra khả năng chịu tải của Connection Pool (HikariCP), Spring Security, Tomcat và PostgreSQL dưới áp lực cạnh tranh tài nguyên cao.

---

## 🚀 Hướng dẫn chạy Stress Test

### Bước 1: Khởi động Database & Redis
```bash
sudo systemctl start docker
docker compose up -d
```

### Bước 2: Khởi động Backend Spring Boot
```bash
./mvnw spring-boot:run
```

### Bước 3: Nạp dữ liệu mô phỏng Flash Sale (200 buyers, 20 items)
Chạy script SQL để tạo 200 tài khoản test và set kho về 20 chiếc:
```bash
docker exec -i commercial-postgres psql -U postgres -d VelaWear < k6/seed-flash-sale.sql
```

### Bước 4: Chạy k6 Stress Test (Mặc định 200 Concurrent VUs)
```bash
k6 run k6/checkout-stress-test.js
```

Hoặc tùy chỉnh số lượng VUs qua biến môi trường:
```bash
k6 run -e VUS=200 k6/checkout-stress-test.js
```

---

## 📊 Kết quả kiểm thử thực tế (Benchmark Summary):
- **Virtual Users (VUs):** 200 concurrent buyers.
- **Total HTTP Transactions:** 800 requests (Auth $\rightarrow$ Cart $\rightarrow$ Preview $\rightarrow$ Checkout).
- **`checkout_deadlock_detected`:** **0** (Zero Deadlocks).
- **`checkout_success`:** **20** (Đúng 20 sản phẩm được bán ra).
- **`checkout_insufficient_stock`:** **180** (180 đơn mua sau bị từ chối có kiểm soát).
- **Final DB Stock:** **0** (Zero Overselling).
