# Fix Spec: OBS-AN-007 — Trigger type badge shows raw enum for PROPOSAL_SENT and FIELD_DATE_APPROACHING

## Problem

The automation rule list and detail pages display trigger type badges. Two trigger types — `PROPOSAL_SENT` and `FIELD_DATE_APPROACHING` — render as raw enum strings instead of human-readable labels. All other trigger types display correctly (e.g., "Task Status", "Invoice Status", "Budget Threshold").

These two types are used by seeded vertical automation templates:
- `PROPOSAL_SENT`: used in `common.json` template pack ("Proposal follow-up reminder")
- `FIELD_DATE_APPROACHING`: used in `accounting-za.json` template pack ("FICA expiry reminder")

## Root Cause

**File**: `frontend/components/automations/trigger-type-badge.tsx`, lines 4-16

The `TRIGGER_TYPE_CONFIG` record maps 8 of the 10 backend `TriggerType` enum values to labels and badge variants. It is missing entries for `PROPOSAL_SENT` and `FIELD_DATE_APPROACHING`.

The fallback on line 23 handles unknown types gracefully by displaying the raw enum string:
```typescript
const config = TRIGGER_TYPE_CONFIG[triggerType] ?? {
    label: triggerType,
    variant: "neutral" as const,
};
```

This fallback is why the badges render at all — but they show `PROPOSAL_SENT` and `FIELD_DATE_APPROACHING` as raw text.

**File**: `frontend/lib/api/automations.ts`, lines 16-24

The `TriggerType` TypeScript union type also only lists 8 values. It is missing `PROPOSAL_SENT` and `FIELD_DATE_APPROACHING`. This means the TypeScript compiler does not enforce exhaustive coverage, and the `TRIGGER_TYPE_CONFIG` record (typed as `Record<TriggerType, ...>`) does not require these entries.

**Backend reference**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerType.java` defines 10 values:
```java
public enum TriggerType {
  TASK_STATUS_CHANGED,
  PROJECT_STATUS_CHANGED,
  CUSTOMER_STATUS_CHANGED,
  INVOICE_STATUS_CHANGED,
  TIME_ENTRY_CREATED,
  BUDGET_THRESHOLD_REACHED,
  DOCUMENT_ACCEPTED,
  INFORMATION_REQUEST_COMPLETED,
  PROPOSAL_SENT,
  FIELD_DATE_APPROACHING
}
```

## Fix

### Step 1: Add missing types to the TriggerType union

**File**: `frontend/lib/api/automations.ts`, lines 16-24

Add the two missing values:

```typescript
export type TriggerType =
  | "TASK_STATUS_CHANGED"
  | "PROJECT_STATUS_CHANGED"
  | "CUSTOMER_STATUS_CHANGED"
  | "INVOICE_STATUS_CHANGED"
  | "TIME_ENTRY_CREATED"
  | "BUDGET_THRESHOLD_REACHED"
  | "DOCUMENT_ACCEPTED"
  | "INFORMATION_REQUEST_COMPLETED"
  | "PROPOSAL_SENT"
  | "FIELD_DATE_APPROACHING";
```

### Step 2: Add missing entries to TRIGGER_TYPE_CONFIG

**File**: `frontend/components/automations/trigger-type-badge.tsx`, lines 4-16

Add entries for the two missing types inside the `TRIGGER_TYPE_CONFIG` record:

```typescript
const TRIGGER_TYPE_CONFIG: Record<
  TriggerType,
  { label: string; variant: "lead" | "warning" | "success" | "neutral" | "outline" }
> = {
  TASK_STATUS_CHANGED: { label: "Task Status", variant: "lead" },
  PROJECT_STATUS_CHANGED: { label: "Project Status", variant: "success" },
  CUSTOMER_STATUS_CHANGED: { label: "Customer Status", variant: "neutral" },
  INVOICE_STATUS_CHANGED: { label: "Invoice Status", variant: "warning" },
  TIME_ENTRY_CREATED: { label: "Time Entry", variant: "outline" },
  BUDGET_THRESHOLD_REACHED: { label: "Budget Threshold", variant: "warning" },
  DOCUMENT_ACCEPTED: { label: "Document Accepted", variant: "success" },
  INFORMATION_REQUEST_COMPLETED: { label: "Request Completed", variant: "lead" },
  PROPOSAL_SENT: { label: "Proposal Sent", variant: "success" },
  FIELD_DATE_APPROACHING: { label: "Date Approaching", variant: "warning" },
};
```

Variant rationale:
- `PROPOSAL_SENT` -> `success` (positive business event, like `DOCUMENT_ACCEPTED`)
- `FIELD_DATE_APPROACHING` -> `warning` (deadline/urgency, like `BUDGET_THRESHOLD_REACHED`)

### Step 3: Update trigger-config-form and rule-form if they have similar label maps

Check these files for hardcoded trigger type option lists that may also need updating:
- `frontend/components/automations/trigger-config-form.tsx`
- `frontend/components/automations/rule-form.tsx`

## Scope

- `frontend/lib/api/automations.ts` — TriggerType union (add 2 values)
- `frontend/components/automations/trigger-type-badge.tsx` — TRIGGER_TYPE_CONFIG (add 2 entries)
- Possibly `frontend/components/automations/trigger-config-form.tsx` and `rule-form.tsx` if they have separate trigger type lists

## Verification

1. Navigate to Settings > Automations
2. Activate the "Proposal follow-up reminder" template (common pack) — verify the trigger badge shows "Proposal Sent" in a green/success badge
3. Activate the "FICA expiry reminder" template (accounting-za pack) — verify the trigger badge shows "Date Approaching" in a yellow/warning badge
4. Open each rule's detail page — verify the trigger type is displayed correctly there too
5. Run `pnpm test` — ensure no TypeScript errors from the TriggerType change (the Record type will now enforce all 10 entries)

## Estimated Effort

S (15-30 min) — straightforward addition of two map entries and two union members.
