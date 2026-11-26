# 665B OBS-before: Settings › Firm II (Audit trail, Data protection, Data requests), task 665.1-equivalent

- **When:** 2026-11-26, 16:19Z to 16:50Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`; the worktree has none), not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code was changed and no SQL was run.

## Stack observed

- **Frontend container:** `docker inspect e2e-frontend` → created `2026-11-26T15:46:02.895Z`, image `sha256:cf0194316993…` (image created `15:46:02.517Z`). Its compose `working_dir` label is `worktree-epic-665A/compose`; that worktree has since been removed.
- **Build source:** the orchestrator built it from 665A's product commit `bf2b15f51` (`2026-11-26T17:44:10+02:00`).
  - `git diff --stat bf2b15f51 60f67bfe3 -- frontend packages compose` is **empty**, and so is `bf2b15f51..dac162907` (the 665A squash-merge).
  - So the observed frontend equals this worktree's base, `main` @ `60f67bfe3`. This assumes the 665A worktree had no uncommitted frontend changes at build time, which cannot be proven after the fact.
- **Backend container:** `e2e-backend`, created `15:24:49Z` (image `sha256:e7fd89e4…`, same compose dir). The data is a fresh e2e seed, plus 665A's gate runs and the legal suites (e.g. the "5qp3" run's DSAR client, information officer and generated documents).
- **Data:**
  - Jurisdiction ZA, information officer "Test Officer 5qp3" / officer@test.com, financial retention 60 months.
  - 5 active retention policies.
  - 7 processing activities.
  - 1 DSAR (DSAR Client 5qp3, ACCESS, RECEIVED).
  - **269 audit events** at the start (6 pages of 50).
- **Nothing was seeded for the audit log.** The ≥ 2-pages requirement was already met, so no seed script exists. The count grew to 280 during the capture: the Team role assignments (3 × "Member Role Changed", WARNING), the reversible saves below, and the "Access denied" NOTICE events from Carol's 403 probes in `00-discover`.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities |
|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / **false** / true | 29 (all) |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST. `GET /api/audit-events` → 403, `GET /api/data-requests` → 403 |
| `carol-cr-holder` | Carol's cookie while holding **"665A OBS Oversight"** | 665A OBS Oversight / false / false | TEAM_OVERSIGHT |
| `carol-cr-lacker` | Carol's cookie while holding **"665A OBS No Oversight"** | 665A OBS No Oversight / false / false | PROJECT_MANAGEMENT |

**Custom roles:**
- **Reused 665A's two custom roles.** 665A created them through Settings › Firm › Roles › New Role and left them on the stack (0 members). `02-roles.cjs check` re-verified both:
  - API: exact capability sets.
  - UI: the Roles tab shows "Edit 665A OBS Oversight" and "Edit 665A OBS No Oversight" (1 each).
- **Created nothing new.**
- **Assignment went through the UI** (Team › Carol's row › member panel › Role › Save, `02-roles.cjs assign`), in this order:
  - Member → 665A OBS Oversight
  - → 665A OBS No Oversight
  - → **Member**
- After each step, `GET /api/members/{id}/capabilities` and Carol's own `/api/me/capabilities` were recorded. The final one shows role Member, `overrides: []`, and the 5 capabilities above.
- `discover-post.json` personas, members and orgRoles are **identical** to `discover-pre.json`.

## Gating (`frontend/lib/settings-nav.ts:65-91` + the tab loaders)

| Tab id (`?tab=`) | Label | Registry gate | Loader's own check | Alice | Carol / lacker | Holder |
|---|---|---|---|---|---|---|
| `audit-trail` | Audit trail | `requiredCapability: "TEAM_OVERSIGHT"` | `audit-trail.tsx:113-124`: backend 403 → h1 "Audit log" + "Not authorised…" | rendered | **notice** "You don't have access to Audit trail — showing General instead." + General | **rendered** (same table, 6 pages) |
| `data-protection` | Data protection | `adminOnly` | `data-protection.tsx:22-31`: inline denial | rendered | notice "…Data protection…" + General | notice + General |
| `data-requests` | Data requests | `adminOnly` | `data-requests.tsx:14-23`: inline denial | rendered | notice "…Data requests…" + General | notice + General |

- **Tab bars** (`tablist "Firm settings"`):
  - Alice: 8 tabs (General, Roles, Audit trail, Data protection, Data requests, Compliance, Features, Subscription).
  - Carol and the lacker: **General, Subscription**.
  - The holder: **General, Audit trail, Subscription**.
- **Unreachable loader branches:** the registry gate falls back to General first, so the loaders' own denials (the audit 403 copy, both "You do not have permission…" texts) are reachable by no persona.
- **Legacy routes:**
  - `/settings/audit-log` → `?tab=audit-trail`
  - `/settings/data-protection` → `?tab=data-protection`
  - `/settings/data-protection/requests` → `?tab=data-requests`
  - All return 200 with 0 "Something went wrong", and they gate identically.
- **Every capture:** `main#main-content` = 1 and h1 in `#main-content` = **1**, on every tab × persona × viewport × theme (48 page states).

## Per tab (Alice 1440 light; full probe in `measurements.json` → `personas.alice.tabs`)

### Audit trail: h1 "Audit log" (title "Firm — Audit trail · Settings | Kazi")

- **Headings:** the h1 is the only heading. The card titles "Preset", "Filters" and "Events" are `data-slot=card-title` **divs**, not headings.
- **Under the h1:** the subtitle "Read-only feed of audit events. Filter by date range, severity, actor, event type, or entity." and an "Export" dropdown (`export-dropdown-trigger`).
  - Menu items: "Download CSV" (`export-dropdown-csv`) and "Download PDF" (`export-dropdown-pdf`). Opened and closed only; nothing exported.
- **Three `data-slot=card` cards:**

| Card | Contents |
|---|---|
| Preset | Select `#audit-preset-select` (`audit-preset-select`, sr-only label "Filter preset"), placeholder "— Choose a preset —". Options: Sensitive (last 30 days) / Compliance (last 90 days) / Security (last 7 days) / Financial approvals (last 30 days) |
| Filters (`audit-filter-row`) | See the next table |
| Events | Title "Events" + "274 total" (font-mono), then the table |

- **Filter controls:**

| Control | Type | Values |
|---|---|---|
| From | date `#audit-filter-from` | UTC 00:00 |
| To | date `#audit-filter-to` | UTC 23:59:59.999 |
| Actor | Select `#audit-filter-actor` | "All actors", group [Members] "Alice Owner 201" / "Bob Admin 54" / "Carol Member 11" |
| Entity type | Select `#audit-filter-entity-type` | "All entity types" + 38 types with counts (alphabetical by label; includes a raw **"REPORT 3"**) |
| Event type | combobox `#audit-filter-event-type` (Popover + cmdk, search placeholder "Search event types…") | 82 items |
| Entity ID | text `#audit-filter-entity-id` (placeholder "UUID") | a non-UUID gives the inline `role=alert` "Enter a full ID" and `aria-invalid=true`, and is not committed |
| Severity | 4 `aria-pressed` chips `severity-toggle-{INFO,NOTICE,WARNING,CRITICAL}` in `role=group "Severity filter"` | |
| Clear filters | `audit-clear-filters` | only while a filter is active |

- **Table:** `data-slot=table` inside `table-container` (`overflow-x-auto`), wrapped in `div.contain-inline-size`.
  - Headers: (toggle) | TIME | SEVERITY | EVENT | ACTOR | ENTITY.
  - 50 rows `audit-row-{id}`, each with a toggle button "Expand details" (`audit-row-toggle-{id}`, `aria-expanded`), a `severity-pill` (`data-severity`), the event label, `actor-display` and the entity cell.
  - Page-1 chips: INFO 44, NOTICE 2, WARNING 3, CRITICAL 1.
  - Entity cells on page 1: `entity-cell-link` 19, `entity-cell-literal` 31, `entity-cell-empty` 0.
- **Row expand:** "Expand details" → the label becomes "Collapse details", `aria-expanded=true`, and one `audit-row-details-{id}` row appears.
  - It holds an `AuditDetailsViewer` ("Object(6) newRole: Member … previousRole: 665A OBS No Oversight") plus Source / IP / Agent ("API", "172.18.0.7", "node").
  - Collapsing removes the details row. Changing page clears expansion (from the code, `audit-log-client.tsx:546-549`; not exercised).
- **Console:** 0 messages on a plain load (`01-capture`).

### Data protection: h1 "Data Protection"

- **Subtitle:** "Configure data protection jurisdiction, information officer details, and manage compliance settings."
- **Cards:** 6 hand-rolled `div.rounded-lg.border` sections, each with an h2. There are 0 `data-slot=card` and **0 testids** on the tab.

| h2 | Contents |
|---|---|
| Jurisdiction | Radix Select (trigger shows "South Africa (POPIA)"; **no accessible name**, F-665B-5). Options: South Africa (POPIA) ✓ / European Union (GDPR) (coming soon) disabled / Brazil (LGPD) (coming soon) disabled |
| Information Officer | `#officer-name` "Name" = "Test Officer 5qp3", `#officer-email` "Email" = officer@test.com, "Save" (**disabled while pristine**) |
| Data Subject Access Requests | Description + link "View DSAR Requests →" → `/settings/firm?tab=data-requests` (`text-teal-600`, K-32) |
| Retention Policies | See below |
| Processing Register | See below |
| PAIA Manual | "Generate PAIA Manual" (enabled: the jurisdiction is set; the amber "Jurisdiction required…" warning shows only without one) |

- **Retention Policies:**
  - `data-slot=table` with headers ENTITY TYPE / RETENTION PERIOD / ACTION / ENABLED / LAST EVALUATED / SAVE.
  - 5 rows: Time Entry 1800, Document 1800, Comment 1080, Client 1825, Audit Event 2555. Every Last Evaluated is "Never".
  - Per row: number input "Retention days for {Type}", native `<select>` "Action for {Type}" (Anonymize | Flag), switch "Toggle {Type} policy", icon button "Save {Type} policy".
  - Below the table: "Run Retention Check", "Execute Purge".
  - **The three `delete` rows show "Anonymize"** (F-665B-1).
- **Processing Register:**
  - Header buttons "Export Register" and "Add Activity".
  - `data-slot=table`: CATEGORY / DESCRIPTION / LEGAL BASIS / DATA SUBJECTS / RETENTION PERIOD / ACTIONS.
  - 7 rows. Legal basis is shown as raw keys, e.g. "contractual_necessity". Description is `max-w-xs truncate`.
  - Per row: "Edit {Category}" / "Delete {Category}" icon buttons.
- **Empty states:** none visible (every table has rows). From the source: "No retention policies configured." and "No processing activities recorded. Click “Add Activity” to create one."

### Data requests: h1 "DSAR Requests"

- **Subtitle:** "Track and manage data subject access requests." The "Log New Request" button is in the header row. 0 testids.
- **Table:** a hand-rolled `<table class="w-full text-sm">` (**no `data-slot`**) in `div.overflow-x-auto` inside one bordered div.
  - Headers in sentence case: Subject / Client | Type | Status | Received | Deadline | Actions.
- **Its 1 row:** "DSAR Client 5qp3" | badge "Access" | badge "Received" | "Nov 26, 2026" | "30d left" | button "Mark Processing" (aria-label "Mark as processing"; not clicked, it changes status).
  - "Received" uses `toLocaleDateString(undefined)`, so headless shows the en-US "Nov 26, 2026", while the audit table prints "26 Nov 2026, 18:19".
- **Empty state:** not observable, because one DSAR exists. Source: "No DSAR requests found. Use the "Log new request" button to record one." It says "Log new request", but the button reads "Log New Request".

## Audit log paging (`audit-paging.json`, `sheet-audit-behaviour.png`, `crop-audit-*.png`)

- **Page size:** **50** (`audit-trail.tsx:21`). 269–280 events give **6 pages**.
- **Pager:** `<nav aria-label="Audit log pagination">`. Text "Page **1** of 6" (the number is in a `<span class=font-medium>`).
  - Two `<button>`s, "Previous" and "Next", with real `disabled` and `cursor-not-allowed bg-slate-50 text-slate-400` when disabled. They are buttons, not links (no hrefs).
  - Page 1: Previous disabled, Next enabled.
- **Page state lives in the URL, zero-based:**
  - Next → `router.push` to `/settings/firm?tab=audit-trail&page=1` ("Page 2 of 6", `scroll: false`).
  - Previous from page 2 → `?tab=audit-trail` (the `page` key is **deleted** at 0, not `page=0`).
  - Server render: the loader reads `page` (`Math.max(0, parseInt)`).
- **Filters are kept across pages:**
  - Actor = Alice Owner → `?tab=audit-trail&actorId=652e707f-…` (no `page`), "Page 1 of 5", 201 total.
  - Next → `…&actorId=…&page=1`, "Page 2 of 5". All 50 rows' actor is "Alice Owner", and the trigger still reads "Alice Owner".
- **A filter change resets to page 1:**
  - From page 2, toggling INFO → `…&actorId=…&severities=INFO` (**`page` removed**), "Page 1 of 4", 194 total. Next → `&page=1` "Page 2 of 4".
  - From page 2, Entity type = Client → `…&entityType=customer`, "Page 1 of 1", 13 rows.
  - Every `setParam` / `toggleSeverity` deletes `page` (`audit-log-client.tsx:495,517`).
  - "Clear filters" drops every filter key and `page` → "Page 1 of 6".
- **Pending state while a page fetch is in flight** (the RSC request held 3 s with `page.route`, sampled at 150 / 600 / 1 500 ms):
  - **The only cue is a `<span class="ml-2 text-xs text-slate-400">Loading…</span>` appended to the Events card title**, from `useTransition`'s `isPending` (`audit-log-client.tsx:898`).
  - **No dimming:** the table, its container and the card stay at `opacity: 1` and `pointer-events: auto`. There is no `aria-busy` anywhere in the document, and no element over 100 px high has opacity < 1.
  - The URL stays on the old page, and the pager text stays "Page 1 of 6", until the response commits (~3.5 s, then `?page=1`, "Page 2 of 6").
  - **Next stays enabled during the fetch.**
  - Crop: `crop-audit-pending.png`.
- **Zero results:**
  - Entity ID = an unused UUID (`…&entityId=00000000-0000-4000-8000-000000000665`) → "Events **0 total**". The table is replaced by `<p class="py-8 text-center text-sm text-slate-500">` **"No audit events in this range. Try widening the date range or changing filters."** That `<p>` is 84 px high and the Events card 190 px.
  - The pager shows **"No events"**, with **no "Page 1 of 0"** (F-664B-2: the kit needs this label). Previous and Next are both disabled.
  - The same happens for `from=2030-01-01…`, and for the Compliance group preset, which uses the sentinel `eventType=__multi__` plus the amber banner (`role=status`, `audit-preset-multi-event-banner`): "Preset “Compliance (last 90 days)” filters to 8 event types: matter.closure.override_used, … — backend multi-value filter pending; results temporarily empty."
  - Crop: `crop-audit-zero-results.png`.
- **Out of range:** `?page=99` → "274 total", "The audit log is empty…", pager "Page 100 of 6" (F-665B-2, `crop-audit-out-of-range.png`).
- **Sensitive preset:** `from=<now-30d>&severities=WARNING,CRITICAL`, 7 rows, "Page 1 of 1".
- **Entity links (K-45, all 6 pages scanned):** what each type's first link does when opened as Alice.

| entity type | count | href pattern | lands |
|---|---|---|---|
| customer | 22 | `/org/{slug}/clients/{uuid}` | 200, client h1 |
| project | 17 | `/org/{slug}/matters/{uuid}` | 200 |
| invoice | 30 | `/org/{slug}/invoices/{uuid}` (text "Fee note INV-0005") | 200 |
| information_request | 4 | `/org/{slug}/information-requests/{uuid}` | 200 |
| member | 9 | `/org/{slug}/team#member-{uuid}` | 200 (Team) |
| **org_role** | 5 | `/org/{slug}/settings/roles/{uuid}` | **404** |
| **data_subject_request** | 1 | `/org/{slug}/settings/data-protection/requests/{uuid}` | **404** |
| **matter_closure** | 1 | `/org/{slug}/matters/{uuid}/closure` | **404** |
| **document** | 5 | `/org/{slug}/documents/{uuid}` | **404** |
| **trust_transaction** | 11 | `/org/{slug}/trust-accounting/transactions/{uuid}` | **404** |

- **Literal (no link) types:** security, org_settings, generated_document, tag, tax_rate, document_template, field_definition, legal_disbursement, time_entry, billing_rate, acceptance_request, checklist_instance(_item), project_member, REPORT, task, conflict_check, adverse_party(_link), trust_investment, trust_creditor_account, trust_account, court_date, prescription_tracker, project_budget, comment, cost_rate, pack_install.
- **The K-45 links also produce console 404s through `<Link>` prefetch** (`03b`):
  - A plain first load gives 0.
  - After the client-side Next: 404s for `/settings/roles/{uuid}`, `/matters/{uuid}/closure`, `/documents/{uuid}` and `/trust-accounting/transactions/{uuid}` ×2.
  - Hovering the links gives one more `/documents/{uuid}`.

## Save models + exact messages (`saves.json`, `sheet-dataprot-interactions.png`; Alice 1440 light)

| Section | Model | Action (reversible) | Message | Time |
|---|---|---|---|---|
| Jurisdiction | Radix Select, **saves on change** (no button); only the first selection (null) opens the AlertDialog "Set Data Protection Jurisdiction" with Cancel / Confirm | **Not saved.** Stored ZA; EU/BR are disabled; re-picking the current value fires nothing. Listbox opened, then Escape | none (`router.refresh`; "Saving..." span while submitting) | — |
| Information Officer | per-card "Save", disabled until a field differs | name + " (665B)" → Save, then the original name → Save (**restored**) | **"Saved successfully."**, a `<span class="text-sm text-teal-600 dark:text-teal-400">` with no role / aria-live. It stays until the next edit (still visible after 2 s); Save is disabled again afterwards | message visible ≤ 30 ms after the click resolved, ~400 ms total, server action 200 |
| Retention Policies row | per-row icon "Save {Type} policy" | "Audit Event" saved unchanged (FLAG / 2555 / on) | **No message by design.** The button is `disabled` with a spinner for ~100 ms; no `role=alert`. Errors would be a red `role=alert` under the button | ~130 ms, server action 200 |
| Processing Register row | Edit dialog "Edit Processing Activity" → "Update" | "Client Information" updated unchanged | **No message.** The dialog closes and `router.refresh` runs. The toast seen afterwards was the earlier Export one | ~720 ms |
| Export Register | toast only | clicked | sonner `data-type=info` **"Export register is coming soon."** | — |

**Opened and cancelled (destructive or not reversible):**

| Control | Kind | Text / fields | Outcome |
|---|---|---|---|
| Execute Purge | native `window.confirm` | "Are you sure you want to execute the retention purge? This action cannot be undone." | dismissed |
| Delete {Category} | native `window.confirm` | "Delete this processing activity?" | dismissed |
| Add Activity | dialog "Add Processing Activity" ("Record a new personal data processing activity.") | Category, Description (textarea), Legal Basis, Data Subjects, Retention Period, Recipients (optional) → Cancel / **Add Activity** | Cancel |
| Log New Request | dialog "Log DSAR Request" ("Record a new data subject access request received from a client.") | Subject Name, Email (optional) (email), Request Type (native select: Access* / Correction / Deletion / Objection / Portability), Client ID (placeholder "Client UUID (required by backend)", hint "Enter the UUID of the linked client record."), Date Received (date, default today 2026-11-26) → Cancel / **Log Request** | Cancel |

- **Not clicked:**
  - "Run Retention Check" writes `lastEvaluatedAt` (`RetentionService.java:94`).
  - "Generate PAIA Manual" creates a document.
  - "Mark Processing" changes the DSAR status.
- **Dialog sizes:** every dialog fits at 1024×768 (Add / Edit activity 66–702, Log DSAR 105–663, submit in the viewport) and at 1440×900. All have `overflow-y: visible` and `max-height: none`.
- **Restore check (`00-discover post`):**
  - Settings, data requests, personas, members and org roles are **identical**.
  - Retention policies and processing activities are **identical as sets**, but their **order changed**: the saved rows moved to the end (no ORDER BY, F-665B-3). The after-capture will list Audit Event last in Retention Policies and Client Information last in Processing Register.
  - The same happens on 665A's Compliance › Retention Policies, which reads the same rows.
  - `saves.json → restored: false` is this order difference only (the script compares ordered arrays).

## Console (`console.json`)

- **60 plain page loads** (01-capture, 4 personas × 3 tabs × 4 variants + legacy routes): **0 errors, 0 warnings, 0 hydration messages.**
- 02-roles and 04-dataprot: 0 messages.
- **31 errors, all "Failed to load resource: 404":** 23 in 03-audit and 8 in 03b. Every one is a K-45 link route, from `<Link>` prefetch after client navigation or from 03 opening the 404 routes on purpose. 0 hydration messages anywhere.

## Measurements: table scroller at 1024 (visible width / scroll width, px; page horizontal overflow 0 everywhere)

| Table | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| Audit events (Alice; holder identical) | **446 / 827** (381 hidden) | 454 / 827 | 814 / 827 (13 hidden) | 822 / 827 |
| Retention Policies | **444 / 631** (187 hidden; ENABLED cut, LAST EVALUATED + SAVE off-screen) | 452 / 631 | 812 / 812 | 820 / 820 |
| Processing Register | **444 / 1156** (712 hidden) | 452 / 1156 | 812 / 1156 (344) | 820 / 1156 |
| DSAR requests | **494 / 634** (140 hidden) | 502 / 634 | 862 / 862 | 870 / 870 |

**Document heights (Alice light):**

| Tab | 1024 | 1440 |
|---|---|---|
| Audit trail | 3 265 | 2 899 |
| Data protection | 2 356 | 2 142 |
| Data requests | 824 | 956 |

## SEL-COMPAT before (`sel-compat.json`)

- **Spec targets:**
  - The specs that touch these tabs are `settings/data-protection-settings.spec.ts`, `audit-log/audit-log-smoke.spec.ts` and `admin-audit-30day/admin-audit-lifecycle.spec.ts` (all 8 tests `test.skip`). The latter two go via `/settings/audit-log`.
  - So do `legal-depth-ii/day-30-45-soa-writeoff.spec.ts:401-416` and `day-75-85-closure.spec.ts:71-93`.
  - `audit-log/matter-closure-audit-tab.spec.ts` targets `/matters/{id}` (closure history), not these tabs. Its `severity-pill[data-severity=CRITICAL]` is recorded for information only (1 on audit page 1).
- **Every strict selector resolves to exactly 1 on its tab for Alice:**
  - h1 "Data Protection" exact; combobox filter `/Select a jurisdiction|South Africa/`; "Generate PAIA Manual"; label "Retention days for Client"; "Save Client policy".
  - headings "Retention Policies" / "Processing Register"; `#officer-name` / `#officer-email`; button "Save" exact; "Add Activity".
  - tab "Data requests"; "Log New Request"; h1 "DSAR Requests"; row "DSAR Client 5qp3".
  - `h1 filter /Audit log/i`; `audit-preset-select`; `#audit-filter-event-type`; first row's "Expand details".
  - Interactive (after the spec's own action): option `/Sensitive/i` 1; placeholder "Search event types…" 1; dialog "Log DSAR Request" 1, and within it "Subject Name" 1, "Client ID" 1, "Log Request" 1.
- **Non-1 counts that the specs read with `.first()` or `.count()`:**
  - Event rows (`:not(toggle):not(details)`): 50.
  - `[data-testid^=audit-row-]:not(details)`: **100** (rows + toggles; the specs use `.first()`, which is the row).
  - `[data-testid^=audit-row-]`: 100.
- **Spec event keys:**
  - "disbursement.written_off" + Enter → first item "Disbursement written off 1", URL `eventType=disbursement.written_off`, 1 row.
  - "matter.closure.override_used" + Enter → "Matter Closure Override Used 1", 1 row.
- **`/No audit events/i`:** matches the zero-results copy. The smoke spec's empty fallback depends on that copy, and on the "No events" pager label, through F-664B-2.
- **Carol and the lacker:** every 665B selector is 0 (General fallback). The holder matches Alice on the audit tab and is 0 on the other two.

## Scripts (`scripts/`, nvm node 20, each takes `PHASE=before|after`; raw tiles in `../archive-665B-$PHASE/`, outside the repo)

| Script | What |
|---|---|
| `lib.cjs` | Helpers (copied from 665A's, adapted): Playwright resolution, `$RAW`, `apiGet`, `TABS`, `LABELS`. Re-exports `661B/walk/scripts/common.cjs` |
| `00-discover.cjs <label>` | GET-only baseline → `discover-<label>.json` (pre / post) |
| `01-capture.cjs <persona>` | 3 tabs × 1024/1440 × light/dark full-page shots, a compact probe (incl. table scroller widths) per variant, the full probe at 1440 light, SEL-COMPAT at 1024 light, legacy routes. No clicks |
| `02-roles.cjs check \| assign "<role>"` | Verify 665A's custom roles (no creation); Team-page role assignment (UI only) |
| `03-audit.cjs` | Paging, URL, filters across pages, reset, pending (RSC held 3 s), zero results, out of range, presets, filter options, row expand, export menu, entity links per type + landing |
| `03b-audit-extra.cjs` | Added after 03 ran: the URLs of the 404s 03 saw in the console, and a tight pending crop |
| `04-dataprot.cjs` | One save per reversible save model, destructive confirms dismissed, dialogs opened / cancelled, 1024 dialog sizes, restore check |
| `05-assemble.cjs` | Merges into `measurements.json`, `sel-compat.json`, `console.json`, `audit-paging.json`, `saves.json`; builds the sheets |

- **Executed:** `00 pre` → `01 alice` → `01 carol` → `02 check` → `02 assign "665A OBS Oversight"` → `01 carol-cr-holder` → `02 assign "665A OBS No Oversight"` → `01 carol-cr-lacker` → `02 assign "Member"` → `03` → `03b` → `04` → `00 post` → `STACK="…" 05`. All exited 0.
- **Edits before a script's first successful run:**
  - `03-audit.cjs`: removed a stray `{ clip: undefined }` before its first run.
  - `04-dataprot.cjs` crashed twice. First, the Jurisdiction box was read after an open Radix Select had aria-hidden the page; nothing had been saved yet. Second, a native `confirm` deadlocked `waitForEvent`. Both were fixed before the successful run.
  - That second attempt had already made both Information Officer saves (restored) and the Audit Event row save, before failing at Execute Purge. The final run repeated them. This is why the retention row order changed before the recorded `before` snapshot.
- **No script was edited after its successful run.**
- **Erratum:** the caption baked into `crop-defect-retention-delete-as-anonymize.png` (from `04-dataprot.cjs`) says "a row Save would persist ANONYMIZE". More precisely, an untouched row still saves `delete` (local state), and touching the select stores ANONYMIZE. See `../found.md` F-665B-1.

## Sheets

| File | Contents |
|---|---|
| `sheet-{audit-trail,data-protection,data-requests}-alice.png` | Alice, full page: cols 1024 light / 1024 dark / 1440 light / 1440 dark |
| `sheet-{…}-carol.png` | Carol, same grid (General fallback + notice) |
| `sheet-{…}-custom-role.png` | Row 1 holder (TEAM_OVERSIGHT), row 2 lacker; same columns |
| `sheet-audit-behaviour.png` | Page 2, Actor filter on page 2, expanded row, Compliance preset (banner + zero), pending viewport, `?page=99` |
| `crop-audit-pending.png` | Events title "Loading…" + unchanged pager while the fetch is in flight |
| `crop-audit-zero-results.png` | Zero results: copy + "No events" pager |
| `crop-audit-out-of-range.png` | F-665B-2 |
| `sheet-dataprot-interactions.png` | Jurisdiction listbox, both officer saves, retention row save, Add Activity / Log DSAR dialogs, Log DSAR at 1024 |
| `crop-defect-retention-delete-as-anonymize.png` | F-665B-1 |
| `sheet-roles-assign.png` | Team › Carol's member panel per assignment |

JSON (single line): `discover-pre.json`, `discover-post.json`, `measurements.json` (per persona → `tabs` full probe, `variants` compact ×4, `legacyRoutes`; `customRoles`; `overflow1024`), `audit-paging.json`, `saves.json`, `sel-compat.json`, `console.json`.

## Register rows owned by 665B, re-observed

- **K-32:** `text-teal-600` on the audit entity links and on "View DSAR Requests →". Both are still present.
- **K-45:** confirmed. The same 5 types return 404, now with counts and the prefetch console 404s (above).
- **K-109:** the Processing Register header at 1024 squeezes the description to ≈ 155 px beside the two buttons (read off the screenshot) (`sheet-data-protection-alice.png`, 1024 tiles). 390 was not captured (not in the brief).

## Anything odd (not fixed; see `../found.md`)

1. **F-665B-1:** stored `delete` retention actions display as "Anonymize".
2. **F-665B-2:** out-of-range page: "274 total" beside "The audit log is empty…", and "Page 100 of 6".
3. **F-665B-3:** a save reorders the retention and processing lists (no ORDER BY).
4. **F-665B-4 (hypothesis):** disabling a policy on Data protection removes it from that list (`findByActive(true)`).
5. **F-665B-5:** the Jurisdiction select has no accessible name.
6. **Pending state:** it has no dimming, no `aria-busy`, and Next stays clickable. A second click during the fetch re-pushes the same `page=1` (it uses the stale `currentPage`), so it is harmless today. This is the before-state for 665B's "pending dimming".
7. **Audit card titles are divs:** the tab has a single heading (h1). "Preset", "Filters" and "Events" are not headings.
8. **Raw entity type:** "REPORT" appears raw in the Entity type options and literal cells. The other types are labelled.
9. **DSAR table:** it is the only one of the four tables without `data-slot=table`, and its headers are sentence case where the others are uppercase. Its dates use the browser locale.
10. **Two "Data requests":** the Data requests tab is `adminOnly`. The app rail also has a "Data requests" entry under CLIENTS (`lib/nav-items.ts:164-167`), which goes to `/compliance/requests`, is gated on `CUSTOMER_MANAGEMENT`, and is a different page (K-105). That entry is visible in Alice's screenshots; the page was not visited.
