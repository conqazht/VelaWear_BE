# ADR-002: File Upload Strategy — Local Storage + Static Resource Serving

## Status
Accepted

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

---

## Implementation Design

### Upload Flow

```
Client
  │
  ├─ POST /api/v1/files
  │    multipart/form-data: { file, folder }
  │
  ▼
FileController
  │  validate folder ∈ {avatars, logos}
  │
  ▼
FileService.store(file, folder)
  │  1. validate file name (not blank, no special characters)
  │  2. validate extension ∈ {jpg, jpeg, png, gif, webp}
  │  3. validate size ≤ 5 MB
  │  4. generate file name: {System.currentTimeMillis()}_{sanitizedName}
  │  5. write file to {base-dir}/{folder}/
  │
  ▼
Response 201: { fileName, folder, fileUrl, size, uploadedAt }

Client uses fileName to update the entity:
  PUT /api/v1/users      { ..., avatar: "1709123456789_photo.jpg" }
  PUT /api/v1/companies  { ..., logo: "1709123456789_logo.png" }
```

### File Naming — Avoiding Conflicts

```
Original:   my photo (2024).jpg
Sanitized:  my_photo_2024_.jpg          ← special characters replaced with _
Stored as:  1709123456789_my_photo_2024_.jpg   ← timestamp prefix added
```

`System.currentTimeMillis()` is used instead of UUID because:
- Human-readable and naturally time-ordered
- Sufficient uniqueness for this use case (no extreme concurrent upload requirements)

### Directory Structure

```
{app.upload.base-dir}/      ← configured in application.yml
├── avatars/
│   └── 1709123456789_photo.jpg
└── logos/
    └── 1709234567890_logo.png
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

Client accesses files at: `GET /uploads/avatars/1709123456789_photo.jpg`

### Validation Rules

| Rule | Reason |
|------|--------|
| File name not blank | `MultipartFile.getOriginalFilename()` may return `""` |
| No special characters | Prevent path traversal (`../`), avoid filesystem errors |
| Extension whitelist | Images only; blocks upload of `.exe`, `.sh`, `.jsp`, etc. |
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

### Why Not Update avatar/logo Directly in POST /files?

Separating two concerns:
1. **File upload** → returns `fileName` (no need to know which user the file belongs to)
2. **Entity update** → `PUT /users` or `PUT /companies` receives `fileName`

**Benefits:**
- `FileController` is fully independent — no dependency on `UserService` or `CompanyService`
- The same upload endpoint can serve any type of image in the future
- Client can preview the file before actually saving it to a user/company

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
      - gif
      - webp
    allowed-folders:
      - avatars
      - logos

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
| Guessing other users' file names | Timestamp prefix provides sufficient randomness; directory listing not exposed |

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

### Trade-offs Accepted
- Local storage is appropriate for the current stage (single server, learning/demo)
- When production scale is needed, implement `CloudFileStorageService implements FileStorageService`

---

## Files Affected

- `feature/file/FileController.java` — POST /files endpoint
- `feature/file/FileService.java` — interface
- `feature/file/FileServiceImpl.java` — validation + file write
- `feature/file/dto/FileUploadResponse.java` — response DTO
- `config/SecurityConfig.java` — permitAll() for /uploads/**, add /api/v1/files to secured routes
- `application.yml` — upload path, size limit, allowed extensions config
