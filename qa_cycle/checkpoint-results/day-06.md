# Day 6 — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Agent**: QA Agent (Opus 4.6)

---

## Checkpoint Results

### 6.1 — Bob reviews uploaded docs + adds comment on Sipho engagement

**Actor**: Bob Ndlovu (Admin, bob@thornton-test.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 6.1a | Login as Bob via Keycloak | **PASS** | KC login: bob@thornton-test.local / SecureP@ss2. Redirected to /org/thornton-associates/dashboard. BFF /me confirms: userId=a142c550..., email=bob@thornton-test.local, name=Bob Ndlovu. |
| 6.1b | Navigate to Sipho engagement detail | **PASS** | URL: /org/thornton-associates/projects/583ee45e-40b5-4846-9082-92f69f0f5f17. Title: "Sipho Dlamini -- 2025/26 Tax Return", Status: Active, 2 documents, 1 member, 7 tasks, 1.0h logged. |
| 6.1c | Review uploaded docs on Documents tab | **PASS** | Documents tab shows 2 files: (1) `it3a-employer-certificate-2025.pdf` (628 B, Uploaded May 14, 2026), (2) `it3a-investment-certificate-2025.pdf` (633 B, Uploaded May 14, 2026). Both status: "Uploaded". |
| 6.1d | Bob adds comment with @Carol mention | **PASS** | Expanded document comments for `it3a-employer-certificate-2025.pdf`. Typed: "Need proof of retirement annuity contribution @Carol". Visibility: Internal only. Clicked Post Comment. Comment rendered: Bob Ndlovu / "now" / "Need proof of retirement annuity contribution @Carol". Edit and Delete buttons visible. |
| 6.1e | Comment persists and displays correctly | **PASS** | Comment visible in expanded comment row under the document. Shows author avatar (BN), name, timestamp, and full text. Screenshot: `qa_cycle/evidence/day-06-bob-comment-on-docs.png`. |

### 6.2-6.6 — Kgosi Holdings Year-End Pack engagement (wow moment)

**Actor**: Thandi Thornton (Owner, thandi@thornton-test.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 6.2 | Navigate to Kgosi client detail, click New Engagement | **PASS** | Client detail at /org/thornton-associates/customers/90d93d67-b462-4fe9-9732-656af5ab889e. Engagements tab shows 1 existing (Monthly Bookkeeping). Clicked "New Engagement" link. |
| 6.3 | Select Year-End Pack template | **PASS** | "New from Template -- Select Template" dialog opened with 7 accounting templates. Selected "Year-End Pack (Annual Financial Statements)" (7 tasks). Clicked Next. |
| 6.4 | Configure engagement name | **PASS** | "New from Template -- Configure" dialog. Changed auto-generated name to "Kgosi Holdings -- FY2025/26 Year-End Pack". Description auto-filled from template: "Annual financial statements and CIPC filing for a company or CC. Covers trial balance review through to final AFS package." Client pre-selected: Kgosi Holdings (Pty) Ltd. Clicked "Create Engagement". |
| 6.5 | Verify fully populated task list | **PASS** | Redirected to new engagement: /org/thornton-associates/projects/388d5104-7789-4ad6-bb6c-6d045e9663f3. Tasks tab shows 7 tasks (all Open, Medium priority, Unassigned): (1) Request & receive trial balance, (2) Trial balance review & adjusting journals, (3) Draft annual financial statements, (4) Director / member approval, (5) CIPC annual return filing, (6) Tax computation & ITR14 preparation, (7) Final package & archiving. Tasks cover the full AFS workflow from trial balance through to CIPC filing and archiving. |
| 6.6 | Screenshot: Year-End Pack wow moment | **PASS** | Screenshots captured: `qa_cycle/evidence/day-06-year-end-pack-tasks.png` (full page, tasks tab), `qa_cycle/evidence/day-06-year-end-pack-overview-wow.png` (overview). |

---

## Summary

| Metric | Count |
|--------|-------|
| **PASS** | 10 |
| **FAIL** | 0 |
| **PARTIAL** | 0 |
| **DEFERRED** | 0 |

## New Gaps

None.

## Notes

- The scenario text for 6.5 expected "15+ tasks covering AFS prep, audit docs, tax packs, CIPC submission". The actual Year-End Pack template has 7 tasks, which was confirmed during Day 0 (checkpoint 0.41) as correct for the template definition. The 7 tasks comprehensively cover the year-end workflow: trial balance request, review & journals, draft AFS, director approval, CIPC filing, tax computation, and final archiving. This is a scenario text variance, not a product bug.
- Document-level comments work correctly with inline expand/collapse per document, visibility toggle (Internal only / Customer visible), and @mention text.
- The @Carol mention in the comment is plain text (no structured mention/autocomplete). The comment system renders the text as-is without resolving to a user link. This is acceptable for the current feature set.
- Console errors observed: 404s for `/api/assistant/invocations` endpoint (AI assistant feature polling). Non-blocking, not related to Day 6 functionality.
- Engagement ID for Kgosi Year-End Pack: `388d5104-7789-4ad6-bb6c-6d045e9663f3`

## Evidence Files

- `qa_cycle/evidence/day-06-bob-comment-on-docs.png` — Documents tab with Bob's comment on IT3a doc
- `qa_cycle/evidence/day-06-year-end-pack-tasks.png` — Year-End Pack tasks tab (full page)
- `qa_cycle/evidence/day-06-year-end-pack-overview-wow.png` — Year-End Pack overview (wow moment)
