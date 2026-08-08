# Lộ trình triển khai Backend

> Được tạo từ đợt audit của **shadcn/improve skill v1.0.0** ngày 2026-07-16.
> Các file tiếng Anh trong `plans/` là **canonical execution context** để Codex
> thực thi trực tiếp, không dùng `improve execute`. Bản tiếng Việt nằm trong
> [`plans/vi/`](vi/) để đọc và đối chiếu; các thuật ngữ IT quan trọng được giữ
> nguyên.

## Baseline lập kế hoạch

- Repository: `conqazht/VelaWear_BE`
- Planned at: commit `2be2362` trên `main`
- Planning branch: `docs/shadcn-improve-plans`
- Phải merge PR tài liệu này vào `main` trước khi tạo implementation branch.
- Live execution status chỉ được cập nhật tại
  [`plans/README.md`](README.md) để tránh English/Vietnamese translation drift.
  `State: TODO` trong từng plan là planned-at metadata bất biến, không được cập
  nhật trong quá trình delivery.
- Mọi Git/GitHub write (commit, push và tạo PR) chỉ được thực hiện khi operator
  hiện tại cho phép rõ ràng; pass test gate không tự tạo quyền thực hiện.

Mỗi executor phải đọc trọn plan tiếng Anh, chạy drift check, tuân thủ STOP
conditions và dùng đúng branch đã chỉ định. Sau khi bạn merge một PR, Codex mới
fetch và fast-forward `main`, kiểm tra clean worktree rồi tạo branch kế tiếp.
Không stack dependent branch trên một PR chưa merge.

## Reconcile dependency drift bắt buộc

Plan sau cố ý phụ thuộc PR trước nên thường sẽ thấy thay đổi so với planned-at
SHA ban đầu. Trước khi sửa source, chạy drift check của plan trên `main` hiện
tại. Nếu in-scope path thay đổi, phải đối chiếu toàn bộ live symbol/citation rồi
cập nhật canonical English plan, mirror tiếng Việt, exact scope/test name và
field `Planned against` của từng plan sang SHA `main` hiện tại **trước khi code**. Commit phần reconcile
trong implementation PR trước source change, hoặc bằng docs-only commit ngay
trước đó. Hai file plan là ngoại lệ duy nhất của source-file allowlist. Không
được execute stale plan hoặc mặc định dependency drift là an toàn.

## Thứ tự thực hiện

| ID | Kế hoạch tiếng Việt | Branch | Priority | Effort | Dependency | Wave |
|---|---|---|---:|---:|---|---:|
| BE-001 | [Mở rộng customer self-service contract](vi/001-expand-customer-self-service-contract.vi.md) | `feature/customer-self-service-contract` | P1 | L | — | 1 |
| BE-002 | [Thu hồi quyền truy cập chéo tài khoản của ROLE_USER](vi/002-revoke-cross-account-role-user-access.vi.md) | `fix/cross-account-role-user-access` | P1 | M | BE-001 và FE-001 đã merge | 1 |
| BE-003 | [Tuần tự hóa cập nhật coupon counter](vi/003-serialize-coupon-counter-updates.vi.md) | `fix/coupon-counter-concurrency` | P1 | M | BE-002 | 2 |
| BE-004 | [Quản trị vòng đời public upload](vi/004-govern-public-upload-lifecycle.vi.md) | `feature/upload-governance` | P1 | L | BE-002 | 2 |
| BE-005 | [Ổn định API error contract](vi/005-stabilize-api-error-contract.vi.md) | `fix/api-error-contract` | P1 | M | BE-002 | 2 |
| BE-006 | [Batch việc tải ảnh trong checkout](vi/006-batch-checkout-image-loading.vi.md) | `perf/checkout-image-batching` | P2 | M | BE-003 | 3 |
| BE-007 | [Batch các query của sale campaign](vi/007-batch-sale-campaign-queries.vi.md) | `perf/sale-campaign-query-batching` | P2 | L | BE-003 | 3 |
| BE-008 | [Loại bỏ N+1 query ở admin list](vi/008-eliminate-admin-list-n-plus-one.vi.md) | `perf/admin-list-fetching` | P2 | L | BE-002 | 3 |
| BE-009 | [Thêm wishlist product-summary contract](vi/009-add-wishlist-product-summary-contract.vi.md) | `feature/wishlist-product-summary` | P2 | M | BE-002 | 3 |
| BE-010 | [Characterization cho các orchestration service lớn](vi/010-characterize-large-orchestration-services.vi.md) | `test/orchestration-characterization` | P2 | L | BE-003 và BE-005–BE-009 | 4 |
| BE-011 | [Refactor authentication orchestration](vi/011-refactor-auth-orchestration.vi.md) | `refactor/auth-orchestration` | P3 | L | BE-001, BE-002, BE-005, BE-010 | 4 |
| BE-012 | [Refactor checkout orchestration](vi/012-refactor-checkout-orchestration.vi.md) | `refactor/checkout-orchestration` | P3 | L | BE-003, BE-006, BE-010 | 4 |
| BE-013 | [Refactor sale-campaign orchestration](vi/013-refactor-sale-campaign-orchestration.vi.md) | `refactor/sale-campaign-orchestration` | P3 | L | BE-007, BE-010 | 4 |
| BE-014 | [Refactor product orchestration](vi/014-refactor-product-orchestration.vi.md) | `refactor/product-orchestration` | P3 | L | BE-007, BE-009, BE-010, BE-013 | 4 |
| BE-015 | [Dọn dependency và tài liệu Backend](vi/015-clean-backend-dependencies-and-docs.vi.md) | `chore/backend-maintenance-docs` | P3 | M | BE-001–BE-014 | 5 |

Xem trạng thái `TODO`, `IN PROGRESS`, `DONE`, `BLOCKED` hoặc `REJECTED` tại
index tiếng Anh canonical.

Reviewer/operator chỉ chuyển trạng thái sau khi PR tương ứng đã merge.
Implementation executor không được thêm thay đổi trạng thái mang tính dự đoán
vào PR của mình.

## Cross-repository sequence bắt buộc

Phần authorization phải đi theo thứ tự **expand → migrate → revoke**:

1. BE-001 thêm endpoint `/me` có ownership binding nhưng tạm giữ legacy route.
2. FE-001 chuyển toàn bộ customer call sang self-scoped contract mới.
3. BE-002 mới gỡ quyền `ROLE_USER` khỏi generic user-ID route.

Đảo thứ tự có thể làm Frontend hỏng hoặc tiếp tục để lại lỗ hổng IDOR.

## Các wave

| Wave | Thứ tự | Checkpoint bắt buộc |
|---:|---|---|
| 0 | PR plan Backend → PR plan Frontend | Cả hai đã merge; hai `main` clean và đồng bộ |
| 1 | BE-001 → FE-001 → BE-002 | Backend verify, Frontend CI suite, full-stack ownership smoke |
| 2 | FE-002 → BE-003 → FE-003 → BE-004 → BE-005 → FE-004 → FE-005 | Full security/correctness checkpoint |
| 3 | BE-006 → BE-007 → BE-008 → BE-009 → FE-006 → FE-007 → FE-008 | Full test, full-stack smoke, query-count assertions |
| 4 | BE-010 → BE-011 → BE-012 → BE-013 → BE-014 → FE-009 → FE-010 | Full regression và full-stack smoke |
| 5 | FE-011 → BE-015 → FE-012 | Final Backend verify, Frontend CI suite, full-stack smoke |

## Flyway migration reservation

- BE-001: `V21__seed_customer_self_service_permissions.sql`.
- BE-002: `V22__revoke_legacy_customer_permissions.sql`.
- BE-004: `V23__scope_customer_file_upload_permissions.sql` để seed quyền
  self-avatar và thu hồi generic upload khỏi `ROLE_USER`; thiết kế v1 không tạo
  upload-governance table.

Nếu version đã tồn tại trên `main`, phải STOP, đồng bộ lại cả hai bản plan và
chọn version trống kế tiếp. Không đổi tên migration đã merge và không phát triển
song song hai branch có migration.

## Preflight trước mỗi PR

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git switch main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$worktree = git status --porcelain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($worktree) { throw "Worktree không clean" }
$head = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$originMain = git rev-parse origin/main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($head -ne $originMain) { throw "HEAD không bằng origin/main" }
git switch -c <branch-trong-plan>
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Kết quả bắt buộc: clean worktree, `HEAD` bằng `origin/main`, branch mới xuất
phát từ commit đó. Nếu sai, STOP; không tự stash, reset hoặc ghi đè.

## Verification gate chuẩn

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Lệnh phải exit 0 và toàn bộ test pass. Các command PowerShell trong plan là
Windows-local gate. Required GitHub Actions check trên Ubuntu tại
`.github/workflows/backend-ci.yml` chạy lệnh tương đương
`./mvnw --batch-mode --no-transfer-progress clean verify` và cũng phải pass
trước khi merge. Ở cuối mỗi wave còn phải chạy Frontend
CI-equivalent suite và full-stack workflow với cả hai ref đều là `main`.

Toàn bộ finding Backend được shadcn/improve chấp nhận đều đã được ánh xạ. Một
số finding liên quan được gom theo cùng invariant; riêng refactor orchestration
lớn được tách thành characterization và từng service để giảm blast radius.

Audit ban đầu có 12 nhóm finding Backend nhưng được chuyển thành 15 plan triển
khai. Số lượng tăng vì authorization phải tách additive/revocation phase,
orchestration lớn được tách thành characterization và bốn refactor độc lập, và
cần thêm backend wishlist-summary companion cho performance finding của
Frontend; các finding error/campaign/maintenance liên quan được gom khi chúng
phải dùng chung một invariant và PR.
