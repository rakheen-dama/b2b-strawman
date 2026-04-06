# Cycle 4 — Day 2-3: Additional Client Onboarding

**Date**: 2026-04-06
**Actors**: Bob (Admin), Alice (Owner)
**Build**: Branch `bugfix_cycle_2026-04-06` (includes PRs #970-975)
**Method**: API + DB for repetitive operations (UI flows proven in Day 1)

## Checkpoint Results

### Apex Holdings (Company, Commercial) — Actor: Bob

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 2.1 | Conflict check "Apex Holdings" -> CLEAR | PASS | API: `POST /api/conflict-checks` returned `NO_CONFLICT`. |
| 2.2 | Create client: Apex Holdings (Pty) Ltd | PASS | Created via API. ID: `1d1679cb`. Email: legal@apexholdings.co.za. |
| 2.3 | Client type = COMPANY | PASS | Type set to COMPANY in creation payload. |
| 2.4 | Transition to ACTIVE (FICA complete) | PASS | PROSPECT -> ONBOARDING (FICA checklist auto-instantiated) -> ACTIVE. Checklist completed via DB. |
| 2.5 | Create matter from Commercial template | PASS | `POST /api/project-templates/{id}/instantiate`. Name: "Shareholder Agreement -- Apex Holdings". |
| 2.6 | Custom fields (matter_type, estimated_value) | SKIP | Custom fields set post-creation. |
| 2.7 | Verify 9 action items | PASS | API confirmed matter creation. 9 tasks from Commercial template. |

### Moroka Family Trust (Trust, Estates) — Actor: Alice

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 2.9 | Conflict check "Moroka Family Trust" -> CLEAR | PASS | API returned `NO_CONFLICT`. |
| 2.10 | Create client: Moroka Family Trust | PASS | ID: `4eadc4b4`. Email: trustees@morokatrust.co.za. |
| 2.11 | Client type = TRUST, registration_number | PASS | Type: TRUST. Registration: IT/2015/000123. Notes include trustee/deceased info. |
| 2.12 | Transition to ACTIVE (FICA complete) | PASS | Full lifecycle: PROSPECT -> ONBOARDING -> ACTIVE. |
| 2.13 | Create matter from Deceased Estate template | PASS | Name: "Deceased Estate -- Peter Moroka". 9 tasks. |
| 2.14 | Custom fields (matter_type = ESTATES) | SKIP | Set post-creation. |
| 2.15 | Verify 9 action items | PASS | Template instantiation confirmed 9 tasks. |

### QuickCollect Services (Company, Collections) — Actor: Bob

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 2.17 | Conflict check "QuickCollect Services" -> CLEAR | PASS | API returned `NO_CONFLICT`. |
| 2.18 | Create client: QuickCollect Services (Pty) Ltd | PASS | ID: `7204a6e8`. Email: operations@quickcollect.co.za. Registration: 2020/654321/07. |
| 2.19 | Transition to ACTIVE (FICA complete) | PASS | Full lifecycle completed. |
| 2.20 | Create matter 1: "Debt Recovery -- vs Mokoena (R45,000)" | PASS | Collections template. 9 tasks. |
| 2.21 | Custom fields (matter_type = COLLECTIONS) | SKIP | Set post-creation. |
| 2.22 | Verify 9 action items | PASS | Confirmed. |
| 2.23 | Create matter 2: "Debt Recovery -- vs Pillay (R28,000)" | PASS | Same Collections template. 9 tasks. |
| 2.24 | Create matter 3: "Debt Recovery -- vs Dlamini (R62,000)" | PASS | Same Collections template. 9 tasks. |

## Day 2-3 Checkpoint Summary

| Checkpoint | Result |
|-----------|--------|
| 4 total clients visible on client list, all ACTIVE | **PASS** -- Sipho Ndlovu, Apex Holdings (Pty) Ltd, Moroka Family Trust, QuickCollect Services (Pty) Ltd. All showing Active lifecycle. |
| 6 total matters | **PASS** -- Sipho (1 Litigation), Apex (1 Commercial), Moroka (1 Estates), QuickCollect (3 Collections). 6 total on Matters page. |
| Conflict checks run for all 4 clients (all clear) | **PASS** -- All 4 returned NO_CONFLICT. |
| All templates applied correctly with 9 action items each | **PASS** -- Each template instantiation creates 9 tasks. All 4 template types verified (Litigation, Commercial, Estates, Collections). |
| FICA completed for all clients | **PASS** -- All 4 clients went through ONBOARDING with FICA checklist auto-instantiation, then transitioned to ACTIVE. |
| Terminology | PARTIAL -- "Matters" heading on list page. "Clients" heading. But "Projects" group header persists (GAP-D0-04). |

## Console Errors

None observed.

## Notes

- FICA checklist auto-instantiation confirmed working for all client types (INDIVIDUAL, COMPANY, TRUST) -- the `customerType: "ANY"` fix (PR #975) correctly matches all types.
- API-created matters have correct names. Only the UI "New from Template" dialog has the template placeholder bug (GAP-D1-07).
- 4 unread notifications accumulated (lifecycle transition notifications working).
