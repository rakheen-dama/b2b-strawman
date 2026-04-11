# Portal Frontend Gaps — Regression QA Cycle (2026-03-20)

Backend APIs exist but have no corresponding frontend implementation.

## Critical Gaps

### GAP-PORTAL-01: Proposal workflow (4 endpoints, 0 frontend)
- `GET /portal/api/proposals` — list proposals
- `GET /portal/api/proposals/{id}` — detail
- `POST /portal/api/proposals/{id}/accept` — accept
- `POST /portal/api/proposals/{id}/decline` — decline
- **Impact**: Customers cannot view, accept, or decline proposals in the portal
- **Severity**: HIGH — entire feature invisible to portal users

### GAP-PORTAL-02: Project comments (2 endpoints, 0 frontend)
- `GET /portal/projects/{id}/comments` — list
- `POST /portal/projects/{id}/comments` — post
- **Impact**: No collaboration between firm and client on project-level comments
- **Severity**: HIGH — key communication channel missing

### GAP-PORTAL-03: Project tasks (1 endpoint, 0 frontend)
- `GET /portal/projects/{projectId}/tasks` — list tasks
- **Impact**: Portal users see document count only, no task visibility
- **Severity**: MEDIUM

### GAP-PORTAL-04: Project summary / time tracking (1 endpoint, 0 frontend)
- `GET /portal/projects/{projectId}/summary` — total hours, billable hours, last activity
- **Impact**: No time/billing visibility in portal project view
- **Severity**: MEDIUM

### GAP-PORTAL-05: Pending acceptance requests (1 endpoint, 0 frontend)
- `GET /portal/acceptance-requests/pending` — list pending acceptances
- **Impact**: Portal users can't see/navigate to pending document acceptances from dashboard. Token-based acceptance works (via email link), but there's no in-portal list.
- **Severity**: MEDIUM

### GAP-PORTAL-06: Profile page (1 endpoint, 0 frontend)
- `GET /portal/me` — contact name, email, customer name, role
- **Impact**: No way for portal users to see their own profile
- **Severity**: LOW

### GAP-PORTAL-07: Org branding on login page (1 endpoint, 0 frontend)
- `GET /portal/branding?orgId=...` — org name, logo, brand color, footer
- **Impact**: Portal login shows generic "DocTeams Portal" instead of customer org branding
- **Severity**: LOW

## Type Mismatches

### GAP-PORTAL-08: PortalProject type incomplete
- Backend returns: `id, name, status, description, documentCount, commentCount, createdAt`
- Frontend type has: `id, name, description, documentCount`
- **Missing**: `status`, `commentCount`, `createdAt`
- Frontend project detail page doesn't use the dedicated detail endpoint — it searches the list

### GAP-PORTAL-09: PortalDocument missing project context
- Backend returns: `id, fileName, contentType, size, scope, status, createdAt`
- Frontend expects: `id, fileName, contentType, size, scope, projectId, projectName, uploadedAt, createdAt`
- **Missing from backend**: `projectId`, `projectName`
- **Impact**: Documents page can't show which project a document belongs to

## Summary

| Priority | Count | IDs |
|----------|-------|-----|
| HIGH | 2 | GAP-PORTAL-01, GAP-PORTAL-02 |
| MEDIUM | 3 | GAP-PORTAL-03, GAP-PORTAL-04, GAP-PORTAL-05 |
| LOW | 2 | GAP-PORTAL-06, GAP-PORTAL-07 |
| Tech debt | 2 | GAP-PORTAL-08, GAP-PORTAL-09 |

## Portal Pages: Implemented vs Missing

| Page | Status |
|------|--------|
| `/portal` (login) | Done |
| `/portal/projects` | Done |
| `/portal/projects/[id]` (detail + docs) | Done (partial — no tasks, comments, summary) |
| `/portal/documents` | Done |
| `/portal/requests` | Done |
| `/portal/requests/[id]` | Done |
| **`/portal/proposals`** | **Missing** |
| **`/portal/proposals/[id]`** | **Missing** |
| **`/portal/acceptances`** | **Missing** |
| **`/portal/profile`** | **Missing** |
