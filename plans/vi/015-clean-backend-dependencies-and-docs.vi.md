# BE-015 — Dọn backend dependency và documentation

> **Nguồn gốc:** Được tạo bởi skill `shadcn/improve` v1.0.0 từ audit tại commit `2be2362`.
> Execution context English canonical có mirror tiếng Việt này. Implement trực tiếp bằng Codex; **không** gọi `improve execute`.

## Hướng dẫn executor

Đọc hết plan. Execute cuối cùng, từ main chứa BE-001–BE-014. Chỉ remove dependency được chứng minh unused, reconcile documentation với code đã ship, và không bao giờ copy value từ `.env` thật. Không mark uncertain OpenSpec work complete.

## Trạng thái

- State: `TODO`
- Risk: Low — dependency/documentation cleanup sau mọi behavior wave
- Category: Maintenance / Documentation / Build hygiene
- Priority / effort / wave: P3 / M / 5
- Planned against: `2be2362` ngày 2026-07-16
- Branch: `chore/backend-maintenance-docs`
- Dependencies: BE-001 đến BE-014
- Migration: không có

## Vì sao

Backend declare MapStruct nhưng không dùng, architecture/upload documentation mô tả state đã bị shipped auth/session/file work supersede, Compose contract reference environment key chưa document, và OpenSpec task list không còn đại diện rõ implemented payment design. Final cleanup giảm false guidance và dependency noise sau mọi behavioral wave.

## Hiện trạng và bằng chứng

- `pom.xml:182-187` declare MapStruct, còn annotation processor `249-269` chỉ gồm Lombok và repository search không tìm MapStruct mapper/reference.
- `docs/PROJECT-RULES.md:345-379` đã ưu tiên explicit/static mapping và yêu cầu justification cho MapStruct.
- `docs/ARCHITECTURE.md:142` nói không có custom filter; `170-176` nói stateless/no server storage và xem upload là future work, conflict với security filter, Redis session, local upload hiện tại.
- `README.md:1-10` chỉ có document link set nhỏ và chưa route operator tới security/API/context material mới.
- `docker-compose.yml:6,15` reference `DB_NAME`; `.env.example` chưa document key đó. Chỉ được thêm placeholder—không bao giờ real value.
- `openspec/changes/add-sandbox-payments-notifications/tasks.md:1-58` còn unchecked dù một phần SePay tồn tại theo design khác; status phải phân biệt implemented, superseded, real backlog thay vì falsely check mọi box.

## Lệnh

Chạy drift check này trước implementation:

```powershell
git diff --stat 2be2362..HEAD -- pom.xml README.md .env.example docker-compose.yml docs openspec src/main/java src/test
```

| Gate | Exact command | Expected result |
|---|---|---|
| Compose | `docker compose --env-file .env.example config --quiet` | Exit 0, không output, không start service |
| OpenSpec | `openspec validate --all --strict --no-interactive` | Exit 0 |
| Dependency | `.\mvnw.cmd --batch-mode --no-transfer-progress dependency:tree` | Exit 0; không direct MapStruct dependency sau approved removal |
| Markdown links | Chạy exact PowerShell checker trong Test plan | Exit 0, không broken-link output |
| Full | `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Exit 0 |
| Worktree | `git status --short` | Chỉ exact allowlist file xuất hiện |
| Scope | `git diff --name-only -- .` | Mọi printed path đều allowlisted; nếu không thì STOP |

## Phạm vi

### Trong phạm vi

- Remove unused MapStruct dependency/config nếu vẫn unused; sửa README/doc navigation và stale architecture/upload/auth claim; thêm safe `DB_NAME` example key; reconcile OpenSpec status trung thực; validate link/spec/build.

### Exact file allowlist

- Build/setup: `pom.xml`, `.env.example`, `README.md`.
- Canonical docs: `docs/API_SPEC.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT-RULES.md`, `docs/PROJECT-STATUS.md`, `docs/OTP_SECURITY_FLOW_VI.md`, `docs/RACE_CONDITION_TESTING_VI.md`, `docs/STOREFRONT_CATALOG_UX_BACKEND_VI.md`, `docs/decisions/file-upload-strategy.md`, `docs/decisions/authorization-permission-strategy.md`, `docs/decisions/refresh-token-strategy.md`.
- Feature contexts: `src/main/java/vn/conganh/commercial/feature/auth/CONTEXT.md`, `src/main/java/vn/conganh/commercial/feature/file/CONTEXT.md`, `src/main/java/vn/conganh/commercial/feature/checkout/CONTEXT.md`, `src/main/java/vn/conganh/commercial/feature/product/CONTEXT.md`, và dependency-delivered `src/main/java/vn/conganh/commercial/feature/salecampaign/CONTEXT.md` nếu tồn tại trên final main.
- OpenSpec: `openspec/changes/add-sandbox-payments-notifications/tasks.md`.
- `docker-compose.yml` chỉ là read-only evidence và không được đổi. Không Java code, test, migration, lockfile, hay dependency khác được đổi nếu chưa reconcile plan.

### Ngoài phạm vi

- Feature/refactor work, dependency upgrade không liên quan removal, real secret/value change, mutate production environment, rewrite product requirement, hoặc claim incomplete OpenSpec task đã done.

## Git workflow

**Authorization gate:** Mọi Git/GitHub write (commit, push và tạo PR) chỉ được thực hiện khi operator hiện tại cho phép rõ ràng. Việc pass verification gate là điều kiện cần, không tự tạo quyền thực hiện các thao tác này.

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git switch main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$worktree = git status --porcelain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($worktree) { throw "Worktree is not clean" }
$head = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$originMain = git rev-parse origin/main
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($head -ne $originMain) { throw "HEAD does not equal origin/main" }
git switch -c chore/backend-maintenance-docs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Dừng khi base bẩn/diverge, branch đã tồn tại, hoặc BE-001–BE-014 chưa hoàn tất. Không stash/reset. Chỉ commit/push sau khi mọi gate pass, rồi publish PR theo operator workflow.

Sau khi dependency merge, nếu drift chạm in-scope path, update citation, allowlist, command, `Planned against` SHA, và English mirror trước khi edit. Không execute stale plan.

## Các bước implementation

### 1. Re-audit dependency usage trên final main

Search source/test/plugin/generated config cho `org.mapstruct`, `@Mapper`, mapper processor, và transitive build assumption. Inspect dependency tree trước removal.

**Verify**: chạy negative search dưới đây. Không output trả exit 0; match bất kỳ trigger STOP và giữ MapStruct.

```powershell
rg -n "org\.mapstruct|@Mapper|mapstruct-processor" src
if ($LASTEXITCODE -eq 0) { exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
exit 0
```

### 2. Chỉ remove MapStruct config được chứng minh unused

Delete direct dependency/property/plugin entry chỉ thuộc MapStruct, giữ Lombok/compiler config hợp lệ. Không opportunistically upgrade library khác.

**Verify**: chạy block dưới đây → exit 0 không MapStruct match, sau đó full build gate exit 0.

```powershell
$mapstruct = .\mvnw.cmd --batch-mode --no-transfer-progress dependency:tree | Select-String "org.mapstruct"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($mapstruct) {
  $mapstruct
  throw "MapStruct still present"
}
exit 0
```

### 3. Reconcile environment và README contract

Thêm chính xác dòng an toàn, không phải secret `DB_NAME=VelaWear` vào `.env.example`, khớp convention `DB_URL`/Compose hiện có; `docker compose ... config` phải render `POSTGRES_DB: VelaWear`. Mở rộng README link/setup note tới API spec, architecture, security/OTP flow, feature context, ADR, OpenSpec, plan index khi phù hợp.

**Verify**: `docker compose --env-file .env.example config --quiet` → exit 0 không output và không container start; exact Markdown checker bên dưới cũng exit 0.

### 4. Sửa architecture và feature documentation

Update stale statement về filter, stateless auth, Redis session/security state, upload lifecycle, avatar/product/review file responsibility, migration, metric, và service boundary từ wave. Thay stale sibling-directory storefront companion link bằng `https://github.com/conqazht/VelaWear_FE/blob/main/docs/STOREFRONT_CATALOG_UX_FRONTEND_VI.md` để backend-only checkout vẫn hợp lệ. Ưu tiên link canonical feature `CONTEXT.md`/ADR thay duplicate detail.

**Verify**: `rg -n "Redis|securityVersion|avatar-upload|reconciliation|CheckoutFingerprintService|ProductResponseAssembler|metrics" docs src/main/java/vn/conganh/commercial/feature -g CONTEXT.md` → mỗi shipped responsibility có trong canonical document và không stale stateless/no-filter claim.

### 5. Reconcile OpenSpec và project status

Review `add-sandbox-payments-notifications` từng task với code/test/active design. Chỉ mark demonstrated completion; label superseded assumption với link/reason và giữ unmet backlog rõ. Update project status và ADR/OpenSpec reference.

**Verify**: `openspec validate --all --strict --no-interactive` → exit 0; task text label rõ demonstrated completion, superseded assumption, remaining backlog.

### 6. Chạy final repository documentation/build gate

Check Markdown link/path, formatting, Maven dependency/build/test, diff hygiene. Review staged diff cho credential, local path, generated artifact, accidental plan/source drift.

**Verify**: chạy từng gate theo thứ tự; build/diff exit 0 và mọi printed path allowlisted.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git status --short
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --name-only -- .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

## Test plan

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress dependency:tree
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
openspec validate --all --strict --no-interactive
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker compose --env-file .env.example config --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git status --short
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --name-only -- .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Chạy exact PowerShell Markdown-link checker sau từ repository root; expected result là không output và exit 0:

```powershell
$errors = @()
Get-ChildItem -Recurse -File -Filter *.md |
  Where-Object { $_.FullName -notmatch '(?:\\|/)(?:target|\.git)(?:\\|/)' } |
  ForEach-Object {
  $file = $_
  $text = Get-Content -LiteralPath $file.FullName -Raw
  [regex]::Matches($text, '\[[^\]]+\]\(([^)]+)\)') | ForEach-Object {
    $target = $_.Groups[1].Value.Trim().Trim('<','>')
    if ($target -and $target -notmatch '^(?:[a-z][a-z0-9+.-]*:|#)') {
      $pathPart = ($target -split '#', 2)[0]
      if ($pathPart) {
        $resolved = Join-Path $file.DirectoryName ([uri]::UnescapeDataString($pathPart))
        if (-not (Test-Path -LiteralPath $resolved)) {
          $errors += "$($file.FullName) -> $target"
        }
      }
    }
  }
}
if ($errors.Count) {
  $errors
  exit 1
}
exit 0
```

## Tiêu chí hoàn thành

- MapStruct chỉ removed nếu vẫn unused; build và annotation processing còn đúng.
- `.env.example` document mọi Compose-required key bằng safe placeholder và không expose secret.
- README, architecture, feature context, ADR, project status, OpenSpec mô tả final main chính xác.
- OpenSpec validation, link review, dependency tree, full backend verification, diff hygiene pass.
- `docker compose --env-file .env.example config --quiet` và exact Markdown-link checker exit 0 mà không start service hay expose secret.

## STOP conditions

- MapStruct được dùng trên final main hoặc removal đổi generated code/build behavior.
- Correct value cho `.env.example` cần copy real/local secret; dùng placeholder hoặc stop xin operator input.
- OpenSpec implementation/supersession status không chứng minh được từ code/test/approved decision.
- Cleanup phát hiện behavioral defect hoặc cần feature code; tách plan/PR mới.

## Ghi chú maintenance

Xem docs, `.env.example`, OpenSpec là executable contract được review cùng code change. Ưu tiên canonical link thay duplicate architecture prose. Sau khi merge, reviewer/operator chỉ cập nhật live status trong `plans/README.md`; không sửa `State` bất biến của plan này. Giữ mirror tiếng Việt đồng bộ cấu trúc.
