# Day 14 — Firm onboards Moroka Family Trust (isolation setup) `[FIRM]`

**Cycle**: bugfix_cycle_2026-06-13 (Legal ZA Full Lifecycle — Keycloak)
**Date**: 2026-06-13 SAST
**Actor**: Thandi Mathebula (Owner), firm app :3000 (Keycloak realm `docteams`)
**Driver**: QA agent via **Playwright MCP** (`mcp__playwright__browser_*`) — clean Chromium, firm browser UI only on :3000. Mailpit API used to capture the portal magic-link (sanctioned). DB read used ONLY to capture the trust-transaction UUID + document UUID/s3_key for Day 15 (diagnostic ID capture, not a QA action that bypasses the UI — every entity was created through the firm UI).
**Status**: **PASS — 11/11 step checkpoints + 3/3 Day-14 summary checkpoints. Zero new gaps. NOT blocked.**

## Retry context (supersedes the prior BLOCKED report)

The earlier Day 14 attempt was **BLOCKED at Keycloak login** by a `Cannot access a chrome-extension:// URL of different extension` error. Root cause: that attempt drove the **claude-in-chrome** MCP tools (`computer`/`find`/`javascript_tool`/`read_page`), which run inside a browser hosting a foreign extension that hijacks input events on the KC sign-in page. This was a **QA-harness/tooling issue, not a Kazi product or true-environment defect** — confirmed because Days 5/7/10 of this same cycle authenticated and drove :3000 successfully via **Playwright MCP**, which uses a clean Chromium without that extension.

This retry used **Playwright MCP exclusively**. On `browser_navigate` to `:3000/dashboard`, the Playwright Chromium already carried a valid Keycloak session (Thandi Mathebula, thandi@mathebula-test.local) — the dashboard rendered directly, no login interaction (or extension hijack) required. All 11 checkpoints then executed end-to-end with no tooling failures.

## Checkpoint results

### Phase A: Create Moroka Family Trust client

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 14.1 | Clients → + New Client | PASS | `/customers` (1 client: Sipho) → "New Client" opened the 2-step Create Client dialog. |
| 14.2 | Fill TRUST client (Moroka Family Trust, IT 001234/2024, moroka.portal@example.com, ZA) | PASS | Step 1: Name "Moroka Family Trust", Type **Trust**, Email moroka.portal@example.com, Country South Africa (ZA), Registration Number **IT 001234/2024**, Entity Type **Trust**. Step 2 (Additional Information) showed the SA Legal — Client Details intake (all optional for a trust; ID/Passport applies to natural persons). **Note on beneficial owners**: the generic Create Client dialog has **no beneficial-owner field** — beneficial ownership is captured in the FICA trust onboarding flow (Compliance), not at client creation. Client was created with the trust identity fields; no back-door used. |
| 14.3 | Submit → client created | PASS | Redirected to client detail `/customers/9894de9b-31d1-4017-af9c-98ea889092f5` — "Moroka Family Trust", Active/Prospect, moroka.portal@example.com, 0 engagements. |
| 14.4 | Run Conflict Check → CLEAR | PASS | More actions → Run Conflict Check → `/conflict-check?customerId=9894de9b-…&checkedName=Moroka Family Trust` (Check Type New Client, Client=Moroka pre-filled) → Run → result **"No Conflict"**, "Checked 'Moroka Family Trust' at 13/06/2026, 13:51:13", History (2). |

### Phase B: Create Moroka matter

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 14.5 | + New Matter → Deceased Estate template | PASS | New Matter form, Customer=Moroka pre-selected; Template list included **"Deceased Estate Administration"** → selected (form showed "9 tasks, 0 tags"). |
| 14.6 | Fill matter (EST-2026-002, Estate Late Peter Moroka, Estates—Deceased, Master's Office JHB) | PASS | Matter Name **Estate Late Peter Moroka**, Reference **EST-2026-002**, Work Type **Estates — Deceased**, Description references the Master's Office Johannesburg + Liquidation and Distribution Account. (The generic New-Matter form has no dedicated "Master's Office" field — that is a template-task field; captured in the description per prior-cycle convention.) |
| 14.7 | Submit → matter created | PASS | Redirected to matter detail `/projects/dc10e9ac-becd-4cd6-babe-c723b501bfb0` — header "Estate Late Peter Moroka", **Active**, **Estates — Deceased**, `EST-2026-002`, client link Moroka Family Trust, "Matter created successfully" toast. |

### Phase C: Seed data on Moroka matter

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 14.8 | Send info request: Liquidation & Distribution Account docs → moroka.portal@example.com, due Day 30 | PASS | Matter → Client group → Requests → New Request → Create Information Request dialog. Template **"Liquidation and Distribution Account Pack (5 items)"** (exact match), Portal Contact = Moroka Family Trust (moroka.portal@example.com), Due Date **2026-07-13** (Day 30), → **Send Now**. List shows **REQ-0003**, status **Sent**, 0/5 accepted, contact Moroka, sent 13 Jun 2026. **Mailpit confirms delivery**: message `fgdEHnUGpsWdePh5P8nVAD` to moroka.portal@example.com, subject "Information request REQ-0003 from Mathebula & Partners". **Magic-link captured**: `http://localhost:3002/auth/exchange?token=GIt69AIK3rFw1YSPaZb2zg8XdDIoqb-wsmUkcxhMA_c&orgId=mathebula-partners`. |
| 14.9 | Upload internal doc to Moroka matter (Work → Documents) | PASS | Matter → Work group → Documents (empty) → drag-and-drop upload area → uploaded `death-certificate-moroka.pdf` (616 B test PDF). Documents table shows **death-certificate-moroka.pdf, 616 B, Uploaded, 13 Jun 2026**. DB confirms visibility=**INTERNAL** (correct — must be invisible to Sipho on portal Day 15). |
| 14.10 | Record R 25 000 trust deposit vs Moroka / EST-2026-002 | PASS | Trust Accounting → Transactions → Record Transaction → Record Deposit. Client combobox (cmdk searchable — **OBS-1001 still fixed, real click**) listed Sipho + Moroka → selected **Moroka Family Trust**. Matter combobox then listed **only** "Estate Late Peter Moroka" (picker correctly scoped to Moroka's matters). Amount **25000**, Reference **DEP/2026/002**, Description "Initial trust deposit — Estate Late Peter Moroka (EST-2026-002)", Date 2026-06-13. → Record Deposit. List now shows **2 transactions**: DEP/2026/001 R 50 000,00 (Sipho) + **DEP/2026/002 R 25 000,00 RECORDED** (Moroka). Backend log 11:55:52Z: portal-trust-activity notification sent to moroka.portal@example.com (contact `651e35a8-…`); 0 ERROR/WARN. Screenshots `day-14-moroka-deposit-form-filled.png`, `day-14-moroka-trust-deposit-recorded.png`. |
| 14.11 | Capture Moroka entity IDs into isolation-probe-ids.txt | PASS | All 5 IDs + portal contact captured (see below); written to `isolation-probe-ids.txt`. |

## Day 14 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Two clients + two matters on tenant (Sipho + Moroka) | PASS | Clients list: **Sipho Dlamini** (individual) + **Moroka Family Trust** (trust). Matters: Sipho's RAF-2026-001 (+ acceptance-auto matter) and Moroka's EST-2026-002. |
| Moroka has ≥1 info request, ≥1 document, ≥1 trust deposit | PASS | REQ-0003 (Sent), death-certificate-moroka.pdf (Uploaded, INTERNAL), DEP/2026/002 R 25 000 (RECORDED). |
| Moroka entity IDs captured for Day 15 | PASS | See Entity IDs below; written to `isolation-probe-ids.txt`. |

## Entity IDs (Moroka Family Trust — Day 15 MUST-BE-INVISIBLE to Sipho)

- **Client (customer)**: `9894de9b-31d1-4017-af9c-98ea889092f5` (Moroka Family Trust, TRUST)
- **Matter (project)**: `dc10e9ac-becd-4cd6-babe-c723b501bfb0` (EST-2026-002, "Estate Late Peter Moroka", Estates — Deceased)
- **Info request**: `458c97b6-637d-41d1-a458-f4f0c03354e5` (REQ-0003, Liquidation and Distribution Account Pack, Sent, due 2026-07-13)
- **Document**: `b72eaa77-ecd2-4ec2-b0e3-cdfc744526fb` (death-certificate-moroka.pdf, UPLOADED, visibility INTERNAL; s3_key `org/mathebula-partners/project/dc10e9ac-…/b72eaa77-…`)
- **Trust transaction**: `23791476-e6c6-4715-8272-5a666a6867b6` (DEP/2026/002, DEPOSIT, R 25 000,00, RECORDED)
- **Portal contact**: `651e35a8-1636-449d-aa8b-11cb1506ac9f` (Moroka portal contact, moroka.portal@example.com)
- **Portal magic-link** (Moroka, not to be used on Sipho's session): `http://localhost:3002/auth/exchange?token=GIt69AIK3rFw1YSPaZb2zg8XdDIoqb-wsmUkcxhMA_c&orgId=mathebula-partners`

## Console / backend notes

- Firm console errors are all known carry-over: **OBS-201** (`/api/assistant/invocations?…PENDING_APPROVAL` 404) and **OBS-506** (`/api/assistant/specialists/INTAKE/sessions` 404 → `[SpecialistLauncher] startSession failed`) — AI assistant proxy not wired in KC mode, WONT_FIX-EXEMPT — plus a benign LocalStack favicon 404. **No new errors** from any Day 14 flow (client/matter/request/upload/deposit).
- Backend: 0 ERROR/WARN at deposit time (11:55:52Z); deposit posted + portal-trust-activity notification to Moroka sent cleanly.

## OBS-1001 carry-over (trust-deposit combobox)

14.10 exercised the Record Deposit Client + Matter comboboxes on a **real click** (cmdk searchable popovers); both opened and selected with no programmatic state-forcing. **OBS-1001 stays VERIFIED — not a reopen.**

## Gaps filed

None. Day 14 passed cleanly with zero new gaps. The prior "blocker" was a QA-harness tooling issue (wrong MCP toolset), not a Kazi defect — no OBS-14xx gap applies. **Picker-level isolation observed as a positive signal**: the Record Deposit Matter combobox listed only Moroka's matter once Moroka was selected as the client.

## Impact on Day 15

Day 15 (portal isolation check — Sipho must NOT see Moroka's data) is now **unblocked**. Moroka exists with a matter, info request, internal document, and trust deposit. Real IDs are in `isolation-probe-ids.txt` for the Day 15 probes (`/projects/[morokaMatterId]`, `/info-requests/[morokaInfoRequestId]`, `/trust`, and backend `GET /portal/api/...`). Sipho's `/trust` balance must remain R 50 000,00 (his deposit only) and must never show the R 25 000 Moroka deposit.
