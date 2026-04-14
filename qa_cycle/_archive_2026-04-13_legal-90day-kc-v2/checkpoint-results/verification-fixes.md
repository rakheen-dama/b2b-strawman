# Verification: 8 FIXED Gaps — QA Cycle 2026-04-13

**Date**: 2026-04-13
**Verified by**: QA Agent (automated)
**Auth**: Bob Ndlovu (bob@mathebula-test.local) via Keycloak
**Stack**: Keycloak dev stack (localhost:3000 / 8080 / 8443 / 8180)

## Results Summary

| GAP_ID | Status | Evidence |
|--------|--------|----------|
| GAP-D0-01 | VERIFIED | Dashboard subtitle reads "Company overview and **matter health**" (not "project health"). Confirmed at `/org/mathebula-partners/dashboard`. |
| GAP-D36-04 | VERIFIED | Matter overview Unbilled Time box shows link "**Generate Fee Note**" (not "Generate Invoice"). Confirmed on Sipho Dlamini matter Overview tab. |
| GAP-D36-05 | VERIFIED | Client detail Unbilled Time section shows action link "**Create Fee Note**" (not "Create Invoice"). Confirmed on Sipho Dlamini client page. |
| GAP-D38-02 | VERIFIED | Fee note detail page (INV-0001) shows section heading "**Fee Note Details**" (not "Invoice Details"). Breadcrumb says "Fee Note", back link says "Back to Fee Notes". |
| GAP-D50-01 | VERIFIED | Fee Notes list page table header says "**Fee Note**" (not "Invoice"). Page heading says "Fee Notes", breadcrumb says "fee notes", button says "New Fee Note". |
| GAP-D6-04 | VERIFIED | Posted NEW comment on task "Letter of demand". Activity feed shows: **'Bob Ndlovu commented on task "Letter of demand"'** — actual task name, not generic "task". Old pre-fix comments still show "task" (expected backward-compatible fallback). |
| GAP-D75-02 | VERIFIED | Clicked "Complete Matter" on Sipho Dlamini matter (9 open tasks), then clicked "Complete" in dialog. Prominent **Alert banner** (alert role element with icon) appeared: "Project has 9 open task(s). Complete or cancel all tasks before completing the project." — clearly visible, not silent failure. |
| GAP-D80-01 | VERIFIED | Navigated to `/org/mathebula-partners/reports/profitability`. Page **redirected** to `/org/mathebula-partners/profitability` and loaded the profitability page correctly. |

## Verification Details

### GAP-D0-01: Dashboard terminology
- **Fix**: PR #1027 — dashboard subtitle uses `t("project")` for vertical translation
- **Check**: Navigated to dashboard after KC login as Bob
- **Observed**: Subtitle "Company overview and matter health" — correct for legal-za vertical

### GAP-D36-04: Matter overview unbilled time link
- **Fix**: PR #1027 — TerminologyText wraps "Generate Invoice" link
- **Check**: Opened Sipho Dlamini matter, scrolled to Overview tab, Unbilled Time box
- **Observed**: Link text "Generate Fee Note" — correct

### GAP-D36-05: Client detail unbilled time action
- **Fix**: PR #1027 — TerminologyText wraps "Create Invoice" label, ActionCard label type widened
- **Check**: Opened Sipho Dlamini client detail page
- **Observed**: Action link "Create Fee Note" in Unbilled Time section — correct

### GAP-D38-02: Fee note detail heading
- **Fix**: PR #1027 — heading uses `t("Invoice")` for translation
- **Check**: Opened INV-0001 (Sipho Dlamini, Paid)
- **Observed**: Section heading "Fee Note Details" — correct

### GAP-D50-01: Fee notes list table header
- **Fix**: PR #1027 — TerminologyHeading wraps table column header
- **Check**: Navigated to `/org/mathebula-partners/invoices`
- **Observed**: Table column header "Fee Note" — correct

### GAP-D6-04: Activity feed comment entity name
- **Fix**: PR #1028 — CommentService resolves entity name from TaskRepository/DocumentRepository at audit time
- **Check**: Posted NEW comment on task "Letter of demand", then checked Activity tab
- **Observed**: New event: 'Bob Ndlovu commented on task "Letter of demand"' — shows actual task name
- **Backward compat**: Old events (pre-fix) still show 'commented on task "task"' — expected fallback

### GAP-D75-02: Complete Matter open tasks error
- **Fix**: PR #1026 — Alert component (destructive variant + CircleAlert icon) replaces subtle `<p>` text
- **Check**: Clicked "Complete Matter" on Sipho matter (9 open tasks), then "Complete"
- **Observed**: Alert banner with icon: "Project has 9 open task(s)..." — prominent and clearly visible

### GAP-D80-01: Reports profitability redirect
- **Fix**: PR #1027 — redirect page at `/reports/profitability` → `/profitability`
- **Check**: Navigated directly to `/org/mathebula-partners/reports/profitability`
- **Observed**: URL redirected to `/org/mathebula-partners/profitability`, page loaded correctly

## Conclusion

All 8 FIXED gaps are VERIFIED. Zero regressions observed. Zero new gaps found during verification.
