# Day 65 Checkpoint Results — Portal Magic Link for Kgosi Holdings

**Date**: 2026-05-15
**Actor**: Thandi (Owner) + Portal user (Kgosi Holdings)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` steps 65.1–65.2

---

## Checkpoint 65.1 — Portal: generate magic link for Kgosi Holdings primary contact

**Status**: PASS

### Steps executed

1. Navigated to portal login page: `http://localhost:3002/login?orgId=thornton-associates`
2. Verified Thornton & Associates branding loaded (logo + org name visible on login page)
3. Entered email: `finance@kgosi-holdings.co.za` (Kgosi Holdings auto-provisioned portal contact)
4. Clicked "Send Magic Link"
5. Success screen: "Check your email for a login link. We sent a link to finance@kgosi-holdings.co.za."
6. Dev mode magic link displayed on screen
7. Verified email received in Mailpit:
   - **To**: `finance@kgosi-holdings.co.za`
   - **Subject**: "Your portal access link from Thornton & Associates"
   - **Body snippet**: "Access Your Portal Hi Kgosi Holdings (Pty) Ltd, Click the button below to securely access your portal. This link will expire in 15 minutes."
   - **Mailpit message ID**: `8Qr6XnCssW5FR8Y6fNpdwH`
8. Clicked magic link — token exchanged, redirected to `/projects`
9. Portal loaded with Kgosi Holdings (Pty) Ltd identity in header

### Portal content verification

**Engagements (projects page)**:
- Kgosi Holdings — FY2025/26 Year-End Pack (ACTIVE, 0 portal-shared documents)
- Kgosi Holdings — Monthly Bookkeeping (Mar 2026) (ACTIVE, 0 portal-shared documents)

**Invoices page** (4 invoices, all belonging to Kgosi Holdings):
- INV-0001: PAID, R 6,411.25 (bookkeeping)
- INV-0003: SENT, R 33,752.50 (year-end pack)
- INV-0004: SENT, R 6,037.50 (bookkeeping April)
- INV-0005: SENT, R 6,037.50 (bookkeeping May)
- PDF download buttons available for each invoice

**Engagement detail** (Year-End Pack):
- Title, description, ACTIVE status visible
- Tasks section: "No tasks yet." (no portal-shared tasks)
- Documents section: "No documents shared yet." (firm-side docs not portal-shared)
- Comments: 2 comments visible (Bob + Thandi from Day 12–13)
- Comment input available for portal user to post

**Home page**:
- Pending info requests: 0
- Upcoming deadlines: 0
- Recent invoices: INV-0001 (R 6,411.25), INV-0003 (R 33,752.50), INV-0004 (R 6,037.50)

### Evidence
- `qa_cycle/evidence/day-65/portal-projects-page.png`
- `qa_cycle/evidence/day-65/portal-engagement-detail.png`
- `qa_cycle/evidence/day-65/portal-invoices-page.png`

---

## Checkpoint 65.2 — Portal respects accounting-za terminology

**Status**: FAIL

### Terminology violations found

The portal sidebar and page headings do NOT respect the accounting-za vertical profile terminology:

| Location | Expected (accounting-za) | Actual | Severity |
|----------|-------------------------|--------|----------|
| Sidebar nav link | **Engagements** | **Matters** | MEDIUM |
| Projects list heading | **Your Engagements** | **Your Projects** | MEDIUM |
| Engagement detail breadcrumb | "Back to engagements" | "Back to projects" | LOW |

The portal app (port 3002) uses hardcoded "Matters" (legal vertical default) in its sidebar navigation and "Your Projects" (generic default) in page headings. It does not read the tenant's vertical profile to apply terminology overrides.

The firm-side app (port 3000) correctly uses "Engagements" throughout — the portal is the outlier.

### Gap filed

**OBS-4010**: Portal does not respect vertical-profile terminology overrides. Sidebar shows "Matters" (legal default) instead of "Engagements" (accounting-za). Page heading shows "Your Projects" instead of "Your Engagements". The portal app needs to read the tenant's vertical profile terminology config and apply it to navigation labels and page headings.

---

## Summary

| Checkpoint | Status | Notes |
|-----------|--------|-------|
| 65.1 — Portal magic link + content verification | PASS | Full flow: login -> magic link -> email -> exchange -> portal content visible |
| 65.2 — Accounting-za terminology in portal | FAIL | OBS-4010: Portal uses hardcoded "Matters"/"Projects" instead of vertical-specific "Engagements" |

**Overall Day 65**: 1 PASS / 1 FAIL
