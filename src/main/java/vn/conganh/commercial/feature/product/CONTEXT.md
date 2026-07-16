# Product Feature Context

Module `product` quản lý thông tin Product gốc trong bảng `products` và giữ API
quản trị `/api/v1/products`.

## Invariant

- `sku` của Product Variant và `slug` gốc của Product được tạo một lần, không sửa
  qua update thông thường.
- Xóa Product là soft archive: đặt `status = ARCHIVED` và `deletedAt`.
- Product Variant, tồn kho và Sale Campaign có workflow riêng; Product không tự
  tính giá khuyến mãi.
- Nội dung storefront resolve theo `locale`, `Accept-Language`, rồi fallback `vi`
  và field core.

## Storefront catalog

API công khai mới nằm ở package sibling `feature.storefrontcatalog`:

```text
GET /api/v1/storefront/products
```

Endpoint này không thay thế API quản trị `/products`. Nó:

- chỉ nạp Product, Category và Product Variant `ACTIVE`, chưa soft-delete;
- nhận `q`, nhiều `categorySlugs`, `colorIds`, `sizeIds`, khoảng giá, sort và page
  1-based;
- áp dụng OR trong một facet, AND giữa các facet;
- bắt buộc color, size và effective price khớp trên cùng variant;
- dùng `VariantPricingService` cho Base/Standard/Flash và quota;
- trả `result`, `meta`, category/color/size counts và `priceRange`.

Giá đại diện là Offer còn khớp có `effectivePrice` thấp nhất. Frontend phải dùng
`pricing.effectivePrice`, không dùng `price` hoặc field sale legacy để suy ra giá
khách trả.

Chi tiết contract, featured sort, facet semantics, hiệu năng và troubleshooting:
[Storefront Catalog UX — Backend](../../../../../../../../docs/STOREFRONT_CATALOG_UX_BACKEND_VI.md).

## Khi sửa

- Mọi thay đổi filter/sort phải thêm test same-variant và effective-price.
- Không đưa storefront sort vào `Pageable` của entity Product.
- Nếu đổi `ProductResponse`, kiểm tra Collection/Search/PDP và tài liệu API.
- Nếu tăng quy mô lớn hơn snapshot hiện tại, đo P95/heap trước khi đẩy logic facet
  xuống database.
