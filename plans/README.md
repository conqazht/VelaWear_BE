# Backend Implementation Plans

> Generated from the **shadcn/improve skill v1.0.0** audit on 2026-07-16.
> These English files are the canonical execution context. Implementation is
> performed directly by Codex, **not** by `improve execute`. Vietnamese reader
> copies are under [`plans/vi/`](vi/), with a Vietnamese roadmap in
> [`plans/README.vi.md`](README.vi.md).

## Planning baseline

- Repository: `conqazht/VelaWear_BE`
- Planned at: commit `2be2362` on `main`
- Planning branch: `docs/shadcn-improve-plans`
- Delivery rule: merge this planning PR into `main` before creating any
  implementation branch.
- Live execution status is maintained only in this file to prevent translation
  drift. Each plan's `State: TODO` is immutable planned-at metadata and is not
  updated during delivery.
- Git/GitHub writes (commit, push, and PR creation) require explicit
  authorization from the current operator; a passed test gate does not grant
  that authorization.

Every executor must read the selected plan completely, run its drift check,
honor its STOP conditions, and use the exact branch named in the plan. After a
PR is merged, fetch and fast-forward `main` before starting the next plan. Do
not stack a dependent plan on an unmerged branch.

## Mandatory dependency-drift reconciliation

Later plans intentionally depend on earlier PRs and will often detect changes
from their original planned-at SHA. Before editing source, run the plan's drift
check against current `main`. If any in-scope path changed, compare every live
symbol/citation and then update the canonical English plan, its Vietnamese
mirror, exact scope/test names, and each plan's `Planned against` SHA to current `main` **before
coding**. Commit that reconciliation in the implementation PR before the source
change, or in a preceding docs-only commit. The two plan files are the sole
allowed exception to a plan's source-file allowlist. Never execute a stale plan
or treat dependency drift as automatically safe.

## Execution order and status

| ID | Plan | Branch | Priority | Effort | Depends on | Wave | Status |
|---|---|---|---:|---:|---|---:|---|
| BE-001 | [Expand the customer self-service contract](001-expand-customer-self-service-contract.md) | `feature/customer-self-service-contract` | P1 | L | — | 1 | DONE |
| BE-002 | [Revoke cross-account role-user access](002-revoke-cross-account-role-user-access.md) | `fix/cross-account-role-user-access` | P1 | M | BE-001 and external FE-001 merged | 1 | DONE |
| BE-003 | [Serialize coupon counter updates](003-serialize-coupon-counter-updates.md) | `fix/coupon-counter-concurrency` | P1 | M | BE-002 | 2 | DONE |
| BE-004 | [Govern the public upload lifecycle](004-govern-public-upload-lifecycle.md) | `feature/upload-governance` | P1 | L | BE-002 | 2 | DONE |
| BE-005 | [Stabilize the API error contract](005-stabilize-api-error-contract.md) | `fix/api-error-contract` | P1 | M | BE-002 | 2 | DONE |
| BE-006 | [Batch checkout image loading](006-batch-checkout-image-loading.md) | `perf/checkout-image-batching` | P2 | M | BE-003 | 3 | DONE |
| BE-007 | [Batch sale-campaign queries](007-batch-sale-campaign-queries.md) | `perf/sale-campaign-query-batching` | P2 | L | BE-003 | 3 | DONE |
| BE-008 | [Eliminate admin-list N+1 queries](008-eliminate-admin-list-n-plus-one.md) | `perf/admin-list-fetching` | P2 | L | BE-002 | 3 | DONE |
| BE-009 | [Add a wishlist product-summary contract](009-add-wishlist-product-summary-contract.md) | `feature/wishlist-product-summary` | P2 | M | BE-002 | 3 | DONE |
| BE-010 | [Characterize large orchestration services](010-characterize-large-orchestration-services.md) | `test/orchestration-characterization` | P2 | L | BE-003 and BE-005–BE-009 | 4 | DONE |
| BE-011 | [Refactor authentication orchestration](011-refactor-auth-orchestration.md) | `refactor/auth-orchestration` | P3 | L | BE-001, BE-002, BE-005, BE-010 | 4 | DONE |
| BE-012 | [Refactor checkout orchestration](012-refactor-checkout-orchestration.md) | `refactor/checkout-orchestration` | P3 | L | BE-003, BE-006, BE-010 | 4 | DONE |
| BE-013 | [Refactor sale-campaign orchestration](013-refactor-sale-campaign-orchestration.md) | `refactor/sale-campaign-orchestration` | P3 | L | BE-007, BE-010 | 4 | DONE |
| BE-014 | [Refactor product orchestration](014-refactor-product-orchestration.md) | `refactor/product-orchestration` | P3 | L | BE-007, BE-009, BE-010, BE-013 | 4 | DONE |
| BE-015 | [Clean backend dependencies and documentation](015-clean-backend-dependencies-and-docs.md) | `chore/backend-maintenance-docs` | P3 | M | BE-001–BE-014 | 5 | DONE |

Status values: `TODO`, `IN PROGRESS`, `DONE`, `BLOCKED (<reason>)`, or
`REJECTED (<reason>)`.

Status transitions are reviewer/operator-maintained after the corresponding
PR is merged. An implementation executor must not add a speculative status
change to its PR.

## Mandatory cross-repository sequence

The authorization migration must use an expand → migrate → revoke sequence:

1. Merge BE-001, which adds ownership-bound `/me` contracts while retaining
   legacy routes temporarily.
2. Merge frontend FE-001, which migrates customer calls to those contracts.
3. Only then merge BE-002, which removes `ROLE_USER` access to legacy generic
   user-ID routes.

Changing this order can either break the frontend or leave an IDOR window.

## Merge waves

| Wave | Ordered work | Required checkpoint |
|---:|---|---|
| 0 | Backend planning PR, frontend planning PR | Both plans are merged; both mains are clean and synced |
| 1 | BE-001 → FE-001 → BE-002 | Backend verify, frontend CI suite, full-stack ownership smoke |
| 2 | FE-002 → BE-003 → FE-003 → BE-004 → BE-005 → FE-004 → FE-005 | Backend verify, frontend CI suite, full-stack security/correctness smoke |
| 3 | BE-006 → BE-007 → BE-008 → BE-009 → FE-006 → FE-007 → FE-008 | Full tests, full-stack smoke, query-count assertions |
| 4 | BE-010 → BE-011 → BE-012 → BE-013 → BE-014 → FE-009 → FE-010 | Full regression and full-stack smoke |
| 5 | FE-011 → BE-015 → FE-012 | Final backend verify, frontend CI suite, full-stack smoke |

The FE IDs refer to the canonical plans in the VelaWear frontend repository.

## Flyway allocation

Migrations are serialized in merge order:

- BE-001 reserves `V21__seed_customer_self_service_permissions.sql`.
- BE-002 reserves `V22__revoke_legacy_customer_permissions.sql`.
- BE-004 reserves `V23__scope_customer_file_upload_permissions.sql` for the
  self-avatar permission and legacy generic-upload revocation. The selected v1
  design does not add an upload-governance table.

If a reserved version already exists on current `main`, STOP, reconcile the
English and Vietnamese plans, and allocate the next free version. Never rename
an already-merged Flyway migration. Do not develop migration-bearing branches
in parallel.

## Standard branch preflight

Before every implementation PR:

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
git switch -c <branch-from-plan>
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Expected: the worktree is clean, local `HEAD` equals `origin/main`, and the new
branch is created from that commit. If any condition is false, STOP; do not
stash, reset, overwrite, or improvise.

## Standard verification gate

Every backend PR ends with:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Expected: exit code 0 and all tests pass. Plan-specific tests must run before
this final gate. The PowerShell commands in these plans are the Windows-local
gate. The required Linux GitHub Actions check in `.github/workflows/backend-ci.yml`
runs the equivalent `./mvnw --batch-mode --no-transfer-progress clean verify`
on Ubuntu and must also pass before merge. At wave boundaries, also run the frontend CI-equivalent suite
and dispatch the frontend repository's full-stack workflow against both `main`
refs.

## Findings disposition

All accepted shadcn/improve backend findings are represented. Related findings
were deliberately combined where they share one invariant, and the large
orchestration finding was split into characterization plus one refactor per
service. No accepted finding was rejected.

The audit reported 12 backend finding groups, but execution uses 15 plans. The
count increases because the authorization rollout is split into additive and
revocation phases, the large orchestration finding becomes characterization
plus four independent refactors, and a backend wishlist-summary companion is
required for the frontend performance finding; related error, campaign, and
maintenance findings are combined where they must share one invariant/PR.
