# Day 61 — Sipho downloads Statement of Account from portal `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini (portal :3002, existing session still valid).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 61.1 | PARTIAL (LZKC-015 consequence) | No "Statement of Account ready" email exists (Day 60 finding). Used the closure-letter "Document ready" email (Mailpit `TVipfc75zA7KqXbLid62Be`) → link `http://localhost:3002/projects/272be4f8…` → landed on portal matter detail, header "Dlamini v Road Accident Fund · CLOSED" |
| 61.2 | PASS | Documents table lists **statement-of-account-dlamini-v-road-accident-fund-2026-07-06.pdf · 5.0 KB · 6 Jul 2026** (and closure letter 1.6 KB, 3 FICA PDFs, 2 medical-evidence PDFs) |
| 61.3 | PASS | Download button opens presigned S3 URL (LocalStack `/docteams-dev/org/tenant_5039f2d497cf/generated/…`, X-Amz sig, 3600s expiry) in new tab; fetched → valid **PDF 1.6/5.1 KB, `PDF document, version 1.6`**, 3 pages |
| 61.4 | PARTIAL → **LZKC-017** | Content verified: firm name heading, "To: Sipho Dlamini, 12 Loveday St Johannesburg 2001 ZA", Matter + **File reference RAF-2026-001**, fees table (2 entries, 2.5h + 1.5h @ R0), Disbursements SHERIFF_FEES **R 1 250,00**, Trust Activity — Opening 0, Deposits **DEP/2026/001 R 50 000,00 + DEP/2026/003 R 20 000,00**, Payments **PAY/2026/001 R 70 000,00**, **Closing balance 0.00**, Summary payments received 1250.00 / closing owing 0.00. **Gaps**: no letterhead logo/contact details; "VAT Reg:" blank; "Payment Instructions" section heading with empty body (no banking details); fee note INV-0001 not itemised by reference (only implied via disbursement + payment received); mixed number locale ("R50 000,00" vs "1250.00") |
| 61.5 | PASS | 📸 `day-61-portal-soa-download.png` |
| 61.6 | PASS | Same S3 object served portal & firm (single `generated/` key; firm list shows 5.0 KB, download 5.1 KB on disk — within rounding) |
| 61.7 | PASS | Portal file name identical to firm-side Work > Documents entry; no "Untitled document" |
| 61.8 | PARTIAL → **LZKC-018** | Closure letter downloads and renders, but **all template variables blank**: "Date:", "Reason for closure:", "Total fees billed:", "Total disbursements:", "Duration (months):" have no values (reason was Concluded, fees/disbursements known). Static copy + client/matter names render fine |
| 61.9 | PASS + observation (**LZKC-019**) | Firm session (Thandi) → matter Activity tab: "**Sipho Dlamini performed portal.document.downloaded on document** — just now / 2 minutes ago" (2 events = SoA + closure letter, actor = portal contact, Day 61 timestamps). Observation: activity feed prints raw event keys for portal/statement events ("portal.document.downloaded on document", "statement.generated on generated_document") without document names, while task events get friendly copy |

## Day 61 day-level checkpoints

- SoA downloads cleanly end-to-end: **PASS**
- Contents reconcile to firm-side Section 86 ledger + Day 60 closure state: **PASS** (trust figures exact: 50k+20k in, 70k out, 0.00 closing)
- Firm-side audit event for portal doc access: **PASS**

## New gaps

- **LZKC-017 (Low)** — SoA PDF not client-ready: VAT Reg blank, empty "Payment Instructions" (no banking details), no letterhead logo/contact block, INV-0001 not referenced, mixed ZA/plain number formats (same defect family as LZKC-007).
- **LZKC-018 (Medium)** — Matter Closure Letter template variables not populated: Date, Reason for closure, Total fees billed, Total disbursements, Duration all render blank in the client-facing PDF (LZKC-010 class).
- **LZKC-019 (Low)** — Matter activity feed prints raw audit event keys for portal/document events ("performed portal.document.downloaded on document") with no document name; inconsistent with friendly task-event copy.

## Console

Portal pages: 0 errors during Day 61 interactions.
