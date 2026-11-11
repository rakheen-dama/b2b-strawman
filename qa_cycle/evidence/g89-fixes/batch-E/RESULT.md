# G89 Batch E: browser verification (PR #1752, main 6e510dc23)

- **Stack:** Keycloak dev stack. Firm app http://localhost:3000, portal http://localhost:3002, org `mathebula-partners`, tenant schema `tenant_5039f2d497cf`. `svc.sh status`: backend, gateway, frontend and portal were all healthy. HEAD = `6e510dc23`.
- **Date:** 2026-11-11 SAST, walk run 02:14–02:30 (00:14–00:30 UTC).
- **Tools:** Playwright MCP. Thandi used the default context, Carol used an isolated context `carol`, Bob used `bob`, and the portal used `portal`. Each layout item was checked at 1440×900 and 1024×768, with the DOM measured (`scrollWidth` vs `clientWidth`) as well as screenshotted.
- **Changes:** none to code. No SQL writes. SQL was read-only. The only REST use was reading the Mailpit API for the magic link.

## Result

| Gap | Status | Observation (exact text, viewport) | Evidence |
|---|---|---|---|
| **G89-F17** Trust › Reconciliation | PASS | **1440 and 1024:** "Reconciliation History · 0 reconciliations found · No reconciliations yet. Start a new reconciliation to begin." There is no "Failed to load reconciliations". The DB has 0 `trust_reconciliations`, so the empty array (which `result.page.*` used to choke on) now renders the empty state. | 01, 37 |
| **G89-F50** S35 interest section lists every run | PASS | Trust account ••••6789, FY end 2027-02-28. Interest Allocation rows: "Lerato Mthembu · 2026-10-01 – 2026-10-31 · R 55,21", "Sipho Dlamini · 2026-11-05 – 2026-11-06 · R 1,34", "Moroka Family Trust · 2026-11-05 – 2026-11-06 · R 17,81", "Total · R 74,36". Both posted runs (Oct and 05→06 Nov) are present, and the total equals 55,21 + 1,34 + 17,81. The PDF and CSV carry the same rows and the same total. | 02, 03, CSV, PDF |
| **G89-F48** S35 PDF money format | **PARTIAL** (format PASS; new layout defect) | Every PDF amount is in rand house format: "R 38 017,81", "R 45 019,15", "R 55,21", "-R 12 000,00". No raw "10000.00". **But** in the *Trust Receipts & Payments* table the AMOUNT column is squeezed by the long `INT-CREDIT-…` item text. 10 of the 14 amounts break across two lines at the thousands space: "R 10 / 000,00", "-R 1 / 250,00", "R 45 / 019,15" (the Total row). *Client Trust Balances* and *Interest Allocation* print on one line. See N1. | 05-page1-hi, s35-pdf.txt |
| **G89-F49** S35 CSV negatives | PASS | `…DEEDS/SIPHO-001 · Sipho Dlamini (PAYMENT),-1250.00`, `…(REFUND),-3751.34`, `…REF/LERATO-g89a-exact … (REFUND),-10055.21`. No apostrophe prefix on any negative. | section-35-data-pack-report.csv |
| **G89-F47** S35 on-screen ITEM wraps | PASS | **1440:** document 1440/1440 with no overflowing ancestor. Columns: SECTION 305–578, ITEM 578–1225, AMOUNT 1225–1375. The ITEM cell is `max-w-[28rem] wrap-anywhere whitespace-normal`, and an INT-CREDIT row is 57 px tall (wrapped). **1024:** document 1024/1024 and AMOUNT is visible on screen. The table's wrapper has a residual 19 px internal scroll (723 vs 704), which is cosmetic: no amount is hidden. | 03, 04 |
| **F7** matter Overview KPI grid | PASS | Grid `md:grid-cols-2 xl:grid-cols-4`. **1440:** `256px ×4`, all four tiles in one row at top=546. **1024:** `320px 320px`. HOURS LOGGED and TASK COMPLETION are at top=574, TRUST BALANCE and OUTSTANDING FEE NOTES at top=740, so it is 2×2 with no lone tile. | 06, 07 |
| **F8** Settings › Documents › Templates | PASS (criteria) · NOTE | The Name cell is `max-w-[32rem] min-w-[16rem] wrap-anywhere whitespace-normal`, and the description wraps (cell height 85 px at 1440, 101 px at 1024). **1440:** all 7 category tables are 856 px wide in an 856 px container (was 1621 px), with no scroll. **1024:** the tables are 666 px, not 1600. **NOTE:** at 1024 the settings side-nav leaves only 440 px, so each table scrolls horizontally inside its `overflow-x-auto` wrapper (666 vs 440). SOURCE, STATUS and ACTIONS are off-screen until you scroll. The outer container has 4 px of page-level overflow (708 vs 704). See N2. | 13, 14 |
| **F9** INV-0001 Payment History | PASS | **1024:** table 670 px, with no overflowing ancestor. The PROVIDER and METHOD headers are `display:none`. The REFERENCE cell shows a secondary line: "EFT/SIPHO-001 / Manual · EFT" and "— / mock · —". The "Provider:"/"Method:" labels are `sr-only`. **1440:** table 1086 px with all 7 columns (STATUS, PROVIDER, METHOD, REFERENCE, AMOUNT, DATE, RECEIPT) as table cells. The secondary line is `xl:hidden`. | 11, 12 |
| **F15** matter Documents tab | PASS | **1024:** document 1024/1024 and table 654 px, with no overflow. The SIZE and UPLOADED headers are `display:none`. The secondary line reads "5.7 KB · 7 Nov 2026" and "1.4 KB · 7 Nov 2026". Names are truncated with an ellipsis ("statement-of-account-lerato-mthembu-raf-claim-mva-2025…"), and the full name is in the `title` tooltip. **1440:** table 1070 px with all columns, and no overflow. | 09, 10 |
| **F5** Overview "Hours logged" refresh | PASS | The Overview was loaded at 00:17:40Z, reading "1.0h HOURS LOGGED", which warms the 1-min Caffeine cache. At 00:17:52Z I logged 30m through Tasks › "Settlement negotiation with RAF" › Log Time. Back on Overview at 00:17:57Z it read **"1.5h HOURS LOGGED"**, inside the TTL and with no reload. The DB has time entry `c3ce8756…` (30 min). | 08 |
| **F41** Interest "Posted Date" is the SA date | PASS | The Oct run was posted at `2026-11-06 23:18:22Z` (= 07 Nov 01:18 SAST) and shows **"07 Nov 2026"**. The 05→06 Nov run was posted at `2026-11-05 21:05Z` (23:05 SAST) and shows "05 Nov 2026". To rule out an artefact of the browser's timezone, I re-ran it in a fresh context with `timezoneId=America/Los_Angeles`. It still showed "07 Nov 2026" (the LA local date would be 06 Nov). | 15, 16 |
| **F40** refusal money format | PASS | **Matter guard:** Thandi recorded Refund R 6 000,00 (`REF/LERATO-g89e-F40-matter`) on Lerato's RAF matter (matter balance R 5 000, client balance R 7 000). Bob clicked Approve and got: **"Insufficient trust balance on matter Lerato Mthembu — RAF Claim (MVA 2025-11-03) for Lerato Mthembu. Available on matter: R5 000,00, Requested: R6 000,00"**. **Client guard:** Bob approved the existing `REF/LERATO-g89a-over` (R 10 056,21) and got: **"Insufficient trust balance for Lerato Mthembu. Available: R7 000,00, Requested: R10 056,21"**. Both use the house format. Both stayed AWAITING APPROVAL. I then rejected the new one through the UI (see the data section). | 29–32 |
| **F57** SoA summary wording | PASS | Lerato RAF › Generate Statement of Account, period 2026-10-01 → 2026-11-11, internal draft (share and email unticked) › Preview & Save. The Summary reads: "**Fees billed in period: R0,00**", "**Disbursements billed in period: R0,00**", "Previous balance owing: R0,00", "Payments received: R0,00", "Closing balance owing: R0,00", "Trust balance held: R7 000,00", "Unbilled work in progress (not yet billed): R3 750,00". The downloaded PDF reads the same. | 33, 34, soa-lerato-2026-11-11.pdf, soa-pdf.txt |
| **F20** DSAR detail | PASS | Data request `10e61cab…` (DB status `COMPLETED`). The badge reads **"Completed"**. The timeline reads "Request Received 06 Nov 2026 · Processing Started · Request Completed 06 Nov 2026". Processing Started has no date line and no "—" (F20b). | 17 |
| **G-D10a** audit rows show no raw key | PASS | Settings › Firm › Audit trail, 337 events. The rows show only the label: "Time entry created", "Access denied", "Comment created", "Role deleted"… No `time_entry.created`-style key line appears under any label. A regex scan for `word.word` in the panel text found 0 matches. | 18 |
| **G-D10b** filter by typed key | PASS | Event type combobox › typed `time_entry`. The options were "Event key starts with "time_entry"" and "Time entry created 7". Choosing the starts-with option gives URL `?eventType=time_entry` and **"7 total"**, all rows "Time entry created". The DB count is `time_entry.created = 7`. | 19, 20 |
| **G88-F27(a)** "Access denied" | PASS | The audit label reads "Access denied" (sentence case) on all 42 `security.access_denied` rows. | 18 |
| **G88-F27(b)** disbursement "Billed" | PASS | Disbursement `1bf0cfb3…` (Sheriff Fees, BILLED). The header badges read "Approved · Billed", and the financial summary reads **"Billed · Yes"**. "Invoiced" appears nowhere on the page. | 21 |
| **G88-F24** Activity status labels | PASS | Task "Settlement negotiation with RAF": Open → In Progress → Open, through the task panel status select. The matter Activity tab reads: "Thandi Mathebula changed task "Settlement negotiation with RAF" status to **In progress**" and "… status to **Open**". There is no "IN_PROGRESS" or "DONE". | 28 |
| **G88-F2** Team has no "Joined —" | PASS | The Team columns are MEMBER · EMAIL · ROLE. The text "Joined" appears nowhere on the page. | 22 |
| **F65** role change refreshes | PASS | Team › Carol › Role Member → Admin › Save. The row updated to **"Admin"** in place, with no reload (DB `admin`). After a reload it still showed "Admin" (the name, not a slug). Admin → Member › Save updated the row to "Member" in place. Only system roles were exercised; the custom-role slug case (`org:associate-no-trust`) was not re-created. | 23, 24 |
| **F64** trust tab without VIEW_TRUST | PASS | In Team › Carol (Member), I unticked View Trust and saved. The DB shows `-VIEW_TRUST`. As Carol, `clients/ec988df3…?tab=trust` showed **"Trust Balance — Trust details are not available"**. While the page loaded, a response listener caught 0 ≥500 responses and 0 console errors. The network panel shows only 200s, including the page `POST …?tab=trust → 200` that 500'd twice in the Batch H walk. The backend log in that window has only 403-style WARNs (`unbilled-summary`, `kyc/status`, `xero/connection`: insufficient_role) and no ERROR. I restored Carol afterwards and verified it with SQL (below). | 25, 26, 27 |
| **F44** portal Firm actions | **PARTIAL**: deposit PASS, interest credit DEFERRED | I signed in as Lerato with the Mailpit magic link (msg `NvxYj5a65cXa9EgP2TftPj`). Activity › Firm actions shows: "Trust deposit received · Thandi Mathebula · 3 days ago" (DEP/LERATO-g89d), "Document generated for you", "Trust refund approved · Bob Ndlovu", and "Trust deposit received · Thandi Mathebula · 4 days ago" (DEP/LERATO-g89a). The unmattered DEP2 (R 2 000) is correctly absent (ADR-366 D7). **There is no interest-credit row.** The reason is structural, not a defect I could observe. The fix adds a *new* audit type, `trust_interest_credit.recorded` (`PortalActivityEventTypes.java:49`), which is emitted only when a run is posted. Both existing runs were posted before the fix (05 Nov and 06 Nov 23:18Z). The tenant has **0** `trust_interest_credit.recorded` rows, and the historic runs wrote only run-level `interest_run.posted`. To observe the interest-credit row, a **new interest run must be posted** (for example period 07→10 Nov). That writes permanent INTEREST_CREDIT ledger entries for 3 clients and moves balances that Batch F and the exit re-score read, so it needs orchestrator authorisation. I did not do it. | 35, 36 |
| **G88-M9** fee-note panels refresh | DEFERRED | Both fee notes (INV-0001 and INV-0002) are already **PAID**, and there is no DRAFT or SENT fee note. Exercising send or pay would mean creating a new fee note from Lerato's unbilled WIP (R 3 750). That consumes INV-0003 and bills two time entries, so it is not cheap. | — |

## Side findings (not Batch E acceptance items; for triage)

- **N1 (LOW, new, caused by the F48 fix):** in the S35 PDF *Trust Receipts & Payments* table, rand amounts wrap mid-number ("R 10" / "000,00") because the AMOUNT column is narrow next to the long INT-CREDIT references and the thousands separator is a breakable space. Possible fix: a `white-space: nowrap` / min-width on the amount cell, or NBSP (U+00A0 / U+202F) as the group separator in the template helper. Evidence: `05-F48-s35-pdf-page1-hi.png`, `s35-pdf.txt`.
- **N2 (LOW):** Settings › Documents › Templates at 1024: the table needs 226 px of horizontal scroll inside its wrapper (666 vs 440 px) because the settings side-nav takes the width. Raw entity type **"ORGANIZATION"** is shown in the ENTITY TYPE column for the PAIA Section 51 Manual (other rows say "Matter"). Evidence: 14.
- **N3 (LOW, raw labels):**
  - The firm audit row label "**REPORT EXPORTED**" (all caps) and entity "**REPORT** fb04c94c" for the S35 export. Other labels are sentence case.
  - "Member Role Changed" and "Cash Digest Sent" are Title Case where the rest are sentence case.
  - The matter Activity reads "Bob Ndlovu edited a comment on entity **"entity"**".
  - Portal › Activity › Your actions shows a raw "**comment.created**".
  - Trust › Transactions statuses render as "AWAITING APPROVAL" / "RECORDED" (uppercase enum style).

  Evidence: 18, 28, 35, 29.
- **N4 (info):** the portal console logged one `401` on `POST http://localhost:8080/portal/auth/exchange` during sign-in, but the exchange succeeded and landed on `/home`. This is probably the effect firing twice (the token is single-use). It does not affect users. The other console entries were favicon 404s and the Next.js `scroll-behavior` warning only.
- **Observation:** the matter Overview TRUST BALANCE reads R 5 000,00 (matter cell), while the SoA "Trust balance held" reads R 7 000,00 (client level, including the unmattered DEP2). This is consistent with the Batch A matter/client split, and is noted only so the next reviewer is not surprised.

## Data created / changed and restored

| Item | Action | State after |
|---|---|---|
| Time entry `c3ce8756-35e2-4088-baa3-de5e1dc7a4aa` | Created through UI (F5): 30 min, billable, on task "Settlement negotiation with RAF" (Lerato RAF), "G89-E QA F5 hours-refresh check" | **Kept.** Unbilled. Lerato's matter hours go 1.0h → 1.5h, unbilled WIP R 3 750,00. Delete it through the task panel if a clean baseline is needed. |
| Task `e2fa3bab…` status | Open → In Progress → Open (G88-F24) | **Restored** to OPEN (SQL). Two activity rows remain (the audit is immutable). |
| Carol's role | Member → Admin → Member (F65) | **Restored**: Member. |
| Carol's capability override | `-VIEW_TRUST` added, then removed (F64) | **Restored**: 0 overrides. |
| Trust refund `d122337b-e81b-4aaa-a465-e294e7419645` `REF/LERATO-g89e-F40-matter`, R 6 000,00 | Recorded by Thandi, approve refused for Bob (F40), then **rejected** by Bob with reason "QA cleanup: G89-E F40 refusal-message check, not a real refund" | REJECTED. No balance effect. |
| `REF/LERATO-g89a-over` (pre-existing, R 10 056,21) | Bob clicked Approve, which was refused | Unchanged: AWAITING_APPROVAL. |
| Generated document `6cd4b8d9-bc40-4ec1-82cf-e9563b7be857` (SoA Lerato, 2026-10-01 → 2026-11-11) | Created through UI (F57) as an internal draft (not shared, not emailed) | **Kept.** `generated_documents` went 18 → 19. It does not appear in the portal. |
| Portal magic link | One magic-link email to lerato.mthembu@email.co.za (Mailpit `NvxYj5a65cXa9EgP2TftPj`) | Consumed. |
| Audit events | Created by all of the above, plus 1 `report.exported` for the S35 PDF/CSV | Immutable. |

Proof (read-only SQL, after teardown):

```
select m.email, r.name from members m join org_roles r on r.id=m.org_role_id order by 1;
 bob@mathebula-test.local    | Admin
 carol@mathebula-test.local  | Member
 thandi@mathebula-test.local | Owner
select count(*) from member_capability_overrides;                         -> 0
Member role caps: AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, MANAGE_DISBURSEMENTS, VIEW_LEGAL, VIEW_TRUST (unchanged)
select status from tasks where id='e2fa3bab-…';                           -> OPEN
select reference, status from trust_transactions where reference like '%g89e%';
 REF/LERATO-g89e-F40-matter | REJECTED
```

The temporary folder `.playwright-mcp/g89e/` was copied here and then deleted. The browsers are closed.

## Files in this folder

- **Screenshots:** `01`–`37`, each named by gap and viewport.
- **S35 exports:** `section-35-data-pack-report.pdf`, its text extract `s35-pdf.txt`, `section-35-data-pack-report.csv`, and the rendered pages `05-F48-s35-pdf-page1-hi.png` and `05-F48-s35-pdf-page2.png`.
- **SoA:** `soa-lerato-2026-11-11.pdf` and its text extract `soa-pdf.txt`.
