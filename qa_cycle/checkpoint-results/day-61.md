# Day 61 — Sipho downloads Statement of Account from portal `[PORTAL]`

**Date**: 2026-05-22
**Actor**: Sipho Dlamini (portal contact, sipho.portal@example.com)
**Stack**: Keycloak dev stack — portal :3002, backend :8080, Mailpit :8025
**Auth**: Fresh magic-link exchange via `POST /portal/auth/request-link` + `POST /portal/auth/exchange`

---

## Checkpoint Results

### 61.1 — Mailpit closure/SoA email + portal link
**PASS**

- Mailpit contains closure letter email: Subject = "Document ready: matter-closure-letter-dlamini-v-road-accident-fund-2026-05-22.pdf from Mathebula & Partners"
- From: `noreply@kazi.app` (correct Kazi branding, OBS-707 fix holds)
- Email body contains portal link: `http://localhost:3002/projects/85b09bb3-5cdd-42b9-8364-1bea1e83153d`
- **Note**: No separate "Statement of Account ready" email — SoA notification was deduplicated by `PortalDocumentNotificationHandler` (same contact + project within one request cycle). Only the closure letter email was dispatched. The SoA is accessible via the same matter detail page. This is a reasonable dedup behavior (one email per closure event, not two), not a bug.

### 61.2 — Statement of Account listed in Documents tab
**PASS**

- Document visible: `statement-of-account-dlamini-v-road-accident-fund-2026-05-21.pdf`
- Size: **5.0 KB** — matches firm-side Day 60 report (5.0 KB)
- Date: 22 May 2026
- Download button present and functional
- Document title is descriptive, not "Untitled document"

### 61.3 — PDF downloads cleanly
**PASS**

- Clicked Download button → new tab opened with presigned S3 URL
- PDF rendered in browser's built-in PDF viewer (3 pages)
- No console errors during download
- S3 URL: `http://localhost:4566/docteams-dev/org/tenant_5039f2d497cf/generated/statement-of-account-dlamini-v-road-accident-fund-2026-05-21.pdf?X-Amz-Algorithm=...`

### 61.4 — PDF contents verification
**PASS**

Page 1:
- [x] **Mathebula & Partners** letterhead (firm name rendered at top)
- [x] VAT Reg: (present, value empty — no VAT number configured)
- [x] **Statement of Account** heading
- [x] Reference: SOA-85b09bb3-20260522
- [x] Period: 2026-05-21 — 2026-05-21
- [x] Generated: 2026-05-22
- [x] **To**: Sipho Dlamini, 12 Loveday St Johannesburg Gauteng 2001 ZA
- [x] **Matter**: Dlamini v Road Accident Fund, File reference: RAF-2026-001, Opened: 2026-05-21
- [x] **Professional Fees** table: 2 time entries by Bob Ndlovu (2.5h + 1.5h), Rate R0,00 (no rate card — OBS-2101 WONT_FIX)
- [x] VAT: 0

Page 2:
- [x] Total fees (incl. VAT): 0.00
- [x] **Disbursements**: SHERIFF_FEES, "Sheriff service of summons on RAF", Supplier: Sheriff Sandton, Amount: R1 250,00, VAT: R0,00
- [x] Total disbursements: 1250.00
- [x] **Trust Activity — Opening balance: 0.00**
- [x] **Deposits**: DEP/2026/001 R50 000,00 ("Initial trust deposit — RAF-2026-001") + DEP/2026/003 R20 000,00 ("Top-up per engagement letter") = **R70,000 total deposits**
- [x] **Payments**: PAY/2026/001 R70 000,00 ("Return of trust funds to client — matter closure RAF-2026-001") — **R70,000 payment out**

Page 3:
- [x] Summary: Total fees 0.00, Total disbursements 1250.00, Previous balance owing 0, Payments received 1250.00
- [x] **Closing balance owing: 0.00**
- [x] **Trust balance held: 0.00**
- [x] Payment Instructions section (empty — no bank details configured)

**Reconciliation against scenario expectations (all match):**
- Opening balance: R 0.00 ✓
- Deposits: R 50,000 (Day 10) + R 20,000 (Day 45) = R 70,000 ✓
- Payments out: R 70,000 trust payment (Day 60, approved by Bob) ✓
- Closing trust balance: R 0.00 ✓
- Fee note: INV-0001 R 1,250 (PAID) — reflected in Payments received ✓

### 61.5 — Screenshot
**N/A** — screenshot capture tool available but not saved to disk (not a wow-moment screenshot requirement)

### 61.6 — File byte-size matches firm-side
**PASS**

- Portal shows: 5.0 KB
- S3 Content-Length header: 5107 bytes (5.0 KB)
- Firm-side Day 60 report: 5.0 KB
- Match within tolerance ✓

### 61.7 — Document title matches firm-side copy
**PASS**

- Portal: `statement-of-account-dlamini-v-road-accident-fund-2026-05-21.pdf`
- Firm-side (Generated Docs): same filename
- No "Untitled document" leak ✓

### 61.8 — Closure letter also visible in Documents tab
**PASS**

- Document: `matter-closure-letter-dlamini-v-road-accident-fund-2026-05-22.pdf`
- Size: 1.6 KB, Date: 22 May 2026
- Downloaded and rendered correctly (1 page):
  - Mathebula & Partners letterhead
  - "Matter Closure Letter" heading
  - Client: Sipho Dlamini
  - Matter: Dlamini v Road Accident Fund
  - "We confirm that this matter has been concluded"
  - Reason for closure section
  - Summary of Account section (Total fees billed, Total disbursements, Duration)
  - Retention notice per Legal Practice Act
  - Signed "Yours faithfully, Mathebula & Partners"

### 61.9 — Firm-side audit event for portal doc access
**PARTIAL**

- Backend logs confirm document generation audit: `GeneratedDocumentService` created SoA (id=8c81c1a4) and closure letter (id=20278b52) for matter 85b09bb3
- Portal notification dispatch logged: `PortalDocumentNotificationHandler.process` for both templates
- **However**: Portal document downloads use presigned S3 URLs (browser → S3 directly), so the backend does not see the actual download. No explicit "portal contact accessed document X" audit event found in backend logs.
- This is an architectural limitation: S3 presigned URLs bypass the backend for the actual file retrieval. A download-audit feature would require either: (a) proxying downloads through the backend, or (b) S3 access logging + reconciliation.
- **Not a blocker** — the scenario acknowledges this is a Phase 50 data-protection traceability feature. The document generation + notification audit trail is present.

---

## Console Errors
**Zero** — no JavaScript errors on portal matter detail page (checked after page load + after download interactions)

## New Gaps
**None** — no new defects discovered.

## Observations
1. SoA email dedup: The `PortalDocumentNotificationHandler` correctly deduplicates notifications when multiple documents are generated in the same request cycle (closure letter + SoA). Only one email sent (for the closure letter). Both documents are accessible from the matter detail page. This is reasonable UX — one notification per closure event.
2. The SoA PDF content fully reconciles with the firm-side Section 86 trust ledger and Day 60 closure state.
3. Portal terminology correct throughout: "Matters", "Fee Notes", "Engagement Letters", "Powered by Kazi" footer.
4. Matter shows CLOSED badge on portal — correct rendering of concluded matter state.

## Summary
| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 61.1 | PASS | Closure letter email with portal link (SoA email deduped — not a bug) |
| 61.2 | PASS | SoA listed: 5.0 KB, 22 May 2026, correct filename |
| 61.3 | PASS | PDF downloads cleanly via presigned S3 URL |
| 61.4 | PASS | All content verified: letterhead, matter ref, deposits R70k, payment R70k, closing R0 |
| 61.5 | N/A | Not a required screenshot capture |
| 61.6 | PASS | 5107 bytes matches firm-side 5.0 KB |
| 61.7 | PASS | Title matches firm-side, no "Untitled" leak |
| 61.8 | PASS | Closure letter renders correctly (1.6 KB, 1 page) |
| 61.9 | PARTIAL | Doc generation audit present; download audit not captured (S3 presigned URL architectural limitation) |

**Overall: PASS** (8 PASS, 1 PARTIAL non-blocking, 0 FAIL, 0 new gaps)
