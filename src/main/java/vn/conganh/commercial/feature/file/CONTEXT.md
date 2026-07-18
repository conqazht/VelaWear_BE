# File Feature Context

## Purpose

File feature quản lý upload ảnh nội bộ, lưu trên local filesystem và serve public
qua `/uploads/**`.

Từ BE-004, customer avatar không còn đi qua generic upload rồi tự set `avatar`
ở profile nữa. Avatar dùng contract self-scoped riêng để backend tự bind user từ
JWT, cập nhật DB trong transaction và dọn file cũ đúng namespace.

## API

- `POST /api/v1/files`
  - Content type: `multipart/form-data`
  - Parts: `file`, `folder`
  - Dành cho operator/admin flow có permission `UPLOAD_FILE`.
  - Không dùng cho customer tự đổi avatar; `ROLE_USER` không còn được cấp quyền
    generic upload này.

- `PUT /api/v1/files/avatar`
  - Content type: `multipart/form-data`
  - Parts: `file`
  - Self-scoped: user lấy từ JWT, request không nhận `userId`, `folder` hoặc
    avatar URL.
  - Response là `UserResponse` mới nhất sau khi cập nhật avatar.

`POST /files` trả `FileUploadResponse` với file name, folder, public URL, size và
timestamp. `PUT /files/avatar` ghi file mới vào `/uploads/avatars/*`, lock row
user bằng `PESSIMISTIC_WRITE`, cập nhật `users.avatar`, xóa file mới nếu DB
rollback và xóa previous managed avatar sau commit.

## Configuration

All runtime upload settings are bound from `app.upload` through `UploadProperties`:

- `base-dir`
- `url-prefix`
- `max-size-bytes`
- `allowed-extensions`
- `allowed-folders`
- `avatar.reconciliation-cron`
- `avatar.orphan-grace`

Defaults are declared in `application.yaml` through environment placeholders. Do not hardcode upload limits, folder names, or extension lists in service logic.

## Security

- `/uploads/**` is public static resource access.
- `POST /api/v1/files` requires JWT and the `UPLOAD_FILE` RBAC permission; BE-004
  removes this permission from `ROLE_USER`.
- `PUT /api/v1/files/avatar` requires JWT and the `UPDATE_MY_AVATAR` permission.
- Avatar upload is rate-limited by Redis policy `avatar-upload`: `user 5/1h`,
  `IP 30/1h`, `global 300/1m`.
- `FileServiceImpl` validates folder whitelist, extension whitelist, content type,
  image signature, size limit, file name, and path traversal before writing.
- Stored file name uses UUID prefix and writes through `.tmp` then atomic move when
  supported by the filesystem.
- Managed deletion only touches `/uploads/avatars/*`; external URLs and other
  upload namespaces such as `/uploads/products/*` or `/uploads/reviews/*` are never
  deleted by avatar replacement.
- `AvatarReconciliationJob` scans only managed avatar namespace every 6h by
  default, applies 24h grace, rechecks DB reference before delete and emits
  `security.avatar.cleanup` metrics.
