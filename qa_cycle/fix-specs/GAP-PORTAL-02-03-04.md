# Fix Spec: GAP-PORTAL-02/03/04 — Portal Read Model Empty After Seed

## Problem

After E2E database seed (or reseed), the portal read model schema (`portal.*`) tables are empty:
- `portal.portal_projects` — 0 rows
- `portal.portal_documents` — 0 rows
- `portal.portal_comments` — 0 rows
- `portal.portal_tasks` — 0 rows
- `portal.portal_project_summaries` — 0 rows

This blocks three portal features that depend on the read model:
- **GAP-PORTAL-02**: Project comments — `GET /portal/projects/{id}/comments` returns 404
- **GAP-PORTAL-03**: Project tasks — `GET /portal/projects/{id}/tasks` returns 404
- **GAP-PORTAL-04**: Project summary — `GET /portal/projects/{id}/summary` returns 404

The portal project LIST endpoint (`GET /portal/projects`) works because it uses `PortalQueryService` which queries the tenant schema directly. But project DETAIL, comments, tasks, and summary use `PortalReadModelService` which queries `portal.*` tables.

## Root Cause (confirmed by code review)

**Two issues, both must be fixed:**

### Issue 1: Seed script never calls portal resync

The seed script (`compose/seed/seed.sh`, line 14, CMD) and the rich seed orchestrator (`compose/seed/rich-seed.sh`) create customers, projects, tasks, time entries, invoices, comments, etc. via the REST API. These API calls publish domain events (`CustomerProjectLinkedEvent`, etc.) which the `PortalEventHandler` (at `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java`) listens to and projects into the portal read model.

However, the seed script runs in a separate Docker container. Domain events fire during the seed's API calls, and the `PortalEventHandler` processes them via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. This should work in theory, but the seed creates data in a rapid sequence, and if any event handler fails (e.g., due to timing, schema not yet ready, or race conditions), the portal read model ends up incomplete.

More critically, the **reseed** scenario (`compose/scripts/e2e-reseed.sh`) wipes the database but never triggers a portal resync. The resync endpoint exists at `POST /internal/portal/resync/{orgId}` (at `backend/src/main/java/.../customerbackend/controller/PortalResyncController.java`), but neither `seed.sh` nor `rich-seed.sh` calls it.

### Issue 2: Seed container cannot call internal endpoints (missing env var context)

The seed container uses `API_KEY="${API_KEY:-e2e-test-api-key}"` (line 6 of `seed.sh`) and already calls `/internal/orgs/provision` and `/internal/members/sync` with `X-API-KEY` header. The backend e2e profile configures `internal.api.key: e2e-test-api-key` (at `backend/src/main/resources/application-e2e.yml`, line 17). So the seed CAN call internal endpoints — it just never calls `/internal/portal/resync/{orgId}`.

Note: The QA report mentions the resync endpoint returns 401 when called from *outside* the Docker network (e.g., `curl http://localhost:8081/internal/portal/resync/e2e-test-org`). This is because the caller didn't pass the `X-API-KEY` header. The `ApiKeyAuthFilter` (at `backend/src/main/java/.../security/ApiKeyAuthFilter.java`, line 35) requires the header for all `/internal/*` paths.

## Fix

### Step 1: Add portal resync call to the seed script

Add a final step to `compose/seed/seed.sh` (after Step 7, before the summary banner) that calls the portal resync endpoint:

```sh
# -- Step 8: Sync portal read model -----------------------------------------
echo ""
echo "==> Step 8: Sync portal read model"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/internal/portal/resync/${ORG_ID}" \
  -H "X-API-KEY: ${API_KEY}")
check_status "Portal resync" "$STATUS"
```

### Step 2: Add portal resync call to the rich seed script

Add a final step to `compose/seed/rich-seed.sh` (after proposals module, before the summary banner) that calls the portal resync endpoint:

```sh
# ── Portal resync (always runs — idempotent) ───────────────────────
echo ""
echo "==> Portal resync"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/internal/portal/resync/${ORG_ID}" \
  -H "X-API-KEY: ${API_KEY}")
case "$STATUS" in
  2[0-9][0-9]) echo "    [ok] Portal resync (HTTP ${STATUS})" ;;
  *) echo "    [WARN] Portal resync failed (HTTP ${STATUS}) — non-fatal" ;;
esac
```

Note: The rich seed uses `BACKEND_URL` and `API_KEY` from `lib/common.sh`. The `ORG_ID` is also defined there. Verify these are available in scope.

### Step 3: Verify `lib/common.sh` exports needed variables

Check that `compose/seed/lib/common.sh` defines `ORG_ID`, `BACKEND_URL`, and `API_KEY` (or confirm they're inherited from the seed scripts).

## Scope

| File | Change |
|------|--------|
| `compose/seed/seed.sh` | Add Step 8: portal resync call |
| `compose/seed/rich-seed.sh` | Add portal resync call after proposals |

**No backend changes needed.** The resync endpoint and service already work correctly. The API key auth works correctly. The only gap is that the seed scripts never call the resync after populating data.

## Verification

1. Run `bash compose/scripts/e2e-reseed.sh`
2. Verify seed output shows "Portal resync" step with HTTP 200
3. Connect to DB: `docker exec -it e2e-postgres psql -U postgres -d app`
4. Query: `SELECT COUNT(*) FROM portal.portal_projects;` -- should be >0
5. Query: `SELECT COUNT(*) FROM portal.portal_tasks;` -- should be >0
6. Authenticate as Kgosi portal contact
7. Navigate to a project detail page -- should load (not 404)
8. Comments tab should render (empty or with data)
9. Tasks tab should render
10. Summary section should render

## Estimated Effort

**XS** (< 30 minutes). Two small additions to shell scripts. No code compilation. No Docker rebuild needed (seed container picks up script changes on next run).
