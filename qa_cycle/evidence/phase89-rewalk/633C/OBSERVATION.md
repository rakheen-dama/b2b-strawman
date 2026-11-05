# 633C — observed PASS per gap (builder, 2026-11-05)

**Stack:** e2e stack built from this worktree (backend :8081 = main ≥ `be5dbf5af` + no backend
change in this PR), portal **production build** from the worktree on :3002
(`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm run build && pnpm run start`, the 632C
precedent), real Chromium (Playwright) at **1024×768** and **1440×900**, signed in as the Acme
contact `contact@acme.example.com` (magic link → exchange → the portal's localStorage session).
Walker: `observe.mjs` (this folder; run as `node observe.mjs <portal dir> <out dir>`), full DOM text
per page in `observe-log.md`. For each page it records `main.innerText`, a regex check for raw
enums / short ids (`OPEN|IN_PROGRESS|IN PROGRESS|DONE|SENT|SUBMITTED|DEPOSIT|PAYMENT|REFUND|FEE_TRANSFER|Matter <8 hex>`)
and `scrollWidth <= clientWidth` (no horizontal overflow).

Firm-side setup through the firm API as Alice (run-unique `3j8p3`): a **client-level** information
request for Acme (no `matterId`) → sent as **REQ-0006** (`matterId=null`); three tasks on Website
Redesign moved to OPEN / IN_PROGRESS (claim) / DONE (claim + complete); a HEARING court date on
Website Redesign (court "Johannesburg High Court OBS-3j8p3", reference `JHB-OBS-3j8p3`, judge
`Judge FIRM-ONLY-3j8p3`, description `FIRM-ONLY-NOTES 3j8p3`) and a GENERAL_3Y prescription
(notes `FIRM-ONLY-NOTES 3j8p3`); both cancelled / interrupted at the end. (Earlier walker attempts
that timed out on a hidden mobile duplicate left REQ-0003…0005 and two more task triples; their
court dates / trackers were cancelled / interrupted by hand — `portal.portal_deadline_view` = 0
rows afterwards.)

Every page at both widths: **no horizontal overflow: true**, and **none of the scanned values** (the
regex above: the task / request / trust-type enums and `Matter <8 hex>` short ids — the four registered
gaps) appears (`observe-log.md`). **Known exception, not in the regex:** `/deadlines` shows the raw
deadline status **"UPCOMING"** in every row and in both detail panels at both widths
(`observe-log.md` lines 133, 156/164/173, 179 and 309, 332/340/349, 355) — decision **D2**, deliberately
not fixed in this PR, proposed `G89-` follow-up (see "Visible but out of scope").

## Per gap

- [x] **G88-F29 — PASS (closer 633C).** `/requests` list (`portal-{1024,1440}-requests-list.png`):
  status column reads **"Requested"** (SENT — D1) and **"In progress"** (REQ-0002, submitted by the
  90-day Day 3 run); no "SENT" / "IN_PROGRESS". Request detail
  (`portal-{1024,1440}-request-client-level.png`): **"0/1 submitted • Requested"**. Item label after
  submit ("Submitted", no "status: SUBMITTED") is asserted by the 90-day Day 3 e2e step
  (`page.getByText("Submitted", { exact: true })` + `"1/1 submitted • In progress"`, gate run
  13/13) and by `request-copy.test.tsx`.
- [x] **G88-F30 — PASS (closer 633C).** Client-level REQ-0006 detail h1 = **"Request REQ-0006"**
  under the mono "REQ-0006" eyebrow (was an empty h1); the list title for the client-level rows
  reads "Request REQ-0003…0006" (D3) while matter requests keep "Website Redesign".
- [x] **G88-F20 — PASS (closer 633C).** Matter "Website Redesign" Tasks table
  (`portal-{1024,1440}-matter-tasks.png`): `Obs open … Open`, `Obs in progress … In progress`,
  `Obs done … Done` — no "OPEN" / "IN PROGRESS" / "DONE"; the matter badge still reads "Active".
- [x] **G88-F31 (portal half) — PASS (closer 633C).** `/trust/<Website Redesign>`
  (`portal-{1024,1440}-trust.png`): balance card **"Website Redesign"** (from
  `PortalTrustMatterSummary.matterName`, API `{"matterId":"55751839-…","matterName":"Website Redesign"}`),
  no "Matter 55751839"; Transactions **Type** cell **"Deposit"** (`5 Nov 2026 | Deposit | Portal Day
  21 deposit 1zuet | R 1 500,00`). The 90-day Day 21 e2e step asserts the Type cell = "Deposit" and
  no "DEPOSIT" on the row. `formatMatterLabel` grep → 0. The trust-activity **email** "Type DEPOSIT"
  is backend (D6) — follow-up, not this PR.
- [x] **§5b row 19 — PASS on the e2e stack (closer 633C; 633B = backend half).** `/deadlines`
  (`portal-{1024,1440}-deadlines.png`): rows "Hearing — Johannesburg High Court OBS-3j8p3" (Court
  date, 19 Nov 2026) and "Prescription deadline" (Prescription, 26 Nov 2026). Hearing panel text:
  `Label Hearing — Johannesburg High Court OBS-3j8p3 · Due date 19 Nov 2026 · Status UPCOMING ·
  Description JHB-OBS-3j8p3 · Source From: Court date` — **no judge, no firm notes**
  (`judge shown: false` at both widths). Prescription panel: `Prescription deadline · 26 Nov 2026 ·
  UPCOMING · General 3 years · From: Prescription` — **no notes**. No mark-read control (decision 7).
  After cancel / interrupt: portal rows for the two = **0**. The un-fixme'd Day 75 e2e case
  asserts the same end-to-end (gate run 13 passed / 0 skipped; frame `e2e-day-75-deadline-detail.png`).
  **KC `/deadlines` observation (Lerato Mthembu's backfilled court date) is the orchestrator's,
  post-merge, after the KC backend restart (D5)** — not done by the builder; the prescription is
  observed on the e2e stack only (D5, do not mutate the walked tenant).

## Visible but out of scope

- The deadline **status badge** prints the upper-cased enum ("UPCOMING") in the row and the panel
  (`deadline-list.tsx:299`, `deadline-detail-panel.tsx:152` `formatStatus` = `replace(/_/g, " ")`).
  Same bug class, not one of the four registered gaps → **not bundled** (orchestrator decision D2);
  proposed `G89-` follow-up. The guard, as specified, does not flag function-wrapped formatting.
