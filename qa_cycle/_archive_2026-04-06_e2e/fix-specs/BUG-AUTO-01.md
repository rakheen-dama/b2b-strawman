# Fix Spec: BUG-AUTO-01 — Actions not persisted when creating/editing automation rules via UI

## Problem

When a user creates or edits an automation rule via the Settings > Automations UI, the rule metadata (name, description, trigger type, trigger config, conditions) is saved correctly, but **actions are silently dropped**. The frontend sends `actions` in the JSON payload, but the backend `CreateRuleRequest` and `UpdateRuleRequest` DTOs have no `actions` field, so Jackson ignores the data. Seeded rules are unaffected because their actions were persisted by `AutomationTemplateSeeder` directly.

Evidence: QA checkpoint T1.2 — created rule "Notify Alice on invoice payment" with a SendNotification action. Rule appeared in list but Actions column showed "0". Editing and re-saving also failed to persist actions.

## Root Cause (confirmed)

The backend DTOs `CreateRuleRequest` and `UpdateRuleRequest` in `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/dto/AutomationDtos.java` (lines 20-32) do not include an `actions` field:

```java
public record CreateRuleRequest(
    @NotBlank String name,
    String description,
    @NotNull TriggerType triggerType,
    @NotNull Map<String, Object> triggerConfig,
    List<Map<String, Object>> conditions) {}
// No actions field ^^^
```

The backend has a separate `AutomationActionController` with CRUD endpoints (`POST /api/automation-rules/{id}/actions`), but the frontend form sends actions as part of the rule create/update payload — not as separate API calls.

The `AutomationRuleService.createRule()` (line 84) returns `toRuleResponse(rule, List.of())` — it never processes actions. Similarly, `updateRule()` (lines 115-138) never syncs actions.

## Fix

**Approach**: Add `actions` to the backend DTOs and have the service persist them atomically with the rule. This is simpler and more correct than making the frontend call separate action endpoints, because rule + actions should be saved as a single transaction.

### Step 1: Add `actions` field to both DTOs

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/dto/AutomationDtos.java`

Add an `actions` field to `CreateRuleRequest`:
```java
public record CreateRuleRequest(
    @NotBlank String name,
    String description,
    @NotNull TriggerType triggerType,
    @NotNull Map<String, Object> triggerConfig,
    List<Map<String, Object>> conditions,
    List<CreateActionRequest> actions) {}
```

Add an `actions` field to `UpdateRuleRequest`:
```java
public record UpdateRuleRequest(
    @NotBlank String name,
    String description,
    @NotNull TriggerType triggerType,
    @NotNull Map<String, Object> triggerConfig,
    List<Map<String, Object>> conditions,
    List<CreateActionRequest> actions) {}
```

**Note**: The frontend sends `config` (not `actionConfig`) for the action config field. Check if `CreateActionRequest` uses `actionConfig` — if so, add a Jackson `@JsonProperty("config")` alias or rename to match what the frontend sends.

### Step 2: Persist actions in `AutomationRuleService.createRule()`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleService.java`

After saving the rule (line 70), iterate over `request.actions()` (if non-null) and save each action:

```java
List<AutomationAction> savedActions = new ArrayList<>();
if (request.actions() != null) {
    for (var actionReq : request.actions()) {
        var action = new AutomationAction(
            rule.getId(),
            actionReq.sortOrder(),
            actionReq.actionType(),
            actionReq.actionConfig(),
            actionReq.delayDuration(),
            actionReq.delayUnit());
        savedActions.add(actionRepository.save(action));
    }
}
```

Change line 84 from `toRuleResponse(rule, List.of())` to `toRuleResponse(rule, savedActions)`.

### Step 3: Sync actions in `AutomationRuleService.updateRule()`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleService.java`

After saving the rule update (line 124), if `request.actions()` is non-null, delete existing actions for the rule and re-create from the request:

```java
List<AutomationAction> actions;
if (request.actions() != null) {
    actionRepository.deleteByRuleId(id);
    entityManager.flush();
    actions = new ArrayList<>();
    for (var actionReq : request.actions()) {
        var action = new AutomationAction(
            rule.getId(),
            actionReq.sortOrder(),
            actionReq.actionType(),
            actionReq.actionConfig(),
            actionReq.delayDuration(),
            actionReq.delayUnit());
        actions.add(actionRepository.save(action));
    }
} else {
    actions = actionRepository.findByRuleIdOrderBySortOrder(id);
}
```

### Step 4: Verify frontend payload field name alignment

File: `/frontend/app/(app)/org/[slug]/settings/automations/new/create-rule-client.tsx` (line 42)

The frontend sends `config` (not `actionConfig`):
```ts
config: a.actionConfig,
```

The backend `CreateActionRequest` expects `actionConfig`. Either:
- (a) Rename the frontend property from `config` to `actionConfig`, OR
- (b) Add `@com.fasterxml.jackson.annotation.JsonAlias("config")` to the `actionConfig` field in `CreateActionRequest`

Option (a) is cleaner. Change line 42 in `create-rule-client.tsx`:
```ts
actionConfig: a.actionConfig,
```

And in `rule-detail-client.tsx` line 125:
```ts
actionConfig: a.actionConfig,
```

Also change in `frontend/lib/api/automations.ts` the `ActionRequest` interface (line 132):
```ts
export interface ActionRequest {
  actionType: ActionType;
  actionConfig: Record<string, unknown>;  // was "config"
  sortOrder: number;
  delayDuration?: number | null;
  delayUnit?: DelayUnit | null;
}
```

## Scope

Backend + Frontend

Files to modify:
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/dto/AutomationDtos.java`
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleService.java`
- `/frontend/app/(app)/org/[slug]/settings/automations/new/create-rule-client.tsx`
- `/frontend/app/(app)/org/[slug]/settings/automations/[id]/rule-detail-client.tsx`
- `/frontend/lib/api/automations.ts`

Files to create: none

Migration needed: no

## Verification

Re-run Track 1 (T1.2, T1.3):
1. Create a new automation rule with a SendNotification action
2. Reload the page — actions should persist (Actions column shows "1")
3. Edit the rule, add a second action, save — both actions persist
4. Verify seeded rules still work (no regression)

## Estimated Effort

S (< 30 min) — DTO field additions + service loop + frontend property rename
