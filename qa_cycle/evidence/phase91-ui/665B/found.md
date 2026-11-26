# 665B: out-of-scope or pre-existing defects found during OBS-before (reported, not fixed; root CLAUDE.md §7)

Everything here is pre-existing on `main` @ `60f67bfe3`. 665B restyles these tabs frame-only. None of these is fixed in this capture.

## F-665B-1: Data protection › Retention Policies shows a stored "delete" action as "Anonymize"; touching that select stores ANONYMIZE

- **Where:**
  - `frontend/components/data-protection/retention-policies-table.tsx:24`: `ACTIONS = ["ANONYMIZE", "FLAG"]`. The row's action is a controlled `<select value={row.action}>` (`:237-247`).
  - `backend/.../retention/RetentionService.java:293-296`: the ZA jurisdiction seed writes `"delete"` (lower case) for TIME_ENTRY, DOCUMENT and COMMENT. `:298` writes `delete` for AUDIT_EVENT too, unless one already exists.
- **Observed (`before/saves.json` → `retentionSelects`, `before/discover-pre.json` → `retentionPolicies`):**
  - `GET /api/settings/retention-policies` returns `TIME_ENTRY:1800:delete`, `DOCUMENT:1800:delete` and `COMMENT:1080:delete`.
  - On the tab, each of those rows shows **"Anonymize"**, and the select's DOM `value` is `ANONYMIZE`. The browser falls back to the first option when no option matches.
  - Crop: `before/crop-defect-retention-delete-as-anonymize.png`.
- **Consequence (from the code; not executed, because it would change the stack):** `saveRow` sends `row.action` (`:131-135`). The local state still holds `"delete"`, so an untouched row saves back correctly. But as soon as the user touches that row's select (even re-picking the shown "Anonymize"), the save stores `ANONYMIZE`. Either way the page tells the owner that three deleting policies anonymise.
- **Sibling:** F-665A-7 (`../665A/found.md`) is the same defect class on Compliance › Retention Policies. That is a different component, `components/compliance/RetentionPolicyTable.tsx`. The two retention editors are K-105.
- **Suggested owner:** backlog, decided together with F-665A-7. The fix needs a decision: either the frontend lists the backend's action vocabulary (`delete` → "Delete"), or the seed uses the UI's enum. This is behaviour, not a 665B frame change.

## F-665B-2: Audit trail `?page=` past the end says "The audit log is empty" beside "274 total", and the pager reads "Page 100 of 6"

- **Where:** `frontend/app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:902-907` (empty copy chosen by `hasActiveFilters` only) and `:949-955` (pager text `currentPage + 1` of `totalPages`). The loader clamps only the lower bound (`audit-trail.tsx:57`).
- **Observed (`before/audit-paging.json` → `steps.outOfRange`, `before/crop-audit-out-of-range.png`):**
  - `?tab=audit-trail&page=99`, no filters.
  - The Events card says "274 total" and then "The audit log is empty. Activity is logged automatically once team members start working in Kazi."
  - The pager says "Page 100 of 6". Previous is enabled and leads to `page=98`, which is also out of range. Next is disabled.
- **Sibling:** F-664B-3 (other-creditors "Page 10 of 3"), which was kept for parity and put on the backlog.
- **Suggested owner:** backlog. Alternatively 665B, if the kit pager it adopts already handles out-of-range; parity is the default.

## F-665B-3: Retention policies and processing activities have no stable order, so a save moves the row to the bottom

- **Where:**
  - `backend/.../retention/RetentionPolicyService.java:80`: `policyRepository.findByActive(true)` has no ORDER BY.
  - `backend/.../datarequest/ProcessingActivityService.java:25`: `repository.findAll(pageable)`, and the frontend calls `?size=100` with no sort (`data-protection-actions.ts:144`).
- **Observed:** compare `before/discover-pre.json` with `before/discover-post.json`.
  - **Retention policies:** before, `AUDIT_EVENT` was first. After an unchanged row save it is **last**.
  - **Processing activities:** before, "Client Information" was first. After an unchanged Edit › Update it is **last**.
  - The values are identical as sets. Only the order changed. This is PostgreSQL heap order after an UPDATE.
- **Effect:** the row the user just saved jumps to the bottom on the next render. The after-capture's tables will list these rows in the new order. This is not a 665B regression.
- **Suggested owner:** backlog (backend `Sort`). LOW.

## F-665B-4 (HYPOTHESIS, not reproduced): turning a retention policy off on Data protection removes it from the list, with no UI way back

- **Where:** `RetentionPolicyService.listSettingsPolicies()` (`:79-81`) returns `findByActive(true)` only. The Data protection table's "Enabled" switch plus row Save sends `enabled: false` (`retention-policies-table.tsx:131-135`).
- **Expected from the code:** after the save and a refresh, the row is gone from Data protection. Compliance › Retention Policies (K-105) may still show it, because it uses a different endpoint (`/api/retention-policies`).
- **Not exercised:** the only restore would be the Compliance tab or the API, so it was not reversible through this tab. It needs an integration test before anyone acts on it (feedback rule: debt-register entries are hypotheses).
- **Suggested owner:** backlog. Verify first.

## F-665B-5: the Data protection "Jurisdiction" select has no accessible name

- **Where:** `frontend/components/settings/jurisdiction-selector.tsx:98-112`. The `SelectTrigger` has no `aria-label`, no `<label>` and no `aria-labelledby`. The card's h2 "Jurisdiction" is not associated with it.
- **Observed (`before/saves.json` → `jurisdiction`):** `ariaLabel: null`, `labelledBy: null`. The combobox's name is its value, "South Africa (POPIA)". `data-protection-settings.spec.ts:57` finds it by its text for this reason.
- **Suggested owner:** 665B, if its SettingsSection restyle gives section controls a label. Otherwise backlog. LOW (WCAG 4.1.2). A role/name change is SEL-COMPAT relevant: the spec's `getByRole("combobox").filter({ hasText })` keeps working if only `aria-label` is added.

## F-665B-6 (found in OBS-after, introduced by 665B, LOW / a11y nit): the audit "Events" h2's accessible name runs into the total: "Events289 total", and "Events285 totalLoading…" while a page loads

- **Where:** `frontend/app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:939-948`. The `SettingsSection` `title` is `Events<span class="ml-2 …">{n} total</span>{isPending && <span class="ml-2 …">Loading…</span>}`. `ml-2` is visual spacing only, so there is no text separator.
- **Observed (`after/zero-and-names.json`, `after/audit-paging.json` → `extra.pendingCrop.titleInfo`):**
  - h2 `textContent` / `innerText` = "Events289 total".
  - `getByRole("heading", { level: 2, name: /^Events\d/ })` = 1; `/^Events \d/` = 0.
  - During the held page fetch the text is "Events285 totalLoading…".
- **Why now:** before 665B the same markup was a `CardTitle` div (no heading). 665B promoted it to an h2, so it now shows up in the screen-reader heading list with this name. Visually it is fine (`crop-audit-pending.png`).
- **Suggested fix:** a `{" "}` (or an sr-only separator) before each span, or move the total and "Loading…" out of the heading into `description` / `actions`. SEL-COMPAT: no spec reads this heading.
- **Suggested owner:** 665B fix round, if in scope; otherwise backlog. LOW.
- **Resolved** in the 665B review-fix commit (`fix(epic-665B): keep pager focus on audit paging …`): a `{" "}` before the total span and before the "Loading…" span, so the h2 text reads "Events 289 total" / "Events 285 total Loading…"; vitest `audit-trail.test.tsx` pins `textContent` "Events 150 total". No e2e or qa locator reads this heading.

## F-665B-7 (found in OBS-after, residual K-109, LOW): at 390 the Processing Register description is squeezed to 93 px (8 lines) beside the action column

- **Where:** `frontend/components/data-protection/processing-register-table.tsx:~405-420` (`SettingsSection` `actions`) and the kit `CardHeader` grid (`grid-cols-[1fr_auto]`, `packages/ui/src/card.tsx`).
- **Observed (`after/k109-widths.json`, `after/crop-k109-390-light.png`):**
  - K-109's acceptance is met: "Add Activity" ends at x = 349 (≤ 390). The two buttons wrap inside the 207 px action column, and the page has no horizontal overflow at 390.
  - But the title and the description share the remaining 93 px column. "Processing Register" wraps onto 2 lines and the description onto 8.
  - At 1024 the description is 150 px wide (5 lines). The register said ≈ 90 px before, and the before README read ≈ 155 px off the screenshot. At 1280 and 1440 it is 358 and 518 px.
- **Suggested fix (product/layout call):** below a breakpoint, stack the header actions under the title (e.g. a `@container` rule on `CardHeader`, or let the section's `actions` drop to a row of their own). This is a kit-level decision (every `SettingsSection` with two actions behaves this way), so it is not a 665B-local patch.
- **Suggested owner:** backlog / kit owner. LOW.
