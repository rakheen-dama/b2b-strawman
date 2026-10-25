# 625A / task 625.4 — observer evidence (PR #1679, head 4b9c7006e)

Run: 2026-10-25 00:14:10Z (`start_ts`) – 00:33Z. Observer only: no source changed, no SQL writes. Every action was driven in the browser (Playwright MCP) on :3001 (mock auth) and :3002 (portal).

## Build info
- Worktree `/Users/rakheendama/Projects/2026/worktree-epic-625A` @ `4b9c7006e`. `git status --short` was clean before and after.
- `bash compose/scripts/e2e-rebuild.sh backend` ran from the worktree (exit 0, `rebuild.log`).
  - New image `sha256:07937f3a39ed…`, created `2026-10-25T00:20:42Z`. Container `e2e-backend` created `00:20:44Z`.
  - The previous container was created `2026-10-24T22:50:39Z`.
  - `/app/app.jar` contains `io/b2mash/b2b/b2bstrawman/audit/AuditDetails.class`, which is new in this PR.
  - `/actuator/health` is UP.
  - Side effect: compose also recreated `e2e-localstack`, so S3 was empty afterwards. New documents were unaffected.
- Portal: the KC portal was stopped and the worktree portal was built with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081` (`portal-build.log`). It was started with `pnpm run start` on :3002 (PID 72880) and killed afterwards.
- Tenant: `tenant_7d218705360b`. e2e Mailpit: :8026.

## Ids
| Item | Id |
|---|---|
| Estate matter (reproducer's) "Estate Late Ndlovu 625A repro" | `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` |
| Client Sipho Ndlovu mvmzw78f31c9 / portal contact | `1d7cae32-0deb-43fe-af41-a3b9452f3128` / `65f788e3-7d32-4125-ade0-1d25a5e0a85a` |
| Trust deposit DEP/625A-OBS/001, R 30 000 (Alice), RECORDED | `be72f588-1e42-4d66-9d5e-c37a6fce1c59` |
| Trust payment PAY/625A-OBS/001, R 1 500 (recorded by Alice, approved by **Bob**), APPROVED | `341c2ff9-17c2-477a-b75d-adfd9bb38206` |
| Acceptance #1, "Engagement Letter — General": viewed + accepted on :3002 | Mailpit msg `MBVZgM89GTdWGUEHNXSSUc` |
| Acceptance #2, "Standard Engagement Letter": viewed + declined with a reason on :3002 | Mailpit msg sent 00:26:15Z |
| Time entry 2h on "Open estate bank account" | (audit 00:26:59) |
| Fee note INV-0003, R 5 000 + VAT (generate flow, estate lines only): approved, sent, part payment R 2 000 EFT/625A-OBS/001, now PARTIALLY_PAID | `7b7e7dde-15db-4105-a38c-9da37c3189e5` |
| Disposable matter "625A observer closure override", closed with override | `8ca3c73d-fb8d-4aed-99f0-cc12dbe9bdd6`, `matter_closure.closed` audit `da19d591-edc7-43f9-85bf-56951a9180de`, closure log `013caa57-a83a-41b0-b579-cc464d3a335e` |
| Portal magic link (1 of the 3-per-15-min cap used) | Mailpit msg `iydpZLvitLw9V4bh5m3s7b` |

## Spec item → observed
| Spec item (625.4) | Observed | Verdict | Evidence |
|---|---|---|---|
| Matter feed lists the deposit | "Alice Owner performed trust_deposit.recorded on trust_transaction" | PASS (sentence wording is 625B) | `03` |
| Matter feed lists the payment (recorded + approved) | "Alice Owner performed trust_payment.recorded" and "**Bob Admin** performed trust_payment.approved" | PASS | `03` |
| Engagement letter sent / viewed / accepted, actor Sipho, PORTAL | acceptance.created and .sent (Alice). acceptance.viewed and .accepted show "**Sipho Ndlovu mvmzw78f31c9** performed …" with an SN avatar, not "System". DB: `actor_type=PORTAL_CONTACT`, `actor_id=65f788e3…`, `source=PORTAL`. acceptance.certificate_generated shows as "System". | PASS | `01`, `03`, `audit-rows.txt` |
| Second request declined (extra) | acceptance.created / .sent / .viewed (Sipho) / **.declined (Sipho)**. `decline_reason` stored. | PASS | `02`, `03` |
| Fee note created / approved / sent (+ payment) | invoice.created, invoice.approved, invoice.sent and invoice.partially_paid are all on the feed. created and approved were missing before the fix. | PASS (sentences are 625B) | `03` |
| Entity branch: a legacy `project` row without `details.project_id` appears | **No such row exists.** All 17 `entity_type='project'` rows in the only tenant schema carry a matching `project_id`, and every current `project` emitter writes it. Not observable on this data. I did not fabricate one: the audit table is read-only for QA, with no SQL inserts. The EXPLAIN below proves the branch is wired and indexed (BitmapOr with `idx_audit_entity`). | NOT OBSERVABLE (covered only by `ActivityServiceIntegrationTest`) | `explain-findByProjectId.txt` |
| Audit viewer shows `justification` on `matter_closure.closed` | Firm → Audit trail, filter `matter_closure.closed`, row expanded: `justification: 625A observer: disposable test matter opened in error, no work performed`, `override_used: true`, `closure_log_id`. The DB details match. | PASS | `04` |
| N-6: portal Firm actions shows the trust payment approval | "**Trust payment approved** — Bob Admin" (8 minutes ago) | PASS | `06` |
| Portal "Your actions" humanised, no raw slugs | "You declined a document", "You viewed a document", "You accepted a document", "You viewed a document". It was empty in the BEFORE run. | PASS | `05` |

Feed totals: the matter Activity tab showed 20 rows, then 4 more after "Load more", 24 in all. This equals `count(*)` from the `findByProjectId` query: 19 rows from this run plus 5 older rows from the reproducer run that already carried the key. The reproducer's pre-fix rows (trust/acceptance/invoice.created/approved from 2026-10-24) are still absent, because there is no backfill. That is expected; the ADR has no backfill.

## Audit rows since start_ts (`audit-rows.txt`)
| t (UTC) | event_type | actor_type | actor | project_id | customer_id |
|---|---|---|---|---|---|
| 00:22:37 | trust_deposit.recorded | USER | Alice | estate | yes |
| 00:23:17 | trust_payment.recorded | USER | Alice | estate | yes |
| 00:23:37 | trust_payment.approved | USER | **Bob** `c9ccb6c0…` | estate | yes |
| 00:24:25 | document.generated / generated_with_clauses | USER | Alice | estate / — | — |
| 00:24:58 | acceptance.created, acceptance.sent | USER | Alice | estate | yes |
| 00:25:15 | acceptance.viewed | PORTAL_CONTACT / PORTAL | `65f788e3…` (Sipho) | estate | yes |
| 00:25:21 | acceptance.accepted | PORTAL_CONTACT / PORTAL | `65f788e3…` | estate | yes |
| 00:25:21 | acceptance.certificate_generated | SYSTEM / INTERNAL | — | estate | yes |
| 00:25:50 | document.generated / generated_with_clauses | USER | Alice | estate / — | — |
| 00:26:15 | acceptance.created, acceptance.sent | USER | Alice | estate | yes |
| 00:26:27 | acceptance.viewed | PORTAL_CONTACT | `65f788e3…` | estate | yes |
| 00:26:34 | acceptance.declined | PORTAL_CONTACT | `65f788e3…` | estate | yes |
| 00:26:59 | time_entry.created | USER | Alice | estate | — |
| 00:28:11 | invoice.created | USER | Alice | estate | yes |
| 00:28:28 | invoice.approved | USER | Alice | estate | yes |
| 00:28:39 | invoice.sent | USER | Alice | estate | yes |
| 00:28:39 | document.generated (fee-note PDF) | USER | Alice | **—** | — |
| 00:28:58 | invoice.partially_paid | USER | Alice | estate | yes |
| 00:30:05 | project.created_from_template | USER | Alice | closure matter | — |
| 00:30:26 | matter_closure.closed (**justification present**) | USER | Alice | closure matter | — |
| 00:30:26 | matter.closure.override_used, document.generated, statement.generated | USER | Alice | closure matter | — |

## EXPLAIN (ANALYZE, BUFFERS): new `findByProjectId` (estate id, entityType NULL, since NULL, LIMIT 20)
The table has 149 rows. The planner picks a seq scan (cost 10.12): 7 buffers, 0.071 ms, 24 rows matched, 125 removed. The count query is the same shape at 0.046 ms. With `enable_seqscan=off`, both OR arms are index-backed:
```
Limit (actual 0.036..0.039 rows=20)
  -> Sort (occurred_at DESC)
     -> Bitmap Heap Scan on audit_events (rows=24)
        -> BitmapOr
           -> Bitmap Index Scan on idx_audit_project  (details->>'project_id' = '9a27…')  rows=24
           -> Bitmap Index Scan on idx_audit_entity   (entity_type='project' AND entity_id='9a27…') rows=1
Execution Time: 0.089 ms
```
So the OR does not force a seq scan at scale: `idx_audit_project` and `idx_audit_entity` combine via BitmapOr. Full output: `explain-findByProjectId.txt`.

## N-6 verdict: FIXED (observed)
`trust_payment.approved` now carries `project_id` and `customer_id`, and is on the allow-list with the label "Trust payment approved". It renders on the portal Firm actions tab with actor Bob Admin. Before the fix, the BEFORE run showed no trust row. The MINE half also works: viewed / accepted / declined now carry `actor_id` = the portal contact and render as "You … a document".

## Surprises / defects found (not 625A regressions; for routing)
1. **The entity branch cannot be observed on the e2e data.** No legacy `project` row lacks `details.project_id` (17/17 carry it). The branch is proven only by the integration test and the EXPLAIN plan.
2. **The fee-note PDF `document.generated` row has no `project_id`** (00:28:39). It is outside the six publisher families, so it is not on the matter feed. Possible follow-up.
3. **Portal Firm actions shows rows for internal-draft closure documents.** The closure was done with "Share on the client portal" and "Email the client now" unticked, and `statement.generated.details.shared_with_client=false`. Even so, "Statement of Account generated" and "Document generated for you" (1 minute ago) are on Sipho's Firm actions tab. Only the label and timestamp show, no document. The BEFORE run showed similar older rows, so this predates 625A. This looks like a 624G intent-flag gap in the activity filter. Should be routed.
4. **The firm audit viewer links `matter.closure.override_used` to `/matters/{closure_log_id}/closure`, which returns 404.** The entity is `matter_closure` and the id is a closure-log id. A console 404 appeared on prefetch. This predates 625A (the reproducer's `383c83bb…` row has the same link).
5. The rebuild also recreated `e2e-localstack`, which wiped S3 objects created before the rebuild. Older generated documents may 404 on download.
6. Feed sentences are still raw ("performed x on y"). This is expected; it is 625B's job.

## Cleanup
- The worktree portal (PID 72880) was killed. Ran `svc.sh start portal`. `svc.sh status` shows `portal 78681 yes yes 3002`, cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`.
- `git -C worktree-epic-625A status --short` is empty, so `portal/next-env.d.ts` is unchanged.
- The e2e backend was left on the rebuilt image.

## Files
Screenshots `00-matter-activity-baseline-after-rebuild.png`, `01-portal-acceptance-accepted.png`, `02-portal-acceptance-declined.png`, `03-matter-activity-after-actions.png`, `04-audit-viewer-matter-closure-closed-justification.png`, `05-portal-activity-your-actions.png`, `06-portal-activity-firm-actions-trust-approval.png`. Originals are also in `b2b-strawman/.playwright-mcp/625A-obs/`. Logs and SQL: `audit-rows.txt`, `explain-findByProjectId.txt`, `rebuild.log`, `portal-build.log`, `portal-start.log`.
