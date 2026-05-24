# Day 10 — Bob uploads bank statements to bookkeeping engagement documents

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, KC `:8180`, Mailpit `:8025`)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
**Actor**: Bob Ndlovu (Admin)

---

## Pre-flight

- Logged out Carol Mokoena (previous session) via User Menu > Sign out.
- Navigated to `http://localhost:3000/dashboard` -> KC redirect to Keycloak login.
- Authenticated as `bob@thornton-test.local` / `[REDACTED]`.
- Redirected to `/org/thornton-associates/dashboard`. Sidebar confirms "Bob Ndlovu" / `bob@thornton-test.local`.
- Dashboard shows: 3 Active Engagements, 9.5h Hours This Month, 0 Overdue Tasks.

---

## Checkpoint 10.1 — Bob uploads bank statements to bookkeeping engagement documents

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| 10.1a | Login as Bob (Admin) | **PASS** | KC login: bob@thornton-test.local / [REDACTED]. Redirected to `/org/thornton-associates/dashboard`. Sidebar: "Bob Ndlovu". |
| 10.1b | Navigate to Engagements list | **PASS** | `/org/thornton-associates/projects` loaded. 3 engagements listed: Kgosi Holdings Year-End Pack, Kgosi Holdings Monthly Bookkeeping (Mar 2026), Sipho Dlamini Tax Return. |
| 10.1c | Open Kgosi Holdings Monthly Bookkeeping engagement | **PASS** | Clicked engagement link. URL: `/org/thornton-associates/projects/a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`. Header: "Kgosi Holdings -- Monthly Bookkeeping (Mar 2026)" / Active. Client: Kgosi Holdings (Pty) Ltd. Ref: BK-2026-03-0001. Type: BOOKKEEPING. Metadata: "0 documents, 1 member, 6 tasks". |
| 10.1d | Click Documents tab | **PASS** | Documents tab selected. Initial state: "No documents yet" with drag-and-drop upload area. |
| 10.1e | Upload bank-statement-mar-2026.pdf | **PASS** | Clicked upload area -> file chooser. Selected `bank-statement-mar-2026.pdf` (416 B). File appeared in documents table: Name=bank-statement-mar-2026.pdf, Size=416 B, Status=Uploaded, Date=May 15, 2026. Download button available. |
| 10.1f | Upload bank-statement-feb-2026.pdf | **PASS** | Clicked upload area -> file chooser. Selected `bank-statement-feb-2026.pdf` (416 B). File appeared in documents table alongside first file. Name=bank-statement-feb-2026.pdf, Size=416 B, Status=Uploaded, Date=May 15, 2026. Download button available. |
| 10.1g | Verify document count in engagement header | **PASS** | Header metadata updated to "2 documents" (was "0 documents" before uploads). |
| 10.1h | Verify download works (S3 storage) | **PASS** | Clicked "Download bank-statement-mar-2026.pdf" button. Navigated to S3 presigned URL: `http://localhost:4566/docteams-dev/org/thornton-associates/project/a32c67d5-8e09-47b9-82ec-f0e82fa94ec4/15bdca7c-b0d7-459f-aa9f-98274f571d2d`. Document ID: `15bdca7c-b0d7-459f-aa9f-98274f571d2d`. File served from LocalStack S3. |
| 10.1i | Verify Activity tab records upload events | **PASS** | Activity tab shows 5 events from Bob Ndlovu: (1) "uploaded document bank-statement-mar-2026.pdf", (2) "document.created", (3) "uploaded document bank-statement-feb-2026.pdf", (4) "document.created", (5) "document.accessed" (from download test). All attributed to Bob Ndlovu. |
| 10.1j | Verify documents persist after tab switch | **PASS** | Switched to Activity tab then back to Documents tab. Both files still present in table with correct metadata. |

---

## Day 10 Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 10.1 Bob uploads bank statements to bookkeeping engagement documents | **PASS** | 2 PDFs uploaded (Mar + Feb 2026 bank statements). Both stored in S3, both downloadable, both recorded in activity log. Engagement header reflects "2 documents". |

**Day 10 Result: 1 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**

---

## Console Errors

- `/api/assistant/invocations` returns 404 (pre-existing, non-blocking -- AI assistant feature not wired). Not a new issue.
- No new JavaScript errors during Day 10 walk.

---

## New Gaps Filed

None. Day 10 completed cleanly with no issues.

---

## Evidence

| File | Description |
|------|-------------|
| `qa_cycle/evidence/day-10/documents-uploaded-bank-statements.png` | Documents tab showing both bank statements uploaded with upload confirmation toasts |
| `qa_cycle/evidence/day-10/documents-tab-final-state.png` | Documents tab final state after tab switch (persistence verified) |

---

## Entities Touched

- Document `bank-statement-mar-2026.pdf` (ID: `15bdca7c-b0d7-459f-aa9f-98274f571d2d`) uploaded to engagement `a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`
- Document `bank-statement-feb-2026.pdf` uploaded to engagement `a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`
- Engagement document count: 0 -> 2
- S3 path: `docteams-dev/org/thornton-associates/project/a32c67d5-8e09-47b9-82ec-f0e82fa94ec4/`
- Test fixtures created: `qa_cycle/test-fixtures/bank-statement-mar-2026.pdf`, `qa_cycle/test-fixtures/bank-statement-feb-2026.pdf`
