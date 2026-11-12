# 642B observe-first "before" (task 642.6)

Captured 2026-11-12, final run started 19:37:35Z, against the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090). The stack was built at about 18:47Z from the 642A branch. That branch's client-detail code is the same as main's, and the live DOM confirms it: the header is main's shadcn `Card` with no hero tiles. The worktree is at `9b3ec3555` (= main).

- **Driver:** `obs-before.cjs`, a Node Playwright library script (`chromium.launch()`, nvm node v20.11.1, `@playwright/test` 1.59.1 from the main checkout's `frontend/node_modules`; the worktree has no `node_modules`). Playwright MCP was not used because the user's live Chrome holds it.
- **Raw data:** `obs-before.json`, written compactly. It contains `loads[]` (one record per page load: DOM facts, console, HTTP status), `lists` (the Clients list as each user sees it), `probes` (GET-only backend reads, per user) and `smartActionClicks`.
- **Theme:** set with `localStorage['kazi-theme']`, then the page was reloaded. `<html class>` matched the requested theme on all 11 loads.
- **Waits:** `waitUntil: 'load'`, then the first `h1` visible, then the active `[role=tabpanel][data-state=active]` visible, plus 2.5 s. No `networkidle`.
- **Artefact:** in the full-page shots at 1024×768 the dark sidebar stops at viewport height. This is the sticky sidebar under `fullPage`, the same as in 641B, and not a product defect.
- **Read-only SQL:** used only to find ids (`SELECT` on `tenant_7d218705360b`). No `INSERT` or `UPDATE` was run.

## Data created through the UI (second-currency fee note)

`setup-usd-feenote.cjs` drives Alice through the UI one stage at a time, so a re-run never duplicates data. There was no SQL and no REST write. These are the four rows it created:

| stage | UI path | result |
|---|---|---|
| `rate` | Acme Corp → Fees › Tariffs → **Add Override**: Alice Owner, 100, currency picker → USD, Create Override | billing rate `31632605-…` (Alice × Acme, USD 100,00, from 2026-11-12) |
| `task` | Website Redesign matter → Tasks → **New Task** "642B USD fee note probe" | task created |
| `time` | task row → **Log Time** 1h. Rate preview: "Billing rate: US$100,00/hr (client override) 1h x US$100,00 = US$100,00" | time entry `fae1115e-…`, snapshot 100.00 **USD**, 60 min |
| `feenote` | Acme → Fees › Fee notes → **New fee note** → Currency `USD` → Fetch Unbilled Time (the 1 USD entry is pre-selected; "1 of 1 selectable") → Validate & Create Draft → Create Draft | draft `7e204c5c-dd58-41db-90be-46b021f57647`, USD 115,00 (100 + 15 % VAT) |
| `send` | fee note detail → **Approve** → **Send fee note** | **INV-0003, SENT, USD 115,00, due 12 Dec 2026** |

**Can the create flow pick a currency?** Yes:

- The fee-note dialog has a free-text ISO currency input (`frontend/components/invoices/invoice-generation-dialog.tsx:120-131`).
- Only entries whose `billingRateCurrency` equals the chosen currency can be selected (`:363`).
- Disbursements are ZAR-only (`:520-523`).
- So a second-currency fee note first needs a rate in that currency. This run used a client rate override.

**The script needed two fixes during the run, and the committed version contains both:**

1. The `rate` stage failed after submit on a strict-mode locator: Radix popovers also carry `role=dialog`. The rate had already been created; read-only SELECT confirmed it. Dialogs are now located by `[data-slot=dialog-content]`.
2. The `feenote` stage waited for a redirect to the draft that never comes. The dialog closes and the page stays on `?tab=invoices`. The draft had already been created. The stage now waits for the dialog to close.

Neither stage was re-run, because a re-run would duplicate the data. The `task`, `time` and `send` stages ran with the committed code.

**These rows stay on the stack:** the USD rate override, the task, the 1h USD time entry and INV-0003. The after-capture (642.10) can reuse Acme as the foreign-currency client without setting it up again.

## Clients used

Org default currency is **ZAR** (`/api/settings`). Primary trust account: `d12292b0-…` "Mathebula & Partners Trust Account".

| key | id | URL | why |
|---|---|---|---|
| rich | `658fb311-67e2-475f-8bc0-43be675c7b39` | `/org/e2e-test-org/clients/658fb311-67e2-475f-8bc0-43be675c7b39` | Sipho Ndlovu mwdwiakrbc6a. ONBOARDING, has an ID-number header fact, 2 linked matters (1 ACTIVE "transition repro", 1 CLOSED "Ndlovu v RAF — Depth II"), **trust R 3 750,00**, R 5 000,00 unbilled time, **no outstanding fee notes**. Used for both Alice and Carol. |
| fx | `3f3219bf-f8cd-47ce-bb59-f1bf1020330e` | `/org/e2e-test-org/clients/3f3219bf-f8cd-47ce-bb59-f1bf1020330e` | Acme Corp. ACTIVE, 1 ACTIVE matter, **INV-0001 ZAR 8 625,00 SENT + INV-0003 USD 115,00 SENT** + a ZAR 3 450,00 draft. No trust. |

The stack has only 4 clients. No single client has both a trust balance and outstanding fee notes, which is why two clients were needed.

## Screenshots (full page, default tab = **Overview**)

| file | user / client | viewport | theme |
|---|---|---|---|
| alice-rich-1024-light.png | Alice / Sipho | 1024×768 | light |
| alice-rich-1024-dark.png | Alice / Sipho | 1024×768 | dark |
| alice-rich-1440-light.png | Alice / Sipho | 1440×900 | light |
| alice-rich-1440-dark.png | Alice / Sipho | 1440×900 | dark |
| carol-rich-1024-light.png | Carol / Sipho | 1024×768 | light |
| carol-rich-1024-dark.png | Carol / Sipho | 1024×768 | dark |
| carol-rich-1440-light.png | Carol / Sipho | 1440×900 | light |
| carol-rich-1440-dark.png | Carol / Sipho | 1440×900 | dark |
| alice-fx-1440-light.png | Alice / Acme (foreign-currency client), default tab | 1440×900 | light |
| alice-fx-invoices-1440-light.png | Alice / Acme, `?tab=invoices` (Fees › Fee notes) | 1440×900 | light |

Carol / Acme at 1440 light was loaded for DOM facts only, with no screenshot. Every load returned HTTP 200 with exactly one `h1`.

## Header DOM facts (`[data-testid=client-header-card]`)

These facts were identical across viewport and theme for each user/client pair.

| | Alice / Sipho | Carol / Sipho | Alice / Acme | Carol / Acme |
|---|---|---|---|---|
| header card | `DIV` (shadcn Card) | same | same | same |
| `h1` count | 1 | 1 | 1 | 1 |
| `h1[data-testid=client-name]` | "Sipho Ndlovu mwdwiakrbc6a" | same | "Acme Corp" | same |
| `customer-status-badge` | **absent** (ACTIVE record + lifecycle → hidden, R-11) | absent | absent | absent |
| `lifecycle-badge` | "Onboarding" | "Onboarding" | "Active" | "Active" |
| `client-header-fields` | "ID 8501015800087" | same | absent | absent |
| `client-contact` | "sipho.ndlovu.mwdwiakrbc6a@example.com" | same | "contact@acme.example.com · +1-555-0100" | same |
| `client-context` | "Since 12 Nov 2026 · **2 matters**" | "Since 12 Nov 2026 · **0 matters**" | "Since 12 Nov 2026 · 1 matter" | "… · **0 matters**" |
| `client-header-actions` | present | present | present | present |
| actions (in order) | `smart-primary-action` "Activate Client", "Generate Document" (no testid), `client-overflow-trigger` "More actions" | "Activate Client", "More actions" | "Edit", "Generate Document", "More actions" | "Edit", "More actions" |
| `smart-primary-action` | "Activate Client" (**does nothing**, see F3) | "Activate Client" (**does nothing**) | "Edit" (**does nothing**) | "Edit" (**does nothing**) |

Header `textContent`:

- Alice / Sipho: "Sipho Ndlovu mwdwiakrbc6aOnboardingID8501015800087sipho.ndlovu.mwdwiakrbc6a@example.comSince 12 Nov 2026 · 2 mattersActivate ClientGenerate DocumentMore actions"
- Carol / Sipho: "Sipho Ndlovu mwdwiakrbc6aOnboardingID8501015800087sipho.ndlovu.mwdwiakrbc6a@example.comSince 12 Nov 2026 · 0 mattersActivate ClientMore actions"
- Alice / Acme: "Acme CorpActivecontact@acme.example.com · +1-555-0100Since 12 Nov 2026 · 1 matterEditGenerate DocumentMore actions"

**Header box and tab-bar position.** Page y-coordinate of `[data-testid=grouped-tab-bar]` (`role=tablist`):

| | header card (y, h) | tab bar y |
|---|---|---|
| Alice / Sipho 1024 | 136, 215 (name wraps to 2 lines, actions wrap) | **375** |
| Alice / Sipho 1440 | 136, 187 | **347** |
| Carol / Sipho 1024 and 1440 | 136, 187 | **347** |
| Alice / Acme 1440, Carol / Acme 1440 | 136, 159 | **319** |

**Tab groups:**

- Alice: Details, **Overview** (selected), Matters, Fees, FICA & compliance, Activity.
- Carol: the same without Activity. Her Fees group has no chevron, which means a single sub-tab.

## What the default (Overview) tab shows for money

| | money strings in the active panel | outstanding? | trust? |
|---|---|---|---|
| Alice / Sipho | "R 5 000,00" (Unbilled Time card, 2.0 hours) | **not shown** | **not shown**. "Trust" appears only as template names ("Client Trust Statement", "Trust Receipt"). |
| Alice / Acme | none (Client Readiness "Setup complete", Document Templates, FICA Verification) | **not shown** | not shown |
| Carol / Sipho, Carol / Acme | none (only the "FICA Verification" card) | not shown | not shown |

Today's client page shows **no outstanding total and no trust balance** in the header or on the Overview tab. Outstanding money appears only on Fees › Fee notes, one row per fee note, each in its own currency. Trust appears only on Fees › Trust (`TrustBalanceCard`, SWR client fetch).

## Foreign-currency finding: today's presentation

- **Client page (`alice-fx-invoices-1440-light.png`):** the Fee notes tab lists INV-0003 `US$115,00` Sent, INV-0001 `R 8 625,00` Sent, and Draft `R 3 450,00`. Each row is formatted in its own currency. **There is no total anywhere on the client page**, so nothing is silently summed across currencies. Header and Overview show no outstanding figure at all (`alice-fx-1440-light.png`).
- **Clients list (642A, Alice):** the Acme row reads **"R 8 625,00 · 1"** in Outstanding fee notes. The backend (`ClientListRowRepository.findAggregatesRaw`) groups by currency and keeps **one** group, the org default first (`LIMIT 1`). The USD fee note is therefore **dropped without any hint**: it is not summed, but there is no "excludes 1 in USD" note and the count says 1. This is information only, outside 642B scope; 644B or a later phase could add an exclusion hint.
- **Debtor read that 642.7 will use** (`GET /api/collections/debtors/{id}`, Alice, 200): `outstandingInvoices` = `[{INV-0001, balanceDue 8625, ZAR}, {INV-0003, balanceDue 115, USD}]`. The path in `frontend/lib/api/collections.ts:141-143` is confirmed. A summing rule that ignores currency would show "R 8 740,00". The 642.7 rule (sum the default currency only, hint "Excludes 1 fee note in other currencies") should show **R 8 625,00 + hint**.

## Backend reads the 642.7 hero will source (GET-only probes, `obs-before.json` → `probes`)

| read | Alice (owner) | Carol (member) |
|---|---|---|
| `/api/me/capabilities` (checked ad hoc) | — | `AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST`, so **no INVOICING** |
| `/api/trust-accounts` | 200, 1 account, `isPrimary: true` | 200 (same) |
| `/api/trust-accounts/{primary}/client-ledgers/{Sipho}` | 200, `balance: 3750` | 200, `balance: 3750` |
| `/api/trust-accounts/{primary}/client-ledgers/{Acme}` (no ledger card) | **200, `id: null, balance: 0`** (not 404) | 200 (same) |
| `/api/collections/debtors/{id}` | 200 (shape above; Sipho `[]`) | **403** "Insufficient permissions" |
| `/api/clients/{id}/fica-status` | 200: Sipho `IN_PROGRESS`, Acme `DONE` | 200 (same) |
| `/api/clients/{id}/matters` | 200. Sipho 2 rows, Acme 1. Keys **`id, name, description, createdAt` only, no `status`** | 200, **`[]`** for both clients |

So the member gets a Trust tile (VIEW_TRUST plus the module are on). The Outstanding tile is omitted, because the page gate `canViewCollections` (`page.tsx:106`) needs owner, admin or INVOICING. Open matters for the member is 0.

## OQ #15: server wrapper for the per-client ledger read

```
$ grep -n "client-ledgers/" "frontend/app/(app)/org/[slug]/trust-accounting/actions.ts"
(no output, exit 1)
```

The only ledger read in that file is the list read at `:102-103`: `` `/api/trust-accounts/${accountId}/client-ledgers?size=200` ``. It has no `/` after `client-ledgers`, which is why the literal grep returns nothing.

```
$ grep -rn "client-ledgers/" frontend/lib frontend/app | head
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts:86:  return api.get<ClientLedgerCard>(`/api/trust-accounts/${accountId}/client-ledgers/${clientId}`);
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts:112:    `/api/trust-accounts/${accountId}/client-ledgers/${clientId}/history?${qs}`
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts:136:    `/api/trust-accounts/${accountId}/client-ledgers/${clientId}/statement?${qs}`
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page.tsx:14:} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page.tsx:161:    return `/org/${slug}/trust-accounting/client-ledgers/${clientId}${qs ? `?${qs}` : ""}`;
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page.tsx:171:    return `/org/${slug}/trust-accounting/client-ledgers/${clientId}?${qs}`;
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx:12:} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx:239:                              href={`/org/${slug}/trust-accounting/client-ledgers/${ledger.clientId}`}
```

**Evidence:** a per-client server read **already exists**. It is `fetchClientLedger(accountId, clientId)` at `$FA/trust-accounting/client-ledgers/actions.ts:82-87`, in a `"use server"` module, and returns `ClientLedgerCard`. It is the function `TrustBalanceCard` uses (`frontend/components/trust/TrustBalanceCard.tsx:28,95`). `fetchTrustAccounts()` is at `$FA/trust-accounting/actions.ts:31`.

**Recorded decision for OQ #15 (recommendation, for the orchestrator to confirm):**

- Do **not** add `fetchClientLedgerCard` to `$FA/trust-accounting/actions.ts`.
- Have `hero-tiles.ts` reuse `fetchTrustAccounts()` → primary → `fetchClientLedger(primaryId, clientId)`.
- No new wrapper or endpoint is needed.
- Note: a missing ledger card returns **200 with balance 0**, not 404. The planned "404 → R 0,00" branch would never run on this stack. Keep it as a defensive catch, but the vitest should also cover "200, balance 0".

## Carol (member) behaviour

- **Clients list `/org/e2e-test-org/clients`:** HTTP 200, but the page reads "**You don't have access to Clients** — Contact your organisation admin to update your role. Go to dashboard". There is no `h1`, no table and 0 rows.
- **Client detail page (both clients):** HTTP 200 with full content:
  - one `h1`, the header card, ID fact, contact line and lifecycle badge
  - context "**0 matters**": Carol is not a member of any matter, and `/clients/{id}/matters` returns `[]` for her (ADR-346 visible-matters filtering)
  - smart action shown, with no Generate Document control
  - the Overview tab holds only the FICA Verification card
- In summary, a member can open a client page by direct URL (it is "on her list" in the ADR-346 sense), but cannot browse the Clients list. That list gate is existing behaviour and is not changed by 642B.

## Console

- **Browser:** 0 console errors, 0 warnings and 0 `pageerror` events on all 11 client-page loads and both list loads, so there are no hydration warnings.
  - In earlier exploratory runs, one `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` appeared during the mock-login → dashboard navigation (the fetch is aborted by the navigation). The buffer is reset after login, and it did not occur in the final run.
- **Backend WARNs during the final run (19:37:30–19:40Z):**
  - 7 × `Access denied: path=/api/integrations/xero/connection … insufficient_role`
  - 7 × `Access denied: path=/api/clients/<id>/unbilled-summary … insufficient_role`

  These come from Carol's 7 client-page loads (5 captures + 2 smart-action clicks). The page asks a member for admin-only reads and swallows the 403s. Also 2 × debtors 403 from this script's own Carol probes.

## Findings that exist before 642B (not fixed here)

- **F1 (blocks 642.7 as written). `GET /api/clients/{id}/matters` has no `status` field.**
  - The response is `LinkedMatterResponse(UUID id, String name, String description, Instant createdAt)` (`backend/…/customer/dto/ClientDtos.java:168`).
  - The frontend types it as `Matter[]` (`frontend/lib/api/clients.ts:189-190`). `linkedProjects[i].status` is therefore always `undefined` at runtime.
  - 642.7's rule "Open matters = `linkedProjects` count of `status === "ACTIVE"`" would show **0 for every client** (Sipho has 1 ACTIVE + 1 CLOSED; the list's Open matters column correctly says 1).
  - Options, for the orchestrator to choose:
    - (a) a backend change adding `status` to `LinkedMatterResponse`, which needs a BE-VERIFY;
    - (b) another source, such as the clients-list aggregate `openMatterCount` (`ClientListRowRepository`) or `GET /api/matters?customerId=` if it exists and is filtered per member;
    - (c) re-scope the tile.
- **F2. A missing ledger card returns 200 / `balance: 0`, not 404** (see OQ #15). This changes a test premise only.
- **F3. The header smart action is a dead button in all observed states.** Clicking it opens no dialog and does not change the URL (`smartActionClicks`):
  - Alice / Sipho (ONBOARDING, checklist 0/8): "Activate Client"
  - Alice / Acme (ACTIVE): "Edit"
  - Carol on both clients
  - Cause: `client-header-card.tsx:183` binds `onClick={onPrimaryAction}`, but the wrapper passes `undefined` whenever `targetLifecycleStatus` is null (`client-header-card-with-lifecycle.tsx:70`). The `lifecycleActionPrompt` (`page.tsx:441`) is null for a member, for ACTIVE/DORMANT ("Edit"), and for ONBOARDING with an incomplete checklist.
  - The e2e already records the Edit/OFFBOARDING half as `628F-BLOCKER` (`customer-lifecycle.spec.ts:160,181`).
  - 642B keeps `smart-primary-action` as is (SEL-COMPAT). Wiring it up is a product decision, not this slice.
- **F4. `client-context` counts every linked matter, closed ones included** ("2 matters" for Sipho, while the list says 1 open). For a member it shows "0 matters" (visible only). The new Open matters tile will sit next to this line; reconciling the wording is a 642B design note.
- **F5. A member's client page triggers two swallowed 403s per load** (`xero/connection`, `unbilled-summary`), as listed under Console. This is the same class as 641B F4 (`/time-summary/by-member`).
- **F6 (info). The Clients list Outstanding column drops non-default-currency fee notes without a hint** ("R 8 625,00 · 1" for Acme, which also has USD 115,00 outstanding).

## SEL-COMPAT "before"

Raw grep output is in `sel-compat-before.txt`. `frontend/e2e` is byte-identical to main.

| selector | e2e consumers (paths relative to `frontend/`) |
|---|---|
| `client-header-card` | none as a selector. Only 2 hits, inside `628F-BLOCKER` annotation strings (`customer-lifecycle.spec.ts:160,181`) that cite the file name. |
| `client-name` | none (testid). The h1 is reached by role. |
| `customer-status-badge` | none |
| `lifecycle-badge` | 13 hits: `customer-lifecycle.spec.ts:63,81,154,166,173,187,194`, `customer-data-protection.spec.ts:142,156`, `day-01-03-onboarding.spec.ts:76,193`, `day-14-conveyancing.spec.ts:180`, `helpers/legal-depth-ii.ts:231` (`startClientOnboarding`). All use `toHaveText(<label>)`, so the badge's **text must stay exactly the lifecycle label**. |
| `client-contact`, `client-context`, `client-header-fields`, `client-header-field-*` | none |
| `client-header-actions` | `document-generation.spec.ts:109`: `getByTestId("client-header-actions").getByRole("button", { name: "Generate Document" })`. Generate Document must stay **inside** `client-header-actions`. |
| `smart-primary-action` | `customer-lifecycle.spec.ts:77` (text "Start Onboarding"), `:190` (click → alertdialog "Complete Offboarding"), `customer-data-protection.spec.ts:158` (count 0 when ANONYMIZED) |
| smart action by role | `helpers/legal-depth-ii.ts:233`: `getByRole("button", { name: "Start Onboarding" }).first()`. `.first()` resolves to the header button. The Overview ActionCard's "Start Onboarding" is a link (`Button asChild` → `<Link>`, `components/setup/action-card.tsx:25-26`), not a button. Still, keep the **header smart action as the first "Start Onboarding" button in DOM order**. `day-14-conveyancing.spec.ts:182`: `getByRole("button", { name: "Activate Client" })` toHaveCount(0) after activation. |
| `openClient` h1 wait | `helpers/legal-depth-ii.ts:156-163`: list link (exact name) → `clickThrough(link, getByRole("heading", { level: 1, name: clientName }))`, substring match, then the URL `/clients/<uuid>`. Callers: `day-75-complex:107,245`, `day-01-03-onboarding:132,139`, `day-14-conveyancing:149`, `day-30-45-soa-writeoff:206`, `day-75-85-closure:179`, plus `openClientFeeNotes` (`helpers/legal-lifecycle.ts:396-397`; `day-30-billing:155,177,272`, `day-60-interest:299`). |
| other client-page h1 waits | `customer-crud.spec.ts:72,102,120,139`, `customer-lifecycle.spec.ts:59,71`, `customer-data-protection.spec.ts:51,81,104,114,146`, `document-generation.spec.ts:106`. All are `getByRole("heading", { level: 1, name, exact: true })`, so there must be **exactly one h1 whose accessible name is the client name, with nothing appended**. (`document-generation.spec.ts:47` is the matter page.) |

## Line count

`wc -l "frontend/app/(app)/org/[slug]/clients/[id]/page.tsx"` → **756** (as expected). For reference, `client-header-card.tsx` is 221 lines and `client-header-card-with-lifecycle.tsx` is 99.
