# QA Cycle Status — Portal Experience & Proposal Acceptance / Keycloak Dev Stack (2026-03-25)

## Current State

- **QA Position**: Not started — Infra verification COMPLETE, stack is READY
- **Cycle**: 0 (setup)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_portal_2026-03-25`
- **Scenario**: `qa/testplan/portal-experience-proposal-acceptance.md`
- **Focus**: Magic link auth, portal home, project/document viewing, proposal lifecycle (create->send->accept->project), document download, org branding, cross-customer data isolation
- **Auth Mode**: Keycloak (not mock-auth). Portal uses magic link tokens. Firm uses JWT via gateway BFF.
- **Depends On**: Phase 49 T0 seed data (Thornton & Associates lifecycle with 4 customers, portal contacts, proposals, documents)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP (PID 66435) |
| Backend | http://localhost:8080 | UP (PID 95686) |
| Gateway (BFF) | http://localhost:8443 | UP (PID 20774) |
| Portal | http://localhost:3002 | UP (PID 66487) |
| Keycloak | http://localhost:8180 | UP (realm=docteams verified) |
| Mailpit | http://localhost:8025 | UP (API responding) |
| LocalStack | http://localhost:4566 | UP (v4.13.2) |
| Postgres | b2mash.local:5432 | UP (backend actuator healthy) |

## Existing Data (from previous cycles)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (admin)
- All passwords: `password`

### Customers (5 total)

| Customer | Status | Portal Contact | Contact Email |
|----------|--------|----------------|---------------|
| Naledi Corp QA | ACTIVE | Naledi Corp QA | naledi@qatest.local |
| Kgosi Holdings QA Cycle2 | OFFBOARDED | Kgosi Holdings QA | kgosi@qatest.local |
| Lifecycle Chain C4 | OFFBOARDED | Lifecycle Chain C4 | lifecycle-c4@qatest.local |
| Invalid Transition Test Customer | PROSPECT | (none) | -- |
| Test Integrity Customer | OFFBOARDED | Test Integrity Customer | integrity@qatest.local |

**Note**: The test plan referenced "Kgosi (Thabo)" and "Vukani (Sipho)" from Phase 49 seed data. These do NOT exist. The actual data is from previous QA cycles with different names. Only **Naledi Corp QA** is ACTIVE and usable for full portal testing.

### Proposals (6 total, all linked to Naledi Corp QA)

| Number | Title | Status |
|--------|-------|--------|
| PROP-0001 | QA Test Proposal Invalid | DRAFT |
| PROP-0002 | QA Cycle 2 Test Proposal | ACCEPTED |
| PROP-0003 | QA Decline Test Proposal | DECLINED |
| PROP-0004 | QA Expired Test Proposal | ACCEPTED |
| PROP-0005 | GAP-DI-07 Verification - Expired Proposal | EXPIRED |
| PROP-0006 | GAP-DI-07 Regression - Non-Expired Proposal | ACCEPTED |

### Generated Documents (2 found)

| Template | Entity | File |
|----------|--------|------|
| QA Cycle 4 Template Edited | CUSTOMER: Naledi Corp QA | qa-cycle-4-test-template-naledi-corp-qa-2026-03-23.pdf |
| Engagement Letter -- Monthly Bookkeeping | PROJECT: QA Onboarding Verified Project | engagement-letter-monthly-bookkeeping-qa-onboarding-verified-project-2026-03-24.pdf |

### Projects (9 total, 5 linked to Naledi Corp QA)

Includes projects created from accepted proposals (PROP-0002, PROP-0004, PROP-0006) plus earlier QA projects.

## API Auth Note

Direct grant tokens MUST include `scope=openid organization` to get org claims. Without the `organization` scope, the JWT has no org claims and the backend returns 403 (insufficient scope).

```bash
TOKEN=$(curl -sf -X POST "http://localhost:8180/realms/docteams/protocol/openid-connect/token" \
  -d "client_id=gateway-bff" \
  -d "client_secret=docteams-web-secret" \
  -d "grant_type=password" \
  -d "username=thandi@thornton-test.local" \
  -d "password=password" \
  -d "scope=openid organization" | jq -r '.access_token')
```

Portal contacts are under `/api/customers/{id}/portal-contacts` (not a top-level `/api/portal-contacts` endpoint).

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-25T00:00Z | Setup | Portal Experience & Proposal Acceptance QA cycle initialized on branch bugfix_cycle_portal_2026-03-25. Scenario: qa/testplan/portal-experience-proposal-acceptance.md. Reusing org data from previous cycles. |
| 2026-03-25T05:40Z | Infra | Stack verification complete. All 8 services UP. Keycloak realm=docteams verified. Seed data inventoried: 5 customers (1 ACTIVE), 4 portal contacts, 6 proposals (1 DRAFT), 2 generated documents, 9 projects. Phase 49 named contacts (Kgosi/Thabo, Vukani/Sipho) NOT present — data is from earlier QA cycles. PROP-0001 (DRAFT) available for proposal lifecycle testing. Dev stack marked READY. |
