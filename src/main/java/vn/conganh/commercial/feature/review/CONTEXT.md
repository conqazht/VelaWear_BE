# Review Feature Context

Module `review` quản lý review append-only gắn với User và đúng OrderItem đã mua.
Edit, delete và moderation không thuộc phạm vi hiện tại.

## API công khai và API của khách hàng

| Method | Endpoint | Scope |
|---|---|---|
| `GET` | `/api/v1/reviews/product/{productId}` | Public DTO, filter sao, sort whitelist, page 1-based |
| `GET` | `/api/v1/reviews/product/{productId}/summary` | Public summary, đủ count 1–5 |
| `GET` | `/api/v1/reviews/me` | JWT principal, tùy chọn `orderId` |
| `POST` | `/api/v1/reviews` | JWT principal, multipart JSON + tối đa 5 ảnh |

Các API `/reviews`, `/reviews/user/{userId}`, `/reviews/order/{orderId}` và
`/reviews/order-item/{orderItemId}` là API quản trị cũ, tiếp tục được RBAC bảo vệ.

## Invariant nghiệp vụ

- Identity lấy từ JWT subject; request tạo review không nhận `userId`.
- OrderItem phải thuộc User hiện tại. ID của OrderItem user khác được che bằng 404.
- Order phải có `status = COMPLETED`.
- `rating` từ 1 đến 5; `comment` tối đa 1.000 ký tự.
- Một User chỉ review một lần cho một OrderItem. Service kiểm tra sớm và unique
  constraint `uq_reviews_user_order_item` chặn concurrent duplicate.
- `PublicReviewResponse` không được thêm `userId`, `orderId`, `orderCode` hoặc
  `orderItemId`.

## Ảnh review

- Chỉ JPG/JPEG/PNG/WebP, tối đa 5 ảnh và 5 MB/ảnh.
- Extension, MIME và magic signature phải khớp.
- Server tạo UUID, ghi `<uuid>.tmp`, atomic move sang `<uuid>.<ext>` trong
  `<app.upload.base-dir>/reviews`.
- Generic `/files` từ chối `folder=reviews`.
- Transaction rollback xóa file đã tạo; job cleanup chỉ xóa orphan cũ hơn 24 giờ.
- DB chỉ lưu URL `/uploads/reviews/<uuid>.<ext>`.

## Mã lỗi ổn định

- `409 REVIEW_ORDER_NOT_COMPLETED`: đơn chưa hoàn tất.
- `409 REVIEW_ALREADY_EXISTS`: duplicate thường hoặc race tại unique constraint.
- `404`: OrderItem thiếu hoặc không thuộc principal.
- `400`: request/rating/comment/image/sort sai.
- `413`: vượt Servlet multipart limit.

Chi tiết contract, sequence, cấu hình và troubleshooting:
[Storefront Catalog UX — Backend](../../../../../../../../docs/STOREFRONT_CATALOG_UX_BACKEND_VI.md).

## Khi sửa

- Giữ public DTO riêng với owner/admin DTO.
- Không tin URL, identity hoặc stored filename từ client.
- Thêm format ảnh phải cập nhật MIME/signature test và giới hạn Servlet.
- Đổi storage/transaction phải giữ rollback test, orphan cleanup test và duplicate
  race test.
- Edit/delete/moderation cần OpenSpec change riêng cùng quyết định audit/retention.
