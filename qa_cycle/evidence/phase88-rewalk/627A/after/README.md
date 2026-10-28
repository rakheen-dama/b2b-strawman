# Slice 627A — observation (after), PR #1693 head e81ad7e6d

- Date: 2026-10-28. Stack: E2E mock-auth stack. Only the frontend was rebuilt, from worktree-epic-627A at e81ad7e6d: `docker compose -f compose/docker-compose.e2e.yml build frontend` (exit 0), then `up -d --no-deps frontend`. Backend and DB were left untouched.
- Browser: Playwright MCP (Chromium). User: Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`. Viewport 1440x900 unless stated otherwise.
- DB access was read-only SELECTs only. The one data change, closing matter `b06e6fb8`, went through the UI.

## Summary

| Check | Verdict |
|---|---|
| O1 G-B9 matter facts strip | PASS (one cosmetic note at 1024) |
| O2 G-B9 negative | PASS |
| O3 G-B9 client ID | PASS |
| O4 G-B14 URL-driven tabs | PASS |
| O5 G-A6 conflict link-out | **FAIL (partial)**: ID number is never prefilled from the dialog |
| O6 G-D2 COMPLETED → Close | PASS |
| O7 G-D2 regression | PASS |
| O8 A17 console / sidebar testid | PASS |

## O1 (G-B9): facts strip on the matter header. PASS

**Steps.** Opened `/org/e2e-test-org/matters/7a577920-a45b-4148-8595-72f6c77cb9d6` (Overview).

**DOM facts.**
- `[data-testid="matter-header-fields"]` is a `<DL>` containing three items: `matter-header-field-case_number`, `-court_name` and `-opposing_party`.
- `textContent` is `Case no.2026/12345·CourtGauteng Division, Pretoria·Opposing PartyRoad Accident Fund`.
- Visible labels: only "Case no." is visible. "Court" and "Opposing Party" are `sr-only`.
- Separators: two `·` spans, both `aria-hidden="true"`. They are `["·","·"]`.

**1440x900.**
- The strip is at y=247, directly under the title (y=181) and the status/work-type row. It is 479x20, on a single line.
- The visual reads "Case no. 2026/12345 · Gauteng Division, Pretoria · Road Accident Fund".
- Header actions sit at x=1013 y=181, right-aligned and unchanged.
- No document horizontal overflow, and no element overflow. Screenshot 01.

**1024x768.**
- The title column narrows to 256 px, so the strip wraps onto 3 lines (256x68).
- Header actions stay aligned at y=181: Close Matter x=597, Complete Matter x=740.
- No horizontal overflow (`scrollWidth` = 1024). Screenshot 02.
- *Cosmetic note:* at this width the wrapped lines begin with the `·` separator ("· Gauteng Division, Pretoria", "· Road Accident Fund"). Nothing breaks.

## O2 (G-B9 negative): no strip without values. PASS

- `84e2263c-…` "Team spec" (LITIGATION, `custom_fields = {}`): 0 `matter-header-fields` and 0 `matter-header-field*` elements. The header reads Title / Active / LITIGATION / Internal Matter / Close Matter / Complete Matter, the same as before. Screenshot 03.
- `9559194d-…` conveyancing matter: its custom fields are `erf_number`, `deeds_office` and `property_address`, and none of them is a header slug. Result: 0 strip elements and no overflow.

## O3 (G-B9 client): ID on the client header. PASS

- Page: `/clients/421235da-e59f-47fc-be08-be9a3e777b7d`.
- `[data-testid="client-header-fields"]` is a `<DL>` with `client-header-field-id_passport_number`. Its `textContent` is `ID8501015800087`, which renders as "ID 8501015800087".
- Placement: under the Active/Onboarding badges, above the email. No overflow. Screenshot 04.

## O4 (G-B14): tabs driven by the URL. PASS

**Steps.** Loaded `/matters/7a577920-…?tab=tasks`. A MutationObserver recorded `performance.now()` at each change to the active `[role=tabpanel]` and to `location.search`. Playwright `page.on('request')` captured every request carrying `_rsc=`, `RSC: 1` or `Next-Action`.

| Click | Panel | URL | ms click→panel | history.length | Skeleton nodes | Page RSC navigation |
|---|---|---|---|---|---|---|
| Fees › Time recording (`tab-item-time`) | time | `?tab=time` | 256 (includes opening the menu) | 8→8 | 0 | none |
| Overview | overview | `?tab=overview` | 25 | 8→8 | 0 | none (see prefetch note) |
| Diary (court-dates) | court-dates | `?tab=court-dates` | 26 | 8→8 | 0 | none (2 server actions, see note) |
| Activity › Audit trail | audit | `?tab=audit` | 234 (includes the menu) | 8→8 | 0 | none (1 server action) |
| Details › Custom fields | fields | `?tab=fields` | 253 (includes the menu) | 8→8 | 0 | none |

- At the moment the panel switched, the URL already held the new `tab` value in every row.
- `history.length` did not change, which confirms `replaceState`.

**Request notes.**
- When the Overview panel becomes visible, its KPI `<Link>`s produce `GET …?tab=time|tasks|statements&_rsc=…`. All of them carry the header `next-router-prefetch: 1`: they are viewport prefetches of the in-panel links, not a navigation for the tab click. A second Overview click produced none.
- Diary and Audit trail issue `POST …` with a `Next-Action` header. These are the panels' own data-loading server actions, not page RSC navigations.

**Browser back.**
- Starting from `?tab=tasks`, reached by tab clicks after loading `?tab=tasks`: back goes to the previous distinct navigation, `/clients/421235da-…`. Forward returns to the matter at `?tab=tasks` with the Tasks panel.
- After in-panel KPI link navigations (pushes): back returns to the matter at `?tab=overview` (the replaced entry), and the panel follows the URL.

**Reload.** Reloading `?tab=time` lands on the Time panel, with `tab-group-finance` selected. Screenshot 06.

**Deep link after tab clicks.** Screenshot 07.

| Step | URL | Panel |
|---|---|---|
| Load `?tab=time` and click the Overview tab | `?tab=overview` | overview |
| Follow the KPI link `?tab=time` | `?tab=time` | time |
| Fees › Disbursements tab, then Overview tab | `?tab=disbursements`, then `?tab=overview` | follows |
| Follow the KPI `?tab=tasks` | `?tab=tasks` | tasks |
| Overview tab, then KPI `?tab=statements` | `?tab=statements` | statements |

The panel follows the link every time.

**Other params are preserved.**
- `?tab=tasks&keep=me` plus a real Overview click gives `?tab=overview&keep=me`.
- `?tab=tasks&taskId=358510e9-…` on matter `84e2263c` opens the Task Detail sheet, which is modal (screenshot 08). With the sheet open, the tab triggers cannot receive a real click. With synthetic pointer events on the Overview trigger, the URL becomes `?tab=overview&taskId=358510e9-…`, so `taskId` is preserved. Closing the sheet with Escape removes `taskId`, which is the existing sheet behaviour.

## O5 (G-A6): conflict link-out from New Matter. FAIL (partial)

**Card.** Matters list › "New Matter" opens the "Create Matter" dialog (screenshot 09).
- The card reads: Conflict Check / "Run a conflict of interest check before creating this matter." / link `Run a conflict check first →` (`data-testid="new-matter-conflict-check-link"`).
- There is no "Coming Soon" text and no disabled button.

**With a client selected.**
- Selected "Sipho Ndlovu mvrll4pa359c". The link's href became `/org/e2e-test-org/conflict-check?checkType=NEW_MATTER&customerId=421235da-…&checkedName=Sipho+Ndlovu+mvrll4pa359c`.
- Clicking it landed on that URL with Name to Check = `Sipho Ndlovu mvrll4pa359c`, Check Type = `NEW_MATTER` and Client = Sipho Ndlovu mvrll4pa359c. These are correct.
- **ID Number is blank** (screenshot 10).
  - DB: `customers.id_number = '8501015800087'`.
  - The same happens for Khumalo Property Holdings (`id_number = 7605125009081`): the href has no `checkedIdNumber`.

**No client.** The href is `?checkType=NEW_MATTER`. The page lands with Check Type = New Matter, Name blank, ID blank and Client None (screenshot 11).

**BOGUS check type.** `/conflict-check?checkType=BOGUS` gives Check Type = New Client (the default), with no alert and no error (screenshot 12).

**Control.** A hand-typed `…&checkedIdNumber=8501015800087` does prefill the ID field. The page side works.

**Defect O5-1 (Minor/Medium; G-A6 not fully closed).**
- The dialog's client list comes from `fetchActiveCustomers()` → `listClients({status:"ACTIVE"})` → `GET /api/customers`. Since 615B, that endpoint returns the dense `ClientListRow` projection (`backend/.../customer/dto/ClientListRow.java`), which has **no `idNumber`** field.
- As a result, `selectedCustomer.idNumber` in `create-project-dialog.tsx` is always undefined, and `checkedIdNumber` is never set.
- The unit test in `create-project-dialog.test.tsx` mocks a client that has `idNumber`, so it passes without exercising the real shape.
- Repro: open New Matter, pick Sipho Ndlovu mvrll4pa359c, click "Run a conflict check first →". The ID Number field is empty.

## O6 (G-D2): a COMPLETED matter can be closed. PASS

**COMPLETED header** for `b06e6fb8-…` "627A repro — completed matter":
- Badge `Completed`.
- `header-lifecycle-actions` testids are `close-matter-btn`, `archive-project-btn` and `project-overflow-menu`. The text reads "Close Matter / Archive".
- The overflow menu holds `Reopen Matter`.
- Screenshot 13. Before the fix there was no Close action.

**Closure walk.**
1. Clicked Close Matter. The dialog "Close matter" shows the gate report (screenshot 14). Every gate passed except "No final bill issued. 0h of unbilled time + 0 unbilled approved disbursements remain." (Fix this).
2. Clicked Continue (`matter-closure-next-btn`) to reach step 2 (screenshot 15).
   - Reason: Concluded.
   - Generate closure letter and Generate SoA: checked (defaults).
   - Share and email: unchecked.
   - The "Override failing gates" toggle is available to the owner.
3. Turned on the override and entered a justification (screenshot 16), then clicked `matter-closure-confirm-close-btn`.

**Result.**
- The dialog closed. The header shows the badge `Closed` and only `reopen-matter-btn` ("Reopen Matter"), both immediately and after a reload (screenshot 17).
- DB `projects.status = CLOSED`.
- `matter_closure_log` row `52c20a6d-…`: `reason=CONCLUDED`, `override_used=t`, the justification is stored, and `closure_letter_document_id=ccf90fad-…`.

## O7 (G-D2 regression). PASS

| Matter | Status | Header actions | Overflow |
|---|---|---|---|
| `7a577920` | ACTIVE | `close-matter-btn` + `complete-project-btn` | Archive Matter |
| `84e2263c` | ACTIVE | `close-matter-btn` + `complete-project-btn` | Archive Matter |
| `a3d1f3e8` | CLOSED | only `reopen-matter-btn` (screenshot 18) | none |
| `e8224c2e` | CLOSED | only `reopen-matter-btn` | none |

## O8 (A17). PASS

- `browser_console_messages(level=warning, all=true)` for the whole session reports **0 errors and 0 warnings**. The session covered the matter pages (ACTIVE, COMPLETED, CLOSED, every tab switch), the client page, the matters list and dialog, the conflict-check page, and the closure.
- `[data-testid="sidebar-lifecycle-action"]` count is 0 on every matter page and on the client page.

## Side effects on the stack (made through the UI)

- Matter `b06e6fb8-…` went from COMPLETED to CLOSED with an override. A closure letter document was recorded in the closure log. The SoA checkbox was left at its default (checked), but its output was not verified.
- No other data was changed. Navigating to the New Matter dialog did not create a matter.
