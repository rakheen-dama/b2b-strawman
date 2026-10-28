# 627B (G-D3, matter retention) — reproduction BEFORE fix

- Date: 2026-10-28, ~06:03–06:11 UTC
- Stack: E2E mock-auth (:3001 frontend / :8081 backend / `e2e-postgres` db `app`), product = main `f469434d7`. Not rebuilt / reseeded.
- Tenant: `tenant_7d218705360b` (org `e2e-test-org`), user Alice (owner) via `/mock-login`.
- All state changes driven through the UI (Playwright). DB access = read-only SELECTs only (`sql.txt`).
- No code changes, nothing committed.

## Matters used

| Matter | id | Use |
|--------|----|-----|
| **627B retention repro** (new; client "Sipho Ndlovu mvrovhe0b72d" `3c1891ee-…`, template Commercial) | `7c28d1cf-6fc5-428a-9690-054f49ae7370` | Created via New Matter UI 06:04:46Z → 9 template tasks Cancelled via task-detail status UI → **Complete Matter** 06:06:30Z → **Close matter** 06:07:11Z (gate FINAL_BILL "No final bill issued" failing → Override + justification) → **Reopen** 06:08:21Z → **Close** again 06:08:47Z (override again) |
| Apex Holdings — Shareholders' Agreement Review mvrovhe0b72d (pre-existing, closed by depth-ii suite) | `68b5e1e8-ece3-479e-88e9-ffdd7b38b364` | Read-only: card already "unconfigured" (01-*.png); its closure at 05:45:57.179Z is the first-ever closure on this tenant |

## Claim table

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| main-1 | Closure response shows a retention date | **REPRODUCED (response body) — but not shown in UI** | `closure-response.json`: server-action result `retentionEndsAt: "2031-10-28"` (= closedAt 2026-10-28 + effective 5y default). The close dialog just closes; no toast/dialog text shows the date. `retentionEndsAt` is typed in `frontend/lib/api/matter-closure.ts:48` and consumed nowhere else in the frontend. |
| main-2 | Overview card says "isn't configured" | **REPRODUCED** | `05-*.png`, `08-*.png`, `01-*.png`: `[data-testid=retention-card][data-state=unconfigured]` text "Retention clock started on 28 Oct 2026. Your firm's matter-retention period isn't configured yet, so the scheduled deletion date can't be computed. Configure retention period →". The page's MatterResponse (same RSC payload) has `retentionEndsOn: null`. |
| main-3 | `legal_matter_retention_years` is NULL | **REPRODUCED** | `sql.txt`: `select legal_matter_retention_years from org_settings` → empty (NULL), before and after all actions. |
| main-4 | Firm › Compliance has no years field | **REPRODUCED** (also on Data protection) | `09-*.png` Compliance tab inputs: Dormancy Threshold (days), Data Request Deadline (days), retention-policy rows (days). `10-*.png` Data protection tab: jurisdiction, information officer, retention policies (days). No "retention period (years)" anywhere. The card's "Configure retention period →" link (`/settings/data-protection` → 308 → `/settings/firm?tab=data-protection`) lands on a page with no such field (`11-*.png`). Frontend grep: `legalMatterRetentionYears` appears only in `components/legal/retention-card.tsx` + `lib/types/matter.ts`; backend `DataProtectionSettings.setLegalMatterRetentionYears` has no caller in `backend/src/main/java`. |
| (a) | COMPLETED already starts the retention clock | **REPRODUCED** | After UI Complete: `status=COMPLETED, completed_at=06:06:30.458388, closed_at=NULL, retention_clock_started_at=06:06:30.458388` (identical to completed_at). After close, `retention_clock_started_at` stays 06:06:30.458 (not closed_at 06:07:11). Card in COMPLETED state: "Retention will begin when this matter moves to Closed. Anchor preview: 28 Oct 2026" (`03-*.png`) — copy says retention begins at Closed, but the DB clock is already stamped at completion. |
| (b) | Closing inserts a firm-wide `retention_policies` MATTER row if absent | **REPRODUCED (by timestamp; row pre-existed my run)** | Row `8334a16d… MATTER / MATTER_CLOSED / ARCHIVE / 1825 days` `created_at 05:45:57.171219Z`, 8 ms before the tenant's first-ever closure (matter `68b5e1e8` `closed_at 05:45:57.179425Z`); tenant provisioned 05:41:17 with only CUSTOMER + AUDIT_EVENT rows. My two closures did not add rows or touch `updated_at` (UNIQUE `(record_type, trigger_event)`; code `MatterClosureService.ensureMatterRetentionPolicy` → `insertIfAbsent`). I did not observe the insert on an empty table myself (would need a fresh tenant). |
| (c) | No persisted per-matter retention end date | **REPRODUCED** | `projects` has only `retention_clock_started_at` among retention columns; no column matching `%retention_end%` in any tenant table. `retentionEndsOn` is computed on read (`ProjectService.computeRetentionEndsOn`). |
| (d) | Re-close after reopen keeps first `retention_clock_started_at` | **REPRODUCED** | Reopen → `ACTIVE, closed_at NULL, completed_at 06:06:30 (retained), retention_clock_started_at 06:06:30.458388`. Re-close → `CLOSED, closed_at 06:08:47.929, retention_clock_started_at 06:06:30.458388` (unchanged). Two `matter_closure_log` rows (b43e4939 reopened 06:08:21; 74a6e7cd open). MATTER policy row untouched (active, cancelled_at NULL) across reopen. Card after re-close still "unconfigured" (`08-*.png`). ACTIVE after reopen → no retention card (`06-*.png`). |

## Root of the mismatch (observed + source)
- Closure response: `MatterClosureService` (~L319–328) uses `getEffectiveLegalMatterRetentionYears()` (NULL → `DEFAULT_LEGAL_MATTER_RETENTION_YEARS = 5`) and anchors on **`closedAt`**: `closedAt.toLocalDate().plusYears(5)` → `2031-10-28`.
- Card: reads `MatterResponse.retentionEndsOn` from `ProjectService.computeRetentionEndsOn`, which uses `getRawLegalMatterRetentionYears()` (NULL → Optional.empty → `null`) and anchors on **`retentionClockStartedAt`** (completion time).
- So the two computations differ both in years source (effective vs raw) and anchor (closedAt vs retention clock). They coincide in date here only because complete and close happened the same day.
- The MATTER `retention_policies` row (1825 days) is seeded from the same effective default, so the firm already has a 5-year matter policy in `retention_policies` while the card says the period "isn't configured".

## Other surprises (not in the claim list — report, don't fix)
1. **Compliance tab misrenders the MATTER policy row** (`09-*.png`): `RetentionPolicyTable` option lists are `RECORD_TYPES = [CUSTOMER, AUDIT_EVENT, DOCUMENT, COMMENT]`, `TRIGGER_EVENTS = [CUSTOMER_OFFBOARDED, RECORD_CREATED]`, `ACTIONS = [FLAG, ANONYMIZE]` (`frontend/components/compliance/RetentionPolicyTable.tsx:26-28`), so the MATTER/MATTER_CLOSED/ARCHIVE row displays as CUSTOMER / CUSTOMER_OFFBOARDED / FLAG with 1825. Pressing that row's Save would plausibly overwrite it (not tested — would mutate data).
2. **Data protection tab shows MATTER action as "Anonymize"** while DB says `ARCHIVE` (`ACTIONS = ["ANONYMIZE","FLAG"]` in `components/data-protection/retention-policies-table.tsx:23`) (`10-*.png`).
3. Closure gates: a fresh matter with 0h / 0 disbursements still fails "No final bill issued" — closing needs the override. Complete Matter is blocked until all template tasks are done/cancelled.
4. Reopen keeps `completed_at` set while status returns to ACTIVE.
5. Page `<title>` reads "Firm — Audit trail · Settings" while on the Compliance tab and (on tab-click) Data protection tab; correct title when landing directly via URL.
6. The re-close server-action response body could not be retrieved from Playwright (empty body returned for that request); re-close state is evidenced by DB only.

## Files
- `sql.txt` — every SELECT with verbatim output, timestamped per phase (before / after complete / after close / after reopen / after re-close)
- `closure-response.json` — first close server-action result + the page's project DTO fields from the same payload
- `network-114-close-action-rsc-payload.txt` — full RSC payload of the close action (raw)
- `migrations.md` — dev + e2e tenant Flyway versions; next free tenant version = **V8**
- `01`–`11` screenshots
