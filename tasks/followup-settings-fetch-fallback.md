# Follow-up Fix Spec: Silent-Defaults on Settings Fetch Failure (bug class)

**Status**: DONE — implemented and merged (PR #1537, 2026-07-09). All 8 pages fixed + `settings/error.tsx` added + 3 regression tests. Verification: both reviews APPROVE (0 findings), CodeRabbit no actionable findings, Frontend CI pass, and an observed Playwright check on the e2e stack (backend stopped → recovery UI, no overwritable defaults form; backend restored → full recovery). Live-check note: with the whole backend down the settings *layout*'s `fetchMyCapabilities()` fails first and the parent app boundary catches (layout errors bypass sibling error.tsx); `settings/error.tsx` covers the page-level partial-failure case, unit-tested.
**Origin**: CodeRabbit finding on PR #1536 (collections settings page), fixed there for collections only; investigation on 2026-07-10 confirmed the same defect in 8 sibling settings pages.
**Authorization note**: this is a same-bug-class cluster fix (8 identical instances + shared error boundary). Per the Quality Gates (§7 scope discipline), shipping them in ONE PR requires explicit authorization — granted by the user's request to define and plan this follow-up. Confirm again at implementation kickoff.

## The defect (verified mechanism, not hypothesis)

Server components for settings pages fetch org settings with a swallowing catch:

```ts
let settings: OrgSettings = { defaultCurrency: "USD" };
const settingsResult = await api.get<OrgSettings>("/api/settings").catch(() => null);
if (settingsResult) settings = settingsResult;
// per-field: settings.timeReminderDays ?? "MON,TUE,WED,THU,FRI" etc.
```

`GET /api/settings` **never 404s for a provisioned org** — `OrgSettingsService.getSettingsWithBranding` (OrgSettingsService.java:109-153) returns a fully-defaulted `SettingsResponse` via `.orElse(...)`. The same holds for `GET /api/settings/collections` (:767-772). So the catch can only ever mask a REAL failure (network / 5xx / auth).

Consequence: on a transient failure the admin sees an **editable form pre-filled with hardcoded defaults**, and every group save endpoint (`PATCH /api/settings/{group}`) **replaces all fields in that group** (verified: e.g. `updateTimeReminderSettings`, OrgSettingsService.java:696-720 sets all 4 fields unconditionally — NOT null-means-keep). One click on Save destroys the org's real policy for that group.

The correct pattern is what PR #1536 left in the collections page: no catch — `const settings = await getCollectionsSettings();` — a real failure propagates instead of silently rendering overwritable defaults.

## Scope — the 8 affected pages (category a: swallowed error → editable form → group-replace save)

All paths under `frontend/app/(app)/org/[slug]/`:

| # | Page (file:line of the catch) | Save path at risk |
|---|---|---|
| 1 | `settings/time-tracking/page.tsx:37` | `PATCH /api/settings/time-reminders` (4 fields) + `PATCH /api/settings/expense` |
| 2 | `settings/general/page.tsx:50` | `PUT /api/settings` (brandColor, documentFooterText, currency overwritten unconditionally) + `PATCH /api/settings/tax` |
| 3 | `settings/batch-billing/page.tsx:37` | `PATCH /api/settings/batch-billing` (3 fields) |
| 4 | `settings/request-settings/page.tsx:39` | `PATCH /api/settings/request-reminders` |
| 5 | `settings/capacity/page.tsx:23` | `PATCH /api/settings/capacity` |
| 6 | `settings/acceptance/page.tsx:39` | `PATCH /api/settings/acceptance` |
| 7 | `settings/tax/page.tsx:31` | `PATCH /api/settings/tax` (the `:36` tax-rate LIST catch is display-only — leave it) |
| 8 | `settings/project-naming/page.tsx:35` | `PUT /api/settings` (reduced risk — action re-fetches first; fix anyway for consistency) |

**Out of scope** (deliberately): category-b display-only fallbacks (customers/[id], projects/[id], dashboard, invoices, audit-log, pipeline, disbursements, portal) — a swallowed error there degrades a read-only display and cannot corrupt data. Do not touch them in this PR.

## The fix (three parts, one PR)

### 1. Remove the swallowing catch in the 8 pages
For each page: delete the `.catch(() => null)`, the null-guard, and the local defaults object; fetch directly:

```ts
const settings = await api.get<OrgSettings>("/api/settings");
```

Keep the per-field `?? default` fallbacks ONLY where the response field is genuinely nullable in the DTO (e.g. `timeReminderTime` may be null on a fresh org) — those are legitimate value defaults, not error masking. Remove `??` fallbacks whose left side is non-nullable in `SettingsResponse`.

### 2. Add the missing settings error boundary
There is NO `error.tsx` anywhere above the settings routes (verified: only `customers/`, `customers/[id]/`, `invoices/`, `projects/` have one). Add:

- `frontend/app/(app)/org/[slug]/settings/error.tsx`

Mirror the existing `customers/error.tsx` precedent exactly (client component, `reset()` retry button, same copy style). This makes the propagate-on-failure pattern degrade gracefully for ALL settings pages, including the already-fixed collections page.

### 3. Regression tests (currently zero coverage of the bug class)
- One test per fixed page is overkill; add page-level tests for TWO representatives (time-tracking — group-PATCH flavor; general — PUT flavor) asserting: a rejected `api.get("/api/settings")` propagates (page render throws) and does NOT render an editable defaulted form. Mock `lib/api` per the existing test conventions.
- One test for the new `settings/error.tsx` (renders message + retry calls `reset`), mirroring whatever test exists for `customers/error.tsx` (if none, model on component-test conventions).

## Execution plan (epic_v2-style, single PR)

1. Worktree `worktree-fix-settings-fetch` off main; branch `fix/settings-fetch-silent-defaults`.
2. Builder implements parts 1–3. Frontend-only. Gates: `pnpm lint && pnpm build && pnpm test` + `format:check` (CI runs prettier separately).
3. Watch for test fallout: existing component tests mock forms directly and should be unaffected; any page test that relied on the null path must be updated deliberately, not deleted.
4. Standard review cycle (two reviewer agents + CodeRabbit) + audit trail + merge gate. No backend changes → Backend CI will be skipped by the path filter; frontend CI + local frontend gates are the bar.
5. Estimated size: ~10 files (8 page edits + 1 error.tsx + 1–3 test files), all mechanical except the error boundary.

## Verification checklist (before claiming done)

- [ ] `grep -n "catch(() => null)" frontend/app/(app)/org/[slug]/settings/` returns ONLY category-b/display call sites (tax-rate list), none feeding editable forms
- [ ] Each of the 8 pages renders correctly against a healthy backend (manual spot-check of 2–3 via the mock-auth stack on :3001)
- [ ] Simulated fetch failure (block the API) on one settings page shows the new error boundary with working retry — observed in the browser, not inferred
- [ ] Full frontend suite green; no `?? default` removed for a genuinely-nullable DTO field (cross-check `SettingsResponse` nullability)

## Risk notes

- The `?? default` cleanup (step 1, second paragraph) is where subtle mistakes can happen — if unsure whether a DTO field is nullable, KEEP the `??` (harmless) rather than risk a runtime crash on a legitimate null.
- `settings/general` PUT also overwrites branding fields unconditionally; the fix removes the silent-defaults trigger, but the unconditional-overwrite server semantics themselves are pre-existing product behavior — do NOT change backend semantics in this PR.
