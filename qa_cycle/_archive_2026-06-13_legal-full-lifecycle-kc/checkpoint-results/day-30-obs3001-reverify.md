# OBS-3001 Re-verify on main — Portal fee-note PDF download `[FIRM + PORTAL]`

**Date**: 2026-06-13
**Cycle**: 25 (re-verify of OBS-3001 fix, PR #1439 merged to main)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080 PID 16741 — restarted post-merge, portal :3002, Mailpit :8025)
**Tooling**: **Playwright MCP exclusively** (clean Chromium; orphaned prior-session Chrome holding the MCP profile lock was killed + lock cleared first — QA-harness tooling fix, not a Kazi defect). DB reads via `docker exec b2b-postgres psql -d docteams` for assertion; `curl` for S3 presigned-URL HTTP round-trip + Mailpit API for the fresh magic link.
**Fix under test**: OBS-3001 — `InvoiceEmailEventListener.handleInvoiceSent` now routes the send-path render through `GeneratedDocumentService.generateDocument` so the emailed cover-letter PDF is also persisted (S3 + `generated_documents` row), making the portal `/download` resolve a presigned URL instead of 404.

---

## Why a fresh send was needed

The fix fires on `InvoiceSentEvent`. **INV-0001 was sent on Day 28 — BEFORE the fix** — so it has no persisted `GeneratedDocument` (confirmed: `generated_documents` empty tenant-wide on resume; INV-0001 already PAID). A PAID fee note **cannot be re-sent** (`Invoice.markSent()` throws `IllegalStateException` on the PAID→SENT transition → 409, no resend endpoint exists). So per the OBS-3001 spec's fallback, I produced a **fresh `InvoiceSentEvent`** via a new fee note — all browser-driven, mandate-clean.

### How the fresh send was produced (browser UI, Bob :3000)
1. RAF-2026-001 → Finance/Disbursements → **New Disbursement**: Court Fees, **R 100,00** (zero-rated pass-through), Office Account, supplier "Registrar, Gauteng Division", 2026-06-13 → disbursement `d706d5ac-…` (Draft/Unbilled).
2. Disbursement detail → **Submit for Approval** (Draft→Pending) → **Approve** confirm-dialog (Pending→**Approved**/Unbilled).
3. **New Billing Run** (period 2026-06-01→06-30): Step 2 discovered **Sipho — Unbilled Expenses R 100,00**; wizard auto-generated a draft fee note.
4. Draft fee note `27858fee-…` → set Due 2026-07-13 + Tax Type VAT → Save → **Approve** (→ **INV-0002**, Issued 13 Jun, Due 13 Jul) → **Send Fee Note** → expected tax-number soft-warning (Sipho INDIVIDUAL) → admin **Send Anyway** → **SENT**.

---

## Verification

| Check | Result | Evidence |
|-------|--------|----------|
| Backend persists GeneratedDocument on send | **PASS** | Backend trail (requestId `d29ad6e7-…`, 0 ERROR/0 rollback): `Marked invoice 27858fee-… as sent` → `Generated PDF: template=invoice-cover-letter … size=1173bytes` → **`Created generated document: id=cdfa4f1c-… template=… entity=INVOICE/27858fee-…`** → `Invoice email sent … to=sipho.portal@example.com`. |
| `generated_documents` row exists | **PASS** | DB `tenant_5039f2d497cf.generated_documents`: 1 row — `INVOICE / 27858fee-… / invoice-cover-letter-inv-0002-2026-06-13.pdf / s3_key=org/tenant_5039f2d497cf/generated/invoice-cover-letter-inv-0002-2026-06-13.pdf`. (Was **0 rows tenant-wide** pre-fix.) |
| Firm-side fee-note detail shows the PDF | **PASS** | INV-0002 detail "Generated Documents" section: **Invoice Cover Letter / Bob Ndlovu / 13 Jun 2026 / 1.1 KB / PDF** (pre-fix this read "No documents generated yet"). |
| Portal fee-note detail renders + Download PDF present | **PASS** | Sipho portal (fresh magic-link `Qb1nFQns7vuuoX9dvpaSugae7gvxZEuhp7GM_GNoXTU`, zero KC) → `/invoices/27858fee-…`: INV-0002 **SENT**, line Court fees R 100,00 (Zero-rated 0%), Subtotal/Total R 100,00, Pay Now + **Download PDF**. 0 console errors on load. |
| **Download PDF → 200 (not 404)** | **PASS** | Click **Download PDF** → opened new tab to the **LocalStack S3 presigned URL** for `invoice-cover-letter-inv-0002-2026-06-13.pdf`. Network: `GET :8080/portal/invoices/27858fee-…/download` → **HTTP 200** (was **404** pre-fix). NO `alert("Download failed")`. |
| Presigned URL serves valid PDF bytes | **PASS** | `curl` the presigned URL → **HTTP 200, content-type application/pdf, 1173 bytes, magic bytes `%PDF-1.6`** — byte-size matches the render logged on send (one render, one artefact, emailed == downloadable). |
| Portal console clean | **PASS** | Fee-note detail: **0 errors / 0 warnings** (the OBS-3001 404-on-Download is gone). |

📸 `day-30-obs3001-reverify-portal-download.png` (portal INV-0002 SENT, Download PDF).

---

## Verdict

**OBS-3001 → VERIFIED on main (cycle 25).** Browser-driven end-to-end: a fee note sent AFTER the fix persists a `GeneratedDocument`; the portal **Download PDF** returns HTTP 200 with a presigned URL that yields a valid `application/pdf`, and the firm-side detail also serves it. The fix (PR #1439, commit `ddbe3de1f`) resolves the Day-30 30.8 + "Receipt download works" FAIL.

## Carry-over exemptions observed (noted, not re-filed)
- **OBS-201** — firm-side `/api/assistant/invocations` 404 on the INV-0002 draft page (KC proxy unwired) — exempt.
- **OBS-2101** — non-tariff cascade (no TIME lines on this fee note; pure disbursement). The R 100,00 line is zero-rated pass-through (Court Fees default), Total = Subtotal — consistent display.
- **Payments** = mock gateway only (Pay Now CTA present, not exercised this re-verify — Day-30 already covered payment lifecycle).
