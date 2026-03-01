# Rich Seed Library — Handoff for Smoke Testing

## Status: Code Complete, Needs Smoke Test + Fixes

All files committed on `main` at `82ed265b`. The scripts are written but need live testing against the E2E stack.

## What Works (confirmed in previous session)
- **common.sh** — JWT caching, api_get/api_post/api_put with temp file pattern, check_status to stderr, find_existing, complete_checklists
- **customers.sh** — 4 customers across lifecycle stages, auto-fixes boot-seed Acme Corp from ONBOARDING to ACTIVE
- **projects.sh** — 6 projects, adds Bob+Carol as project members
- **tasks.sh** — 20 tasks with DONE (PATCH /complete) and IN_PROGRESS (PUT with full body) transitions
- **time-entries.sh** — 16 time entries across 5 projects
- **rates-budgets.sh** — Billing rates for Alice/Bob, budgets on 2 projects
- **invoices.sh** — 2 invoices with manual lines (Acme Corp + Bright Solutions)

## What Needs Debugging

### 1. retainers.sh — `POST /api/retainers` returns "Failed to read request" (400)
The JSON body matches the `CreateRetainerRequest` record exactly but Spring can't deserialize it. Might be:
- A Jackson enum deserialization issue with `RetainerType` or `RetainerFrequency`
- A missing `@JsonCreator` or constructor issue with the record
- Check backend logs: `docker compose -f compose/docker-compose.e2e.yml logs backend | grep -i "jackson\|deseriali"`
- Try sending enums as lowercase (`hours_based` vs `HOURS_BASED`)

### 2. documents.sh — Not yet tested
Uses presigned URL upload flow: init → PUT to S3 → confirm. Might need:
- LocalStack S3 URL might not be reachable from host (presigned URL may point to `localstack:4566` instead of `localhost:4566`)

### 3. comments.sh — Not yet tested
Should work — simple POST with entity type/id/body. Watch for:
- `entityType` values: "TASK", "DOCUMENT", "PROJECT"
- `visibility`: "INTERNAL" or "SHARED"

### 4. proposals.sh — Not yet tested
Paginated response (.content[]). Watch for:
- `feeModel` enum values
- `expiresAt` format (ISO-8601 with timezone)

### 5. Dockerfile update needed
Add `lib/` and `rich-seed.sh` to `compose/seed/Dockerfile` so it works inside Docker too.

## How to Test

```bash
# Start E2E stack fresh
bash compose/scripts/e2e-down.sh
bash compose/scripts/e2e-up.sh

# Wait for boot seed, then run rich seed
sleep 10
bash compose/seed/rich-seed.sh 2>&1

# Test idempotency (run again — everything should [skip])
bash compose/seed/rich-seed.sh 2>&1

# Test --only flag
bash compose/seed/rich-seed.sh --only customers,projects 2>&1

# Test --reset flag (wipes and recreates)
bash compose/seed/rich-seed.sh --reset 2>&1
```

## Key Architecture Decisions (from smoke testing)

1. **check_status goes to stderr** — prevents pollution when functions are called via `$()` for ID capture
2. **Temp file pattern for HTTP status** — `api_get` writes status to `$_API_TMPFILE`, body to stdout. Avoids subshell variable propagation problem.
3. **find_existing instead of check_or_create** — simpler, avoids shell word-splitting issues with function-as-string arguments
4. **complete_checklists auto-transitions** — completing all checklist items auto-transitions ONBOARDING→ACTIVE, so check status after and skip explicit transition if already ACTIVE
5. **Project members must be added** — tasks with assignees require the assignee to be a project member first
6. **Task DONE via PATCH /complete** — `PUT /api/tasks/{id}` requires ALL fields (title, status, priority), PATCH is simpler for status changes
7. **Bare arrays vs paginated** — customers/projects/tasks/time-entries/invoices/retainers/members/documents/comments return `[...]`, proposals/billing-rates return `{content:[...]}`

## Files
- Design doc: `docs/plans/2026-03-01-e2e-seed-library-design.md`
- Implementation plan: `docs/plans/2026-03-01-e2e-seed-library-plan.md`
- Scripts: `compose/seed/lib/*.sh` + `compose/seed/rich-seed.sh`
