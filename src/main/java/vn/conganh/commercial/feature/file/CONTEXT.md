# File Feature Context

## Purpose

The file feature exposes a JWT-protected upload endpoint for image files used by user avatars and company logos. It stores files on the local filesystem and serves them under `/uploads/**`.

## API

- `POST /api/v1/files`
- Content type: `multipart/form-data`
- Parts:
  - `file`: uploaded image
  - `folder`: configured target folder such as `avatars` or `logos`

The endpoint returns `FileUploadResponse` with the stored file name, folder, public URL, file size, and upload timestamp. It does not update user or company records directly; clients use the returned `fileName` in the relevant entity update endpoint.

## Configuration

All runtime upload settings are bound from `app.upload` through `UploadProperties`:

- `base-dir`
- `url-prefix`
- `max-size-bytes`
- `allowed-extensions`
- `allowed-folders`

Defaults are declared in `application.yaml` through environment placeholders. Do not hardcode upload limits, folder names, or extension lists in service logic.

## Security

- `/uploads/**` is public static resource access.
- `POST /api/v1/files` requires JWT and the `UPLOAD_FILE` RBAC permission.
- `FileServiceImpl` validates folder whitelist, extension whitelist, size limit, file name, and path traversal before writing.
