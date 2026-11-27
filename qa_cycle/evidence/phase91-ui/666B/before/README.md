# 666B OBS-before: Fee-note detail page `/invoices/[id]` — task 666.6 "Observe-first"

- **When:** 2026-11-27, 01:54Z to 02:05Z. Discover-pre 01:54:24Z; 02-setup ~01:55Z (rate 01:55:18Z, task 01:55:21Z, 2 drafts 01:55:28 / 01:55:36Z, then stopped — see Data); 02b-setup 01:56:23Z (3 more drafts); 03-lifecycle 01:58:56–01:59:4xZ; Alice capture 02:00:44Z; Carol capture 02:02:13Z; spec run 02:02:42–02:02:49Z; discover-post 02:02:55Z; crops 02:03:32Z; assemble 02:04:43Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. Not restarted, rebuilt, reseeded or torn down.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code, no SQL, no API writes. Reads through the API (GET); every OBS write went through the UI (1 billing rate, 1 task, 5 time entries, 5 fee notes generated and driven to APPROVED / SENT / PARTIALLY_PAID / PAID / VOID, 3 sends, 2 payments). The spec run made its own API calls.
- **Raw files:** ~95 raw PNGs in `/tmp/666B-before-raw/`, outside the repo. Committed: 12 contact sheets + 6 crops.

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created **`2026-11-27T01:40:27.68Z`** from image `docteams-e2e-frontend` built **`2026-11-27T01:29:09Z`**; compose `working_dir` label `/Users/…/worktree-epic-666A/compose` (that worktree is gone). 666A's final product head was `97d84aeed` (committed 01:25:00Z, before the image build).
- **It equals this worktree:** `git diff --stat 97d84aeed b7c8091c2 -- frontend backend compose` is **empty**, and `55de88b29` (the 666A squash) ↔ `97d84aeed` has no frontend/backend diff. Worktree HEAD = `b7c8091c2` = `main`. Caveat as before: uncommitted changes in the 666A worktree at build time can't be ruled out after the fact.
- **Backend:** `e2e-backend` created 01:40:27.65Z from an image built `2026-11-25T16:31:27Z`; the last backend commit (`d8aaba16a`) is 2026-11-25T17:41Z+02 = 15:41Z, before it.
- **Fresh seed:** 8 fee notes before the OBS (`discover-pre.json`): seeded DRAFT (Acme, no number) and SENT INV-0001 (Acme, due 27 Dec 2026), plus **6 take-on fee notes** (SENT, `takeOnBatchId 4a65c415-…`, created 01:42:59Z by Bob Admin): PS-1001…PS-1004, PS-1006 and one numbered **INV-0007**; 5 of them past due. No billing rates, no tasks, no unbilled time; Mailpit 2.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | `GET /api/invoices/{id}` |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (incl. INVOICING, TEAM_OVERSIGHT) | 200 |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | 5 (AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST) — no INVOICING | 403 |

## Data created / changed (ids for OBS-after)

| What | How | Final state |
|---|---|---|
| Billing rate Alice MEMBER_DEFAULT **R 2 500/h ZAR** from 2026-11-27 → `53ef49db-b928-4707-9e85-23d97f52acef` | Settings › Fees › Tariffs › row `billing-rate-owner` › "Add rate for Alice Owner" › Create Rate (`02-setup`) | left |
| Task "666B OBS fee-note detail" on matter "Website Redesign" (Acme Corp, `b72d5a1e-…`) → `bcbd5571-c8e9-49df-b096-2f16a695f36e` | Matter › Work › Tasks › New Task | left (OPEN) |
| 5 billable time entries on that task, today: 30m, 45m, 1h, 15m, 20m ("666B OBS entry — approved / sent overdue / partially paid / paid / void") | task row › Log Time | each billed by its fee note |
| **APPROVED INV-0008** `c168ca45-2ebb-4f71-bf07-1e990686b9ea` (R 1 437,50) | Acme Corp ?tab=invoices › New fee note › Fetch Unbilled Time › Validate & Create Draft › "Create Draft (1 issues)" (tax number missing), then detail › Approve | APPROVED, not sent |
| **SENT overdue INV-0009** `f455bf31-2842-4f75-ad76-c3d4f809cdac` (due **2026-11-01**; total **R 1 875,00** — VAT lost, F-666B-B1) | generated; draft Due Date 2026-11-01 › Save Changes; Approve; Send fee note | SENT, 1 email |
| **PARTIALLY_PAID INV-0010** `051bcd1f-9a68-41ac-a323-ff00da29f43d` (R 2 875,00; paid R 1 000,00 EFT ref "666B-OBS-PART"; balance R 1 875,00) | generated; Approve; Send; Record Payment | PARTIALLY_PAID, 1 email |
| **PAID INV-0011** `73b4326f-36fa-455e-bcca-f4c344284de5` (R 718,75; EFT ref "666B-OBS-PAID", default amount) | generated; Approve; Send; Record Payment | PAID, 1 email |
| **VOID INV-0012** `22974ebd-a3c1-4663-8ff1-ff530ab1a8c0` (R 958,24) | generated; Approve; Void (native confirm accepted) | VOID |
| Seeded DRAFT `de6ac48f-bcfe-4c5e-b122-a8d012f152ec` | "Add Line" and "Edit Consultation — …" opened and **cancelled** (`03`) | unchanged (`updatedAt` 01:40:57Z) |
| **Spec run** (02:02:42–49Z): 3 clients "InvLife/InvCrud/Arith Client 6pf*", 13 fee notes (9 DRAFT, 1 APPROVED, 1 PAID, 2 VOID), 3 emails | `e2e/tests/invoices/` | left |

- **Reused targets (not created):** DRAFT = seeded `de6ac48f-…`; SENT not overdue = seeded **INV-0001** `12ebbd0c-c056-4ef4-9462-82c5c07dc38d`; SENT overdue take-on = **PS-1001** `180ef093-8fc7-463e-a2cd-e224626c6d02` (Thandiwe Mokoena, due 14 Aug 2026).
- **Why the setup:** the only UI path to a fee note is the client's "New fee note" generation dialog, which needs unbilled items with a rate; the stack had none. One entry is logged before each generation so each draft bills exactly one entry.
- **02 stopped after 2 drafts:** it typed "60" into Log Time's minutes field (max 59), so the dialog stayed open. Scripts are not edited after running: `02b-setup.cjs` (the copy with hours/minutes split) resumed idempotently. 02's 5 PNGs are on `sheet-setup.png`; its run has no json/console record.
- **Overdue reachable through the UI:** yes — the draft form's Due Date took **2026-11-01**, Approve kept it (Issued 27 Nov 2026), Send kept it.
- Mailpit 2 → 8: 3 OBS sends (INV-0009/0010/0011) + 3 spec sends. Approve and Record Payment sent no email. No destructive confirm beyond the one Void.

## Per status (1440 light unless noted; `measurements.json` → `variants`)

Every target: `main#main-content` = 1, `<main>` = 1, h1 in `#main-content` = 1, h1 page-wide = 1, document h-overflow **0** at 1024 and 1440, light and dark; 0 console errors / warnings / hydration for Alice. h1 = `font-display text-2xl` 24 px Sora with the **HelpTip inside the h1** (button "Help: Fee note numbering"); accessible name = the text only.

| Target | h1 | Badges | Meta row | Header actions (variant) | Totals rows |
|---|---|---|---|---|---|
| DRAFT seeded | **"Draft fee note"** | Draft (neutral) | Client: Acme Corp · Currency: ZAR | Preview (soft) · Approve (accent) · Delete Draft (destructive) | Subtotal R 3 000,00 · VAT — Standard (15%) R 450,00 · Total R 3 450,00 |
| APPROVED INV-0008 | "INV-0008" | Approved (lead) | Client · Issued: 27 Nov 2026 · Due: 27 Dec 2026 · Currency: ZAR | Preview · Send fee note (accent) · Void (destructive) | R 1 250,00 · R 187,50 · R 1 437,50 |
| SENT overdue INV-0009 | "INV-0009" | Sent (lead) | … Due: **1 Nov 2026** … | Preview · Record Payment (accent) · Void | R 1 875,00 · **VAT R 281,25 · Total R 1 875,00** (F-666B-B1) |
| SENT INV-0001 | "INV-0001" | Sent | … Due: 27 Dec 2026 … | same | R 7 500,00 · R 1 125,00 · R 8 625,00 |
| SENT take-on PS-1001 | "PS-1001" | Sent + **"Brought forward — take-on"** (neutral, `fee-note-takeon-badge`) | Client: Thandiwe Mokoena · Issued: 15 Jul 2026 · Due: 14 Aug 2026 | same | Subtotal R 4 600,00 · **Tax R 0,00** · Total R 4 600,00 |
| PARTIALLY_PAID INV-0010 | "INV-0010" | Partially Paid (warning) | … | Preview · Record Payment (no Void) | … Total R 2 875,00 · Amount paid R 1 000,00 · Balance due R 1 875,00 |
| PAID INV-0011 | "INV-0011" | Paid (success) | … | **Preview only** | … Total R 718,75 · Amount paid R 718,75 · Balance due R 0,00 |
| VOID INV-0012 | "INV-0012" | Void (destructive) | … | **Preview only** | R 833,25 · R 124,99 · R 958,24 |

- **Above the header:** "Back to fee notes" link (7.57:1) + "Generate Document" dropdown on every status. Breadcrumb last crumb = number ("Fee note" for the draft). Document title generic (F-666B-B10).
- **Section order** (`InvoiceDetailClient`): header → [record-payment-form] → [Payment Received / Part payment received / voided indicator] → [Payment History, SENT/PARTIALLY_PAID/PAID only] → Fee note details (draft: form `invoice-due-date`, `-payment-terms`, `-po-number`, `-tax-type`, `-billing-period-start/-end`, `-notes` + "Save Changes"; others: read-only `dl`, e.g. "Due Date: 1 Nov 2026") → Line Items → [draft: Add Tariff Items, Add Disbursements] → totals (`max-w-xs`, 320 px, right-aligned). Then page-level: custom-fields one-liner → Generated Documents → Collection activity → Audit trail.
- **Indicators:** PARTIALLY_PAID "Part payment received — Paid R 1 000,00 · Balance due R 1 875,00" (`partially-paid-indicator`, with `invoice-balance-due` inside); PAID "Payment Received — Paid on: 27 Nov 2026 · Reference: 666B-OBS-PAID"; VOID "This fee note has been voided." (no date/actor); none for SENT (no overdue cue, F-666B-B2).
- **Payment History** (`h3`, bordered card): SENT → "No payment events yet."; after payment a table STATUS | PROVIDER (xl) | METHOD (xl) | REFERENCE | AMOUNT | DATE | RECEIPT, row "Completed · Manual · EFT · 666B-OBS-PART · R 1 000,00 · 27 Nov 2026 · Receipt INV-0010/R1"; below xl Provider/Method collapse into "Manual · EFT" under the reference (`payment-provider-method-compact`). 678/678 px at 1024, 1094 at 1440 — no overflow.
- **Line items:** draft rows carry "Edit <description>" / "Delete <description>" icon buttons + header "Add Line"; "Add Line" opens "Add Line Item" with placeholder **"Line item description"** (0 → 1); "Edit …" opens "Edit Line Item" (placeholder 0). Non-draft: no ACTIONS column, no buttons. Take-on PS-1001 has no TAX column (no per-line tax). Columns at 1024: Description 146 px (draft) / 204 px (non-draft) — K-74.
- **Custom fields (663A one-liner):** "No custom fields configured · Custom fields let you track additional information specific to your workflow. · Configure Fields" — 56 px; **no** `custom-field-section` / `field-group-selector` testid (0 invoice field definitions/groups on the stack: the EmptyState branch renders).
- **Generated Documents:** "No documents generated yet" (draft, approved, void, take-on); after send a table with "Fee Note … PDF"; after payment also "Payment Receipt".
- **Collection activity:** dashed box "No collection activity for this fee note yet." (`collections-history-empty`) on every status.
- **Audit trail:** renders for Alice (`invoice-audit-section`, TEAM_OVERSIGHT), 2–6 entries ("Fee note created / updated / approved / sent / partially paid / paid / receipt generated / voided", take-on "Fee note brought forward at take-on" by Bob Admin). No empty state seen (every fee note has ≥ 2 events).
- **Take-on badge (P-1):** **present on this stack** — 6 fee notes carry `takeOnBatchId`; captured on PS-1001 (`crop-takeon-badge-1440.png`); 6.76:1 light / 5.84:1 dark.
- **Carol:** HTTP **404**, in-shell h1 **"Page not found"** (`notFound()` — no INVOICING, not admin/owner); 4 console errors = the 404 document itself.

## Record payment — what updates without a reload (`measurements.json` → `recordPayment`, `lifecycle`)

INV-0010, SENT → "Record Payment" → `record-payment-form` (h3 "Record Payment", `payment-form-balance-due` "Balance due R 2 875,00", Amount default **2875** (the balance), `payment-method-select` "EFT" (EFT/Card/Cash/Other), "Payment Reference (optional)", Confirm Payment / Cancel) → Amount 1000, ref "666B-OBS-PART" → Confirm. After the server action (no reload, +1.5 s):

| Element | Before (SENT) | After save, no reload | After reload |
|---|---|---|---|
| Status badge | Sent / lead | **Partially Paid / warning** | same |
| Header actions | Preview, Record Payment, Void | **Preview, Record Payment** | same |
| `partially-paid-indicator` / `invoice-balance-due` | 0 / 0 | **1 "Part payment received Paid R 1 000,00 · Balance due R 1 875,00" / 1 "R 1 875,00"** | same |
| Totals | Subtotal, VAT, Total R 2 875,00 | **+ Amount paid R 1 000,00, Balance due R 1 875,00** (`totals-balance-due` 1) | same |
| Payment History | "No payment events yet." | **1 row** (Completed · Manual · EFT · 666B-OBS-PART · R 1 000,00 · Receipt INV-0010/R1) | same |
| Generated Documents / Audit trail | Fee Note / 3 events | **+ Payment Receipt / + "partially paid", "receipt generated"** | same |
| `record-payment-form` | — | closed (0) | 0 |

INV-0011 (default amount = full balance R 718,75): badge **Paid / success**, actions **Preview only**, "Payment Received" indicator, totals Amount paid R 718,75 / Balance due R 0,00, `invoice-balance-due` **0** (only the part-paid indicator carries it), Payment History 1 row — all without reload. `sameAfterReload: true` for both. Requests: server-action POSTs only (the RSC payload rides the action response); no success toast — the form simply closes.

## Measurements (`measurements.json` → `containerWidths`)

- **Content frame** (`div.space-y-8` = header / totals / sections width): **712 px at 1024**, **1128 px at 1440** (light; `#main-content` inner 760 / 1176) — the same as 666A's tile container. Dark: 720 / 1136 (frame) and 768 / 1184 (main) — the shell, see found.md. So a 2×2 tile strip at 1024 gets 712 px (≈ 348 px tiles at gap 16) and 4×1 at 1440 gets 1128 px (≈ 270 px tiles).
- **Header block** (`flex items-start justify-between gap-4`): 1440 → 1128 × **60** for every status (left 255–578 px, actions 100–301 px). 1024 → 712 × **84** when 2–3 buttons (approved, sent ×3, partial: left 395–456, actions 240–301; meta wraps, F-666B-B4), 712 × 60 for draft (left 255, actions 299), paid and void (actions 100). Buttons 32 px high (`size sm`): Preview 100, Send fee note 116, Void 53 px.
- **Totals block:** 320 px (`w-full max-w-xs`), right-aligned at x = 672 (1024) / 1088 (1440).
- **Contrast:** light all ≥ 4.5 (accent buttons 5.46, Void/Delete 4.77, badges 4.5–6.76). Dark: accent buttons **3.16:1** (F-666B-B7); relative times / "No documents generated yet" 4.24.

## SEL-COMPAT before (`sel-compat.json`, 1024 light)

| Selector (spec) | draft | approved | sent (×3) | partial | paid | void | Carol |
|---|---|---|---|---|---|---|---|
| h1 "Draft fee note" (`day-30-billing:64`, `trust-balanced-month:527`, helpers `legal-lifecycle:445`; substring) | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| text "Draft" / "Approved" / "Sent" / "Paid" / "Void" exact (`invoice-lifecycle`, `.first()`) | 1/0/0/0/0 | 0/1/0/0/1 (button) | 0/0/1/0/1 (button) | 0/0/0/0/0 ("Partially Paid" 1) | 0/0/0/1/0 | 0/0/0/0/1 | 0 |
| buttons Approve / Delete Draft / Send fee note / Record Payment / Void | 1/1/0/0/0 | 0/0/1/0/1 | 0/0/0/1/1 | 0/0/0/1/0 | 0/0/0/0/0 | 0/0/0/0/0 | 0 |
| `main` rows (`invoice-crud:55`; every table in main: lines + payment history + generated documents) / row Edit / Delete buttons / Add Line | 2 / 1 / 1 / 1 | 2 / 0 / 0 / 0 | 4 (INV-0001, INV-0009) · 2 (PS-1001) / 0 / 0 / 0 | 7 / 0 / 0 / 0 | 7 / 0 / 0 / 0 | 2 / 0 / 0 / 0 | 0 |
| placeholder "Line item description" (closed → after Add Line) | 0 → 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| testid `invoice-balance-due` (`day-30:248`, `trust-balanced:924`) / `totals-balance-due` | 0/0 | 0/0 | 0/0 | **1 "R 1 875,00" / 1** | 0 / **1 "R 0,00"** | 0/0 | 0 |
| `record-payment-form` / `payment-method-select` / `payment-form-balance-due` (closed → opened) | 0 | 0 | 0 → 1/1/1 (INV-0010 / INV-0011 while SENT, `03`) | 0 → 1/1/1 (`08` crops) | 0 | 0 | 0 |
| `send-override-dialog` (`day-30:70`) | never opened (OBS limit) | | | | | | |
| `fee-note-takeon-badge` (`take-on:986`) | 0 | 0 | 0 / 0 / **1 "Brought forward — take-on"** (PS-1001) | 0 | 0 | 0 | 0 |
| `invoice-audit-section` + h2 "Audit trail" / "Generated Documents" / "Collection activity" | 1 / 1 / 1 | 1/1/1 | 1/1/1 | 1/1/1 | 1/1/1 | 1/1/1 | 0 |
| text `/overdue/i` in main | 0 | 0 | INV-0001 0 · PS-1001 0 · INV-0009 **1 = its own line description "…666B OBS entry — sent overdue"** (OBS data, not a cue) | 0 | 0 | 0 | 0 |

## Spec baseline (`specs-baseline.json`)

- **Command:** from the **main checkout** `frontend/` @ `b7c8091c2` (`frontend/e2e` tree identical to the worktree's), nvm Node 20: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 SHELL=/bin/bash NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/invoices/ --reporter=line`. Log `/tmp/666B-before-raw/spec-run.log`.
- **Result: 21 passed, 0 failed, 0 skipped** (7.1 s): BR-01 ×2, INV-01 ×5, INV-02 ×8, INV-03 ×6.
- **Tree:** `git status --short` of the main checkout identical before and after. Only the git-ignored `frontend/test-results/.last-run.json` was rewritten by Playwright (ignored, not shown by status); nothing to restore.

## Console (`console.json`)

36 capture page loads + setup, lifecycle and crop runs: **4 errors, 0 warnings, 0 page errors, 0 hydration** — all 4 are "Failed to load resource: … 404" for Carol's notFound document × 4 variants. Nothing else. (02-setup crashed before writing its record.)

## OBS limits

- **`send-override-dialog`** not observed: no send failed a blocking check on this data (Acme's only issue, "tax number missing", is a warning). Making it appear needs a client failing a send validation.
- **Audit-trail empty state** not reachable (every fee note has ≥ 2 audit events); **custom fields with values** not reachable (0 invoice field definitions / groups — only the one-liner empty branch).
- **Online payment link** section (`paymentUrl`) not reachable (no payment provider configured); Xero status chip not rendered (Xero not connected).
- "Add Tariff Items" / "Add Disbursements" dialogs (K-86) not opened.
- Carol only on one URL (APPROVED INV-0008), per task.

## Files

- **Contact sheets:** `sheet-{draft,approved,sent-overdue,sent,sent-takeon,partial,paid,void}-alice.png` (cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page), `sheet-carol.png`, `sheet-lifecycle-alice.png` (each transition, no reload), `sheet-record-payment-alice.png` (before | form opened | filled | after Confirm, no reload | after reload — INV-0010 and INV-0011), `sheet-setup.png` (rate, task, log time, generation steps; draft editors opened and cancelled).
- **Crops:** `crop-defect-vat-dropped-totals-1440.png`, `crop-defect-overdue-header-1440.png`, `crop-header-wrap-1024.png`, `crop-header-1440.png`, `crop-takeon-badge-1440.png`, `crop-record-payment-form-1440.png` (boxes in `measurements.json` → `crops`).
- **JSON** (single line): `discover-{pre,post}.json`, `measurements.json` (`targets`, `variants`, `surfaces`, `containerWidths`, `setup`, `lifecycle`, `recordPayment`, `draftEditors`, `crops`), `sel-compat.json`, `console.json`, `specs-baseline.json`.
- **Scripts** (as executed, in this order): `00-discover pre` → `02-setup` (stopped) → `02b-setup` → `03-lifecycle` → `01-capture alice` → `01-capture carol` → spec run → `00-discover post` → `08-defect-crops` → `05-assemble`. Shared: `lib.cjs`, `probes.cjs`. All take `PHASE=before|after|after-fix`; `01`, `03`, `08` read the fee-note ids from `$RAW/setup-02b.json` (OBS-after: ids above).
- **Defects:** `../found.md` (F-666B-B1…B10).

## Corrections (CodeRabbit round 1, PR #1877)

The scripts stay as executed (D-22); nothing above is rewritten. Two CodeRabbit comments on them were checked against the recorded output:

- **CR-2 — `scripts/01-capture.cjs:91` picks the draft as "the first DRAFT the OBS did not create", not by the seed's `notes`.** No effect on this run. `01-capture alice` ran at 02:00:44Z, before the spec run (02:02:42Z) added 9 drafts. At that time the only DRAFT not created by the OBS was the seed (`discover-pre.json`: 1 DRAFT, `de6ac48f` Acme Corp, notes "E2E seed fee note — draft"; the 5 OBS drafts had moved on to APPROVED/SENT/PARTIALLY_PAID/PAID/VOID in `03-lifecycle`). `measurements.json` → `targets.draft.id` = `de6ac48f-bcfe-4c5e-b122-a8d012f152ec`, and all four `variants.draft.*` show "Client: Acme Corp" with the line "Consultation — website redesign mandate". After the spec run the predicate would hit another draft (`discover-post.json`: 10 DRAFTs, the first is `b23fdfc2` InvCrud Client 6pf9). This is why OBS-after pinned the draft id (`01b-capture.cjs`).
- **CR-5 — `scripts/probes.cjs:179` `auditTimelineItems` also counts the `data-testid="audit-timeline"` container.** Every recorded value is the row count **+ 1**: draft 3 → 2 rows, approved 3 → 2, sent-overdue 5 → 4, sent 5 → 4, sent-takeon 3 → 2, partial 6 → 5, paid 6 → 5, void 4 → 3 (checked against each `variants.*.audit.text`). So "2–6 entries" in the Audit trail bullet under "Per status" should read **2–5 entries**. No other figure in this README comes from this field (the record-payment table counts events from the visible rows).
