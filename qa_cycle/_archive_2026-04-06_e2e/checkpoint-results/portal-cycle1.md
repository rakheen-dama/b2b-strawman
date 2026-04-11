# Portal Cycle 1 — Checkpoint Results (T1, T2, T7)

**Date**: 2026-03-25
**Branch**: `bugfix_cycle_portal_2026-03-25`
**Test Plan**: `qa/testplan/portal-experience-proposal-acceptance.md`
**Stack**: Keycloak dev stack (portal :3002, frontend :3000, backend :8080, gateway :8443)
**Primary Customer**: Naledi Corp QA (ACTIVE, naledi@qatest.local)
**Cross-Customer**: Kgosi Holdings QA Cycle2 (kgosi@qatest.local)

---

## Track 1 — Magic Link Authentication

### T1.1 — Request Magic Link

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.1.1 — Portal auth endpoint exists | PASS | `POST /portal/auth/request-link` responds 200 |
| T1.1.2 — Magic link requested for Naledi | PASS | Request with `{email: "naledi@qatest.local", orgId: "thornton-associates"}` returns generic message + dev-mode magic link |
| T1.1.3 — Magic link email received in Mailpit | PASS | Email received at `naledi@qatest.local`, Mailpit message ID `BLU7Bu5itgSd3pMg2hGC2a` |
| T1.1.4 — Email content correct | PARTIAL | Recipient: naledi@qatest.local. Subject: "Your portal access link from **DocTeams**" (should be "Thornton & Associates"). Body contains: greeting "Hi Naledi Corp QA", magic link button, 15-min expiry notice. See GAP-PE-001. |
| T1.1.5 — Extract magic link URL | PASS | URL: `/auth/exchange?token={token}&orgId=thornton-associates` (dev-mode inline link) |
| T1.1.6 — Link points to portal | PARTIAL | Dev-mode inline link is relative to portal (:3002) and works. Email link points to `http://localhost:3000/portal/auth?token=...` (frontend :3000, not portal :3002). See GAP-PE-002. |

### T1.2 — Exchange Token for Session

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.2.1 — Navigate to magic link URL | PASS | Clicking dev-mode link navigates to `/auth/exchange?token=...&orgId=...` |
| T1.2.2 — Token exchanged for portal JWT | PASS | JWT stored in localStorage under `portal_jwt`. JWT payload: `{sub: "4160e3cb-...", type: "customer", org_id: "thornton-associates"}` |
| T1.2.3 — Redirect to portal home | PASS | Redirected to `/projects` — "Your Projects" page with 6 Naledi projects |
| T1.2.4 — Portal header shows customer name | PASS | Header shows "Naledi Corp QA" (customer name, not contact name) |
| T1.2.5 — No console errors | PASS | Only error: 404 on `/favicon.ico` (missing favicon, non-critical) |

### T1.3 — Token Expiry and Reuse

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.3.1 — Same magic link reused → fails | PASS | Reusing the same token URL shows "Login Failed" page with "Link expired or invalid. Please request a new login link." Backend returns 401. |
| T1.3.2 — Error message is user-friendly | PASS | Clean error page with message and "Back to Login" button. No raw stack trace. |
| T1.3.3 — Time-based expiry | OBSERVATION | Token TTL is 15 minutes (configured in `MagicLinkService.TOKEN_TTL_MINUTES`). Not tested in E2E — would require waiting 15+ minutes. |

### T1.4 — Invalid Token

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.4.1 — Tampered token navigated | PASS | URL: `/auth/exchange?token=FAKE_TAMPERED_TOKEN_12345&orgId=thornton-associates` |
| T1.4.2 — System rejects gracefully | PASS | "Login Failed" page, same user-friendly message. Backend returns 401. |
| T1.4.3 — No portal content without auth | PASS | After clearing localStorage, navigating to `/projects` redirects to `/login`. No content leaked. |

**Track 1 Summary**: 13/14 PASS, 2 PARTIAL (email branding gap, email link target mismatch). No blockers.

---

## Track 2 — Portal Home Experience

### T2.1 — Landing Page After Login

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T2.1.1 — Landing page content | PASS | Lands on `/projects` — "Your Projects" heading with 6 project cards. Each card shows: project name, description (if any), document count, relative time. |
| T2.1.2 — Page loads without errors | PASS | No React crashes, no 500s. Only warning: `scroll-behavior: smooth` (harmless). |
| T2.1.3 — Navigation available | PASS | Header with nav links, profile link, logout button. Footer with firm details. |
| T2.1.4 — Available nav items | PASS | Projects, Invoices (main nav). Profile (user icon). Logout (exit icon). |

### T2.2 — Portal Navigation Structure

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T2.2.1 — Navigation links visible | PASS | Projects, Invoices (main nav). Profile page accessible via user icon. |
| T2.2.2 — Each nav item loads without error | PASS | Projects (/projects): 6 project cards. Invoices (/invoices): table with 3 PAID invoices. Profile (/profile): contact info (name, email, role, customer). |
| T2.2.3 — No firm-side nav leaks | PASS | No "Settings", "Team", "Reports", "Profitability", "Dashboard", "Members", "Billing" in nav or page content. Only "DocTeams" in footer/title (product name, not a nav item). |
| T2.2.4 — Logout works | PASS | Clicking Logout clears session and redirects to `/login`. |

**Track 2 Summary**: 8/8 PASS. No issues.

---

## Track 7 — Cross-Customer Data Isolation

### T7.1 — Naledi Cannot See Other Customers' Data

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.1.1 — Login as Naledi | PASS | Authenticated via magic link, header shows "Naledi Corp QA" |
| T7.1.2 — Projects: only Naledi's | PASS | 6 projects shown, all linked to Naledi (customer_id=4160e3cb). Kgosi's "Annual Tax Return 2026 Updated" NOT visible. |
| T7.1.3 — Invoices: only Naledi's | PASS | 3 PAID invoices (INV-0002, INV-0003, INV-0005), all belong to Naledi. |
| T7.1.6 — Search for "Kgosi" | PASS | Zero matches in page content |
| T7.1.7 — Search for "Lifecycle" | PASS | Zero matches in page content |
| T7.1.8 — Search for "Integrity" | PASS | Zero matches in page content |

### T7.2 — Kgosi Cannot See Naledi's Data

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.2.1 — Login as Kgosi | PASS | Authenticated via magic link, header shows "Kgosi Holdings QA Cycle2" |
| T7.2.2 — Projects: only Kgosi's | PASS | 1 project: "Annual Tax Return 2026 Updated". No Naledi projects. |
| T7.2.3 — Invoices: only Kgosi's | PASS | "No invoices yet." (correct — Kgosi has no invoices) |
| T7.2.4 — Search for "Naledi" | PASS | Zero matches in page content |
| T7.2.5 — Search for "QA Onboarding" | PASS | Zero matches (Naledi project name not visible) |

### T7.3 — Direct URL Access Blocked

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.3.1 — Naledi JWT → Kgosi project (236ce695) | PASS | HTTP 404: "No project found with id 236ce695-..." |
| T7.3.2 — Kgosi JWT → Naledi project (1d50a31f) | PASS | HTTP 404: "No project found with id 1d50a31f-..." |
| T7.3.3 — Kgosi JWT → Naledi invoice (0d7ee846) | PASS | HTTP 404: "No invoice found with id 0d7ee846-..." |
| T7.3.4 — Naledi JWT → unlinked project (7b23b072) | PASS | HTTP 404: "No project found with id 7b23b072-..." (project with no customer) |

### T7.4 — API-Level Isolation

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.4.1 — Naledi JWT → `GET /portal/projects` | PASS | Returns 6 projects, all Naledi's |
| T7.4.2 — Kgosi JWT → `GET /portal/projects` | PASS | Returns 1 project (Annual Tax Return 2026 Updated) |
| T7.4.3 — Naledi JWT → Kgosi project by ID | PASS | 404 — not found |
| T7.4.4 — Kgosi JWT → Naledi project by ID | PASS | 404 — not found |
| T7.4.5 — Kgosi JWT → `GET /portal/invoices` | PASS | Returns empty array `[]` |
| T7.4.6 — Kgosi JWT → Naledi invoice by ID | PASS | 404 — not found |

**Track 7 Summary**: 21/21 PASS. Zero data isolation failures. Cross-customer isolation is solid at both UI and API levels.

---

## Gaps Found

| ID | Category | Severity | Summary | Evidence |
|----|----------|----------|---------|----------|
| GAP-PE-001 | branding | LOW | Magic link email subject says "DocTeams" instead of firm name | Email subject: "Your portal access link from DocTeams". Should say "Thornton & Associates". The `PortalEmailService` builds subject as `"Your portal access link from " + orgName` but `emailContextBuilder.buildBaseContext()` may return "DocTeams" as the default org name. |
| GAP-PE-002 | portal-auth | MEDIUM | Magic link URL in email points to frontend (:3000) not portal (:3002) | Email body link: `http://localhost:3000/portal/auth?token=...`. Portal runs on :3002. The `PortalEmailService` uses `docteams.app.base-url` (default: `http://localhost:3000`). The portal exchange page is at `:3002/auth/exchange?token=...&orgId=...`. In dev, the inline magic link works because it's relative. In production, the email link would need to point to the portal domain. |
| GAP-PE-003 | portal-ux | LOW | Raw JSON error shown on 404 project pages | When accessing a non-existent or unauthorized project, the page shows raw API error JSON: `API error: 404 {"detail":"No project found..."}`. Should show a user-friendly "Project not found" page. |

---

## Observations

1. **Customer status discrepancy**: status.md listed Kgosi/Lifecycle/Integrity as OFFBOARDED, but DB shows all customers as ACTIVE. This allowed Kgosi to authenticate via magic link (exchange checks customer status = ACTIVE). If they were truly OFFBOARDED, magic link exchange would fail with "Customer account is no longer active."

2. **Portal invoice filtering**: The portal shows only PAID invoices to customers (3 of 21 total Naledi invoices). DRAFT, VOID, and APPROVED invoices are hidden from the portal view. This is likely by design — customers should only see finalized invoices.

3. **No "Documents" or "Proposals" nav in portal**: The test plan expected Documents and Proposals sections. The current portal has only Projects and Invoices nav items. Documents may be accessible within project detail views (T3 scope). Proposals may not have a portal UI yet (T4 scope).

---

## Screenshots

- `qa_cycle/screenshots/portal-t1-login-page.png` — Login page with Thornton & Associates branding
- `qa_cycle/screenshots/portal-t1-magic-link-sent.png` — Magic link sent confirmation with dev-mode link
- `qa_cycle/screenshots/portal-t1-authenticated-projects.png` — Authenticated portal showing Naledi's 6 projects
- `qa_cycle/screenshots/portal-t7-direct-url-blocked.png` — 404 when accessing another customer's project
