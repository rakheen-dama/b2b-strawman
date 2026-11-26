# 665A: out-of-scope defects found during OBS-before (reported, not fixed; root CLAUDE.md §7)

665A restyles these tabs frame-only. Both defects below change stored data, so they need their own re-spec.

## F-665A-1: General shows a null brand colour as `#000000`, and "Save Settings" would persist it

- **Where:**
  - `frontend/app/(app)/org/[slug]/settings/firm/_tabs/general.tsx:54`: `brandColor={settings.brandColor ?? "#000000"}`
  - `frontend/components/settings/general-settings-form.tsx:149`: `brandColor: brandColor || undefined`
  - `OrgSettingsService.updateSettingsWithBranding` (`backend/.../settings/OrgSettingsService.java:179`) sets the brand colour to whatever it is sent, including null.
- **Observed:**
  - `GET /api/settings` → `brandColor: null` (`before/discover-pre.json`).
  - The form shows `#000000` in both the "Brand Color" text input and the swatch (`before/measurements.json` → `personas.alice.tabs.general.controls`).
- **Consequence (from the code; not executed, because it would change the stack):**
  - An owner who changes only the currency or the tax label and presses "Save Settings" also stores `#000000` as the firm's brand colour.
  - `PortalBrandingService.java:83` returns the colour unchanged to the portal, and the report templates apply it whenever it is not empty.
  - In this capture the save was made a true no-op by clearing the field first ("" → `undefined` → null).
- **Proposed (needs a decision):** show an empty field for null (the input already accepts "", because `colorValid = !brandColor || …`). Alternatively, send the brand colour only when it was changed.

## F-665A-2: Compliance shows a dormancy threshold of 365 days while the backend uses 90, and "Save Settings" would persist 365

- **Where:**
  - `frontend/app/(app)/org/[slug]/settings/firm/_tabs/compliance.tsx:80`: `dormancyThresholdDays={settings.dormancyThresholdDays ?? 365}`
  - `backend/.../compliance/CustomerLifecycleService.java:220-223`: when it is null, `runDormancyCheck()` uses **90**.
- **Observed:**
  - `GET /api/settings` → `dormancyThresholdDays: null` (`before/discover-pre.json`).
  - The "Dormancy Threshold (days)" input shows **365** (`before/measurements.json` → `personas.alice.tabs.compliance.controls`).
- **Consequence (from the code; not executed):**
  - The page states a threshold that is not in effect: 365 shown, 90 applied.
  - The card's single "Save Settings" saves dormancy, deadline and Phase 93's three FICA review intervals together. Changing only a FICA interval therefore also changes the effective dormancy threshold from 90 to 365 days, with no visible change on the page.
  - The deadline has the same `?? 30` fallback, but there it matches the backend: null resolves to the jurisdiction default, 30 for ZA (`DataSubjectRequestService.java:255-261`, `JurisdictionDefaults.java:20`). So only dormancy is wrong.
- **Proposed (needs a decision):** align the fallback with the backend (90), or have the backend return its effective value. If neither, show null as empty with a "default" hint. The `compliance.tsx:74-76` comment already states the intent ("fallbacks would overwrite the firm's real dormancy … unseen"); only the load-failure path is guarded today.

## F-665A-3: Subscription still tells a locked-out owner to "Contact your administrator" next to K-106's "managed by b2mash" copy

- **Where:** `frontend/app/(app)/org/[slug]/settings/firm/_tabs/subscription.tsx:94` (GRACE_PERIOD suffix) and `:110` (LOCKED branch): "Contact your administrator to restore access."
- **Observed (from the code, found in review):** K-106 rewrote the active-branch body to "Your Kazi subscription is managed by b2mash. Contact support to change your plan or …" (`:92`). The GRACE_PERIOD and LOCKED branches keep the old copy. The owner is the administrator, so this is the same owner-told-to-ask-admin defect K-106 fixed for the active branch.
- **Not fixed here:** the new wording is a founder copy ruling (COPY-FREEZE).
- **Route:** 670A follow-up list.

## F-665A-4: DataTable has no consumer `meta` passthrough, so editable-cell tables cannot use TanStack's `updateData` pattern

- **Where:** `frontend/components/data-table/data-table.tsx:341`: the kit sets its own TanStack `meta: { dataTableSort: … }` and accepts no consumer meta.
- **Observed (from the code, found in review):** editable-cell consumers cannot reach `table.options.meta.updateData`. `RetentionPolicyTable.tsx` (`:184-185`) works around this with a latest-ref: an `actions` ref is refreshed in `useLayoutEffect`, and the memoised columns call `actions.current.*`.
- **Proposed (needs a decision):** later editable tables reuse the latest-ref pattern, or the kit adds a meta passthrough that is merged with `dataTableSort`.
- **Route:** backlog / 669B.

## F-665A-5: The un-fixme'd e2e "Create, edit and delete a custom role" has no cleanup

- **Where:** `frontend/e2e/tests/settings/roles-capabilities.spec.ts:53`. The file has no `afterEach`.
- **Observed (from the code, found in review):** if the test fails between create and delete, an "E2E Role …" custom role stays on the stack and is visible to later runs and suites.
- **Not fixed here:** 665A only un-fixme'd the test (K-102). Spec edits beyond that are out of scope (D-42).
- **Route:** backlog.

## F-665A-6: The demo scenario still names the Subscription page heading "Billing"

- **Where:** `qa/testplan/demos/legal-za-90day-keycloak.md:191`, step 0.65: "panel heading \"Billing\"".
- **Observed:** after K-106 the h1 is "Subscription".
- **Not fixed here:** scenario edits are a product decision (root CLAUDE.md §6).
- **Route:** 670A follow-up list.

## F-665A-7: Compliance › Retention Policies shows policies it has no option for as a different record type / action

- **Where:** `frontend/components/compliance/RetentionPolicyTable.tsx:23-25`: `RECORD_TYPES = ["CUSTOMER", "AUDIT_EVENT", "DOCUMENT", "COMMENT"]`, `ACTIONS = ["FLAG", "ANONYMIZE"]`. The cells are controlled `<select value={row.original.recordType}>` / `value={row.original.action}`. The same lists and the same controlled selects are on `main` @ `02eab076f` (`:27`, `:29`, `:207`, `:248`), so this is **pre-existing**, not caused by 665A.
- **Observed (OBS-after, `after/measurements.json` → `probes.retention`):** on this stack (fresh, after the legal suites ran), `GET /api/retention-policies` returns 5 policies, 3 of them outside those lists:
  - `TIME_ENTRY/RECORD_CREATED:1800:delete`
  - `DOCUMENT/RECORD_CREATED:1800:delete`
  - `COMMENT/RECORD_CREATED:1080:delete`
- **What the table shows:**
  - The TIME_ENTRY row reads **"Client"**; its select's DOM `value` is `CUSTOMER`.
  - All three `delete` rows read **"FLAG"**; their action select's DOM `value` is `FLAG`.
  - The browser falls back to the first option when a controlled value has no `<option>`.
  - So the page tells the owner that time entries are a client policy and that three deleting policies only flag.
- **Consequence (from the code; not executed):**
  - A row's Save sends the React state, so it sends `delete` unchanged.
  - Picking from the Action select, though, can only choose FLAG or ANONYMIZE, and the "current" value shown is wrong.
  - The data-protection tab (665B surface) evidently writes a different vocabulary to the same API.
- **Route:** backlog / 665B owner. The decision needed is to align the two editors' vocabularies, or render unknown values as an explicit option.

## F-665A-8: Compliance › Retention Policies at 1024 px hides the Action, Active and row Save/Delete columns behind a horizontal scroll (narrowed by 665A)

- **Where:** `RetentionPolicyTable.tsx` DataTable `frame={false}` inside the "Retention Policies" `SettingsSection` (`CardContent` `px-6`).
- **Observed (OBS-after, `verdicts.json` → `7-layout.retentionTable1024`; `sheet-saves.png` "Retention Policies 1024 light/dark"):**
  - At 1024×768 the table's scroller is **446 px of 718** (light) and **454 of 718** (dark).
  - The **ACTION, ACTIVE and ACTIONS** columns are outside it. Every row's Delete button is off-screen (`deleteVisibleInScroller: false` ×5).
  - The header reads "AC" / "ACT", cut at the card edge. The page itself does not overflow (0 px).
  - At 1440 nothing is hidden (814/814, 822/822).
- **Before (from `before/` raw tile `tab__compliance-alice-1024-dark-full`, pixel reading; before measured tables only at 1440):**
  - The table sat outside a card, about 504 px wide (496→1000).
  - ACTION was partly visible ("FLAG" select clipped at the edge). ACTIVE and ACTIONS were already hidden.
  - So the hidden row actions are **pre-existing**. 665A's section padding takes about 50 px more and also hides ACTION.
- **Not fixed here:** a layout decision for the orchestrator. Options are a priority/hide column order, stacking at < xl, or accepting the scroll. Overlay scrollbars give no affordance that the strip scrolls.
- **Route:** accepted in 665A by orchestrator ruling D-44 (`tasks/phase91-decisions.md`) → 669A light pass / 670A follow-up list (kit-level layout decision binding 665B–665F).
