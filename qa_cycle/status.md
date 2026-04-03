# QA Cycle Status — Phase 55 Legal Foundations / Keycloak Dev Stack (2026-04-04)

## Current State

- **QA Position**: T4.1 (Conflict Check). T0, T7, T1 (all), T2.1, T3.1–T3.2 complete.
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-04`
- **Scenario**: `qa/testplan/phase55-legal-foundations.md`
- **Focus**: Legal foundations — matter types, practice areas, legal entity management, conflict checks, court/jurisdiction registry, legal calendar, trust accounting foundations
- **Auth Mode**: Keycloak (not mock-auth). Firm uses JWT via gateway BFF.

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP (PID 74243) |
| Backend | http://localhost:8080 | UP (PID 72936) |
| Gateway (BFF) | http://localhost:8443 | UP (PID 74067) |
| Portal | http://localhost:3002 | UP (PID 74299) |
| Keycloak | http://localhost:8180 | UP (Docker: b2b-keycloak) |
| Mailpit | http://localhost:8025 | UP (Docker: b2b-mailpit) |
| LocalStack | http://localhost:4566 | UP (Docker: b2b-localstack) |
| Postgres | b2mash.local:5432 | UP (Docker: b2b-postgres) |

## Existing Data (from previous cycles)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (admin)
- All passwords: `password`

## Legal Tenant Data (Moyo & Dlamini Attorneys)

| Entity | Name | ID |
|--------|------|----|
| Org | Moyo & Dlamini Attorneys | moyo-dlamini-attorneys / schema: tenant_555bfc30b94c |
| User | Alice Moyo (owner) | alice@moyo-dlamini.local / password |
| Client | Sipho Mabena | 23a5f2af-2fc0-4aa3-8a01-46e66f42a230 |
| Client | Kagiso Mining (Pty) Ltd | 55da8ecd-31b3-4156-b425-72dedc2771fb |
| Client | Nkosi Family Trust | 87c11958-13e6-4760-ac1b-3023c0ff842f |
| Client | Precious Modise | 7694912e-b5c7-4024-9fe7-0f5261748b3d |
| Matter | Mabena v Road Accident Fund | 6f63b914-dc41-4426-9623-ce52dc54d99b |
| Matter | Mining Rights Application | 54f1c77f-a8c9-4c3a-aeea-b07321f89400 |
| Matter | Nkosi Estate Administration | 4a32ff82-9f0f-433f-8cf4-0aefe763a2ce |
| Matter | Modise Divorce Proceedings | 19f9a8ab-cb1d-49f7-a50f-79f753b0f58b |
| Adverse Party | Road Accident Fund | 35b4abd9-8f4f-4435-bd75-c6c5c6fff9dd |
| Adverse Party | BHP Minerals SA | 5f242c26-f099-4ce7-b398-65f587e7ba12 |
| Adverse Party | Thandi Modise | 94041847-26b6-44b0-9601-8eb4c58110be |
| Adverse Party | James Nkosi | 05deb75e-ded8-4dae-a5d5-2b0da511d68c |
| Court Date | Mabena TRIAL 2026-05-15 | ae76b4a2-997b-4395-9273-1eed3436c2ee |
| Court Date | Mabena PRE_TRIAL 2026-04-10 | 03b5e56e-849f-4a66-b652-d19e3c0d5af1 |
| Court Date | Mining HEARING 2026-04-25 | 367c7e05-22b6-4867-ab9d-479a608c2b3a |
| Court Date | Modise MEDIATION 2026-04-18 | c6d6259d-5ed8-4580-838d-13513b77521b |
| Prescription | Mabena DELICT_3Y (2027-06-15) | 52af6a78-8351-4146-9fc5-e1fdd60bce90 |
| Prescription | Mining GENERAL_3Y (2026-01-10) | 06fef4ef-4c2b-4f85-bae9-e10d4ee57ed5 |

## API Auth Note

Direct grant tokens MUST include `scope=openid organization` to get org claims:

```bash
TOKEN=$(curl -sf -X POST "http://localhost:8180/realms/docteams/protocol/openid-connect/token" \
  -d "client_id=gateway-bff" \
  -d "client_secret=docteams-web-secret" \
  -d "grant_type=password" \
  -d "username=thandi@thornton-test.local" \
  -d "password=password" \
  -d "scope=openid organization" | jq -r '.access_token')
```

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-P55-001 | Demo provisioning `addMember` fails in KC 26.5.0 | major | OPEN | Backend | — | `POST /organizations/{orgId}/members` returns 400 "User does not exist" for all users. Must use `invite-existing-user` flow instead. |
| GAP-P55-002 | Prescription tracker does not detect expired status | major | OPEN | Backend | — | Tracker with past prescription date shows RUNNING instead of EXPIRED/WARNED. No dynamic status evaluation. |
| GAP-P55-003 | Direct URL for gated modules shows generic error | minor | OPEN | Frontend | — | Frontend error boundary catches 403 "Module not enabled" but shows generic "Something went wrong" instead of module-specific message. |
| GAP-P55-004 | Court Calendar missing date range and client filters | minor | OPEN | Frontend | — | Only Status and Type filter dropdowns available. No client/matter or date range filters. |
| GAP-P55-005 | Court date type badge shows raw enum with underscore | cosmetic | OPEN | Frontend | — | "Pre_trial" instead of "Pre-Trial". Applies to all multi-word type enums. |
| GAP-P55-006 | "New Court Date" dialog crashes with TypeError | major | OPEN | Frontend | — | Clicking "New Court Date" crashes page: `TypeError: Cannot read properties of undefined (reading 'map')` in react-hook-form Controller. Backend API works. Blocks T1.3, T1.8 UI creation. |
| GAP-P55-007 | Postponement reason not visible in court date detail | minor | OPEN | Frontend | — | After postponing, the detail panel does not show the reason entered. Cancellation reason IS shown (under "Outcome" label). |
| GAP-P55-008 | No Edit action for court dates | minor | OPEN | Frontend | — | SCHEDULED and POSTPONED court dates have no "Edit" option in Actions dropdown. Only Postpone/Cancel/Record Outcome available. |
| GAP-P55-009 | Dashboard "Upcoming Court Dates" widget shows error | minor | OPEN | Frontend | — | Dashboard widget displays "Unable to load court dates." despite court dates existing and Court Calendar page loading correctly. |
| GAP-P55-010 | Adverse party search does not match on partial name tokens | major | OPEN | Backend | — | Searching "BHP" or "Road" returns 0 results despite matching parties existing. "Minerals" works. Search implementation likely broken (prefix match or threshold issue). |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-04-04T00:00Z | Setup | Phase 55 Legal Foundations QA cycle initialized on branch bugfix_cycle_2026-04-04. Scenario: qa/testplan/phase55-legal-foundations.md. Reusing org data from previous cycles. |
| 2026-04-04T00:17Z | Infra | Docker infra started (Postgres, Keycloak, LocalStack, Mailpit). Keycloak realm docteams verified with platform admin user. Gateway health check was failing due to Redis health indicator (Redis dep exists for production but not used in dev with JDBC sessions). Fixed by disabling `management.health.redis.enabled` in default profile, re-enabled in production profile. All 8 services now UP and healthy. |
| 2026-04-04T00:34Z | QA | T0: Legal tenant provisioned (manual KC invite + internal API workaround for GAP-P55-001). Schema: tenant_555bfc30b94c. Alice (owner) logged in. |
| 2026-04-04T00:38Z | QA | T0: Seeded 4 clients (ACTIVE), 4 matters, 4 adverse parties (linked), 4 court dates (SCHEDULED), 2 prescription trackers. Packs verified (field defs, tariffs, clauses). |
| 2026-04-04T00:40Z | QA | T0.9 readiness checkpoint PASSED. All data counts verified. |
| 2026-04-04T00:42Z | QA | T7: Module gating verified. Accounting tenant (Thornton) sidebar has zero legal nav items. API returns 403 for all 3 legal endpoints. Direct URL shows generic error (GAP-P55-003). |
| 2026-04-04T00:45Z | QA | T1.1: Court Calendar list view verified — 4 dates visible, columns correct, status badges correct. Found GAP-P55-004 (missing filters) and GAP-P55-005 (enum display). |
| 2026-04-04T00:46Z | QA | Checkpoint results written. 5 gaps logged (0 blocker, 2 major, 2 minor, 1 cosmetic). QA position: T1.2. |
| 2026-04-04T00:58Z | QA | T1.2: Calendar View — all 6 checkpoints PASS. Month grid with markers on correct dates, detail popup works on click. |
| 2026-04-04T00:58Z | QA | T1.3: Create Court Date — "New Court Date" button crashes page (GAP-P55-006). Backend API works (created TAXATION for Nkosi Estate via API). |
| 2026-04-04T00:58Z | QA | T1.4: Postpone — PASS. PRE_TRIAL postponed to 2026-04-17, status=POSTPONED. Reason not shown in detail (GAP-P55-007). |
| 2026-04-04T00:58Z | QA | T1.5: Cancel — PASS. Mediation cancelled, reason visible under "Outcome" label. No actions on terminal state. |
| 2026-04-04T00:58Z | QA | T1.6: Record Outcome — PASS. Hearing marked Heard, outcome text visible. Terminal state enforced. |
| 2026-04-04T00:58Z | QA | T1.7: State Machine — terminal states (CANCELLED, HEARD) have no actions. POSTPONED has Cancel+Record Outcome but no Edit (GAP-P55-008). |
| 2026-04-04T00:58Z | QA | T2.1: Prescription List — 2 trackers visible. Mabena DELICT_3Y correct. Mining GENERAL_3Y shows RUNNING (should be EXPIRED, confirms GAP-P55-002). |
| 2026-04-04T00:58Z | QA | T3.1: Adverse Party List — 4 parties visible. Type badges partially formatted (GAP-P55-005 class). |
| 2026-04-04T00:58Z | QA | T3.2: Adverse Party Search — BROKEN. "BHP" and "Road" return 0 results (GAP-P55-010). Backend search query issue. |
| 2026-04-04T00:58Z | QA | Dashboard: "Upcoming Court Dates" widget shows "Unable to load court dates" (GAP-P55-009). |
| 2026-04-04T00:58Z | QA | Checkpoint results written. 10 total gaps (0 blocker, 4 major, 4 minor, 1 cosmetic, 1 from dashboard). QA position: T4.1. |
