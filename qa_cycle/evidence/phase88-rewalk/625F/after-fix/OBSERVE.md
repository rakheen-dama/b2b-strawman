# 625F re-observation after the fix commit (pack-scoped reconcile target)

**Overall verdict: PASS (observed).** The new pack-scoped JPQL query `AutomationRuleRepository.findPackSeededByTemplateSlug(packId, slug, source)` resolves the real pack-seeded rule on the live e2e DB at startup. With DEBUG enabled for the seeder, all five reconcile entries reached the target rule and returned ALREADY_CURRENT. None of them reported NO_TARGET. The data is unchanged.

## Environment
- Worktree HEAD is `295fcbd74`. The production change is in `9195ddd57`.
- I rebuilt only the backend with `SHELL=/bin/bash docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend` at 17:49Z.
  - The build ran `RUN ./mvnw clean package -DskipTests -B` fresh.
  - New image: `sha256:7087002f…`. The previous one was `sha256:5464477f…`.
- I checked the jar: `AutomationRuleRepository.class` contains `SELECT r FROM AutomationRule r WHERE r.templateSlug = :templateSlug AND r.source = :source AND r.sourcePackInstallId IN (SELECT p.id FROM PackInstall p WHERE p.packId = :packId)`, and `automation-templates/common.json` reads `"version": 3`.
- I ran no host `mvn`, did not use `e2e-rebuild.sh` or `e2e-down`, and touched no other service.
- Tenant `tenant_7d218705360b`, rule `19dc292f-6044-4001-97c1-b15844b15a78`, install `71d882c5-3f35-4ab3-bc4a-d351bc7c13f2`.

## Checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Pre-state captured | PASS | `00-pre-db.txt` |
| 2 | App context starts on the new image (the JPQL query is valid) and the equal-version run skips | PASS | `01-startup-equal-version.txt` (+ `-raw.jsonl`) |
| 3 | Gate opens v2 to v3 after the version-only rewind | PASS | `02-startup-gate-open.txt` |
| 4 | The scoped query resolves the target. Positive proof: 5 DEBUG `Reconcile no-op` lines, 0 `no pack-seeded row`, 0 `share slug` | PASS | `02-startup-gate-open.txt` |
| 5 | No `Reconcile skipped (edited)`, no `Reconcile applied`, no `Re-pinned` line (re-pin count 0) | PASS | `02-startup-gate-open.txt` |
| 6 | Post-state: version back to 3; rule, action and all other rules identical to pre | PASS | `03-post-db.txt` |
| 7 | Cleanup restart: fixture removed, equal-version skip, DB identical to post | PASS | `04-startup-cleanup.txt`, `05-post-cleanup-db.txt` |

### 1. Pre-state (17:49:05Z)
- `pack_install`: `automation-common | 3`, `automation-legal-za | 2`.
- Rule: `Document uploaded — notify matter lead | Notifies the matter lead when a document is uploaded | f | DOCUMENT_ACCEPTED | TEMPLATE`, with `created_by` set to the system user `00000000-…`.
  - `source_pack_install_id` is `71d882c5-…`.
  - Hash: `594a06536dd7485b8204193fdaa94f97a3ab62f28b791e1550ac3feed41e232f`.
  - `updated_at` is `17:38:30.688883`.
- Action `37e71985-…`: `{"title": "Document uploaded: {{document.name}}", "message": "{{document.name}} was uploaded to the matter.", "recipientType": "PROJECT_OWNER"}`.
- There are 10 rules, and only 1 has the slug `document-review-notification`.
- `automation_pack_status`: automation-common v3, `appliedAt 17:38:30.703457634Z`.

### 2. Startup on the new image, equal version (17:50:02Z)
```
2026-10-27T17:50:02.204701968Z INFO BackendApplication | Started BackendApplication in 12.7 seconds (process running for 13.025)
2026-10-27T17:50:02.766162135Z INFO PackInstallService | Pack automation-common already installed (internal path), skipping
2026-10-27T17:50:02.776610676Z INFO PackInstallService | Pack automation-legal-za already installed (internal path), skipping
2026-10-27T17:50:02.828796385Z INFO PackReconciliationRunner | Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed
```
- ERROR: 0.
- WARN: 8, the baseline set (6 BeanPostProcessorChecker, 1 CglibAopProxy, 1 PdfConversionService/LibreOffice).
- Health is UP. Spring Data validates `@Query` JPQL when it creates the repository, so a bad query would have failed the boot.

### 3–5. Gate-open path
**Observer fixtures.** These are not product workarounds.
1. Version rewind, at 17:50:53Z. It returned `UPDATE 1`:
   ```sql
   UPDATE tenant_7d218705360b.pack_install SET pack_version='2' WHERE pack_id='automation-common';
   ```
   I did not touch `org_settings.automation_pack_status`. `PackVersionGate.tryOpen` reads only `pack_install.pack_version` (PackVersionGate.java:93-128). OrgSettings is only re-recorded after a true return (PackInstallService.java:142-149).
2. DEBUG for the seeder logger. Through `docker cp` I added an untracked file, `/app/config/application.properties`, containing `logging.level.io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder=DEBUG`. This is Spring Boot's default `./config/` location, and the container's working directory is `/app`. I removed the file in step 7.
   - The reconcile outcome is logged through the seeder's own logger: `AbstractPackSeeder.applyReconcileEntry` calls `entry.applyAndLog(resolveReconcileTarget(...), log, ...)` at AbstractPackSeeder.java:169-170.

After that I ran `docker restart e2e-backend` at 17:50:55Z:
```
2026-10-27T17:51:11.907007750Z INFO BackendApplication | Started BackendApplication in 12.81 seconds (process running for 13.21)
2026-10-27T17:51:12.119735458Z INFO PackReconciliationRunner | Running pack reconciliation for 1 tenants
2026-10-27T17:51:12.383542333Z INFO PackVersionGate | Reconciling AUTOMATION_TEMPLATE pack automation-common v2 -> v3 for tenant tenant_7d218705360b
2026-10-27T17:51:12.427779292Z DEBUG AutomationTemplateSeeder | Reconcile no-op: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field name already current
2026-10-27T17:51:12.430036667Z DEBUG AutomationTemplateSeeder | Reconcile no-op: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field description already current
2026-10-27T17:51:12.433698667Z DEBUG AutomationTemplateSeeder | Reconcile no-op: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field actions[0].actionConfig.title already current
2026-10-27T17:51:12.436259083Z DEBUG AutomationTemplateSeeder | Reconcile no-op: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field actions[0].actionConfig.message already current
2026-10-27T17:51:12.438096292Z DEBUG AutomationTemplateSeeder | Reconcile no-op: tenant=tenant_7d218705360b pack=automation-common automation_rule 'document-review-notification' field enabled already current
2026-10-27T17:51:12.449278833Z INFO PackInstallService | Pack automation-common reconciled to v3 for tenant tenant_7d218705360b (internal path)
2026-10-27T17:51:12.466206417Z INFO PackInstallService | Pack automation-legal-za already installed (internal path), skipping
2026-10-27T17:51:12.508524208Z INFO PackReconciliationRunner | Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed
```

Line counts for this restart:

| Line | Count |
|---|---|
| `Reconciling … v2 -> v3` | 1 |
| `Reconcile no-op` | 5 |
| `Reconcile applied` | 0 |
| `Reconcile skipped (no pack-seeded row)` | 0 |
| `Reconcile skipped (edited)` | 0 |
| `Reconcile entry ignored` | 0 |
| `share slug` | 0 |
| `more than one` | 0 |
| `Reconcile gate closed` | 0 |
| `Re-pinned` | 0 |
| `reconciled to v3` | 1 |
| ERROR | 0 |
| WARN | 8 (baseline) |

**Why the no-op lines prove resolution.** `PackReconcileEntry.applyAndLog` behaves as follows:
- If the resolver returns empty, it logs INFO `Reconcile skipped (no pack-seeded row)` and returns NO_TARGET (PackReconcileEntry.java:126-135).
- It reaches `applyTo` and the DEBUG `Reconcile no-op … already current` branch only when the target is present (PackReconcileEntry.java:137-160).

Each no-op line reads the live rule (`currentValue()` == the `to` value), so the resolver returned exactly the pack-seeded rule for all 5 entries.

**Re-pin.** The re-pin count was 0, so no `Re-pinned` line was logged (AutomationPackInstaller logs only when the count is above 0). The hash is unchanged (see 6).

### 6. Post-state (`03-post-db.txt`)
I diffed the capture against the pre-state, excluding the timestamp header. Exactly one line differs:
```
<         "appliedAt": "2026-10-27T17:38:30.703457634Z"
>         "appliedAt": "2026-10-27T17:51:12.449216792Z"
```
This is the automation-common entry in `org_settings.automation_pack_status`. It is the expected re-record after a true gate return, and the version is still 3.

Everything else is byte-identical to the pre-state:
- `pack_install` automation-common is back to `'3'` (the CAS advance) with the same id.
- The rule row is unchanged: name, description, enabled=f, source, created_by, source_pack_install_id, hash `594a0653…`, and `updated_at 17:38:30.688883`.
- The action config and its `updated_at` are unchanged.
- All 10 rules and all 11 actions are unchanged.

### 7. Cleanup
- I ran `docker exec -u root e2e-backend rm -rf /app/config` and confirmed it was removed.
- I ran `docker restart e2e-backend` at 17:51:38Z. It logged `Pack automation-common already installed (internal path), skipping`. There were 0 Reconcile, 0 DEBUG and 0 ERROR lines, and 8 baseline WARN lines.
- The DB is identical to `03-post-db.txt`.
- The container now runs the 295fcbd74 image with no observer fixture in place.

## Anomalies
None. The `appliedAt` refresh is expected behaviour: a true gate return re-records OrgSettings.

## Files
`00-pre-db.txt`, `01-startup-equal-version.txt`, `01-startup-equal-version-raw.jsonl`, `02-startup-gate-open.txt`, `02-startup-gate-open-raw.jsonl`, `03-post-db.txt`, `04-startup-cleanup.txt`, `05-post-cleanup-db.txt`
