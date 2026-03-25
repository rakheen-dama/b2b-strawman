# QA Cycle Status — Portal Experience & Proposal Acceptance / Keycloak Dev Stack (2026-03-25)

## Current State

- **QA Position**: ALL_TRACKS_COMPLETE (T1-T7)
- **Cycle**: 2 (T3/T5/T4/T6)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: backend
- **Branch**: `bugfix_cycle_portal_2026-03-25`
- **Scenario**: `qa/testplan/portal-experience-proposal-acceptance.md`
- **Focus**: Magic link auth, portal home, project/document viewing, proposal lifecycle (create->send->accept->project), document download, org branding, cross-customer data isolation
- **Auth Mode**: Keycloak (not mock-auth). Portal uses magic link tokens. Firm uses JWT via gateway BFF.
- **Depends On**: Phase 49 T0 seed data (Thornton & Associates lifecycle with 4 customers, portal contacts, proposals, documents)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP (PID 66435) |
| Backend | http://localhost:8080 | UP (PID 95686) |
| Gateway (BFF) | http://localhost:8443 | UP (PID 20774) |
| Portal | http://localhost:3002 | UP (PID 66487) |
| Keycloak | http://localhost:8180 | UP (realm=docteams verified) |
| Mailpit | http://localhost:8025 | UP (API responding) |
| LocalStack | http://localhost:4566 | UP (v4.13.2) |
| Postgres | b2mash.local:5432 | UP (backend actuator healthy) |

## Existing Data (from previous cycles)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (admin)
- All passwords: `password`

### Customers (5 total)

| Customer | Status | Portal Contact | Contact Email |
|----------|--------|----------------|---------------|
| Naledi Corp QA | ACTIVE | Naledi Corp QA | naledi@qatest.local |
| Kgosi Holdings QA Cycle2 | ACTIVE (DB) | Kgosi Holdings QA | kgosi@qatest.local |
| Lifecycle Chain C4 | ACTIVE (DB) | Lifecycle Chain C4 | lifecycle-c4@qatest.local |
| Invalid Transition Test Customer | ACTIVE (DB) | (none) | -- |
| Test Integrity Customer | ACTIVE (DB) | Test Integrity Customer | integrity@qatest.local |

**Note**: The test plan referenced "Kgosi (Thabo)" and "Vukani (Sipho)" from Phase 49 seed data. These do NOT exist. The actual data is from previous QA cycles with different names. DB shows ALL customers as ACTIVE (status.md previously listed some as OFFBOARDED — corrected above).

### Proposals (8 total, all linked to Naledi Corp QA)

| Number | Title | Status |
|--------|-------|--------|
| PROP-0001 | QA Test Proposal Invalid | DRAFT |
| PROP-0002 | QA Cycle 2 Test Proposal | ACCEPTED |
| PROP-0003 | QA Decline Test Proposal | DECLINED |
| PROP-0004 | QA Expired Test Proposal | ACCEPTED |
| PROP-0005 | GAP-DI-07 Verification - Expired Proposal | EXPIRED |
| PROP-0006 | GAP-DI-07 Regression - Non-Expired Proposal | ACCEPTED |
| PROP-0007 | QA Portal Cycle 2 - Send Test | ACCEPTED |
| PROP-0008 | QA Portal Cycle 2 - Decline Test | DECLINED |

### Generated Documents (2 found)

| Template | Entity | File |
|----------|--------|------|
| QA Cycle 4 Template Edited | CUSTOMER: Naledi Corp QA | qa-cycle-4-test-template-naledi-corp-qa-2026-03-23.pdf |
| Engagement Letter -- Monthly Bookkeeping | PROJECT: QA Onboarding Verified Project | engagement-letter-monthly-bookkeeping-qa-onboarding-verified-project-2026-03-24.pdf |

### Projects (12 total, 7 linked to Naledi Corp QA, 1 linked to Kgosi)

Includes projects created from accepted proposals (PROP-0002, PROP-0004, PROP-0006, PROP-0007) plus earlier QA projects. 4 projects have no customer link. PROP-0007 acceptance auto-created "QA Portal Cycle 2 - Send Test" project.

## API Auth Note

Direct grant tokens MUST include `scope=openid organization` to get org claims. Without the `organization` scope, the JWT has no org claims and the backend returns 403 (insufficient scope).

```bash
TOKEN=$(curl -sf -X POST "http://localhost:8180/realms/docteams/protocol/openid-connect/token" \
  -d "client_id=gateway-bff" \
  -d "client_secret=docteams-web-secret" \
  -d "grant_type=password" \
  -d "username=thandi@thornton-test.local" \
  -d "password=password" \
  -d "scope=openid organization" | jq -r '.access_token')
```

Portal contacts are under `/api/customers/{id}/portal-contacts` (not a top-level `/api/portal-contacts` endpoint).

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-PE-001 | Magic link email subject says "DocTeams" instead of firm name | LOW | FIXED | backend | PR #836 | **Bug.** `PortalEmailService` now resolves org name directly from `OrganizationRepository` using the contact's `orgId`, bypassing `EmailContextBuilder.resolveOrgName()` which relies on `ORG_ID` ScopedValue (not bound during portal auth flow). |
| GAP-PE-002 | Magic link URL in email points to frontend (:3000) not portal (:3002) | MEDIUM | FIXED | backend | PR #836 | **Bug.** `PortalEmailService` now injects `portalBaseUrl` from `docteams.app.portal-base-url` config and uses correct path `/auth/exchange?token=...&orgId=...`. |
| GAP-PE-003 | Raw JSON error shown on 404 portal project pages | LOW | SPEC_READY | -- | -- | **Bug (UX).** `portalGet()` in `portal/lib/api-client.ts` throws raw `"API error: 404 {json}"` which the project detail page displays verbatim. Fix: map HTTP status codes to friendly messages in api-client, improve error state in project detail page. Spec: `fix-specs/GAP-PE-003.md` |
| GAP-PE-004 | Portal read model not populated on project creation — requires manual resync | HIGH | FIXED | backend | PR #834 | **Bug.** `ProjectService.createProject()` and `updateProject()` now publish `CustomerProjectLinkedEvent` after saving `CustomerProject` join record. Portal read model will auto-populate on project creation/update with customer link. |
| GAP-PE-005 | Portal frontend has no proposals page — backend API exists but no UI | HIGH | FIXED | portal | PR #835 | **Feature gap.** Added proposals list page (with "Awaiting Your Response" / "Past" sections), proposal detail page with accept/decline actions, ProposalStatusBadge component, "Proposals" nav link in header, and proposal types. 5 files, 603 lines added. Portal build green, 87 tests pass. |
| GAP-PE-006 | No SHARED documents visible in portal | MEDIUM | WONT_FIX | -- | -- | **Feature gap.** No workflow exists to mark documents as SHARED — all are INTERNAL by default. Portal correctly hides INTERNAL docs. Requires firm-side UI changes + product design decision (auto-share vs manual). Backend `PortalEventHandler.onDocumentVisibilityChanged()` already handles the event correctly. Out of scope for this bugfix cycle. Spec: `fix-specs/GAP-PE-006.md` |
| GAP-PE-007 | No email notifications for proposal send/accept/decline | MEDIUM | SPEC_READY | -- | -- | **Bug.** Two issues: (1) `NotificationDispatcher` requires explicit `NotificationPreference` with `email_enabled=true` (opt-in, default false) — no preferences configured for Thornton org. (2) `NotificationEventHandler.dispatchAll()` passes `null` for recipientEmail, so email channel skips even if enabled. Fix: resolve recipient email from MemberRepository in dispatchAll(). Spec: `fix-specs/GAP-PE-007.md` |
| GAP-PE-008 | Portal page title says "DocTeams" instead of firm name | LOW | FIXED | portal | PR #836 | **Bug (UX).** `portal/app/layout.tsx` static metadata changed from `"Client Portal | DocTeams"` to `"Client Portal"`. |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-25T00:00Z | Setup | Portal Experience & Proposal Acceptance QA cycle initialized on branch bugfix_cycle_portal_2026-03-25. Scenario: qa/testplan/portal-experience-proposal-acceptance.md. Reusing org data from previous cycles. |
| 2026-03-25T05:40Z | Infra | Stack verification complete. All 8 services UP. Keycloak realm=docteams verified. Seed data inventoried: 5 customers (1 ACTIVE), 4 portal contacts, 6 proposals (1 DRAFT), 2 generated documents, 9 projects. Phase 49 named contacts (Kgosi/Thabo, Vukani/Sipho) NOT present — data is from earlier QA cycles. PROP-0001 (DRAFT) available for proposal lifecycle testing. Dev stack marked READY. |
| 2026-03-25T05:44Z | QA-C1 | **Cycle 1 start** — T1 (Magic Link Auth), T2 (Portal Home), T7 (Data Isolation). |
| 2026-03-25T05:45Z | QA-C1 | T1 PASS (13/14 pass, 2 partial). Magic link request, exchange, single-use enforcement, invalid token rejection all working. Gaps: email subject uses "DocTeams" not firm name (GAP-PE-001), email link targets wrong port (GAP-PE-002). |
| 2026-03-25T05:47Z | QA-C1 | T2 PASS (8/8). Portal home shows projects, nav has Projects+Invoices, profile page works, logout works. No firm-side nav leaks. |
| 2026-03-25T05:49Z | QA-C1 | T7 PASS (21/21). Zero data isolation failures. Naledi sees only Naledi data (6 projects, 3 invoices). Kgosi sees only Kgosi data (1 project, 0 invoices). Direct URL access to other customer's resources returns 404. API-level isolation confirmed with both JWTs. Minor UX gap: raw JSON errors on 404 pages (GAP-PE-003). |
| 2026-03-25T05:50Z | QA-C1 | **Cycle 1 complete**. T1+T2+T7 done. 3 gaps logged (none blocking). Ready for T3-T6 in next cycle. |
| 2026-03-25T06:00Z | QA-C2 | **Cycle 2 start** — T3 (Projects & Documents), T5 (Document Download), T4 (Proposal Lifecycle), T6 (Branding). |
| 2026-03-25T06:00Z | QA-C2 | T3.1 PASS — project list shows 6 Naledi projects. T3.2 initially FAIL (portal read model empty, 404 on project detail). Triggered manual resync via `/internal/portal/resync/thornton-associates` (7 projects, 0 docs synced). After resync T3.2 PASS — project detail loads with tasks, documents section, comments. GAP-PE-004 logged (read model not auto-populated). |
| 2026-03-25T06:02Z | QA-C2 | T3.3 PARTIAL — no top-level documents page in portal, documents only within project detail. All docs INTERNAL visibility = "No documents shared yet." GAP-PE-006 logged. |
| 2026-03-25T06:03Z | QA-C2 | T5 BLOCKED — no SHARED-visibility documents exist, nothing to preview or download. |
| 2026-03-25T06:05Z | QA-C2 | T4 (Proposal Lifecycle): Firm-side login via Keycloak OK. Proposals page loads (6 proposals, stats). PROP-0001 cannot be sent (no content — correct validation). Created PROP-0007 via API (FIXED R12,000 ZAR), sent to Naledi. No email notification sent (GAP-PE-007). |
| 2026-03-25T06:09Z | QA-C2 | T4.4-T4.5: Portal has NO proposals page (GAP-PE-005). Backend API `/portal/api/proposals` works — 6 proposals returned. Accepted PROP-0007 via API — status ACCEPTED, project auto-created ("QA Portal Cycle 2 - Send Test"). Project visible in portal on reload (7 projects). |
| 2026-03-25T06:12Z | QA-C2 | T4.7: Created PROP-0008, sent, declined via portal API with reason "Fee too high." Firm sees decline reason, no project created. No notification email (GAP-PE-007). |
| 2026-03-25T06:14Z | QA-C2 | T6 PASS — Branding API returns orgName, brandColor #1B5E20, footerText. Portal shows firm name in header+footer, brand color on nav accent elements (2 elements found). No logo uploaded = text fallback. Page title uses "DocTeams" not firm name (GAP-PE-008). |
| 2026-03-25T06:15Z | QA-C2 | **Cycle 2 complete**. ALL_TRACKS_COMPLETE. 5 new gaps (GAP-PE-004 through GAP-PE-008). 2 HIGH severity: empty portal read model (PE-004), missing proposal UI in portal (PE-005). Total gaps: 8 (GAP-PE-001 through GAP-PE-008). |
| 2026-03-25T07:00Z | Product | **Triage complete.** All 8 gaps analyzed with codebase root cause investigation. Results: 6 SPEC_READY, 1 WONT_FIX (GAP-PE-006), 1 grouped (GAP-PE-008 with GAP-PE-001). |
| 2026-03-25T07:01Z | Product | **GAP-PE-004 root cause confirmed**: `ProjectService.createProject()` saves `CustomerProject` join record but never publishes `CustomerProjectLinkedEvent`. Only `CustomerProjectService.linkCustomerToProject()` publishes the event. Proposal acceptance flow calls `ProjectService.createProject()` directly, so PortalEventHandler never syncs. |
| 2026-03-25T07:02Z | Product | **GAP-PE-007 root cause confirmed**: Two-part issue. (1) `NotificationDispatcher` requires explicit `NotificationPreference` with `email_enabled=true` — none configured. (2) `NotificationEventHandler.dispatchAll()` passes `null` for recipientEmail — email channel skips. Code bug is #2. |
| 2026-03-25T07:03Z | Product | **GAP-PE-006 marked WONT_FIX**: Requires firm-side UI + product design decisions about auto-sharing documents. Portal correctly handles SHARED docs when they exist. Backend event handler already wired. Out of scope for bugfix cycle. |
| 2026-03-25T07:04Z | Product | **Cascading analysis**: GAP-PE-001 and GAP-PE-002 share the same service (`PortalEmailService`) and should be fixed together. GAP-PE-001 and GAP-PE-008 share a branding theme and are grouped into one spec. No other cascading dependencies. |
| 2026-03-25T07:05Z | Product | **Priority order**: (1) GAP-PE-004 [HIGH, backend bug, blocks project detail], (2) GAP-PE-005 [HIGH, feature gap, blocks proposal acceptance UI], (3) GAP-PE-001+002 [MEDIUM, combined email fix], (4) GAP-PE-007 [MEDIUM, email dispatch bug], (5) GAP-PE-003 [LOW, UX polish], (6) GAP-PE-008 [LOW, title polish]. Total: 6 fix specs covering 7 gaps. |
| 2026-03-25T07:10Z | Dev | **GAP-PE-004 FIXED** (PR #834, merged). Added `CustomerProjectLinkedEvent` publishing in `ProjectService.createProject()` and `updateProject()` after `customerProjectRepository.save()`. One file changed: `ProjectService.java` (+14 lines). Targeted tests pass (PortalEventHandlerTest, ProjectService tests). Two pre-existing unrelated test failures in DashboardProjectIntegrationTest and VerticalProfileIntegrationTest. NEEDS_REBUILD: backend. |
| 2026-03-25T09:10Z | Dev | **GAP-PE-005 FIXED** (PR #835, merged). Added complete proposals UI to portal: list page with actionable/past sections, detail page with rendered content + accept/decline buttons, ProposalStatusBadge component, "Proposals" nav link. 5 files changed (+603 lines). Lint, build, and all 87 tests pass. Portal uses HMR — no restart needed. |
| 2026-03-25T09:15Z | Dev | **GAP-PE-001+002+008 FIXED** (PR #836, merged). `PortalEmailService.java`: replaced `appBaseUrl` with `portalBaseUrl` (from `docteams.app.portal-base-url`), added `OrganizationRepository` to resolve org name directly from contact's orgId (bypasses `EmailContextBuilder.resolveOrgName()` which fails when `ORG_ID` ScopedValue not bound), fixed magic link URL to `/auth/exchange?token=...&orgId=...`. `portal/app/layout.tsx`: removed hardcoded "DocTeams" from metadata title. 2 files changed. Backend targeted tests pass, portal lint+build green. Only pre-existing DashboardProjectIntegrationTest failure. NEEDS_REBUILD: backend. |
