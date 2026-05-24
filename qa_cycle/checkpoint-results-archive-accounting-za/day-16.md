# Day 16 Checkpoint Results — Trust AFS Engagement

**Date**: 2026-05-15
**Actor**: Thandi (Owner)
**Scenario**: accounting-za-90day-keycloak-v2.md
**Branch**: main (OBS-4006 fix merged via PR #1308)

---

## Pre-Day 16: OBS-4006 Verification

### Verification Step 1: Trust fields rendering on Moroka Family Trust
- **Action**: Navigated to Moroka Family Trust client detail page (ID: `64f79e3d-46b0-4d4b-b9cc-53d1c3968231`)
- **Expected**: "SA Accounting -- Trust Details" section renders all 6 fields
- **Observed**: Section renders with all 6 fields:
  - Trust Registration Number (required) -- editable text input
  - Trust Deed Date (required) -- date input
  - Trust Type (required) -- combobox with options: Inter Vivos, Testamentary, Business Trust
  - Names of Trustees (optional) -- text input
  - Trustee Appointment Type (optional) -- combobox with options: Appointed, Ex Officio, Both
  - Letters of Authority Date (optional) -- date input
- **Result**: **PASS**
- **Evidence**: `qa_cycle/evidence/day-15/obs-4006-trust-fields-rendering.png`

### Verification Step 2: Fill required trust fields and save
- **Action**: Filled Trust Registration Number = "IT 2345/2020", Trust Deed Date = "2020-03-15", Trust Type = "Inter Vivos (Living Trust)", Names of Trustees = "Sipho Moroka, Lerato Moroka, Thabo Moroka". Clicked "Save Custom Fields".
- **Expected**: Save succeeds, Required Fields counter updates
- **Observed**: Save succeeded. Required Fields went from 2/5 to 5/5. Client Readiness jumped to 67%.
- **Result**: **PASS**

### Verification Step 3: Values persist after reload
- **Action**: Hard-reloaded page (full navigation)
- **Expected**: All filled values persist
- **Observed**: All 4 values persisted: Trust Registration Number = "IT 2345/2020", Trust Deed Date = "2020-03-15", Trust Type = "Inter Vivos (Living Trust)" [selected], Names of Trustees = "Sipho Moroka, Lerato Moroka, Thabo Moroka"
- **Result**: **PASS**

### Verification Step 4: Non-trust customer -- trust fields NOT visible
- **Action**: Navigated to Kgosi Holdings (Pty) Ltd detail page (ID: `90d93d67-b462-4fe9-9732-656af5ab889e`, entity type = PTY_LTD)
- **Expected**: Trust fields section does not render (or renders empty)
- **Observed**: "SA Accounting -- Trust Details" section header is present (field group was assigned) but no trust-specific field inputs are rendered. Only the section title appears with no form fields. Visibility condition correctly hides all 6 fields for non-trust entity types.
- **Result**: **PASS**
- **Evidence**: `qa_cycle/evidence/day-15/obs-4006-kgosi-no-trust-fields.png`
- **Note**: Empty section header still renders even when all fields are hidden. Cosmetic issue only -- the core visibility condition logic works correctly.

### OBS-4006 Verdict: **VERIFIED**

---

## Day 16 Checkpoints

### 16.1 — Create engagement from "Annual Trust Financial Statements" template
- **Action**: Navigated to Engagements list with Moroka client pre-selected. "New from Template" dialog opened. Selected "Annual Trust Financial Statements" (7 tasks).
- **Expected**: Template available, selectable
- **Observed**: Template present in list with "7 tasks" badge. Selected and proceeded to configure step.
- **Result**: **PASS**

### 16.2 — Configure and create engagement
- **Action**: Configured engagement:
  - Name: "Moroka Family Trust -- FY2025/26 Annual Trust AFS"
  - Client: Moroka Family Trust (auto-filled)
  - Lead: Thandi Thornton
  - Reference: TAFS-2026-0001
  - Description: Auto-filled from template
- **Expected**: Engagement created with pre-populated task list
- **Observed**: Engagement created. Redirected to detail page at `/projects/0a39ccb1-070d-4078-9240-4a4fab254017`. 7 tasks instantiated. Status: Active. Ref: TAFS-2026-0001. 1 member (Thandi).
- **Result**: **PASS**
- **Engagement ID**: `0a39ccb1-070d-4078-9240-4a4fab254017`

### 16.3 — Verify template-instantiated tasks
- **Action**: Opened Tasks tab
- **Expected**: Tasks include trust deed review, beneficial distributions, IT3T generation, AFS drafting
- **Observed**: All 7 tasks present:
  1. Trust deed review -- Thandi
  2. Beneficiary distribution schedule -- Thandi
  3. IT3(t) certificate generation -- initially Unassigned
  4. Draft annual financial statements -- Thandi
  5. ITR12T preparation -- Thandi
  6. Trustee sign-off -- Thandi
  7. SARS eFiling submission & archive -- Thandi
- **Result**: **PASS**

### 16.4 — Assign tasks to Thandi and Bob
- **Action**: Added Bob as engagement member (via Members tab > Add Member). Then opened IT3(t) certificate generation task detail and assigned to Bob Ndlovu via assignee combobox.
- **Expected**: Bob available in assignee dropdown, assignment persists
- **Observed**: Bob appeared in the assignee list after being added as member. Assignment saved. Task list now shows: 6 tasks to Thandi, 1 task (IT3(t) certificate generation) to Bob.
- **Result**: **PASS**

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| OBS-4006 trust fields render (6/6) | **PASS** | All 3 required + 3 optional fields visible |
| OBS-4006 save + persist | **PASS** | Values persist across reload |
| OBS-4006 non-trust negative check | **PASS** | Fields hidden on PTY_LTD client |
| OBS-4006 overall | **VERIFIED** | PR #1308 fix confirmed working |
| 16.1 Template available | **PASS** | "Annual Trust Financial Statements" in template picker |
| 16.2 Engagement created | **PASS** | ID: 0a39ccb1-..., Ref: TAFS-2026-0001 |
| 16.3 Tasks instantiated (7) | **PASS** | Trust deed review, distributions, IT3(t), AFS, etc. |
| 16.4 Tasks assigned | **PASS** | 6 Thandi + 1 Bob (IT3(t)) |

**Day 16 Result**: 4 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps.
**OBS-4006**: VERIFIED (4 verification steps all PASS).
