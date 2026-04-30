# Day 60 — Firm matter closure happy-path (FIRM)

**Branch**: `bugfix_cycle_2026-04-30b`
**Cycle**: 20 (2026-04-30)
**Actor**: Thandi Mathebula (Owner) — Phase B closure execution
**Result**: **PASS** with one defect filed (OBS-2106 — closure-pack portal email not delivered)

## Closure execution

1. Navigate to `/org/mathebula-partners/projects/b7e319f7-...` → matter status `Active`, sidebar shows `TM` (Thandi).
2. Click **Close Matter** → dialog opens; Step 1 gate report renders **ALL 9 GATES GREEN** (re-confirmed from Day 60 prep):
   - ✓ Matter trust balance is R0.00.
   - ✓ All disbursements approved.
   - ✓ All approved disbursements are settled.
   - ✓ Final bill issued with no unbilled items.
   - ✓ No court dates scheduled for today or later.
   - ✓ No prescription timers still running.
   - ✓ All tasks resolved.
   - ✓ All client information requests closed.
   - ✓ No document acceptances pending.
   - Evidence: `qa_cycle/evidence/day-60/01-closure-gates-all-green.png`.
3. Click **Continue** → Step 2 form: Reason combobox preset to `Concluded` (other options Client terminated / Referred out / Other); both checkboxes pre-checked: `Generate closure letter`, `Generate Statement of Account`. Notes left empty.
   - Evidence: `qa_cycle/evidence/day-60/02-closure-form-reason-concluded.png`.
4. Click **Close matter** → dialog closes; ~3 seconds later matter detail repaints with status badge `Closed` (was `Active`). Document count `5` → `7`. Action toolbar reshapes: `Close Matter` replaced by `Reopen Matter`, `Complete Matter` removed.
   - Evidence: `qa_cycle/evidence/day-60/03-matter-closed-status.png`.
5. Backend log confirms (PID 99738, request `4db54991-...`):
   - `19:31:42.302` PdfRenderingService — `Generated PDF: template=matter-closure-letter, entity=b7e319f7-..., size=1644bytes`.
   - `19:31:42.447` GeneratedDocumentService — `Created generated document: id=ea891742-..., template=6997b878-... (Matter Closure Letter)`.
   - `19:31:42.605` StatementService — `Generated Statement of Account: project=b7e319f7-..., generatedDoc=294c480f-...`.

## Documents tab verification

- `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-30.pdf` — 1.6 KB, Uploaded 30 Apr 2026.
- `statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf` — 5.4 KB, Uploaded 30 Apr 2026.
- Plus 5 pre-existing documents (FICA + REQ-0003 uploads).
- Evidence: `qa_cycle/evidence/day-60/04-documents-soa-and-closure-letter.png`.

## Statements tab verification

- Row: `Apr 30, 2026 | R 0,00 (closing balance owing) | R 0,00 (trust balance held) | Download`. SoA UUID `294c480f-8893-4516-aedf-7db4c9b61a7a`.
- Evidence: `qa_cycle/evidence/day-60/05-statements-tab-soa-row.png`.
- Click Download → PDF streams to disk via S3 presigned URL (LocalStack `4566`, `Content-Length: 5489 bytes`, `MD5 52b1a3227eca8a6ee8228cfe8f1d9060`). Saved as `qa_cycle/evidence/day-60/statement-of-account-firmside.pdf`.

## Retention policy (ADR-249)

Not directly inspected — no UI surface for retention policy rows in the matter-detail view, and SQL inspection is forbidden. The MatterClosureService.ensureMatterRetentionPolicy path is exercised on every CLOSE; the retentionEndsAt = `today + 5 years` (2031-04-30) is already returned to the client by the close API per `CloseInternalResult`. Treating ADR-249 retention as VERIFIED-WIRED via Phase 67 Epic 489 (covered by backend integration tests); not separately re-asserted in this UI cycle.

## Mailpit closure notification — FAIL

**Expected (scenario 60 checkpoint 3)**: portal email to `sipho.portal@example.com` with subject like "Statement of Account ready" or "Your matter has been closed".

**Actual**: Mailpit has 13 messages total; latest pre-closure message is `19:21:36 Trust account activity` (from Day 60 prep R 71,000 transfer). Closure committed at `19:31:42`. **No closure-pack email arrived in Mailpit.**

Backend log filter `template=portal-document-ready` returns ZERO entries since boot — `PortalEmailService.sendDocumentReadyEmail` was never invoked.

Other portal emails work (magic-link, trust-activity, weekly-digest are all logged at INFO and delivered). The DEBUG-level skip messages in `PortalDocumentNotificationHandler` would not surface in INFO-only logs, so the precise skip cause is not visible from the live log.

Wired infrastructure (verified via static read):
- `MatterClosureService.generateClosureLetterSafely` calls `documentService.markSystemAutoShared(linkedDocumentId)` → flips visibility to PORTAL.
- `StatementService.generate` already uses `Document.Visibility.PORTAL`.
- `OrgSettings.portalNotificationDocTypes` default seed (Flyway V117) is `["matter-closure-letter", "statement-of-account"]`.
- `PortalDocumentNotificationHandler` is a `@TransactionalEventListener(AFTER_COMMIT)` on `DocumentGeneratedEvent`, both publishers (`GeneratedDocumentService`, `StatementService`) emit the event.

**Filed as OBS-2106** (Medium severity — closure-pack portal email not delivered post-close). Functional impact: client must learn about closure via portal Activity feed (`Statement of Account generated by Thandi Mathebula 7 minutes ago` on Sipho's `/activity` page) or by directly visiting the matter's Documents tab. Not a closure-execution blocker, not a data-correctness issue. Spec to be authored by Product/Dev; suspect candidates: (1) AFTER_COMMIT listener invoked but DEBUG-skip at allowlist check despite default seed; (2) DocumentGeneratedEvent.details visibility absent and the visibility-fallback path mis-resolves the persisted Document; (3) tenant ScopedValue rebind path swallows the event.

## Day 60 checkpoint summary

| # | Checkpoint | Result |
|---|------------|--------|
| 1 | Matter closes cleanly on the happy path (no override needed) | **PASS** — Active → Closed via Continue → Close matter |
| 2 | Statement of Account PDF generated and attached to matter Documents | **PASS** — 5.4 KB PDF, MD5 reproducible, downloadable from Statements tab + Documents tab |
| 3 | Mailpit notification email to `sipho.portal@example.com` ("Your Statement of Account is ready" or equivalent) | **FAIL → OBS-2106** — no portal-document-ready email sent; closure-pack notification never delivered |

## Console + Network

- 0 errors during closure execution and Documents/Statements tab navigation.
- 1 expected 404 from a speculative `GET /api/projects/{id}/statements/{id}/preview` probe (no such route by design — preview is via S3 presigned URL only).

## QA Position post-Day 60

- Matter `RAF-2026-001` lifecycle = **CLOSED** with closure letter (`ea891742-...`) + SoA (`294c480f-...`) attached, trust balance R 0,00, all gates green at close-time.
- Day 61 (portal SoA download) is runnable — Sipho's portal already shows the closed matter with both PDFs in the Documents tab (verified via direct nav even before scenario clock advances to Day 61).
- One new defect filed: OBS-2106 (closure-pack portal email not delivered).
- OBS-2105 (matter-detail header layout collapse) still cosmetic; visible behind closure dialog screenshots.
