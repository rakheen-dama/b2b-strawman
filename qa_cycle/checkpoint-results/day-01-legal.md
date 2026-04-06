# Day 1 — First Client Onboarding (Legal Lifecycle QA)

**Date**: 2026-04-06
**Actors**: Bob (Admin), Alice (Owner)
**Cycle**: 1 (bugfix_cycle_2026-04-06)

## Checkpoint Results

### Conflict check & client creation (Actor: Bob)

| ID | Step | Result | Evidence / Notes |
|----|------|--------|-----------------|
| 1.1 | Login as Bob, navigate to Conflict Check | **FAIL** | Conflict Check page (`/conflict-check`) crashes with "Something went wrong" error. JS error: `TypeError: Cannot read properties of undefined (reading 'map')`. **GAP-D1-01 — BLOCKER** |
| 1.2 | Search for "Sipho Ndlovu" — result CLEAR | **BLOCKED** | Cannot search — page crashes (GAP-D1-01). |
| 1.3 | Screenshot: Conflict check clear result | **BLOCKED** | Blocked by GAP-D1-01. |
| 1.4 | Navigate to Clients page | PASS | URL: `/org/e2e-test-org/customers`. Page loads with 50 existing test clients. Heading says "Clients" (correct legal term). "New Client" button available. |
| 1.5-1.15 | Client creation and onboarding flow | **NOT EXECUTED** | Client creation UI is available but not tested due to blocker at 1.1. Stopping per execution rules — blocker hit. |

### Create matter from template (Steps 1.16-1.22)

| ID | Step | Result | Evidence / Notes |
|----|------|--------|-----------------|
| 1.16-1.22 | Create matter from Litigation template | **NOT EXECUTED** | Would also be blocked by GAP-D0-01 (no matter templates). |

### Engagement letter (Steps 1.23-1.28)

| ID | Step | Result | Evidence / Notes |
|----|------|--------|-----------------|
| 1.23-1.28 | Engagement letter flow | **NOT EXECUTED** | Not reached due to blocker. |

## Gaps Identified

| Gap ID | Summary | Severity | Blocker? |
|--------|---------|----------|----------|
| GAP-D1-01 | Conflict Check page crashes: `TypeError: Cannot read properties of undefined (reading 'map')` | **CRITICAL** | YES — crashes on page load, blocks all conflict check functionality. The component likely tries to .map() over undefined data (missing API response or null state). |

## Console Errors

```
TypeError: Cannot read properties of undefined (reading 'map')
    at Object.render (966f3e80d7256b4f.js:1:37517)
```

Additionally, intermittent 500 errors on `POST /org/e2e-test-org/dashboard` observed after Bob login (some succeed, some fail). Non-blocking but worth investigating.

## Decision

**STOPPED** at Step 1.1 per execution rules — blocker encountered. The Conflict Check page crashes on load and cannot be used. This blocks the conflict check steps but client creation (1.4+) could technically proceed if the blocker is waived. However, the test plan specifies conflict check BEFORE client creation as a mandatory workflow step.

## Cascading Impact

GAP-D1-01 blocks:
- Day 1: Steps 1.1-1.3 (conflict check for Sipho Ndlovu)
- Day 2-3: Steps 2.1, 2.9, 2.17 (conflict checks for Apex, Moroka, QuickCollect)
- Day 14: Steps 14.1-14.5 (conflict detection)
- Day 75: Steps 75.1-75.5 (complex conflict scenarios)

GAP-D0-01 (no matter templates) blocks:
- Day 1: Steps 1.16-1.22 (create matter from Litigation template)
- Day 2-3: Steps 2.5-2.7, 2.13-2.15, 2.20-2.24 (create matters from templates)
- All subsequent days that reference matters created from templates
