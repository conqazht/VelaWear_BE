# Quản lý nội dung song ngữ Catalog và Sale

> Tài liệu tiếng Việt này mô tả cách backend lưu, đọc, quản trị và seed nội dung
> `vi`/`en`. Đây là tài liệu vận hành dành cho người tiếp tục phát triển dự án.

## 1. Mục tiêu

VelaWear hỗ trợ hai locale được bật:

| Locale | Vai trò |
|--------|---------|
| `vi` | Locale mặc định và bắt buộc |
| `en` | Locale tiếng Anh được bật |

Các chuỗi giao diện cố định như nút, trạng thái và thông báo nằm trong từ điển
i18n của frontend. Nội dung do Admin quản lý nằm trong database:

- Product: tên, slug, mô tả ngắn/dài, chất liệu, bảo quản và SEO.
- Category: tên, slug, mô tả và SEO.
- Sale Campaign: tên và mô tả.

Màu và size tiếp tục là dữ liệu kỹ thuật. Không tạo `color_translations` hoặc
`size_translations`; frontend ánh xạ nhãn như `Black`, `ONE SIZE` sang ngôn ngữ
đang chọn. SKU, campaign code, coupon code và enum trạng thái cũng không dịch.

Review, địa chỉ, ghi chú vận hành và snapshot trong đơn hàng là dữ liệu lịch sử
hoặc do người dùng tạo. Backend không tự dịch những giá trị này.

## 2. Mô hình database

Ba bảng translation dùng khóa ghép giữa entity và locale:

```text
products       1 --- N product_translations       N --- 1 locales
categories     1 --- N category_translations      N --- 1 locales
sale_campaigns 1 --- N sale_campaign_translations N --- 1 locales
```

- Xóa Product, Category hoặc Sale Campaign sẽ cascade bản dịch tương ứng.
- Không thể xóa locale đang được bản dịch tham chiếu.
- Product/Category slug duy nhất trong từng locale.
- Sale dùng `code` ổn định trong URL và audit nên không cần localized slug.
- `sale_campaigns.name/description` vẫn được giữ làm dữ liệu tương thích và
  fallback cuối; nội dung hiển thị theo locale nằm trong bảng translation.
- `banner_url` dùng chung cho cả hai ngôn ngữ.

Locale resolution của public API theo thứ tự:

1. Query `?locale=vi|en`.
2. Header `Accept-Language`.
3. Locale mặc định `vi`.

Nếu bản dịch được yêu cầu chưa tồn tại, backend fallback về `vi`, sau đó mới
fallback về dữ liệu core để tránh trang trắng trong giai đoạn chuyển đổi.
Header `Accept-Language` được chọn theo q-weight, bỏ qua range `q=0` và fallback
từ locale vùng như `en-US` về `en`.

URL storefront hiện không có `/vi` hoặc `/en`. Khi mở Product/Category bằng slug
của một locale rồi đổi ngôn ngữ, backend vẫn tìm entity qua slug của bất kỳ bản
dịch nào và chỉ sau đó mới render nội dung theo locale mới. Cách này tránh 404
cho link English đang mở khi chuyển sang VI (và chiều ngược lại).

Cart và Checkout dùng cùng quy tắc locale. Khi checkout thành công,
`product_name`, `product_slug` và `sale_campaign_name` được lưu thành snapshot
theo ngôn ngữ của request; lịch sử đơn hàng không tự đổi khi người dùng chuyển
locale sau này.

## 3. API quản trị bản dịch

Storefront chỉ nhận nội dung đã resolve cho một locale. Admin sử dụng subresource
riêng để xem và lưu đồng thời nhiều bản dịch.

### Product

```text
GET    /api/v1/products/{id}/translations
PUT    /api/v1/products/{id}/translations
DELETE /api/v1/products/{id}/translations/{locale}
```

Body của `PUT`:

```json
{
  "translations": [
    {
      "localeCode": "vi",
      "name": "Áo thun cotton thiết yếu",
      "slug": "ao-thun-cotton-thiet-yeu",
      "shortDescription": "Áo thun cotton mềm mại.",
      "description": "Nội dung chi tiết tiếng Việt.",
      "material": "100% cotton",
      "careInstruction": "Giặt máy bằng nước lạnh.",
      "seoTitle": "Áo thun cotton thiết yếu",
      "seoDescription": "Mô tả SEO tiếng Việt."
    },
    {
      "localeCode": "en",
      "name": "Essential Cotton Tee",
      "slug": "essential-cotton-tee",
      "shortDescription": "A soft everyday cotton tee.",
      "description": "English product description.",
      "material": "100% cotton",
      "careInstruction": "Machine wash cold.",
      "seoTitle": "Essential Cotton Tee",
      "seoDescription": "English SEO description."
    }
  ]
}
```

### Category

```text
GET    /api/v1/categories/{id}/translations
PUT    /api/v1/categories/{id}/translations
DELETE /api/v1/categories/{id}/translations/{locale}
```

Mỗi phần tử Category gồm `localeCode`, `name`, `slug`, `description`,
`seoTitle`, `seoDescription`.

### Sale Campaign

```text
GET    /api/v1/sale-campaigns/{id}/translations
PUT    /api/v1/sale-campaigns/{id}/translations
DELETE /api/v1/sale-campaigns/{id}/translations/{locale}?version={version}
```

Body của `PUT` có optimistic version:

```json
{
  "version": 3,
  "translations": [
    {
      "localeCode": "vi",
      "name": "Flash Sale nổi bật",
      "description": "Ưu đãi số lượng giới hạn."
    },
    {
      "localeCode": "en",
      "name": "Featured Flash Sale",
      "description": "Limited-quantity deals."
    }
  ]
}
```

Response Sale trả `version` mới cùng danh sách `translations`. Không được xóa
bản `vi`; thao tác dùng version cũ nhận lỗi conflict như các thao tác sửa Sale
khác. Endpoint create/update core cũ vẫn tương thích; Admin quản lý nội dung
song ngữ qua các subresource trên.

Campaign `DRAFT` hoặc `PUBLISHED` chưa kết thúc cho phép sửa translation để cập
nhật nội dung hiển thị. Campaign `CANCELLED`/`ENDED` là lịch sử chỉ đọc.
`end-and-clone` sao chép toàn bộ translation VI/EN sang draft mới; tên VI mới
trong request được dùng cho bản VI của draft kế tiếp.

## 4. Bật/tắt nhanh trong Admin

Product, Category, Brand và Product Variant có endpoint
`PATCH /api/v1/{resource}/{id}/status` với body `{ "status": "ACTIVE" }` hoặc
`{ "status": "INACTIVE" }`.

- Product: `DRAFT/INACTIVE -> ACTIVE`, `ACTIVE -> INACTIVE`.
- Category và Brand: `ACTIVE <-> INACTIVE`.
- Product Variant: chỉ `ACTIVE <-> INACTIVE`.
- Không dùng toggle để ghi đè trạng thái nghiệp vụ đặc biệt như `OUT_OF_STOCK`
  hoặc `DISCONTINUED`; các trạng thái này đi qua form cập nhật đầy đủ.
- Guard campaign đang chạy vẫn được kiểm tra trước khi đổi Product/Variant.

## 5. Development seed

Thứ tự repeatable migration:

| File | Trách nhiệm |
|------|-------------|
| `R__1`–`R__2` | Tạo user, catalog và dữ liệu commerce nền |
| `R__3_dev_large_mock_data.sql` | Reconcile mock order/item/payment theo khóa ổn định, không chọn order ngẫu nhiên |
| `R__4_dev_cong_anh_data.sql` | Tạo fixture dashboard/order của tài khoản Công Anh |
| `R__5_dev_catalog_i18n_data.sql` | Reconcile đầy đủ bản dịch `vi`/`en` |
| `R__6_dev_sale_campaign_data.sql` | Tạo Sale demo và sửa snapshot giá sale cũ |

`R__5` bảo đảm:

- 6 Product hiện tại có đủ 12 translation rows.
- 14 Category hiện tại có đủ 28 translation rows.
- Slug không trùng trong cùng locale, kể cả các cặp danh mục gần nghĩa như
  `dresses`/`dam`, `accessories`/`phu-kien`, `jackets`/`ao-khoac`.
- Entity được tìm bằng base slug hoặc alias VI/EN đã có. Vì vậy R5 vẫn phục hồi
  bản EN nếu Admin đã đồng bộ core slug sang VI.
- Trước khi đặt `vi` làm default, R5 bỏ cờ default khỏi locale khác để không vi
  phạm partial unique index trong quá trình reconcile.
- Upsert theo base slug và `(entity_id, locale_code)` với `DO UPDATE`; chỉnh nội
  dung trong file seed sẽ cập nhật database khi Flyway chạy lại.

`R__6` tạo bốn tình huống phục vụ cả storefront và Admin:

| Code | Loại/trạng thái | Mục đích |
|------|-----------------|----------|
| `DEV-STANDARD-EVERYDAY` | STANDARD, PUBLISHED, LIVE | Trang Sale và giá giảm dài hạn |
| `DEV-FLASH-DEAL` | FLASH, PUBLISHED, LIVE | Quota và giới hạn mỗi khách |
| `DEV-DRAFT-WEEKEND` | STANDARD, DRAFT | Luồng chỉnh sửa/xuất bản Admin |
| `DEV-HISTORICAL-SALE` | STANDARD, PUBLISHED, ENDED | Màn hình lịch sử |

Hai campaign LIVE dùng cửa sổ cố định từ năm 2025 đến hết năm 2099. Không dùng
`CURRENT_TIMESTAMP + 30 days`, vì repeatable migration chỉ chạy lại khi checksum
file thay đổi; dữ liệu demo nếu dùng thời gian tương đối sẽ tự hết hạn nhưng
Flyway không tự gia hạn.

Giá giảm cũ trong 7 order item được chuyển sang snapshot đúng:

```text
list_price            = reference_price của campaign item
price                 = promotional_price thực trả
subtotal              = price * quantity
price_source          = STANDARD_SALE
sale_campaign_item_id = item tạo ra giá
sale_campaign_code    = DEV-STANDARD-EVERYDAY
sale_campaign_name    = snapshot tên campaign
```

Không sửa tổng tiền đơn vì các seed cũ đã tính subtotal theo giá thực trả.
`VW-DEV-1001` được đặt trong cửa sổ và liên kết với
`DEV-HISTORICAL-SALE`; sáu order item còn lại dùng campaign STANDARD đang chạy.

R6 không update campaign nếu business fields không đổi, nên chạy lại không làm
tăng optimistic `version` hoặc thay `created_at`. Nếu seed phát hiện drift thật,
nó reconcile dữ liệu và tăng version đúng một lần.

R3 tạo đúng một BASE item cho từng `VW-MOCK-*`, chỉ chọn trong nhóm mock order,
lấy giá trực tiếp từ variant rồi tính lại `orders.subtotal`, `final_amount` và
payment. Chạy lại block order/item/payment không thêm item hoặc payment và không
thể chạm vào bảy sale order fixture.

## 6. Quy tắc khi thêm seed mới

1. Product/Category mới phải dùng base slug ổn định và thêm cả `vi`, `en` vào
   `R__5` trong cùng thay đổi.
2. Campaign/item mới dùng `code` và `sku`; không hard-code database ID.
3. Dùng `ON CONFLICT ... DO UPDATE` cho dữ liệu có thể được hiệu chỉnh.
4. Giá item lấy `product_variants.price` làm `reference_price`; giá khuyến mãi
   phải lớn hơn 0 và nhỏ hơn giá tham chiếu.
5. STANDARD không có quota; FLASH phải có quota và `max_per_customer <= quota`.
6. Không sửa migration versioned đã được chia sẻ; luôn tạo migration mới.
7. Không dùng dữ liệu random cho fixture có acceptance test hoặc quan hệ audit.

## 7. Kiểm tra

Chạy test seed trên PostgreSQL Testcontainers:

```powershell
.\mvnw.cmd -Dtest=DevSeedDataIntegrationTest test
```

Test phải chứng minh:

- `vi` là mặc định, `en` được bật.
- Không Product/Category/Sale seed nào thiếu `vi` hoặc `en`.
- Không có localized slug trùng.
- Có cả STANDARD và FLASH đang LIVE; Flash có quota hợp lệ.
- `reference_price` khớp giá variant và promotional price nhỏ hơn giá gốc.
- 7 order item sale có snapshot/link campaign chính xác.
- Tổng item của mỗi mock order bằng header subtotal; payment bằng final amount.
- Chạy lại block mock order và trực tiếp `R__5`/`R__6` không tăng dữ liệu.
- R5 phục hồi EN khi core slug dùng VI và sửa lại `vi` default khi EN bị đặt
  nhầm; R6 chỉ bump version khi có drift business field.

Khi thay đổi schema hoặc API, cập nhật đồng thời `DATABASE.md`, `API_SPEC.md`,
`FLYWAY.md`, `SALE_CAMPAIGN_BACKEND.md` và tài liệu này.
