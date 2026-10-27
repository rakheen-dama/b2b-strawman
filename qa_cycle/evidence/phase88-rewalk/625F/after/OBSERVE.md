# 625F / task 625.15: observed after the fix (closes G-B13)

**Overall verdict: PASS (observed).** Seven of the seven checks were run against the live e2e stack. They cover the browser, the backend log, the DB and the API.

## Environment
- Worktree HEAD `6c51f36bcff1fa107018dd86266bd39b4d188b7a` (PR #1684).
- Backend and frontend were rebuilt from the worktree with `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend frontend` at about 17:38Z. No other service was touched.
  - The build log shows `RUN ./mvnw clean package -DskipTests -B` ran fresh, with no cache hit.
  - New backend image: `sha256:5464477f…`. The old one was `sha256:9e4d547f…`.
  - `automation-templates/common.json` inside `/app/app.jar` reads `"version": 3` and has a `reconcile` block.
- Tenant `tenant_7d218705360b` (org `e2e-test-org`), rule `19dc292f-6044-4001-97c1-b15844b15a78`, install `71d882c5-3f35-4ab3-bc4a-d351bc7c13f2`.

## Checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Pre-state: automation-common v2, rule as reproduced, uninstall-check captured | PASS | `00-pre-db.txt`, `00-pre-uninstall-check.json` |
| 2 | Rebuild from the worktree (jar carries v3) | PASS | (above) |
| 3 | Startup reconcile log lines, with no WARN/ERROR beyond the baseline | PASS | `01-startup-log.txt`, `01-startup-log-raw.jsonl` |
| 4 | Post-state DB: v3, renamed and disabled, hash re-pinned, other rules untouched, uninstall-check byte-identical | PASS | `02-post-db.txt`, `02-post-uninstall-check.json` |
| 5 | UI: renamed rule, toggle OFF, badge "Document uploaded" | PASS | `10-settings-automations-rule-renamed-disabled.png` |
| 6 | Negative: Carol's upload fires no automation and no "Document accepted/uploaded:" notification | PASS | `04-negative-upload-db.txt`, `11-…png`, `12-…png`, `13-…png` |
| 7 | Idempotent second restart | PASS | `03-restart-log.txt`, `03-restart-uninstall-check.json` |

### 1. Pre-state (17:36:58Z)
- `pack_install`: `automation-common | AUTOMATION_TEMPLATE | 2`, and `automation-legal-za | 2`.
- Rule: `Document Review Notification | Notifies project owner when a document is accepted | t | DOCUMENT_ACCEPTED`, hash `bfbe0ab9d5ea2957ad81657e082286ab593cca2e4f612294cc73d30f893b4b29`.
- Action: `{"title": "Document accepted: {{document.name}}", "message": "A document has been accepted in project {{project.name}}.", "recipientType": "PROJECT_OWNER"}`.
- `automation_pack_status`: automation-common `version 2`.
- Rule executions: 1. AUTOMATION "Document accepted" notifications: 1. Alice unread: 9.
- Uninstall-check: `{"canUninstall":false,"blockingReason":"1 rule has been executed"}`.

### 3. Startup log (verbatim, `<ts> <level> <logger> | <message>`)
```
2026-10-27T17:38:30.362155717Z INFO PackReconciliationRunner | Running pack reconciliation for 1 tenants
2026-10-27T17:38:30.626507592Z INFO PackVersionGate | Reconciling AUTOMATION_TEMPLATE pack automation-common v2 -> v3 for tenant tenant_7d218705360b
2026-10-27T17:38:30.668092217Z INFO AutomationTemplateSeeder | Reconcile applied: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field name updated
2026-10-27T17:38:30.678540676Z INFO AutomationTemplateSeeder | Reconcile applied: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field description updated
2026-10-27T17:38:30.683175134Z INFO AutomationTemplateSeeder | Reconcile applied: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field actions[0].actionConfig.title updated
2026-10-27T17:38:30.686982967Z INFO AutomationTemplateSeeder | Reconcile applied: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field actions[0].actionConfig.message updated
2026-10-27T17:38:30.688902467Z INFO AutomationTemplateSeeder | Reconcile applied: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field enabled updated
2026-10-27T17:38:30.699517926Z INFO AutomationPackInstaller | Re-pinned content hash of 1 rules of automation pack automation-common after the reconcile block for tenant tenant_7d218705360b
2026-10-27T17:38:30.703504634Z INFO PackInstallService | Pack automation-common reconciled to v3 for tenant tenant_7d218705360b (internal path)
2026-10-27T17:38:30.718435842Z INFO PackInstallService | Pack automation-legal-za already installed (internal path), skipping
2026-10-27T17:38:30.764934926Z INFO PackReconciliationRunner | Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed
```
- There are 0 `Reconcile skipped`, `Reconcile entry ignored` or `Reconcile gate closed` lines, and 0 ERROR lines.
- There are 8 WARN lines, which is the baseline set: 6 BeanPostProcessorChecker, 1 CglibAopProxy and 1 LibreOffice.

### 4. Post-state DB (17:39:10Z)
- `pack_install`: `automation-common | 3`. `automation-legal-za` is still `2`, and the install id is unchanged.
- Rule: `Document uploaded — notify matter lead | Notifies the matter lead when a document is uploaded | f | DOCUMENT_ACCEPTED`.
  - New hash: `594a06536dd7485b8204193fdaa94f97a3ab62f28b791e1550ac3feed41e232f`. The old hash was `bfbe0ab9…`.
  - `updated_at` is `17:38:30.688883`.
- Action: `{"title": "Document uploaded: {{document.name}}", "message": "{{document.name}} was uploaded to the matter.", "recipientType": "PROJECT_OWNER"}`. The action id `37e71985-…` is unchanged.
- `automation_pack_status`: automation-common `version 3`, `appliedAt 2026-10-27T17:38:30.703457634Z`. automation-legal-za is unchanged.
- I diffed all 10 pack-seeded rules (slug, name, enabled, hash, updated_at) before against after. **Only the `document-review-notification` line differs.** The rule count is still 10.
- `cmp 00-pre-uninstall-check.json 02-post-uninstall-check.json`: **BYTE-IDENTICAL**. It contains no "edited" blocker, which shows the re-pin worked.
- `GET /api/automation-rules` returns `Document uploaded — notify matter lead False DOCUMENT_ACCEPTED`.

### 5. UI (Playwright MCP, Alice, `/settings/connections?tab=automations`)
- The row reads "Document uploaded — notify matter lead" with the description "Notifies the matter lead when a document is uploaded".
- The trigger badge reads **"Document uploaded"**, so OD-1(a) was ruled. The before screenshot showed "Document Accepted".
- The switch `Toggle Document uploaded — notify matter lead` is **not checked**. Every other rule kept its previous state: 8 are on and Task Completion Chain is off, as before.
- Last Updated reads "just now" for this rule and "2 hours ago" for the others.
- The console was clean: 0 errors and 0 warnings.

### 6. Negative behaviour (T0 = 2026-10-27T17:39:44Z)
- Baseline at T0: Alice 9 unread, 5 automation_executions in total, 1 for this rule.
- Carol uploaded `625F-observe-upload.pdf` through the drop zone on Ndlovu v RAF op0p › Work › Documents. The row shows status "Uploaded" (`11-carol-upload-matter-documents.png`).
- DB results:
  - Document `f2f2e810-0303-4795-a7db-08db10a4988c` was created with status UPLOADED at 17:40:00.536.
  - `audit_events` has `document.uploaded` at 17:40:00.690, so the event really fired.
  - Rule executions since T0: **0**. Any automation_executions since T0: **0**.
  - AUTOMATION notifications since T0: **0**. "Document accepted%" or "Document uploaded:%" notifications since T0: **0**.
  - The only new notification is `Alice Owner | DOCUMENT_SHARED | Carol Member uploaded "625F-observe-upload.pdf"`. This is expected and is not G-B13.
- Alice's bell went from 9 to 10. The top entry is the DOCUMENT_SHARED one, and there is no "Document accepted: 625F-observe-upload.pdf" (`12-alice-bell-no-automation.png`, `13-alice-notifications-page.png`).
- The backend log during the upload window has no automation activity.

### 7. Idempotency (`docker restart e2e-backend` at 17:41:05Z, same image)
```
2026-10-27T17:41:20.136722504Z INFO PackReconciliationRunner | Running pack reconciliation for 1 tenants
2026-10-27T17:41:20.407572837Z INFO PackInstallService | Pack automation-common already installed (internal path), skipping
2026-10-27T17:41:20.418025046Z INFO PackInstallService | Pack automation-legal-za already installed (internal path), skipping
2026-10-27T17:41:20.461531837Z INFO PackReconciliationRunner | Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed
```
- Line counts: `Reconciling` 0, `Reconcile applied` 0, `Re-pinned` 0, `reconciled to v` 0, forbidden 0, ERROR 0, WARN 8 (baseline).
- The DB state after the restart is identical to `02-post-db.txt`. The hash is still `594a0653…`, `updated_at` is still `17:38:30.688883`, and the pack status is still v3 at the same `appliedAt`.
- The uninstall-check is byte-identical to the pre value.

## Anomalies (none block this slice)
1. **Console 500 on `/notifications`**, logged when the browser closed (`[ERROR] Failed to load resource: 500 … /notifications`). It happened about 30 s after loading the page, which is during the step 7 restart. The frontend log shows `fetch failed … ECONNREFUSED 172.21.0.6:8080`. This is a side effect of restarting the backend, not a product defect.
2. **Pre-existing, unrelated:** Carol (member) opening the matter page logs `WARN GlobalExceptionHandler | Forbidden: path=/api/matters/{id}/time-summary/by-member`. The frontend calls a lead/admin-only endpoint for members and gets a 403.
3. **Pre-existing:** at 1698 px the Automations table's "Last Updated" column is clipped ("LAST U", "2 hour"). The `before/01` screenshot shows the same clipping.
4. The repro's historical "Document accepted: 625F-repro-upload.pdf" notification and its one execution remain. This is expected: the fix does not rewrite history.

## Files
`00-pre-db.txt`, `00-pre-uninstall-check.json`, `01-startup-log.txt`, `01-startup-log-raw.jsonl`, `02-post-db.txt`, `02-post-uninstall-check.json`, `03-restart-log.txt`, `03-restart-uninstall-check.json`, `04-negative-upload-db.txt`, `10-settings-automations-rule-renamed-disabled.png`, `11-carol-upload-matter-documents.png`, `12-alice-bell-no-automation.png`, `13-alice-notifications-page.png`, `625F-observe-upload.pdf` (the uploaded fixture)
