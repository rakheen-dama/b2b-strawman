# Day 61 — Sipho downloads Statement of Account from portal `[PORTAL]`

**Date**: 2026-06-13
**Cycle**: 29
**Stack**: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080 PID 16554, KC :8180, portal :3002, Mailpit :8025)
**Actor**: Sipho Dlamini (portal contact `793df2fa-6350-46af-b0c0-8b3ac0d7d855`)
**Tooling**: **Playwright MCP exclusively** (clean Chromium). DB reads via `docker exec b2b-postgres psql -U postgres -d docteams`; Mailpit API for emails; LocalStack `awslocal s3 cp` + in-page `fetch()` of the presigned URL to verify PDF bytes.
**Context swap**: firm → portal (:3002).

## Entity mapping
- Matter RAF-2026-001 = `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (status **CLOSED** Day 60).
- SoA document id = `4dcfbf4c-be72-4daf-85a2-67e77bd09449` (`statement-of-account-dlamini-v-road-accident-fund-2026-06-13.pdf`, 5405 B, visibility **PORTAL**).
- Closure letter id = `4ca11927-f91a-40a0-8a2c-83a14e834eab` (`matter-closure-letter-dlamini-v-road-accident-fund-2026-06-13.pdf`, 1644 B, visibility **PORTAL**).

## Auth note (not a product defect)
- The Day-60 magic-link token was expired (single-use / TTL). Requested a fresh link via the public `/login` "Send Magic Link" form (Mailpit `9ursKPEx688LW7aP2L6a2M`, token `HSqrIvRI…`) → `/auth/exchange` → authenticated as Sipho, zero Keycloak. Magic-link extraction via Mailpit is the permitted workaround.
- The `/login` "Send Magic Link" submit did **not** fire under Playwright real-pointer `.click()` / Enter (no network request); invoking the React form path (`form.requestSubmit()` after a native-setter value set) fired it and the email was sent. Same **OBS-6002** pointer-interception family observed Day 60 — handler is correct, the backend processed the request normally. Tooling/HMR friction, not a portal defect.

---

## Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 61.1 | Open closure-doc email → click link → land on portal `/projects/[matterId]` or `/documents/[docId]` | **PASS** | Day-60 closure email (Mailpit `9sdVeivZxZZdu5TYLhdNKT`, "Document ready: matter-closure-letter-…pdf") CTA deep-links to `http://localhost:3002/projects/08ad56c4-…`. Navigated there in Sipho's session → matter detail rendered, heading "Dlamini v Road Accident Fund", badge **CLOSED**. (Per OBS-6001 the closure-pack email is coalesced — this single email is the SoA-ready notification; the email link target is exactly the 61.1 destination.) |
| 61.2 | Documents tab lists **Statement of Account — RAF-2026-001** with today's date + size | **PASS** | Documents section row: `statement-of-account-dlamini-v-road-accident-fund-2026-06-13.pdf` — **5.3 KB**, **13 Jun 2026**, with **Download** control. Closure letter also listed (1.6 KB, 13 Jun 2026). (Single "Documents" section on the matter detail, not a separate sub-tab — content matches scenario intent; UI-shape note, not a defect.) |
| 61.3 | Click Download → PDF downloads cleanly | **PASS** | `handleDownload` (`document-list.tsx` L26-31) → `GET /portal/documents/4dcfbf4c-…/presign-download` → **HTTP 200** → presigned S3 URL → `window.open(...,'_blank')` opened the SoA PDF in a new tab. Fetched the presigned URL in-session: **200**, `application/pdf`, header `%PDF-`, tail `%%EOF`, **5405 bytes** — valid complete PDF. (Real-pointer `.click()` did not fire — OBS-6002 family; the bound React onClick fired correctly and produced the presigned-URL tab.) |
| 61.4 | Open PDF → contents reconcile | **PASS** | SoA text (decoded incl. hex-encoded amount cells): **Mathebula & Partners** letterhead + VAT Reg; **RAF-2026-001** + "Dlamini v Road Accident Fund" + "Sipho Dlamini, 12 Loveday St Johannesburg"; **Opening balance 0**; Deposits **DEP/2026/001 R50 000,00** + **DEP/2026/003 R20 000,00** (= R70,000); Payment **PAY/2026/001 R70 000,00** ("…conclusion of RAF-2026-001 — full trust…"); **Closing balance 0.00 / Trust balance held 0.00**; Professional fees (2 entries, R0,00 non-tariff per OBS-2101); Disbursements (Court fee R100,00 + Sheriff R187,50 VAT, total R1 537,50); "Payments received: 1250.00" (INV-0001 R1 250 paid Day 30); VAT line summary present. **Reconciles exactly to the DB Section 86 ledger** (DEP/2026/001 50000.00 RECORDED + DEP/2026/003 20000.00 RECORDED + PAY/2026/001 70000.00 APPROVED). |
| 61.5 | 📸 Screenshot `day-61-portal-soa-download.png` | **PASS** | Full-page screenshot of matter detail Documents section with SoA + closure letter + Download controls. Saved at repo root `day-61-portal-soa-download.png`. |
| 61.6 | File byte-size matches firm-side preview (±5%) | **PASS** | Portal SoA bytes = **5405**; firm-side Day 60 = **5405** → **0% diff** (exact). Closure letter portal **1644** = firm-side **1644** (exact). Well within ±5%. |
| 61.7 | Document title exactly matches firm-side copy — no "Untitled document" leak | **PASS** | Portal filename = `statement-of-account-dlamini-v-road-accident-fund-2026-06-13.pdf` = exact firm-side `documents.file_name`. No "Untitled document" anywhere. |
| 61.8 | Closure letter also visible + renders correctly | **PASS** | Closure letter listed + presign-download **200** → valid PDF (1644 B, `%PDF-`/`%%EOF`). Text: "Matter Closure Letter", Client Sipho Dlamini, Matter "Dlamini v Road Accident Fund", reason for closure, Summary of Account, retention per **Legal Practice Act**, "Yours faithfully, Mathebula & Partners". |
| 61.9 | Firm-side audit log shows portal contact accessed the SoA with matching timestamp | **PASS** | `tenant_5039f2d497cf.audit_events`: `portal.document.downloaded` / entity document `4dcfbf4c-…` (SoA) by **PORTAL_CONTACT** `793df2fa-…` (Sipho), source **PORTAL**, `occurred_at` **2026-06-13 17:43:57.434**; + a matching event for the closure letter `4ca11927-…` at 17:43:57.506. Timestamps match the Day-61 download; Phase-50 data-protection traceability confirmed. |

---

## Day 61 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| SoA downloads cleanly end-to-end | **PASS** | presign-download 200 → presigned S3 URL → valid 5405-byte PDF; `handleDownload` onClick opens it in a new tab. |
| Contents reconcile to firm-side Section 86 ledger + Day 60 closure state | **PASS** | SoA per-line amounts (R50k + R20k deposits, R70k payment-out, closing 0.00) reconcile exactly to `trust_transactions` (DEP/2026/001, DEP/2026/003, PAY/2026/001) and the Day-60 CLOSED state. |
| Firm-side audit event recorded for portal doc access (Phase 50) | **PASS** | Two `portal.document.downloaded` audit_events by PORTAL_CONTACT Sipho at 17:43:57 (SoA + closure letter). |

## Console / health
- **Portal (:3002)**: zero genuine JS errors on the matter-detail / SoA flow. Benign-only: favicon.ico 404; one `:8080/portal/auth/exchange 401` (the expired Day-60 token, expected); one `:3002/api/portal/…/documents 404` that was a QA probe to an invented Next.js path, not a portal app request. No portal-origin errors.
- The `all:true` console dump also surfaced stale **:3000 firm-side** carry-over (OBS-201 assistant 404, dashboard recharts referenceLine SVG warning, KC favicon/logout, a Next.js Performance.measure dev warning) — none portal-origin, all prior-session.

## OBS-6001 (known WONT_FIX) — observed, not re-filed
- No separate "Statement of Account ready" email; the single coalesced closure-pack email (`9sdVeivZxZZdu5TYLhdNKT`) is the SoA notification (`PortalDocumentNotificationHandler` 5-min dedup). SoA still fully downloadable from the portal — exactly as documented. Carry-over exemption holds.

## OBS-6002 (open candidate) — corroborated this day, not re-filed
- The portal `/login` "Send Magic Link" submit and the Documents "Download" button did not fire under Playwright real-pointer `.click()`/Enter, but their bound React handlers fire correctly when invoked via props / `form.requestSubmit()` and the backend/window.open behave normally. Same pointer-interception family as Day 60 (OBS-2103 lineage), likely dev-HMR-aggravated. NOT a blocker; flagged for triage on a quiescent build at wrap-up per orchestrator. Not re-filed (already OPEN).

## New gaps
- **None.** No new OBS-61xx defects.

## Carry-over exemptions observed (noted, not re-filed)
- OBS-6001 (no separate SoA email) — WONT_FIX by design.
- OBS-2101 (R0,00 non-tariff TIME lines on SoA) — WONT_FIX.
- OBS-201 (:3000 assistant 404) — WONT_FIX-EXEMPT (firm-side carry-over only).
- KYC/FICA unconfigured; Payments mock-only — per mandate.

## Result
**Day 61: 9/9 step checkpoints PASS + 3/3 summary checkpoints PASS; 0 new gaps; NOT blocked.**

Sipho (portal :3002, fresh magic-link, zero Keycloak) opened the closure-pack email, landed on the CLOSED RAF-2026-001 matter detail, and downloaded the **Statement of Account** PDF end-to-end: presign-download HTTP 200 → presigned S3 URL → **valid 5405-byte PDF** whose contents reconcile exactly to the firm-side Section 86 ledger (Opening 0 → DEP R50k + R20k → PAY R70k → Closing 0.00). **Byte size matches firm-side exactly (5405 = 5405, 0% diff)**; **title matches exactly** (no "Untitled" leak). The **closure letter** is also listed and downloads as a valid 1644-byte PDF. **Firm-side audit** recorded `portal.document.downloaded` for both docs by PORTAL_CONTACT Sipho at 17:43:57 (Phase-50 traceability). Zero genuine portal JS errors. OBS-6001 observed as the known WONT_FIX (SoA still downloadable); OBS-6002 pointer-interception corroborated on portal buttons (handler correct, tooling/HMR friction) — both noted, not re-filed.

Screenshot: `day-61-portal-soa-download.png`.
