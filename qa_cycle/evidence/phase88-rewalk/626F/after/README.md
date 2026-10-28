# Slice 626F — "after" observation (e2e mock-auth, 2026-10-28 ~01:40–01:48 UTC)

Frontend image built from worktree-epic-626F @ cc59f005a (first build failed transiently on the next/font Google
fetch — `obs-build.log`; retry `obs-build-2.log` BUILD=0), recreated with `up -d --no-deps frontend`; backend/LocalStack
untouched. Image contains "Preview unavailable" / "Show unsaved" (grep of .next). Alice (owner), 1280x800.
M2 `2cb472bd-ab3e-41f3-8ad8-91ae08a7ff2a`, DRAFT fee note `852969e7-…`.

| # | What I did | Observed | Verdict |
|---|---|---|---|
| O1 SoA dialog (G-C4) | M2 → Generate Statement of Account (default start 2026-10-29 > end 2026-10-28 → client error; set start 2026-10-01) → Preview & Save → sampled 11× / 500 ms (`o1-soa-sampling.json`) | **Stable** (no growth): every sample grid col 842px, form 842 (min-width auto), wrapper 842 (scrollW 842 = clientW 842), a4 outer `794px` (scale 1.0), paper right 1227 = wrapper content right 1227, preview container 842/842, docScrollW 1280. **But** dialog is 512 px; the dialog inner div scrollWidth 890 vs clientWidth 510 → 380 px horizontal overflow. Paper renders unscaled at 794 px and is clipped on the right; Period end input and description text clipped (01). Download PDF at x 961–1113, y 787–823 (dialog right edge 896, viewport 800) → `elementFromPoint` null. After wheel-scroll (scrollLeft 380, scrollTop 88) button at (581,699)–(733,735), hit = BUTTON (02); click → server-action POST #116 200, toast "Failed to download document." (known presigner FIXME). **Diagnostic (DOM-only):** `form.style.minWidth='0'` → grid 462, wrapper 462, a4 414px (scale 0.521), paper right 847 = content right 847, inner 510/510 (03); after removing the override it stayed at 462 — a neutral equilibrium: any width W with paper = W−48 is self-consistent, and `useState(1)` seeds W=842 on first open. | **FAIL** — runaway fixed, but the SoA preview is a stable 842-px column inside a 512-px dialog (paper unscaled, 380 px horizontal scroll, actions off-dialog) |
| O2 Generate Document dialog | M2 → Generate Document → Project Summary Report (preview), sampled 11× (`o2-generate-sampling.json`); closed without download/save | All samples identical: grid 462, scroll item 462/462 (before: 486/462), wrapper 462/462, a4 414px, paper right 847 = content right 847, docScrollW 1280. Download PDF (529,647)–(681,683), hit self (04) | PASS |
| O3 Tariff | DRAFT fee note → Add Tariff Items; selected 2(a) + 4(a); Close (nothing added; fee note still 2 lines by SQL). /legal/tariffs expanded | Dialog: R 780,00 / R 1 250,00 / R 195,00 / R 7 800,00 / R 4 680,00 / R 260,00 / R 390,00; selected badges R 1 250,00, R 7 800,00; summary "2 items · R 9 050,00" (code points `52 a0 39 a0 30 35 30 2c 30 30` — NBSP after R and as group separator) (10). Tariffs page: 19 amounts, all `R 780,00`/`R 1 250,00` style, none dot-decimal (12). CTA still "Add to Invoice" (pre-existing) | PASS |
| O4 Fee-note preview | Preview on DRAFT → new tab; then `/api/invoices/a3f1c2d4-5b6e-4f70-8a91-b2c3d4e5f607/preview` in that tab; malformed id; curl without session | Preview: legal-za fee note, logo `data:image/png` 120 px, Tel/Email/Web/VAT 4123456789 letterhead, "DRAFT — not yet issued"; money `R2 500,00` (`52 32 a0 35…`), R562,50, R4 312,50, R300,00, R45,00, R345,00, R4 050,00, R607,50, Total R4 657,50 — identical to before (13). Random UUID: **404, `text/html; charset=utf-8`**, title "Preview unavailable", body "No invoice found with id a3f1c2d4-…", `<main role=alert>`, 0 scripts, no raw JSON (14). Malformed id → 400 `text/plain` "Bad Request" (pre-existing UUID guard, not routed through the HTML page). No-session curl → 307 to /mock-login (middleware) | PASS |
| O5 Matter Generated documents (G-B6) | M2 → Work › Generated documents; SQL (`o5-sql-M2.txt`); keyboard + mouse toggle; reload | SQL: 3 rows, 1 unsaved PDF (b91daaa4), 0 DOCX. Default: 2 rows (both SoA, saved), button "Show unsaved (1)" aria-pressed=false (05). Tab from Activity → tabpanel → button (focus-visible true); Space → "Hide unsaved", aria-pressed=true, focus kept, 3 rows, "Project Summary Report [Not saved]" badge, saved rows unbadged (06). Enter → "Show unsaved (1)", 2 rows. Mouse click show 3 / hide 2 (07). Reload → hidden again, "Show unsaved (1)". Unsaved row actions: Download, Send for Acceptance, Delete (no Share) — "Send for Acceptance" on an unsaved row is pre-existing. DOCX: no DOCX rows in the tenant → not observable | PASS (DOCX sub-check not observable) |
| O6 Client Generated documents | M2 client 513b4d65 → Matters › Generated documents | "No documents generated yet", 0 toggles; SQL: no CUSTOMER rows for this client (`o6-sql-customer.txt`) (08) | PASS — no unsaved rows → no toggle |
| O7 Invoice Generated Documents (R-5) | INV-0001 `ed1c7f17` (SENT; 6/6 rows unsaved by SQL) | 6 "Fee Note … PDF" rows visible, 0 "Not saved" badges, 0 toggles (09) | PASS |
| O8 AI cost | Settings › Connections › AI › Execution History; also /invoices/unbilled | "No executions yet" (15); unbilled: "No unbilled work" (16) | Not observable on e2e |
| O9 Console | `mcp-raw/console-*` for this run | Only `Missing Description or aria-describedby` for DialogContent (Generate + Tariff dialogs — same warning in the before run) and the deliberate 404/400 probe loads. No new errors | PASS |

## Side observations (not 626F scope)
- SoA dialog default Period start (2026-10-29) is after Period end (2026-10-28) once a statement exists for today → Preview & Save rejects with "Period end must be on or after period start".
- /legal/tariffs: schedule row shows an empty neutral badge (no text) next to "LSSA 2024/2025 High Court Party-and-Party" (11). Not in the diff.
- Tariff dialog CTA still "Add to Invoice".

## State changed
| Change | Restored? |
|---|---|
| e2e frontend container recreated from cc59f005a image | No (intended) |
| New SoA on M2: generated_documents `0240c404-6343-4911-9f40-0b30a4d3d7be` + documents `e11fd0e5-1c02-4196-9eca-d84f70a71e10` (INTERNAL, not shared, no email) | No |
| Tariff dialog selections | Closed, nothing saved (2 lines) |
| DOM-only `min-width:0` on SoA form | Removed; page navigated away |
