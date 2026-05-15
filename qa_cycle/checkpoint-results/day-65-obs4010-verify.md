# OBS-4010 Verification — Portal Terminology Fix

**Date**: 2026-05-15
**Actor**: Kgosi Holdings client (finance@kgosi-holdings.co.za via magic link)
**Stack**: Portal on localhost:3002

## Verification Steps

1. Navigated to portal login page (localhost:3002/login?orgId=thornton-associates)
2. Entered email: finance@kgosi-holdings.co.za → sent magic link
3. Retrieved magic link from Mailpit → authenticated → redirected to /projects

### Terminology Checks

| Element | Expected | Observed | Status |
|---------|----------|----------|--------|
| Sidebar nav link | "Engagements" | "Engagements" (ref=e36) | **PASS** |
| Page heading (list) | "Your Engagements" | "Your Engagements" (ref=e72) | **PASS** |
| Breadcrumb (detail page) | "Back to engagements" | "Back to engagements" (ref=e110) | **PASS** |
| Sidebar "Engagement Letters" | "Engagement Letters" | "Engagement Letters" (ref=e52) | **PASS** |

4. Clicked into Year-End Pack engagement → verified breadcrumb "Back to engagements"
5. Sidebar consistently shows "Engagements" not "Matters"
6. Page heading shows "Your Engagements" not "Your Projects"

### Evidence

- Screenshot: `qa_cycle/evidence/obs-4010-verified-terminology.png`

### Result

**OBS-4010: VERIFIED** — Portal respects accounting-za terminology overrides. All navigation labels, headings, and breadcrumbs correctly show "Engagements" (not "Matters" or "Projects").
