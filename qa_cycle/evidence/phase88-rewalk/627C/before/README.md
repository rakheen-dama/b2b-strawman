# Slice 627C — before (pre-fix) reproduction

Captured 2026-10-28 ~07:32–07:36Z, E2E mock-auth stack (:3001 / :8081), Playwright MCP browser. No source changed.

## Stack provenance
- `e2e-backend` / `e2e-frontend` images built 2026-10-28T07:21Z by compose with working_dir
  `/Users/rakheendama/Projects/2026/worktree-epic-627B/compose` (the 627B branch worktree, since removed; 627B head fa0225e72,
  squash-merged as f78b041f6). Containers started 07:21Z (fresh data; depth-ii run `mvrsgjlw9b22` already executed against it).
- Tenant `tenant_7d218705360b` (org `e2e-test-org`). Flyway max version **8** (`phase88 retention comment attachments`), so 627B V8 is applied.
  `projects.retention_ends_on`, `projects.retention_clock_started_at` and `org_settings.legal_matter_retention_years` all exist.

## Matters used
| id | name | state at start | state at end | action by this reproducer |
|---|---|---|---|---|
| 5f760d69-07b4-4745-9867-6d14aad3940e | Ndlovu v RAF — Depth II mvrsgjlw9b22 | CLOSED, ends_on 2031-10-28 | unchanged | viewed only |
| b6548bbe-1052-44d9-8b4f-09034a23fa51 | Apex Holdings — Shareholders' Agreement Review mvrsgjlw9b22 (= depth-ii `cleanMatterName()`, the fixme test's matter) | CLOSED, ends_on 2031-10-28 | **COMPLETED** | Reopen, then Complete Matter (UI, Alice) |
| bafe73ff-9ea6-4648-9793-f47589aaa329 | Ndlovu — transition repro mvrsgjlw9b22 | ACTIVE, no tasks | **COMPLETED** | Complete Matter (UI, Alice) |

## R1. CLOSED matter retention card: reproduced (shows persisted date, no firm-default note)
Matter 5f760d69 (and b6548bbe before reopen, same text). `data-state="active"`, visible text:
```
Retention period
Clock started on 28 Oct 2026. This closed matter will be permanently deleted on 28 Oct 2031 (1826 days remaining).
View data-protection settings
```
- SQL `retention_ends_on = 2031-10-28`. Read-only `GET /api/matters/5f760d69…` returns `retentionEndsOn: "2031-10-28"`, `retentionClockStartedAt: "2026-10-28T07:26:18.614637Z"`.
- The date is the **persisted** column. `MatterResponse` maps `project.getRetentionEndsOn()`, and `retention-card.tsx` only formats `retentionEndsOn` without computing anything.
- **No "(firm default 5 years)" note** and no mention of the retention period length anywhere on the card.
- The link goes to `/org/e2e-test-org/settings/data-protection`, which redirects to `/settings/firm?tab=data-protection`. That page has no retention-years field.
- Note: the b6548bbe card text was captured by DOM evaluation only, with no screenshot, before it was reopened in R3. Screenshots 01/02 are of 5f760d69.
- Screenshots: `01-closed-matter-5f760d69-overview.png`, `02-closed-matter-5f760d69-retention-card.png`.

## R2. COMPLETED-not-closed and ACTIVE cards: reproduced
- ACTIVE (bafe73ff before completion): **no retention card is rendered** (`RetentionCard` returns null for non-COMPLETED/CLOSED). Screenshot `03-active-matter-bafe73ff-no-card.png`.
- COMPLETED, never closed (bafe73ff after UI "Complete Matter" → confirm "Complete"): SQL `completed_at` is set and `retention_clock_started_at`/`retention_ends_on` are both NULL. This confirms 627B: completion no longer starts the clock. The card shows `data-state="pre-clock"`:
  ```
  Retention period
  Retention clock not yet stamped. Close this matter from the actions menu to start the retention period (5 years by default; your firm's setting overrides).
  ```
  No date is shown, which matches the 627C target. The copy says "from the actions menu", but Close Matter is now a header button (627A). It is not in the More actions menu.
  Screenshots `04-completed-matter-bafe73ff-overview.png`, `05-completed-matter-bafe73ff-retention-card.png`.

## R3. Reopen → Complete ("Anchor preview" oddity): reproduced
Matter b6548bbe, Alice:
1. Before: CLOSED, `closed_at 07:26:12`, `retention_clock_started_at 07:26:12.897322`, `retention_ends_on 2031-10-28`.
2. Header "Reopen Matter" opens the "Reopen matter" dialog (notes ≥10 chars). Submitting gives ACTIVE, with `closed_at` NULL, `retention_clock_started_at` **kept** (07:26:12.897322) and `retention_ends_on` **cleared** (NULL). The card is not rendered. Screenshot `06-…`.
   (Reopen is a header button, not an overflow item. The "More actions" menu has Save as Template / Edit / Archive / Delete.)
3. "Complete Matter" → "Complete" gives COMPLETED, `completed_at 07:34:25`, clock still 07:26:12, `retention_ends_on` NULL. Card `data-state="completed-pending-close"`:
   ```
   Retention period
   Retention will begin when this matter moves to Closed. Anchor preview: 28 Oct 2026. Default retention period is 5 years; your firm's setting overrides.
   ```
   The "Anchor preview" date is the **previous close date** (the kept `retention_clock_started_at`). It is not a real anchor: the next close re-stamps the clock (627B), so the preview is misleading. API: `retentionClockStartedAt "2026-10-28T07:26:12.897322Z"`, `retentionEndsOn null`.
   Screenshots `07-reopened-completed-b6548bbe-overview.png`, `08-reopened-completed-b6548bbe-anchor-preview-card.png`.

## R4. Firm settings › Compliance: reproduced (no retention-years field)
URL: `http://localhost:3001/org/e2e-test-org/settings/firm?tab=compliance` (tab `adminOnly: true` in `lib/settings-nav.ts`).
- Alice (owner) sees heading "Compliance" ("Manage retention policies, dormancy thresholds, and data request deadlines.") with three sections:
  - **General Settings** has two fields, *Dormancy Threshold (days)* (365) and *Data Request Deadline (days)* (30), plus a "Save Settings" button.
  - **Retention Policies** is a table (Record type / Trigger event / Retention days / Action / Active / Actions) with 2 rows: CUSTOMER / CUSTOMER_OFFBOARDED / 1825 / FLAG / on, and AUDIT_EVENT / RECORD_CREATED / 2555 / FLAG / on. It has Add Row, Save/Delete per row and Run Retention Check. There is no MATTER row, as expected after 627B.
  - **Compliance Packs** lists generic-onboarding, legal-za-individual-onboarding, legal-za-trust-onboarding, sa-fica-company and sa-fica-individual (all v1.0.0).
  - **No "Matter retention period (years)" field** anywhere on the page. No frontend code references `legalMatterRetentionYears` apart from doc comments.
  - Screenshot `09-alice-firm-settings-compliance.png`.
- Bob (admin; the sidebar shows "Bob Admin", bob@e2e-test.local) sees **identical** Compliance content, with the same fields, all enabled. Screenshot `11-bob-firm-settings-compliance.png`.
  **Gating note for 627C:** the Compliance tab is admin+owner, but `PATCH /api/settings/data-protection` (legalMatterRetentionYears) is **owner-only**. The new field needs owner-only edit, or read-only for admin.
- Data protection (`?tab=data-protection`, adminOnly). Alice and Bob see the same:
  - Jurisdiction select ("Select a jurisdiction", unset).
  - Information Officer: Name and Email inputs, plus a Save button (disabled while empty).
  - Data Subject Access Requests link.
  - Retention Policies table: entity type / retention period (days) / action Anonymize|Flag / enabled toggle / last evaluated "Never" / Save per row, for CUSTOMER 1825 and AUDIT_EVENT 2555. Also Run Retention Check and Execute Purge.
  - Processing Register (empty), with Export Register and Add Activity.
  - PAIA Manual, with Generate disabled ("Jurisdiction required").
  - No retention-years field. Screenshots `10-alice-…-data-protection.png`, `12-bob-…-data-protection.png`.

## R5. `org_settings.legal_matter_retention_years`: reproduced = 5
SQL gives `5`. Read-only `GET /api/settings` (mock-IDP token for Alice) returns `legalMatterRetentionYears: 5`.

## R6. Fixme'd test (static comparison only, not run)
`frontend/e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts:313–321`:
```ts
test.fixme("Alice: Closed matter's retention card shows the deletion date (closure + 5 years)", async ({ page }) => {
  await loginAs(page, "alice");
  await openMatter(page, cleanMatterName(), { closed: true });
  const retention = page.getByTestId("retention-card");
  await expect(retention).toHaveAttribute("data-state", "active", { timeout: 15_000 });
  await expect(retention).toContainText(/permanently deleted on/);
  await expect(retention).toContainText(String(new Date().getFullYear() + 5));
});
```
`cleanMatterName()` is `Apex Holdings — Shareholders' Agreement Review <runId>`, which is matter b6548bbe. Before this reproducer reopened it, its card was `data-state="active"` with "…permanently deleted on 28 Oct 2031…". **All three assertions would already pass statically** on the 627B build (2026 + 5 = 2031).
The FIXME comment above it (lines 301–312) is **stale**. It describes `data-state="unconfigured"`, NULL `legal_matter_retention_years`, no settings endpoint and `retentionEndsOn = null`, and 627B fixed all of these. Un-fixme'ing now needs only the comment removed. It does not depend on the 627C card changes, but it pins neither the "(firm default 5 years)" note nor the settings field.

## Other observations
- `GET /api/matters/{id}` for the CLOSED matter returns `closedAt: null` while SQL `closed_at` is set. The field is probably not part of `MatterResponse`. Not in 627C scope; recorded only.
- `GET /api/settings` returns `dormancyThresholdDays: null` / `dataRequestDeadlineDays: null`, while the Compliance form shows 365 / 30 (UI defaults). Not in scope.
- The `retention-card.tsx` doc comments and the `lib/types/matter.ts` comment still describe `retentionEndsOn` as "server-computed … null when years not configured". The "unconfigured" state B is effectively unreachable now, because years always resolves to an effective value.
