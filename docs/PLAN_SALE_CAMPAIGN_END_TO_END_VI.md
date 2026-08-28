# Kế hoạch triển khai Sale Campaign end-to-end

## 1. Nghiệp vụ đã chốt

- Không còn `Daily Sale` hoặc `sale_price` độc lập. Chỉ có:
  - `STANDARD`: giảm giá theo lịch, không quota, được áp coupon.
  - `FLASH`: giảm giá theo lịch, có quota, không được áp coupon.
- Một campaign chọn được một hoặc nhiều sản phẩm; DB quản lý đến từng biến thể size/màu.
- FLASH bắt buộc có quota tổng; `maxPerCustomer` là tùy chọn trên từng biến thể và cộng dồn qua mọi đơn của khách.
- Không giữ hàng khi thêm vào giỏ. Chỉ giữ khi checkout thành công.
- Chuyển khoản:
  - 15 phút thanh toán.
  - Thêm 30 giây kiểm tra webhook.
  - Sau 15 phút 30 giây mới giải phóng stock/quota.
  - Tiền đến sau khi đã giải phóng được đánh dấu `REFUND_PENDING`, không tự phục hồi đơn.
- COD có sản phẩm Flash được xác nhận quota ngay khi tạo đơn; nếu đơn bị hủy hợp lệ thì hoàn stock và đảo quota/lượt mua đúng một lần.
- Coupon:
  - Dòng `FLASH_SALE` không nằm trong subtotal đủ điều kiện.
  - Dòng `STANDARD_SALE` và `BASE` vẫn được xét theo điều kiện coupon.
- Giá ưu tiên: FLASH còn quota → STANDARD → giá gốc.
- Cấm hai campaign cùng loại trùng thời gian trên cùng biến thể. STANDARD và FLASH được phép trùng; giá Flash phải thấp hơn giá Standard đang chồng thời gian.
- Trạng thái lưu DB: `DRAFT`, `PUBLISHED`, `CANCELLED`.
- `UPCOMING`, `LIVE`, `ENDED` là phase tính từ `startsAt/endsAt`, không lưu DB.
- Quyền sửa:
  - DRAFT: sửa/xóa tự do.
  - PUBLISHED + UPCOMING: sửa toàn bộ, có version và kiểm tra overlap lại.
  - LIVE: chỉ sửa tên/mô tả/banner, tăng quota, kết thúc sớm hoặc kết thúc-và-nhân-bản.
  - ENDED/CANCELLED: chỉ đọc.

## 2. Thiết kế DB và contract

### Các bảng Sale mới

| Bảng | Trách nhiệm chính |
|---|---|
| `sale_campaigns` | `code`, tên, mô tả, banner, `STANDARD/FLASH`, trạng thái, thời gian, `version`, audit và người tạo/publish |
| `sale_campaign_items` | Một dòng trên mỗi variant; lưu giá tham chiếu, giá sale, quota, reserved, sold và giới hạn khách |
| `sale_customer_usages` | Bộ đếm `reserved_quantity` và `purchased_quantity` theo khách + campaign item |
| `sale_allocations` | Liên kết order item với suất Flash; trạng thái `RESERVED`, `CONFIRMED`, `RELEASED`, `REVERSED` |

Quy tắc dữ liệu:

- Unique `(campaign_id, variant_id)`.
- Unique `(campaign_item_id, user_id)` cho customer usage.
- Unique `order_item_id` trong allocation.
- Mọi counter không âm và `reserved + sold <= quota`.
- `promotional_price > 0` và nhỏ hơn `reference_price`.
- FLASH yêu cầu quota khi publish; STANDARD không được có quota/limit.
- `max_per_customer` nullable; nếu có thì phải từ `1..quota`.
- Thời gian sử dụng `TIMESTAMPTZ`, lưu UTC; admin nhập theo `Asia/Ho_Chi_Minh`.
- Phase sử dụng khoảng `[startsAt, endsAt)`.

### Thay đổi bảng hiện có

- `order_items`:
  - Giữ `price` là giá thực trả.
  - Thêm `list_price`.
  - Thêm `price_source`: `BASE | STANDARD_SALE | FLASH_SALE`.
  - Thêm `sale_campaign_item_id`.
  - Snapshot mã/tên campaign để lịch sử đơn không đổi khi admin sửa nội dung hiển thị.
- `orders`:
  - `payment_due_at`.
  - `reservation_expires_at`.
  - `resources_released_at`.
  - `checkout_idempotency_key`.
  - `checkout_request_hash`.
  - Unique `(user_id, checkout_idempotency_key)`.
- Mở rộng payment status với `REFUND_PENDING`.
- Sửa constraint hiện tại để hỗ trợ provider/payment method `SEPAY`.
- Xóa index, constraint và cột `product_variants.sale_price` ở migration contract cuối.
- Không backfill giá sale cũ; người dùng đã chọn xóa toàn bộ.
- Không sửa migration cũ `V1`/`V6`; tạo migration mới từ version tiếp theo hiện tại là V14.

### Contract giá dùng chung

Catalog, PDP, cart và checkout dùng một shape thống nhất:

```text
pricing:
  listPrice
  effectivePrice
  priceSource
  campaignId
  campaignItemId
  campaignCode
  campaignName
  startsAt
  endsAt
  remainingQuota
  maxPerCustomer
  customerRemaining
```

`customerRemaining` chỉ có khi request đã xác thực. Public Sale response kèm `serverTime` để FE tính countdown chính xác.

### API chính

Admin, có RBAC:

- `GET /api/v1/sale-campaigns`
- `GET /api/v1/sale-campaigns/{id}`
- `POST /api/v1/sale-campaigns`
- `PUT /api/v1/sale-campaigns/{id}` với `version`
- `DELETE /api/v1/sale-campaigns/{id}` — chỉ DRAFT
- `POST /api/v1/sale-campaigns/{id}/publish`
- `POST /api/v1/sale-campaigns/{id}/cancel` — UPCOMING
- `PATCH /api/v1/sale-campaigns/{id}/display` — trường hiển thị khi LIVE
- `POST /api/v1/sale-campaigns/{id}/items/{itemId}/increase-quota`
- `POST /api/v1/sale-campaigns/{id}/end`
- `POST /api/v1/sale-campaigns/{id}/end-and-clone`

Public:

- `GET /api/v1/sales?type=STANDARD&phase=LIVE`
- `GET /api/v1/sales?type=FLASH&phase=LIVE,UPCOMING`
- `GET /api/v1/sales/{code}`

Checkout:

- `POST /api/v1/checkout/preview`: đọc giỏ phía server, tính giá/coupon/shipping và trả `pricingFingerprint`.
- `POST /api/v1/checkout`: nhận `pricingFingerprint` và header `Idempotency-Key`; backend vẫn tự tính lại toàn bộ.
- Client không còn là nguồn sự thật của `shippingFee`, giá hoặc số lượng khả dụng.

Error response bổ sung `code` và `data` nhưng giữ tương thích với `ApiResponse` cũ. Các mã chính:

- `INSUFFICIENT_STOCK`
- `FLASH_SALE_SOLD_OUT`
- `FLASH_SALE_ENDED`
- `FLASH_SALE_LIMIT_EXCEEDED`
- `PRICE_CHANGED`
- `CAMPAIGN_OVERLAP`
- `CAMPAIGN_VERSION_CONFLICT`
- `CAMPAIGN_ALREADY_STARTED`
- `PAYMENT_WINDOW_EXPIRED`
- `IDEMPOTENCY_KEY_REUSED`

## 3. Các phase triển khai

### Phase 0 — Khóa contract và tạo khung tài liệu

- Chốt enum, DTO, error code, API và state machine như trên.
- Lập danh sách toàn bộ vị trí đang dùng `salePrice`.
- Tạo khung hai tài liệu FE/BE tiếng Việt và cập nhật dần theo từng phase.
- Xác nhận không ghi đè các thay đổi frontend hiện có trong worktree.

Điều kiện hoàn thành: FE và BE cùng dùng một contract giá và lifecycle thống nhất trên tài liệu.

### Phase 1 — Expand DB và domain backend

- Tạo migration thêm bốn bảng Sale, order snapshot, idempotency và payment expiry.
- Sửa constraint SePay và seed quyền:
  - ADMIN/MANAGER: quản lý campaign.
  - STAFF: chỉ xem.
  - Public: chỉ các GET storefront.
- Tạo entity, enum, repository và validation.
- Tạo demo STANDARD/FLASH mới trong dev seed; bỏ toàn bộ giá sale cũ.
- Chưa drop `sale_price` trong phase này để tránh làm hỏng code khi chuyển đổi dở dang.

Điều kiện hoàn thành: Flyway chạy được trên DB mới và DB đã có dữ liệu; constraint/index được kiểm thử bằng PostgreSQL.

### Phase 2 — BE quản lý campaign và pricing engine

- Xây CRUD admin, publish, cancel, end, clone và live-safe edit.
- Admin chọn một hoặc nhiều product, sau đó chọn toàn bộ hoặc một phần variant.
- Khi publish/update UPCOMING:
  - Khóa variant theo thứ tự ID.
  - Snapshot giá gốc.
  - Kiểm tra variant active, giá hợp lệ, lịch hợp lệ và overlap.
  - Hai admin publish campaign xung đột thì chỉ một transaction thành công.
- Tạo `VariantPricingService` làm nguồn giá duy nhất cho product, variant, search/filter, cart và checkout.
- Storefront sort/filter theo `effectivePrice`; admin product vẫn lọc theo giá gốc.
- Chặn sửa giá gốc, xóa hoặc vô hiệu hóa variant đang thuộc campaign UPCOMING/LIVE; admin phải sửa/cancel campaign trước.
- Flash hết quota:
  - Trang Flash vẫn hiển thị sold out.
  - Catalog/PDP có thể quay về giá STANDARD hoặc BASE để khách mua theo giá thường.

Điều kiện hoàn thành: không còn logic tự tính `salePrice ?? price` nằm rải rác trong service.

### Phase 3 — Checkout, payment và chống race condition

Trong một transaction checkout:

1. Khóa cart của user để tuần tự hóa double-click/cùng tài khoản.
2. Kiểm tra `Idempotency-Key`; request lặp trả lại cùng order.
3. Resolve pricing và so sánh `pricingFingerprint`.
4. Xử lý các variant theo thứ tự ID cố định.
5. Atomic reserve customer usage.
6. Atomic reserve Flash quota.
7. Atomic decrement stock.
8. Atomic consume coupon trên eligible subtotal.
9. Tạo order, order item snapshot và allocation.
10. Xóa cart.

Nếu bất kỳ bước nào thất bại, toàn transaction rollback. Ví dụ quota vừa reserve nhưng stock không đủ thì quota/customer usage cũng được hoàn tác tự động, không có trạng thái giữ dở dang.

Tạo `OrderResourceLifecycleService` dùng chung cho checkout, cancel, SePay IPN và scheduler:

- Mọi transition khóa order row trước.
- `resources_released_at` và conditional update bảo đảm chỉ hoàn tài nguyên một lần.
- Online:
  - `RESERVED → CONFIRMED` khi IPN thành công.
  - `RESERVED → RELEASED` khi hủy, lỗi hoặc quá hạn.
- COD:
  - Tạo đơn là `CONFIRMED`.
  - Hủy hợp lệ chuyển allocation thành `REVERSED`, hoàn stock/quota/usage đúng một lần.
- Payment window:
  - 15 phút thanh toán.
  - 30 giây trạng thái “đang kiểm tra”.
  - Scheduler chạy theo batch, khóa row và release sau 15 phút 30 giây.
- IPN và scheduler tranh cùng một row lock:
  - IPN thắng thì order được xác nhận.
  - Scheduler thắng thì tài nguyên được nhả; IPN đến sau ghi `REFUND_PENDING`.
- Reservation hợp lệ được tạo trước khi campaign kết thúc vẫn được thanh toán đến hạn của nó.
- Campaign kết thúc sớm chỉ chặn checkout mới, không hủy reservation đang hợp lệ.
- Redis chỉ được dùng cache/throttle; PostgreSQL là nguồn quota chính thức.

Điều kiện hoàn thành: toàn bộ test concurrency bằng Testcontainers PostgreSQL vượt qua ổn định.

### Phase 4 — UI quản trị Sale riêng Coupon

Routes:

- `/dashboard/sales`
- `/dashboard/sales/new`
- `/dashboard/sales/[id]`

UI gồm:

- Sidebar “Chương trình giảm giá” nằm ngang hàng, không nằm trong Coupon.
- Danh sách có search, type, status, phase, thời gian, số item và tiến độ quota.
- Form full-page ba bước:
  1. Thông tin/type/thời gian.
  2. Chọn một hoặc nhiều sản phẩm và variant.
  3. Kiểm tra, lưu DRAFT hoặc publish.
- Bảng variant hiển thị SKU, màu, size, giá gốc, giá sale; FLASH thêm quota và giới hạn khách.
- Hỗ trợ bulk apply giá tuyệt đối hoặc phần trăm giảm; DB chỉ lưu giá sale tuyệt đối.
- UPCOMING được sửa toàn bộ.
- LIVE chỉ mở field an toàn, tăng quota, kết thúc sớm và kết thúc-và-nhân-bản.
- Hiển thị rõ lỗi overlap, version conflict và campaign vừa chuyển LIVE.
- API/query Sale nằm file riêng, không đưa vào module Coupon hoặc tiếp tục phình `admin-commerce`.

Điều kiện hoàn thành: admin tạo được campaign chứa nhiều sản phẩm/variant, publish và nhìn thấy đúng trạng thái trên storefront.

### Phase 5 — UI khách hàng, cart và checkout

- `/sale`: các STANDARD campaign đang LIVE.
- `/flash-sale`: FLASH LIVE trước, UPCOMING phía sau.
- Header trỏ đúng hai route riêng.
- Product card/PDP:
  - Giá gốc gạch ngang, giá hiệu lực và badge nguồn giá.
  - Variant được chọn quyết định chính xác giá/quota.
  - FLASH có countdown, quota progress, giới hạn khách và sold-out.
- Cart:
  - Dùng response server làm canonical state sau mỗi mutation.
  - Ghi rõ “Thêm vào giỏ chưa giữ hàng Flash”.
  - Quantity ceiling trên UI chỉ mang tính hỗ trợ; checkout vẫn kiểm tra lại.
- Checkout:
  - Gọi preview trước khi đặt.
  - Hiển thị subtotal đủ điều kiện coupon và dòng Flash bị loại.
  - Tạo `Idempotency-Key` cho mỗi ý định đặt đơn.
  - Khi `409`, refetch cart và chỉ rõ dòng nào hết stock, hết Flash, vượt giới hạn hoặc đổi giá.
  - Không tự giảm quantity hoặc tự chấp nhận giá cao hơn.
- Màn QR:
  - Countdown dựa trên `serverTime/paymentDueAt`.
  - Sau 15 phút chuyển sang “Đang kiểm tra thanh toán” tối đa 30 giây.
  - Sau expiry hiển thị đơn hết hạn; không cho tiếp tục dùng QR như đơn hợp lệ.
- Order detail hiển thị snapshot giá gốc, giá trả và nguồn campaign.
- Flash query override cache mặc định 5 phút:
  - `staleTime` ngắn.
  - Refetch định kỳ khi LIVE.
  - Refetch đúng mốc bắt đầu/kết thúc.
  - Không cần WebSocket trong V1.

Điều kiện hoàn thành: khách thấy cùng một giá từ catalog → PDP → cart → preview → order, hoặc nhận thông báo thay đổi rõ ràng.

### Phase 6 — Contract migration và xóa `sale_price`

- Chuyển toàn bộ BE/FE/test fixture/dev seed sang `pricing`.
- Xóa `salePrice` khỏi entity, request/response, filter và admin product form.
- Tạo migration contract:
  - Drop index sale price.
  - Drop hai constraint sale price.
  - Drop `product_variants.sale_price`.
- Không sửa V1/V6 đã tồn tại; active schema sau khi chạy toàn bộ migration không còn cột này.
- Không backfill dữ liệu giá sale cũ theo lựa chọn đã chốt.

Điều kiện hoàn thành: code runtime không còn tham chiếu `salePrice`; chỉ migration lịch sử có thể còn chuỗi cũ.

### Phase 7 — Kiểm thử, tài liệu và nghiệm thu

- Chạy toàn bộ BE tests, FE lint/typecheck/build, Vitest và Playwright.
- Kiểm tra responsive, dark/light, tiếng Việt/Anh và timezone.
- Kiểm tra migration từ schema hiện tại và tạo DB mới từ đầu.
- Hoàn thiện tài liệu và đối chiếu tài liệu với code thực tế.

## 4. Test bắt buộc

Backend:

- STANDARD/FLASH validation, phase boundary và pricing precedence.
- Publish/update overlap đồng thời.
- Quota 1, hai khách checkout đồng thời: đúng một người thành công.
- Một khách gửi hai checkout đồng thời: không vượt `maxPerCustomer`.
- Reserve quota thành công nhưng stock hết: mọi counter rollback.
- Hai request cancel đồng thời: stock/coupon/quota chỉ hoàn một lần.
- IPN lặp: sold/usage chỉ confirm một lần.
- IPN đối đầu timeout hoặc cancel: chỉ một nhánh thắng.
- COD hủy: allocation chỉ reverse một lần.
- Coupon trong cart BASE + STANDARD + FLASH chỉ tính eligible subtotal.
- Idempotency key lặp trả cùng order; tái sử dụng key với payload khác bị từ chối.
- Nhiều item được xử lý theo thứ tự cố định, không deadlock.

Frontend:

- Bổ sung Vitest, React Testing Library và Playwright.
- Unit/component: pricing mapper, countdown, phase, form STANDARD/FLASH, bulk edit, error mapping.
- E2E:
  - Admin tạo → publish → storefront hiển thị.
  - UPCOMING chuyển LIVE.
  - Mixed cart và coupon.
  - Flash sold out/vượt giới hạn/đổi giá.
  - Double submit.
  - QR hết hạn và trạng thái kiểm tra 30 giây.
  - UI xử lý kết quả của hai buyer tranh quota cuối.

## 5. Tài liệu tiếng Việt

Tạo mới:

- `commercial/docs/SALE_CAMPAIGN_BACKEND.md`
  - Nghiệp vụ, thuật ngữ stock/quota/usage/allocation.
  - ERD Mermaid và giải thích từng bảng.
  - State machine campaign/allocation/payment.
  - Pricing precedence và coupon eligibility.
  - Transaction checkout, atomic update, row lock, rollback.
  - COD, SePay, timeout, IPN muộn và idempotency.
  - API/error examples và test matrix.

- `commercial-fe/docs/SALE_CAMPAIGN_FRONTEND.md`
  - Cấu trúc route admin/storefront.
  - Contract pricing và API/query layer.
  - Luồng catalog → PDP → cart → preview → checkout.
  - Countdown/server time/cache.
  - UX cho race condition và structured error.
  - Hướng dẫn Vitest/Playwright và troubleshooting.

Cập nhật:

- `commercial/docs/DATABASE.md`: ERD, bảng mới, cột order/order item/payment, index, constraint, quan hệ, table count, migration notes và việc xóa `sale_price`.
- `commercial/docs/API_SPEC.md`: campaign, pricing, preview, idempotency và error envelope.
- Checkout `CONTEXT.md`: sửa mô tả idempotency/resource release cho đúng implementation.
- Project status của BE/FE sau khi feature hoàn tất.

## 6. Mặc định kỹ thuật

- Checkout yêu cầu tài khoản đăng nhập; giới hạn khách tính theo `user_id`.
- Không có giới hạn tổng số sản phẩm Flash mỗi đơn trong V1.
- Thời gian 15 phút + 30 giây là application config, không phải field admin.
- Nhiều campaign cùng lúc được phép nếu không đụng cùng variant và cùng type.
- Order luôn lưu snapshot; việc sửa tên/banner campaign không thay đổi lịch sử đơn.
- Giữ nguyên và merge cẩn thận các thay đổi frontend đang tồn tại; không reset hoặc ghi đè worktree của người dùng.
