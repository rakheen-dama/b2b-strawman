# QA Cycle Status — Phase 55 Legal Foundations / Keycloak Dev Stack (2026-04-04)

## Current State

- **QA Position**: ALL_TRACKS_COMPLETE (Cycle 1). T0, T7, T1 (all), T2.1, T3.1–T3.2, T4 (all, API), T5 (all, API), T6 (BLOCKED), T8 (partial), T9 (SKIP — job not run), T10 (all, API+UI) complete.
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
| GAP-P55-001 | Demo provisioning `addMember` fails in KC 26.5.0 | major | WONT_FIX | Backend | — | KC 26.5 Organizations API changed. `POST /organizations/{orgId}/members` requires invite flow. Needs KC API research. See `fix-specs/GAP-P55-001.md`. |
| GAP-P55-002 | Prescription tracker does not detect expired status | major | SPEC_READY | Backend | — | No dynamic status evaluation at query time. Fix: compute effective status in `buildResponse()`. See `fix-specs/GAP-P55-002.md`. |
| GAP-P55-003 | Direct URL for gated modules shows generic error | minor | SPEC_READY | Frontend | — | Pages use `notFound()` for disabled modules. Fix: render module-specific "not available" message instead. See `fix-specs/GAP-P55-003.md`. |
| GAP-P55-004 | Court Calendar missing date range and client filters | minor | SPEC_READY | Frontend | — | Backend supports all filters. UI only wires status+type. Fix: add date range inputs + client search. See `fix-specs/GAP-P55-004.md`. |
| GAP-P55-005 | Court date type badge shows raw enum with underscore | cosmetic | SPEC_READY | Frontend | — | `dateTypeLabel()` doesn't handle underscores. Fix: split on `_`, capitalize each word, join with `-`. Grouped with GAP-P55-014. See `fix-specs/GAP-P55-005+014.md`. |
| GAP-P55-006 | "New Court Date" dialog crashes with TypeError | major | SPEC_READY | Frontend | — | `fetchProjects()` can return `undefined` when API response shape is unexpected. Fix: defensive `?? []` on return values. Grouped with GAP-P55-011. See `fix-specs/GAP-P55-006+011.md`. |
| GAP-P55-007 | Postponement reason not visible in court date detail | minor | SPEC_READY | Backend | — | Postpone reason logged to audit only, not stored on entity. Fix: store in `outcome` field (like cancel does). See `fix-specs/GAP-P55-007.md`. |
| GAP-P55-008 | No Edit action for court dates | minor | SPEC_READY | Frontend | — | Backend `PUT /api/court-dates/{id}` exists. Frontend lacks edit dialog + menu item. Fix: create EditCourtDateDialog, wire into actions dropdown. See `fix-specs/GAP-P55-008.md`. |
| GAP-P55-009 | Dashboard "Upcoming Court Dates" widget shows error | minor | SPEC_READY | Frontend | — | Wrong URL: frontend calls `/api/court-calendar/upcoming` but endpoint is at `/api/court-dates/upcoming`. Fix: correct the URL path. See `fix-specs/GAP-P55-009.md`. |
| GAP-P55-010 | Adverse party search does not match on partial name tokens | major | SPEC_READY | Backend | — | `pg_trgm similarity()` fails on short tokens vs long names. Fix: add `ILIKE` substring match as primary search path. See `fix-specs/GAP-P55-010.md`. |
| GAP-P55-011 | Conflict Check page crashes on load (TypeError in Controller) | major | SPEC_READY | Frontend | — | Same root cause as GAP-P55-006. Fix: defensive `?? []` on `fetchProjects()`/`fetchCustomers()` return values. See `fix-specs/GAP-P55-006+011.md`. |
| GAP-P55-012 | Tariff Schedules page crashes on load (data shape mismatch) | major | SPEC_READY | Frontend | — | Backend returns `List<>` (array), frontend expects `{ content, page }`. Fix: change frontend to accept array. See `fix-specs/GAP-P55-012.md`. |
| GAP-P55-013 | No manual trigger for court date reminder job | minor | WONT_FIX | Backend | — | Out of scope for bugfix cycle. Job logic verified correct via code review. GAP-P55-002 fix addresses the primary impact. See `fix-specs/GAP-P55-013.md`. |
| GAP-P55-014 | "Customer:" label on matter detail instead of "Client:" for legal tenant | cosmetic | SPEC_READY | Frontend | — | Hardcoded "Customer:" label not using terminology system. Fix: use `<TerminologyText>`. Grouped with GAP-P55-005. See `fix-specs/GAP-P55-005+014.md`. |

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
| 2026-04-04T01:16Z | QA | T4: Conflict Check — page crashes on load (GAP-P55-011, same Controller .map() bug as GAP-P55-006). All 10 sub-checkpoints tested via API: exact ID match PASS, reg number match PASS, fuzzy name match PASS (0.84 score), alias match PASS (POTENTIAL_CONFLICT, 0.65), no-conflict PASS, customer cross-check PASS (EXISTING_CLIENT), resolve PROCEED PASS, resolve WAIVER_OBTAINED PASS, history+filters PASS, validation PASS. |
| 2026-04-04T01:16Z | QA | T5: Tariff Management — page crashes on load (GAP-P55-012, data shape mismatch: API returns array, frontend expects paginated). All backend operations via API: 1 system schedule (19 items), immutability enforced (400 on PUT/DELETE), clone works (deep copy, 19 items), edit/add/delete items on custom schedule PASS, create from scratch PASS. |
| 2026-04-04T01:16Z | QA | T6: Invoice Tariff Integration — BLOCKED. Invoice creation fails 422 (customer missing address_line1, city, country, tax_number). These are pack-managed fields requiring field group setup. Time entries also require tasks (none exist). |
| 2026-04-04T01:16Z | QA | T8: Matter Detail Integration — Court Dates tab on Mabena matter shows 2 dates (PRE_TRIAL postponed, TRIAL scheduled), sorted chronologically. "New Court Date" button present. Adverse Parties tab on Mining Rights shows "BHP Minerals SA (Pty) Ltd" (Opposing Party). Prescription trackers not shown on matter detail page. |
| 2026-04-04T01:16Z | QA | T9: Notifications — SKIP. Reminder job cron-only (6 AM daily), no manual trigger (GAP-P55-013). Zero COURT_DATE_REMINDER or PRESCRIPTION_WARNING notifications. Only 4 AUTOMATION_ACTION_FAILED notifications exist. Code review confirms job logic is correct. |
| 2026-04-04T01:16Z | QA | T10: Multi-Vertical Coexistence — Data isolation PASS (Moyo 4 projects/4 customers, Thornton 14/5, zero overlap). Module gating PASS (403 on all legal APIs for Thornton). Pack isolation PASS (legal: "SA Legal — Matter Details", accounting: "SA Accounting — Engagement Details"). Terminology PASS (sidebar: "Matters"/"Clients") with minor inconsistency on detail page (GAP-P55-014: "Customer:" instead of "Client:"). Tariff isolation PASS (403 for Thornton). |
| 2026-04-04T01:16Z | QA | ALL TRACKS COMPLETE (Cycle 1). 14 total gaps: 0 blocker, 6 major, 5 minor, 2 cosmetic, 1 dashboard. Core architecture (schema isolation, module gating, conflict detection, tariff management) is solid at the API level. Primary issue: 3 legal module frontend pages crash on load (court date dialog, conflict check page, tariffs page) — all related to undefined array access in react-hook-form/component rendering. |
| 2026-04-04T02:00Z | Product | Triage complete. 14 gaps analyzed, codebase searched for root causes. Results: 12 SPEC_READY, 2 WONT_FIX. |
| 2026-04-04T02:00Z | Product | WONT_FIX: GAP-P55-001 (KC 26.5 API change, needs research), GAP-P55-013 (manual job trigger, out of scope). |
| 2026-04-04T02:00Z | Product | Grouped specs: GAP-P55-006+011 (same defensive `?? []` pattern), GAP-P55-005+014 (both display/terminology). |
| 2026-04-04T02:00Z | Product | Root causes confirmed: GAP-P55-009 = wrong URL path (`/api/court-calendar/upcoming` vs `/api/court-dates/upcoming`). GAP-P55-012 = backend returns `List<>`, frontend expects paginated `{ content, page }`. GAP-P55-010 = `pg_trgm similarity()` fails on short tokens. GAP-P55-002 = no dynamic status computation at query time. |
| 2026-04-04T02:00Z | Product | Fix specs written to `qa_cycle/fix-specs/GAP-P55-*.md`. 9 spec files total. Priority order: (1) crashers 006+011, 012; (2) wrong data 002, 009, 010; (3) missing features 004, 007, 008; (4) cosmetic 005+014, 003. |
