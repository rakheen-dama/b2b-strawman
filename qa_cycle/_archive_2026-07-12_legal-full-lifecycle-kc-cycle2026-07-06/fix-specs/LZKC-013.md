# Fix Spec: LZKC-013 — `task-completion-chain` automation default-on fights matter closure

## Problem
Day 60 / 60.7: every task marked Done auto-spawned an IN_PROGRESS "Follow-up: {task}" task assigned to the project owner (automation `task-completion-chain`), directly fighting the matter-closure Open-Tasks gate — the user had to cancel each follow-up before closing.

## Root Cause (verified)
- Rule definition: `backend/src/main/resources/automation-templates/common.json:5-26` — slug `task-completion-chain`, trigger `TASK_STATUS_CHANGED→DONE`, action `CREATE_TASK "Follow-up: {{task.name}}"`. No enabled/default flag exists in the JSON schema.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRule.java:85` — constructor hardcodes `this.enabled = true;` — every template rule is seeded enabled.
- `automation/template/AutomationTemplateDefinition.java:19-27` — deserialization record has no `defaultEnabled` field.
- `automation/template/AutomationTemplateSeeder.java:85-95` — seeds every rule via that constructor, no override.
- Existing-tenant impact: pack install is idempotent-by-presence — `packs/PackInstallService.java:138-143` returns early when the pack is already installed; there is no content re-apply. **Editing common.json alone only affects newly-provisioned tenants**; existing tenants need a data migration.

## Fix
1. Add optional `Boolean defaultEnabled` to `AutomationTemplateDefinition` (null → true, so all other templates keep current behaviour).
2. In `AutomationTemplateSeeder.applyPack` (lines 84-95): after `ruleRepository.save(rule)`, call `rule.toggle()` (AutomationRule.java:96) when `defaultEnabled == Boolean.FALSE` (or set enabled before save if a setter path is cleaner).
3. `common.json`: add `"defaultEnabled": false` to the `task-completion-chain` block only. Rule stays available for firms that want it — just off by default.
(Step 4 removed — AUTHORIZED DECISION below: new tenants only, NO migration.)

**Product-decision note:** disabling by default is a behaviour change for all tenants including any that deliberately use the follow-up chain. The migration flips it off for everyone (we cannot distinguish "deliberately kept on" from "never noticed"). Orchestrator should confirm the migration scope (option: migration only for tenants where the rule was never manually toggled — but no toggle-audit exists, so blanket-off is the practical choice).

## Scope
Backend only
Files to modify: `AutomationTemplateDefinition.java`, `AutomationTemplateSeeder.java`, `automation-templates/common.json`
Files to create: none
Migration needed: no (authorized: new tenants only)

## Verification
Provision a fresh tenant (or e2e-reseed) → automation list shows `task-completion-chain` disabled; mark a task Done → no "Follow-up:" task spawns. Existing tenant after migration: same observation. Toggle the rule on manually → chain works as before. Seeder unit test asserts defaultEnabled=false is honoured.

## Estimated Effort
M (30 min – 2 hr)

## AUTHORIZED DECISION (orchestrator/user, 2026-07-06)
**New tenants only — NO migration.** Implement steps 1–3 (defaultEnabled flag honoured by seeder + common.json defaultEnabled:false for task-completion-chain). Drop step 4 entirely (no Flyway migration; existing tenants keep their current setting). The QA tenant (tenant_5039f2d497cf) will have the rule toggled off manually via the UI by the QA agent during verification.
