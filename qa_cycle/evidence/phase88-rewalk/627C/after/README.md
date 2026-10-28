# Slice 627C — after (PR #1695) browser observation

Captured 2026-10-28 07:57–08:05Z on the E2E mock-auth stack (:3001 / :8081), using a Playwright MCP browser (Chromium) at 1280×900 unless stated. No source was changed and nothing was committed.

## Build provenance
- The frontend image was built from worktree `/Users/rakheendama/Projects/2026/worktree-epic-627C` at HEAD **896505d76246ff52cb83e983955cb355c41b0f58** (PR #1695 head).
  Command: `docker compose -f compose/docker-compose.e2e.yml build frontend`, exit 0 (log `/tmp/627C/obs-build.log`). Then `up -d --no-deps frontend`.
  Container `e2e-frontend` was created 2026-10-28T07:57:20Z, image `sha256:63d5759a2774…`. Root `.dockerignore` excludes `frontend/.next` and `node_modules`, so the concurrent orchestrator gate in the same worktree could not leak into the build context.
- Backend was **not** rebuilt. It still runs the 627B image (see `../before/README.md`), and data was not reset.
- New code is live: Firm › Compliance shows "Matter retention period (years)", and the card shows "Scheduled deletion: …".

## Pre-state (07:57Z)
5f760d69 CLOSED ends 2031-10-28. b6548bbe COMPLETED, clock 07:26:12, ends NULL. bafe73ff COMPLETED, never closed. `legal_matter_retention_years` = 5. Full SQL is in `sql.txt`.

## O1 — CLOSED 5f760d69 card: **PASS**
`data-state="active"`, visible text:
```
Retention period
Scheduled deletion: 28 Oct 2031 (firm default 5 years)
Clock started on 28 Oct 2026. This closed matter will be permanently deleted on 28 Oct 2031 (1826 days remaining).
View retention settings
```
Clicking "View retention settings" landed on `/org/e2e-test-org/settings/firm?tab=compliance`, title "Firm — Compliance · Settings | Kazi". Screenshots `01`, `02`, `03`.

## O2 — COMPLETED cards (stale clock / never closed): **PASS**
- b6548bbe (stale `retention_clock_started_at` 07:26:12) is `data-state="completed-pending-close"`: "Retention starts when this matter is closed. Closed matters are kept for **5 years** (firm default) and then permanently deleted." There is no date, no "Anchor preview" anywhere on the page, and no link. Screenshots `04`, `05`.
- bafe73ff (never closed) shows the identical text, with no link and no Anchor preview. Screenshot `06`.

## O3 — Close b6548bbe: **PASS**
Steps: header "Close Matter", then the gate report showed 9 green gates, then "Continue". Reason was left at the default "Concluded", closure letter + SoA kept at their defaults (checked), portal share/email unchecked, then "Close matter". The header went to "Closed".
The card reads "Scheduled deletion: 28 Oct 2031 (firm default 5 years)", "Clock started on 28 Oct 2026 … 28 Oct 2031 (1826 days remaining)". Screenshots `07`, `08`.
SQL: `CLOSED | closed_at 07:58:24.420225 | clock 07:58:24.420225 | ends 2031-10-28` (today UTC 2026-10-28 + 5 years).

## O4 — Reopen / re-close: **PASS**
- Header "Reopen Matter" opened the "Reopen matter" dialog (notes ≥ 10 chars), then submit. The header went to "Active", and **no retention card is rendered**. SQL: `ACTIVE | closed_at NULL | clock 07:58:24.420225 (kept) | ends NULL`. Screenshots `09`, `10`.
- Re-close went directly from ACTIVE through the header "Close Matter" → Continue → Close matter. The card shows "28 Oct 2031 (firm default 5 years)". SQL: `CLOSED | closed_at 07:59:19.529302 | clock **07:59:19.529302** (re-stamped) | ends 2031-10-28`. Screenshot `11`.

## O5 — Firm › Compliance as Alice (owner): **PASS**
- The page subtitle is "Manage the matter retention period, retention policies, dormancy thresholds, and data request deadlines."
- Section order (h2): **Matter retention**, General Settings, Retention Policies, Compliance Packs.
- The field "Matter retention period (years)" shows `5`. Help text: "Closed matters are permanently deleted this many years after the close date. Default 5 years. A change applies to matters closed from now on — matters already closed keep their scheduled deletion date."
- Invalid input, each followed by "Save retention period". The inline `role=alert` error appeared each time, with `aria-invalid=true`:
  - `0` gives "Retention period must be between 1 and 30 years." (screenshot `12`)
  - `31` gives "Retention period must be between 1 and 30 years."
  - `2.5` gives "Retention period must be a whole number of years." (screenshot `13`)
  - empty gives "Enter the number of years."
- **No save was sent:**
  - In the browser network log, the only server-action POSTs before the valid save were #36 and #80. Both have `next-action 0026968803…` with body `[]`; this is the periodic zero-arg action that also fires at page load.
  - The retention update action (`next-action 60d27cbcc4…`) fired exactly once, with body `["e2e-test-org",7]`.
  - The backend log has exactly two "Updated data protection settings" lines in the window, at 08:00:05 (7) and 08:03:41 (5, restore).
- Entering `7` and clicking "Save retention period" showed green "Matter retention period updated." (screenshot `14`). After a reload the field shows `7`. SQL `legal_matter_retention_years = 7`.
- Audit: `data.protection.settings.updated | org_settings | actor Alice | 08:00:05.470268Z | {"org_role":"owner","actor_name":"Alice Owner","jurisdiction":"","retention_enabled":false,"legal_matter_retention_years":7}`.

## O6 — Behaviour with years = 7: **PASS**
- CLOSED 5f760d69 reads "Scheduled deletion: 28 Oct 2031 **(5-year retention period)**". The date is frozen at 2031-10-28 and the card says "firm default" nowhere. Screenshot `15`.
- COMPLETED bafe73ff reads "… Closed matters are kept for **7 years** and then permanently deleted." There is no "(firm default)". Screenshot `16`.
- Closing bafe73ff was **blocked by gates**: "1 approved disbursements are unbilled." and "No final bill issued. 0h of unbilled time + 1 unbilled approved disbursements remain." I cancelled rather than use an owner override (screenshot `17`). Instead I reopened b6548bbe (notes entered) and re-closed it, with the same Concluded defaults. The card reads "Scheduled deletion: **28 Oct 2033 (7-year retention period)**" and "… (2557 days remaining)". Screenshot `18`. SQL: `CLOSED | closed_at 08:01:27.425037 | clock 08:01:27.425037 | ends 2033-10-28`.

## O7 — Roles: **PASS** (one minor pre-existing-class note)
- **Bob (admin)** on Compliance:
  - The field shows `7` and is **disabled**. "Save retention period" is **disabled**. The note "Only the firm owner can change the matter retention period." is shown.
  - The other sections stay editable: Dormancy / Data Request Deadline inputs, "Save Settings", "Add Row", policy rows' Retention days / Action / Active / Save / Delete, "Run Retention Check" and the pack buttons are all enabled. Record-type and trigger selects are disabled, as before.
  - Screenshot `19`. Bob on 5f760d69 sees the same card as Alice: "28 Oct 2031 (5-year retention period)".
- **Carol (member)**:
  - `?tab=compliance` shows only the General and Subscription tabs, with the banner "You don't have access to Compliance — showing General instead." No retention field is shown. Screenshot `20`.
  - On 5f760d69 she is not on the team, so the page shows "This matter isn't on your list …" and no card.
  - To observe the card for a member, I (Alice) **temporarily added Carol** to 5f760d69's team (Details · Team → Add Member). As Carol, the card rendered `active`: "Scheduled deletion: 28 Oct 2031 (5-year retention period)" plus the clock sentence and the "View retention settings" link (screenshot `21`). `GET /api/settings` is ungated, so members resolve firm years.
  - Clicking that link as Carol lands on "You don't have access to Compliance — showing General instead." (screenshot `22`). See defect D1.
  - Carol was then removed (Alice → row menu → Remove). SQL confirms the team is Alice only.

## O8 — Restore: **PASS**
- Alice set the value to `5`, then "Save retention period", and got "Matter retention period updated.".
- SQL `legal_matter_retention_years = 5`. Audit has a second `data.protection.settings.updated` row at 08:03:41.82796Z with years 5 by Alice Owner.
- After the restore, the b6548bbe card still reads "28 Oct 2033 (7-year retention period)". This is correct, because the date is frozen.

Final states:
| id | status | closed_at | retention_clock_started_at | retention_ends_on |
|---|---|---|---|---|
| 5f760d69-07b4-4745-9867-6d14aad3940e | CLOSED | 07:26:18.614637Z | 07:26:18.614637 | 2031-10-28 |
| b6548bbe-1052-44d9-8b4f-09034a23fa51 | CLOSED | 08:01:27.425037Z | 08:01:27.425037 | **2033-10-28** (closed under 7 years) |
| bafe73ff-9ea6-4648-9793-f47589aaa329 | COMPLETED | — | NULL | NULL |

b6548bbe was CLOSED in the original depth-ii run, so the depth-ii `cleanMatterName()` closed-matter test still finds it closed. Its date is now 2033 instead of 2031, so any assertion of `getFullYear()+5` against this matter on this stack would now fail. Reset the stack (e2e-down/up) before a gate-grade depth-ii run.

## O9 — Layout at 1280 / 1024: **PASS for 627C** (pre-existing overflow noted)
- The new "Matter retention" section has the same classes and computed styles as its siblings: `rounded-lg border border-slate-200 bg-white p-6`, 24px padding, 8px radius, h2 18px/600, and the same 781px width / x offset as General Settings and Compliance Packs. Its own content does not overflow (`scrollWidth == clientWidth`). Screenshots `23`, `24` (1280), `25`, `26` (1024).
- The retention card does not overflow at either width. At 1024 it is 656px wide inside the viewport, and the document `scrollWidth` is 1024. At 1280 it is 912px wide and the document is 1280. Screenshots `27`, `28`.
- **Pre-existing** (D2): the Compliance tab document is 1316px wide at both 1280 and 1024, which forces horizontal scroll.
  - Hiding the new Matter retention section leaves 1316. Hiding the (unchanged) Retention Policies section gives 1280 / 1024.
  - So the Retention Policies table section sets the min width. At 1024 every section, including the new one, is clipped on the right; the input and Save button stay visible.
  - That section is unchanged by this PR: the `compliance.tsx` diff only adds the new form and renumbers comments.

## Defects / notes
- **D1 (minor, UX):** the CLOSED-matter card shows "View retention settings" to members. The target `firm?tab=compliance` is admin-only, so the link lands on "You don't have access to Compliance — showing General instead.". The pre-627C link (`/settings/data-protection`, also adminOnly) had the same problem, so this is not a regression. Possible follow-up: hide the link for non-admins.
- **D2 (pre-existing, layout):** the Retention Policies table section forces the Compliance tab to 1316px. This causes horizontal scroll at ≤1280 and clips all sections at 1024.
- **Note:** the data-protection PATCH merges nulls (backend code reading, `OrgSettingsService` ~L1150). It could not be shown by observation here, because jurisdiction and information officer were already null/blank before and after. The audit row records `jurisdiction: ""`, `retention_enabled: false`, which are the stored values.
- **Note:** bafe73ff cannot be closed without an override (unbilled disbursement / no final bill). This is seeded-data state and not a 627C issue.

## Files
Screenshots `01`–`28` (see names), `sql.txt` (SQL after each step plus the backend log lines). Playwright MCP snapshot files created by this run were deleted from `.playwright-mcp/` (62 files; nothing else touched).
