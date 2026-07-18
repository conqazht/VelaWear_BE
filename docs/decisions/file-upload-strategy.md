# ADR-002: File Upload Strategy — Local Storage + Static Resource Serving

## Status
Accepted, amended by BE-004 avatar governance

## Context

The system needs file upload support for:
- Updating **user avatars**
- Updating **company logos**

Decisions required: where to store files, how to name them, what to validate,
and what to return to the client for subsequent use.

---

## Options Considered

### Option A: Cloud Storage (S3, GCS, Cloudinary)
- Files uploaded directly to a cloud provider (AWS S3, Google Cloud Storage, etc.)
- URLs returned are public cloud URLs
- **Pros:** scalable, built-in CDN, no disk usage on server
- **Cons:** added complexity, requires external dependencies and credentials config,
  not suitable for learning/demo stage — and costs money

### Option B: Local File System — CHOSEN
- Files saved to a server directory, served via Spring's static resource handler
- Simple, no external service dependency, easy to debug
- Easy to upgrade to cloud later by swapping out `FileStorageService`
- Completely free

### Option C: Database BLOB Storage
- Base64-encode files, store in a `LONGBLOB` column
- **Cons:** bloats the database, slow queries, cannot be served directly,
  not scalable → rejected immediately

---

## Decision
**Option B** — Local file system + Spring static resource serving.

When cloud migration is needed, only `FileStorageService` needs to be re-implemented;
the controller and validation layer remain unchanged.

BE-004 narrows the customer avatar contract: generic `POST /api/v1/files` remains
for operator/admin-style uploads, but customer self-avatar update uses
`PUT /api/v1/files/avatar` so backend can bind identity from JWT, update DB and
clean up managed files safely.

---

## Implementation Design

### Upload Flow

```
Client
  │
  ├─ POST /api/v1/files
  │    multipart/form-data: { file, folder }
  │    operator/admin generic upload; ROLE_USER is not granted this permission
  │
  ▼
FileController
  │  validate folder ∈ {avatars, logos}
  │
  ▼
FileService.store(file, folder)
  │  1. validate file name (not blank, no special characters)
  │  2. validate extension ∈ {jpg, jpeg, png, webp}
  │  3. validate size ≤ 5 MB
  │  4. validate content type and image signature
  │  5. generate file name: {UUID}_{sanitizedName}
  │  6. write .tmp then atomic move to {base-dir}/{folder}/
  │
  ▼
Response 201: { fileName, folder, fileUrl, size, uploadedAt }

Customer avatar flow:
  PUT /api/v1/files/avatar multipart/form-data: { file }
    → backend resolves user from JWT
    → lock users row with PESSIMISTIC_WRITE
    → store new file in /uploads/avatars
    → set users.avatar
    → rollback deletes new file; after commit deletes previous managed avatar
```

### File Naming — Avoiding Conflicts

```
Original:   my photo (2024).jpg
Sanitized:  my_photo_2024_.jpg          ← special characters replaced with _
Stored as:  7f2f9b7a-5488-4c1a-b2f7-1cbd9e37f21e_my_photo_2024_.jpg
```

UUID prefix is used because:
- avoids filename collisions better under concurrent uploads
- does not leak upload timing through public URL names
- keeps original sanitized name readable enough for debugging

### Directory Structure

```
{app.upload.base-dir}/      ← configured in application.yml
├── avatars/
│   └── 7f2f9b7a-5488-4c1a-b2f7-1cbd9e37f21e_photo.jpg
└── logos/
    └── 8a82a8de-4d8c-4d1c-87ec-cd1dd55af5d4_logo.png
```

Served via Spring static resources:

```yaml
# application.yml
app:
  upload:
    base-dir: uploads   # relative to working dir, or absolute path

spring:
  web:
    resources:
      static-locations: file:${app.upload.base-dir}/

# SecurityConfig: permitAll() for /uploads/**
```

Client accesses files at:
`GET /uploads/avatars/7f2f9b7a-5488-4c1a-b2f7-1cbd9e37f21e_photo.jpg`

### Validation Rules

| Rule | Reason |
|------|--------|
| File name not blank | `MultipartFile.getOriginalFilename()` may return `""` |
| No special characters | Prevent path traversal (`../`), avoid filesystem errors |
| Extension whitelist | Images only; blocks upload of `.exe`, `.sh`, `.jsp`, etc. |
| Content type + signature validation | Rejects files renamed to image extensions |
| Size ≤ 5 MB | Protects disk space, prevents DoS via large files |
| Folder whitelist | Prevents clients from creating arbitrary directories on the server |

**Path traversal prevention:**

```java
// BAD — vulnerable to attack
Path target = Paths.get(baseDir).resolve(folder).resolve(fileName);

// GOOD — normalize + check prefix
Path target = Paths.get(baseDir).resolve(folder).resolve(fileName).normalize();
if (!target.startsWith(Paths.get(baseDir).normalize())) {
    throw new InvalidRequestException("Invalid file path");
}
```

### Why Customer Avatar Now Updates Directly in PUT /files/avatar

Original strategy separated two concerns:
1. **File upload** → returns `fileName`
2. **Entity update** → another endpoint receives `fileName`

That is still acceptable for operator/admin generic uploads, but it is weaker for
customer avatars because client-controlled `avatar` fields can leave orphan files,
cross-namespace references or accidental deletion risk.

BE-004 therefore uses a dedicated self-scoped endpoint:
- request contains only the file
- user identity comes from JWT
- DB update and file cleanup are coordinated by backend
- previous managed avatar under `/uploads/avatars/*` is deleted after commit
- file created during a rolled-back DB transaction is deleted
- product/review namespaces and external URLs are never deleted by avatar cleanup

---

## Configuration

```yaml
# application.yml
app:
  upload:
    base-dir: uploads
    max-size-bytes: 5242880        # 5 MB
    allowed-extensions:
      - jpg
      - jpeg
      - png
      - webp
    allowed-folders:
      - avatars
      - logos
    avatar:
      reconciliation-cron: ${AVATAR_RECONCILIATION_CRON:0 0 */6 * * *}
      orphan-grace: ${AVATAR_ORPHAN_GRACE:24h}

spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB       # file + metadata overhead
  web:
    resources:
      static-locations: file:${app.upload.base-dir}/
```

**Note:** `spring.servlet.multipart.max-file-size` is a Servlet-layer limit
(returns 413 automatically). The 5 MB limit in `FileService` is a business validation
(returns 400 with a clear message).

---

## Security Considerations

| Risk | Mitigation |
|------|------------|
| Path traversal | `normalize()` + prefix check |
| Executable file upload (`.php`, `.jsp`) | Extension whitelist — images only |
| DoS via large files | Double check: Servlet limit (413) + Service validation (400) |
| Unauthenticated upload | Endpoint requires JWT (`🔒`) |
| Brute-force/self-avatar abuse | Redis `avatar-upload` rate limit: user 5/1h, IP 30/1h, global 300/1m |
| Guessing other users' file names | UUID prefix; directory listing not exposed |
| Deleting wrong namespace | Managed avatar delete only accepts `/uploads/avatars/*` |
| Orphan avatar files | Scheduled reconciliation with grace period and DB recheck before delete |

---

## Consequences

### Positive
- Simple, no external service dependency
- Easy to test locally
- Easy to migrate to S3/GCS later (just replace `FileStorageService`)

### Negative
- Does not scale horizontally: multiple server instances → files only exist on one node
  → Mitigation: use shared volume (NFS) if multi-node is needed
- No built-in CDN
- Upload directory requires separate backup
- In multi-node deployments, avatar reconciliation must run against shared storage
  or be replaced with cloud/object-storage lifecycle policies.

### Trade-offs Accepted
- Local storage is appropriate for the current stage (single server, learning/demo)
- When production scale is needed, implement `CloudFileStorageService implements FileStorageService`

---

## Files Affected

- `feature/file/FileController.java` — POST /files endpoint, PUT /files/avatar endpoint
- `feature/file/FileService.java` — interface
- `feature/file/FileServiceImpl.java` — validation + file write
- `feature/file/AvatarUploadService.java` — self-scoped avatar mutation and cleanup
- `feature/file/AvatarReconciliationJob.java` — orphan managed-avatar cleanup
- `feature/file/dto/FileUploadResponse.java` — response DTO
- `config/SecurityConfig.java` — permitAll() for /uploads/**, add /api/v1/files to secured routes
- `application.yml` — upload path, size limit, allowed extensions config
