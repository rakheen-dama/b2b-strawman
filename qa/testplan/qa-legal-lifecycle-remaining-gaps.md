# Remaining Gaps — 90-Day SA Law Firm Lifecycle QA

**Source**: QA cycle on branch `bugfix_cycle_2026-04-06` (PR #983)
**Date**: 2026-04-07
**Total gaps found**: 31 | **Fixed**: 19 | **Remaining**: 12

---

## Feature Gaps (WONT_FIX — requires new development, not bugfix)

### GAP-D0-02 — Trust Accounting: No "Create Trust Account" dialog (HIGH)
- **Impact**: Blocks ALL trust accounting functionality — deposits, fee transfers, reconciliation, interest runs, investments, Section 35 compliance reporting. This is the single largest gap for fork-readiness.
- **Root cause**: Phase 61 built the full trust accounting backend + dashboard, but never built `CreateTrustAccountDialog`. The dashboard loads, shows summary cards, but the "New Account" button is missing.
- **Workaround**: Create trust accounts via direct API call — but GAP-D0-09 (403) may block this in mock-auth mode.
- **Fix scope**: New React component (~200 lines) wired into the trust dashboard page. Estimated 2-4 hours. Backend API endpoints exist and work.
- **Blocked test steps**: 14.1-14.11, 14.22-14.24, 30.16-30.20, 45.1-45.7, 60.1-60.20, 90.8-90.15

### GAP-D0-03 — No Settings > Modules page (LOW)
- **Impact**: Cannot view/toggle which legal modules are enabled per org. Cosmetic — modules work correctly via the vertical profile system.
- **Fix scope**: New settings page with module toggle UI. Not critical for fork-readiness since modules are auto-configured by profile.

### GAP-D7-03 — No task status change for Member role (LOW)
- **Impact**: Carol (Candidate Attorney / Member) cannot change task status from the task detail dialog. The "Claim" button self-assigns but doesn't transition status.
- **Root cause**: Task detail dialog doesn't expose a status dropdown for Member role — by design, not a bug.
- **Fix scope**: Add status dropdown to task detail with permission-aware controls. Estimated 1-2 hours.

### GAP-D30-04 — Invoice prerequisite UX: address fields not prompted during client creation (LOW)
- **Impact**: Users must manually fill Address Line 1, City, Country, Tax Number on the client custom fields before they can create their first fee note. The prerequisite dialog warns correctly, but the address fields aren't part of the standard client creation form.
- **Fix scope**: UX redesign of client creation form to include address fields, or add a guided "complete client profile" step before first invoice. Estimated 2-4 hours.

### GAP-D45-02 — No auto-prescription from matter type (MEDIUM)
- **Impact**: Prescription trackers must be manually created. The test plan expects personal injury matters to auto-create a 3-year tracker per the Prescription Act.
- **Root cause**: No mapping exists between matter types and prescription periods. The manual "Add Tracker" button works correctly.
- **Fix scope**: New prescription rule mapping (matter_type → prescription period), triggered on matter creation. Estimated 4-6 hours including Prescription Act schedule data.

### GAP-D75-01 — Information request dialog lacks Subject and Items fields (MEDIUM)
- **Impact**: Cannot compose ad-hoc information requests from the UI. The dialog only shows Template, Portal Contact, and Reminder Interval fields.
- **Root cause**: The design is template-first — items come from the selected template. Ad-hoc requests (no template) create empty requests that can be edited later.
- **Fix scope**: Add inline Subject and Items fields for ad-hoc composition. Estimated 2-3 hours.

---

## Unfixed Bugs (OPEN/SPEC_READY)

### GAP-D0-09 — Trust account API returns 403 for Owner role (MEDIUM, SPEC_READY)
- **Impact**: Cannot create trust accounts via API workaround in E2E mock-auth stack. May be related to stale member records or role mapping in mock-auth mode.
- **Root cause hypothesis**: The trust account endpoints may check for a specific capability or role that isn't correctly mapped from the mock IDP's JWT claims. The triage expected this to resolve after the GAP-D1-02 rebuild, but it was never re-verified.
- **Fix scope**: Investigate trust endpoint permission checks, verify role mapping. Estimated 30-60 min.

### GAP-C5-01 — Empty state terminology: "customers"/"projects" instead of "clients"/"matters" (LOW, OPEN)
- **Impact**: Cosmetic. When no clients or matters exist, the empty state text uses generic terms.
- **Affected pages**: Clients page ("No customers yet", "Customers represent the organisations..."), Matters page ("No projects yet", "Projects organise your work...")
- **Fix scope**: Wrap empty state strings with `t()` — same pattern as PR #976. Estimated 15 min.

### GAP-C5-02 — Fee Notes summary shows "$0.00" instead of ZAR (LOW, OPEN)
- **Impact**: Cosmetic. The summary cards (Total Outstanding, Overdue, Paid This Month) display USD format instead of ZAR.
- **Affected page**: Fee Notes list page summary strip
- **Fix scope**: Use org currency setting in the summary formatter. Estimated 30 min.

### GAP-C5-03 — Remaining "Invoice"/"Project" terminology in buttons/links (LOW, OPEN)
- **Impact**: Cosmetic. Partial terminology fix — some buttons and back-links still use generic terms.
- **Affected locations**: "New Invoice" button (should be "New Fee Note"), "Back to Invoices" link, "Back to Projects" link (should be "Back to Matters")
- **Fix scope**: Wrap remaining strings with `t()`. Estimated 15 min.

### GAP-C5-04 — Custom field save may not persist (Country dropdown value issue) (MEDIUM, OPEN)
- **Impact**: When saving custom fields (Address, City, Country, Tax Number) via the UI "Save Custom Fields" button, values may not persist on reload. The Country dropdown may be sending the display label ("South Africa") instead of the option value ("ZA").
- **Fix scope**: Investigate frontend custom field save action — confirm whether dropdown sends value or label. Estimated 30-60 min.

---

## Partially Fixed Items

### GAP-D60-01 — Profitability report heading terminology (LOW, PARTIAL)
- **What's fixed**: Component-level `TerminologyHeading` works — table heading shows "Matter Profitability" within the component.
- **What's not fixed**: The report detail page heading still shows "Project Profitability Report" because it comes from the backend report definition name, not the frontend terminology system.
- **Fix scope**: Either update the backend report definition name to be terminology-aware, or override the page heading with `t()`. Estimated 15 min.

### GAP-D30-03 — Invoice list heading terminology (LOW, PARTIAL)
- **What's fixed**: Main heading says "Fee Notes", sidebar link says "Fee Notes".
- **What's not fixed**: "New Invoice" button text and "Back to Invoices" link still use "Invoice".
- **Fix scope**: Wrap remaining strings. Overlaps with GAP-C5-03. Estimated 15 min.

---

## Code Review Findings (Noted, Not Fixed)

From the PR #983 review by code-reviewer agent:

### ReportService N+1 query risk (LOW PRIORITY)
- **Location**: `backend/.../report/ReportService.java` (lines 263-277)
- **Issue**: The customer name fallback path calls `projectRepository.findById()` then `customerRepository.findById()` inside a loop. For orgs with many projects, this is an N+1 query.
- **Fix**: Batch query approach — `findByIdIn` + collect customer IDs + batch fetch. Low priority since profitability reports cover bounded date ranges.

### Duplicate formatZAR utility (LOW PRIORITY)
- **Location**: `frontend/components/legal/tariff-item-browser.tsx` and `tariff-line-dialog.tsx`
- **Issue**: Identical `formatZAR(amount: number): string` function defined in both files.
- **Fix**: Extract to `lib/format.ts`. Simple one-liner, but not urgent.

### MemberFilter JWT role claim not validated against whitelist (LOW PRIORITY)
- **Location**: `backend/.../member/MemberFilter.java` (lines 168-175)
- **Issue**: Reads `jwt.getClaimAsString("role")` without restricting to known values. Invalid roles cause a 500 error (via `orgRoleService.findSystemRoleBySlug`) rather than a cleaner 400/403.
- **Fix**: Add role whitelist check before setting `effectiveRole`. Low priority since the IDP is trusted in both mock and Keycloak modes.

---

## Summary by Priority

| Priority | Count | IDs |
|----------|-------|-----|
| **HIGH (fork-blocking)** | 1 | GAP-D0-02 (trust dialog) |
| **MEDIUM (functional)** | 4 | GAP-D0-09, GAP-D45-02, GAP-C5-04, GAP-D75-01 |
| **LOW (cosmetic/terminology)** | 7 | GAP-D0-03, GAP-D7-03, GAP-D30-04, GAP-C5-01/02/03, GAP-D60-01 partial |

**Estimated total effort to clear all remaining items**: ~15-20 hours, dominated by trust accounting dialog (2-4h), auto-prescription (4-6h), and information request enhancement (2-3h). The LOW items can be batch-fixed in ~2 hours.
