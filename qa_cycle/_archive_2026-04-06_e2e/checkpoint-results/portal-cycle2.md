# Portal Cycle 2 — Checkpoint Results (T3/T5/T4/T6)

**Date**: 2026-03-25
**Branch**: `bugfix_cycle_portal_2026-03-25`
**Auth Mode**: Keycloak dev stack
**Portal Contact**: naledi@qatest.local (Naledi Corp QA)
**Firm User**: thandi@thornton-test.local (Owner)

---

## T3 — Portal Project & Document Viewing

### T3.1 — Project List

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T3.1.1 Navigate to portal projects page | PASS | Auth redirects to /projects automatically |
| T3.1.2 Naledi's projects listed | PASS | 6 projects visible (QA Onboarding Verified, T1 Test, Rate Hierarchy Test, QA Cycle 2 Test, QA Expired Test, GAP-DI-07 Regression). After proposal acceptance, 7th project ("QA Portal Cycle 2 - Send Test") appeared. |
| T3.1.3 No other customer's projects visible | PASS | Only Naledi Corp QA projects shown. Already verified in T7 (Cycle 1). |
| T3.1.4 Each project shows name, status, doc count | PASS | Each card shows: name, description (if present), document count, relative time ("9 hours ago"). Screenshot: t3-portal-projects-list.png |

### T3.2 — Project Detail

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T3.2.1 Click into project | FAIL (then PASS after resync) | **Initial attempt**: 404 error — "No project found with id ...". Root cause: `portal.portal_projects` read model table was empty. After manual resync via `POST /internal/portal/resync/thornton-associates`, project detail loaded. See GAP-PE-004. Screenshot: t3-project-detail-404.png |
| T3.2.2 Project name displays correctly | PASS | "QA Onboarding Verified Project" with ACTIVE badge |
| T3.2.3 Visible content | PASS | Shows: status badge (ACTIVE), description, tasks table (task name/status/assignee), documents section, comments section with input form |
| T3.2.4 Information visible vs. hidden | PASS | Visible: name, status, description, tasks (name, status, assignee), documents, comments. Hidden: no time/billing data, no budget info, no edit controls. |
| T3.2.5 Client cannot edit/create/delete | PASS | No edit buttons, no create-task button, no delete button. Only interaction is adding comments. Screenshot: t3-project-detail-success.png |

### T3.3 — Document List

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T3.3.1 Navigate to documents page | PARTIAL | No top-level "Documents" nav link in portal. Documents are accessible per-project only (inside project detail). The portal app (`portal/`) has no `/documents` route — only `projects/`, `invoices/`, `profile/`. |
| T3.3.2 Documents visible | PARTIAL | All existing documents have INTERNAL visibility (not SHARED), so portal shows "No documents shared yet." for every project. Resync confirmed 0 documents synced. See GAP-PE-006. |
| T3.3.3 Document metadata | N/A | No SHARED documents to verify |
| T3.3.4 No cross-customer documents | PASS | Already verified in T7 (Cycle 1) |

---

## T5 — Portal Document Download & Preview

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T5.1.1–T5.1.4 Document visibility | BLOCKED | No SHARED-visibility documents exist for Naledi. All 2 documents in tenant schema are INTERNAL. Generated documents (2 PDFs) are in `generated_documents` table, not the `documents` table used by the portal. See GAP-PE-006. |
| T5.2.1–T5.2.4 Document preview | BLOCKED | No documents to preview |
| T5.3.1–T5.3.4 Document download | BLOCKED | No documents to download |
| T5.4.1–T5.4.3 Non-shared docs hidden | PASS | INTERNAL documents correctly hidden — portal only shows SHARED. Working as designed, but no SHARED documents to test positive case. |

---

## T4 — Proposal Lifecycle

### T4.1–T4.3 — Create & Send (Firm Side)

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T4.1.1 Navigate to Proposals page | PASS | `http://localhost:3000/org/thornton-associates/proposals` — shows "Engagement Letters" page with 6 proposals, stats (Total: 6, Pending: 0, Accepted: 3, Rate: 60%) |
| T4.1.2 PROP-0001 (DRAFT) exists | PASS | "QA Test Proposal Invalid" in DRAFT status, Fee Model: Hourly, Rate: R500/hr |
| T4.1.3 Cannot send PROP-0001 | PASS (validation) | Send returns 400: "Proposal content must not be empty" — correct validation, proposal has no content |
| T4.1.4 Create new proposal via API | PASS | Created PROP-0007 "QA Portal Cycle 2 - Send Test" with FIXED fee R12,000 ZAR, content JSON, expiry 2026-04-08 |
| T4.1.5 Saved as DRAFT | PASS | API returned status: "DRAFT" |
| T4.2.1 Proposal detail displays | PASS | All data correct: title, number (PROP-0007), fee model (FIXED), amount (R12,000), currency (ZAR), expiry |
| T4.3.1 Send proposal | PASS | `POST /api/proposals/{id}/send` with portalContactId → status changed to SENT, sentAt: "2026-03-25T06:08:15Z" |
| T4.3.2 Status changes to SENT | PASS | API confirmed |
| T4.3.3 Sent date recorded | PASS | sentAt populated |
| T4.3.4 Email sent to portal contact | FAIL | No email in Mailpit after sending proposal. No notification email triggered. See GAP-PE-007. |
| T4.3.5 Email content | BLOCKED | No email sent |

### T4.4–T4.5 — Portal View & Accept

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T4.4.1 Navigate to proposal in portal | FAIL | **No portal frontend page for proposals.** The portal backend has endpoints at `/portal/api/proposals` (list, detail, accept, decline) but the portal frontend (port 3002) has NO proposals page — only projects, invoices, profile. See GAP-PE-005. |
| T4.4.2 Proposal displays correctly | PASS (API only) | `GET /portal/api/proposals` returns all 6 proposals including PROP-0007 (SENT) with correct data |
| T4.4.5 Accept/Decline buttons present | N/A | No frontend page |
| T4.5.1 Accept proposal via API | PASS | `POST /portal/api/proposals/{id}/accept` → status: ACCEPTED, acceptedAt: "2026-03-25T06:11:22Z", message: "Your project has been set up." |
| T4.5.2 Confirmation | PASS (API) | Response includes success message |
| T4.5.4 Status = ACCEPTED | PASS | Confirmed via both portal API and firm API |

### T4.6 — Firm Verifies Acceptance + Auto-Created Project

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T4.6.1 Proposal shows ACCEPTED on firm side | PASS | `GET /api/proposals/{id}` returns status: "ACCEPTED" |
| T4.6.2 Status = ACCEPTED | PASS | Confirmed |
| T4.6.3 Acceptance metadata | PARTIAL | acceptedAt recorded. No acceptedBy name in firm API response. |
| T4.6.4 Acceptance notification email | FAIL | No email in Mailpit. See GAP-PE-007. |
| T4.6.5 Project auto-created | PASS | Project "QA Portal Cycle 2 - Send Test" created at acceptance time (2026-03-25T06:11:22), visible in both DB and portal project list (confirmed on portal reload showing 7 projects). createdProjectId in proposal response links to the new project. |
| T4.6.6 Project details | PARTIAL | Project name matches proposal title. Customer correctly linked (Naledi Corp QA). No milestones/team converted (not part of this proposal's data). |
| T4.6.7 Audit trail | Not verified | Did not check audit events table |

### T4.7 — Proposal Rejection Flow

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T4.7.1 Create decline proposal | PASS | Created PROP-0008 "QA Portal Cycle 2 - Decline Test", FIXED R2,500 ZAR, expiry 2026-04-01 |
| T4.7.2 Send to Naledi | PASS | Sent at 2026-03-25T06:12:00Z |
| T4.7.3 Decline via portal API | PASS | `POST /portal/api/proposals/{id}/decline` with reason → status: DECLINED, declinedAt: "2026-03-25T06:13:45Z" |
| T4.7.4 Reason recorded | PASS | "Fee too high, will reconsider next quarter" stored |
| T4.7.5 Status = DECLINED | PASS | Confirmed from both portal and firm APIs |
| T4.7.6 No project auto-created | PASS | createdProjectId: null |
| T4.7.7 Rejection notification email | FAIL | No email in Mailpit. See GAP-PE-007. |
| T4.7.8 Rejection reason visible to firm | PASS | `GET /api/proposals/{id}` returns declineReason: "Fee too high, will reconsider next quarter" |

### T4.8 — Proposal Expiry

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T4.8.1 ExpiryProcessor exists | PASS | PROP-0005 is in EXPIRED status (verified in previous QA cycle — GAP-DI-07) |
| T4.8.2 Manual trigger | Not tested | |
| T4.8.3 Observation | N/A | Expiry verified through previous cycle data |

---

## T6 — Org Branding in Portal

### T6.1 — Brand Colour

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T6.1.1 Login to portal | PASS | Authenticated as Naledi |
| T6.1.2 Brand colour #1B5E20 visible | PASS | Found 2 elements with brand color: active nav link text (Projects tab) and active tab indicator bar. Evaluated via JS: `rgb(27, 94, 32)` matches `#1B5E20`. |
| T6.1.3 Brand colour assessment | PASS | Brand color applied to navigation accent elements |

### T6.2 — Firm Name

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T6.2.1 Firm name visible in portal | PASS | "Thornton & Associates" in header (top-left) and footer ("Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001") |
| T6.2.2 No generic "DocTeams" branding | PARTIAL | "Powered by DocTeams" appears in footer (fine as secondary branding), header shows firm name (correct). Page title is "Client Portal | DocTeams" — could use firm name. See GAP-PE-008. |

### T6.3 — Logo

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T6.3.1 Logo displayed | N/A | No logo uploaded (logoUrl: null in branding API) |
| T6.3.2 Fallback | PASS | Firm name text shown in header instead of logo — no broken image icon |

### T6.4 — Branding API

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| T6.4.1 Branding endpoint | PASS | `GET /portal/branding?orgId=thornton-associates` returns data |
| T6.4.2 Response includes correct data | PASS | orgName: "Thornton & Associates", brandColor: "#1B5E20", footerText: "Thornton & Associates \| Reg...", logoUrl: null |
| T6.4.3 Portal frontend consumes it | PASS | Header, footer, and nav accent colors all reflect branding data |

---

## New Gaps Found

| ID | Summary | Severity | Category | Notes |
|----|---------|----------|----------|-------|
| GAP-PE-004 | Portal read model (portal.portal_projects) not populated on project creation — requires manual resync | HIGH | portal-content | Projects list works (queries tenant schema directly via PortalQueryService) but project detail fails with 404 (queries empty portal.portal_projects via PortalReadModelService). Resync via `POST /internal/portal/resync/{orgId}` fixes it. The PortalEventHandler should sync on project creation/customer-link events but the read model was empty for all Thornton projects. |
| GAP-PE-005 | Portal frontend has no proposal page — backend API exists but no UI | HIGH | proposal-lifecycle | Backend: `/portal/api/proposals` (list, detail, accept, decline) all functional. Frontend (portal app on :3002): no proposals route, no proposals nav item. Proposals can only be accepted/declined via direct API calls. The PendingAcceptancesList component on the projects page shows document acceptance requests, NOT proposal acceptances. |
| GAP-PE-006 | No SHARED documents visible in portal — all existing documents are INTERNAL | MEDIUM | portal-content | Documents table has 2 entries, both with visibility=INTERNAL. Generated documents (2 PDFs in generated_documents table) are separate from portal-visible documents. No mechanism to auto-share generated documents to portal, or to change document visibility to SHARED. Portal correctly hides INTERNAL docs but there's no way to make any docs portal-visible without direct DB manipulation. |
| GAP-PE-007 | No email notifications for proposal send, accept, or decline | MEDIUM | proposal-lifecycle | Mailpit empty after all three actions. ProposalSentEvent exists and NotificationEventHandler is registered, but no emails delivered. May be a notification channel configuration issue or the proposal notification templates are not registered. |
| GAP-PE-008 | Portal page title says "Client Portal \| DocTeams" instead of firm name | LOW | branding | Browser tab shows generic "DocTeams" branding instead of the org/firm name. Minor polish issue. |
