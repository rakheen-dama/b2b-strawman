# 634B — AFTER (post-fix) observation

Observed 2026-11-05 10:53–10:56 UTC by a QA observer agent (Playwright MCP, Chromium). No source modified. No rebuild, reseed or teardown. Read-only SQL only. Before-state: `before/OBSERVATION.md`.

## Stack / build

| Item | Value |
|------|-------|
| Stack | E2E mock-auth (frontend :3001, backend :8081, postgres :5433) |
| `docker inspect e2e-backend --format '{{.Created}}'` | `2026-11-05T10:43:05.310695669Z` (started 10:43:12Z) |
| `docker inspect e2e-frontend --format '{{.Created}}'` | `2026-11-05T10:43:06.147961086Z` (started 10:43:27Z) |
| Built from | PR branch at `1c36655ac` (feat(634B): matter-page cluster) |
| Branch HEAD now | `94adbff7b`. It changes backend behaviour only when the trust module is OFF (trust balance returns 0) and makes a Java parameter (`matterId`) nullable. Trust is ON here, so this commit is **not exercised** by this observation. |
| Tenant schema | `tenant_7d218705360b`, org `e2e-test-org` |
| Matter | Website Redesign `5be2a454-fdb3-4e98-a363-6d91390d8f46` (Acme Corp) |

## DB facts (re-queried just before the walk, read-only SELECTs)

```
select coalesce(invoice_number,'(draft)'), status, total, amount_paid, total-amount_paid from invoices
 (draft)  | DRAFT          | 3450.00 |   0.00 | 3450.00   <- excluded (draft)
 INV-0001 | SENT           | 8625.00 |   0.00 | 8625.00
 INV-0002 | PARTIALLY_PAID | 2300.00 | 500.00 | 1800.00
=> expected outstanding = 8625.00 + 1800.00 = R10 425,00

select project_id, transaction_type, status, count(*), sum(amount) from trust_transactions group by 1,2,3
 5be2a454-… | DEPOSIT | RECORDED | 1 | 1500.00
select balance, total_deposits from client_ledger_cards  -> 1500.00 | 1500.00
=> expected matter trust balance = R1 500,00

project_members ⨝ members ⨝ org_roles (this matter)
 Alice Owner | alice@e2e-test.local | lead | Owner        <- only member on the matter
members ⨝ org_roles (tenant)
 Alice Owner Owner | Bob Admin Admin | Carol Member Member

audit_events where details->>'project_id' = matter, grouped by actor_type, source
 PORTAL_CONTACT | PORTAL | 3
 USER           | API    | 15
portal rows:
 portal.document.upload_initiated | PORTAL_CONTACT | PORTAL | 05:36:31
 portal.request_item.submitted    | PORTAL_CONTACT | PORTAL | 05:36:31
 portal.document.downloaded       | PORTAL_CONTACT | PORTAL | 05:36:39

select count(*) from acceptance_requests -> 0
```

The values match the before-state; nothing changed between the two walks.

## Results per gap

### G88-M13: Overview money tiles — PASS (observed at 1024 + 1440)

- Before: `-- Outstanding fee notes`, no trust tile.
- After, logged in as Alice (Owner): four tiles: `0.0h Hours logged`, `0% Task completion`, **`R 1 500,00 Trust balance`**, **`R 10 425,00 Outstanding fee notes`**. Both money values match the DB exactly. The trust value counts the RECORDED deposit.
- Links:
  - Trust tile → `?tab=trust`. Clicking it opened **Fees · Trust**, which shows a "Trust Balance R 1 500,00 · Funds Held" card (Deposits R 1 500,00). The value matches the tile.
  - Outstanding tile → `?tab=statements`. Clicking it opened **Fees · Statements of account**, checked at both widths. This target was already in the code before 634B; the slice only changed the value and visibility. The matter's Fees menu has no separate "fee notes" tab (Time recording, Disbursements, Fee estimate, Tariffs, Financials, Statements of account, Trust), so Statements of account is a reasonable target.
- Overflow: `document.documentElement.scrollWidth === clientWidth` at both widths (1440/1440, 1024/1024). No element in `main` extends past the viewport. No tile or value text is clipped (no `scrollWidth > clientWidth` inside the tiles). At 1024 the tiles are 208px wide and "R 10 425,00" fits.
- Cosmetic note (not a FAIL against the stated criteria): the tile grid is `grid gap-4 md:grid-cols-2 lg:grid-cols-3`. With 4 tiles, the Outstanding tile wraps alone onto a second row, leaving two empty cells, at both widths. At 1024x768 the Outstanding tile is below the fold. This is worth a follow-up to change to `lg:grid-cols-4`, or to accept it.
- Screenshots: `after-1440-01-overview-kpi-tiles.png`, `after-1440-02-outstanding-tile-click-statements.png`, `after-1440-03-trust-tile-click-trust-tab.png`, `after-1024-01-overview-kpi-tiles.png`, `after-1024-02-overview-outstanding-tile-scrolled.png`, `after-1024-03-outstanding-tile-click-statements.png`

### Capability gating (Carol, Member) — NOT TESTABLE on this seed (skipped)

Carol is not a member of this matter; only Alice is in `project_members`. Logged in as Carol at 1024x768 and opened the matter URL. The page showed: "This matter isn't on your list — It may not exist, or you haven't been added to its team. Ask the matter lead or a firm admin to add you." The Overview tiles never render, so I could not observe whether the outstanding tile is hidden or shows "--" for a member. Adding Carol to the matter would change state and was out of scope. The gating is only unit-tested (`kpi-dashboard.test.tsx`, `state !== "absent"`); it is **not browser-observed**.
- Screenshot: `after-1024-06-carol-member-not-on-matter.png`

### G88-M2: Team tab matter-role badge — PASS (observed at 1024 + 1440)

- Before: Role badge "Owner" (amber), which was her org role.
- After: Alice's Role cell shows a **"Lead"** badge (`bg-teal-100 text-teal-700`) followed by **"Owner"** as muted secondary text (`text-xs text-slate-500`). This matches `project_role = lead` and org role Owner. No horizontal overflow at 1024.
- Not observable: the "Member" badge for a non-lead (with "Admin" secondary for Bob). Alice is the only member on this matter, and adding Bob would change state. The non-lead branch has unit-test coverage only.
- Screenshots: `after-1440-04-team-lead-badge.png`, `after-1024-04-team-lead-badge.png`

### G-B8: "Client portal" chip on Activity — PASS (observed at 1024 + 1440)

- Before: 0 occurrences of "portal" in the Activity panel.
- After: 18 activity rows, matching the 18 matter-scoped audit_events. Exactly **3 "Client portal" chips**, one on each of the 3 portal-origin rows:
  - `Acme Corp downloaded document "statement-of-account-website-redesign-2026-11-05.pdf"` — Client portal
  - `Acme Corp submitted "Certified ID copy 44twh" for REQ-0002` — Client portal
  - `Acme Corp started uploading document "id-copy.pdf"` — Client portal
- The 15 USER/API rows (Alice Owner…, plus the two "Information request REQ-000x sent to Acme Corp" rows) carry **no** chip. That gives 3 chips for 3 portal rows (100%) and 0 chips on the 15 firm rows.
- DB confirms all 3 chipped rows have `actor_type='PORTAL_CONTACT'` **and** `source='PORTAL'`, and all 15 others are `USER`/`API`.
- At 1024 the chips are not clipped. On the wrapped "downloaded" row the chip sits right-aligned; on single-line rows it sits inline after the text. This is a minor visual inconsistency only.
- API payload check: the activity data arrives through a Next.js server action (`POST …?tab=activity`, `text/x-component`). Playwright MCP did not capture the response body, so `actorType`/`source` in the payload were **not** verified directly. The rendered chips plus the DB fields are the evidence.
- **Acceptance-row caveat:** the spec's observation names a document-acceptance row. The e2e seed has `acceptance_requests` = 0 rows, and there is no portal container on this stack, so no acceptance row exists to check. The chip depends only on `source === "PORTAL" || actorType === "PORTAL_CONTACT"`, which is the same condition an acceptance row would meet, but **an acceptance row specifically was not observed**.
- Screenshots: `after-1440-05-activity-client-portal-chip.png` (full page), `after-1024-05-activity-client-portal-chip.png` (full page)

## Console / logs

- `browser_console_messages` (level warning, all, whole Alice session: Overview, Statements, Trust, Team and Activity at both widths): **0 errors, 0 warnings**.
- Carol session (matter page): 0 errors, 0 warnings.
- `docker logs e2e-backend --since 10:52Z | grep ERROR|WARN|Exception`: none. `e2e-frontend` error/fail grep: none.

## Out-of-scope note (not 634B, not investigated)

On Fees · Statements of account, the single generated statement (5 Nov 2026) shows **Closing balance owing R 0,00 / Trust balance held R 0,00**. According to the activity order, it was generated after INV-0001 was sent. This could be a statement-period/as-of effect or a separate defect. 634B did not touch it, but anyone landing there from the "R 10 425,00 outstanding" tile will see R 0,00, which could confuse them. Flagging for triage.

## Housekeeping

Screenshots were saved under `b2b-strawman/.playwright-mcp/634B-after/` (gitignored) and moved here with `mv`. That directory and this session's snapshot `.yml` files were removed, so nothing was left in the main repo. Main checkout `git status` is unchanged (only the pre-existing `portal/next-env.d.ts` and `tasks/insights/latest.stats.json`). Browser closed.
