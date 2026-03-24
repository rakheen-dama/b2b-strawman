# QA Cycle Status — Automation & Notification Verification / Keycloak Dev Stack (2026-03-25)

## Current State

- **QA Position**: Cycle 1 complete. Next: Cycle 2 — T2.2 (INVOICE_STATUS_CHANGED), T2.4 (TIME_ENTRY_CREATED), T4 (email content), T6.2+ (preference tests)
- **Cycle**: 1 (complete)
- **Dev Stack**: READY
- **Branch**: `bugfix_cycle_2026-03-25`
- **Scenario**: `qa/testplan/automation-notification-verification.md`
- **Focus**: Automation rules, triggers, actions, email templates, in-app notifications, execution tracking
- **Auth Mode**: Keycloak (not mock-auth). JWT via direct grant with organization scope.
- **Auth Note**: gateway-bff client is confidential — requires `client_secret=docteams-web-secret`

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP |
| Backend | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit | http://localhost:8025 | UP |

## Existing Data (from previous cycles)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (admin)
- All passwords: `password`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-AN-001 | "New Automation" button is non-functional — click does nothing | HIGH | OPEN | frontend | — | Blocks rule creation for end users. Backend API works. |
| GAP-AN-002 | Rule row click does not open edit form or detail page | HIGH | OPEN | frontend | — | Blocks rule editing for end users. Backend PUT works. |
| GAP-AN-003 | UI toggle switch does not change backend state | HIGH | OPEN | frontend | — | Server Action fires (POST to page) but state unchanged. API toggle (POST) works. |
| GAP-AN-004 | "View Execution Log" link does not navigate when clicked | MEDIUM | OPEN | frontend | — | Direct URL `/settings/automations/executions` works fine. |
| GAP-AN-005 | "Other" notification preference category has 19 raw enum names | LOW | OPEN | frontend | — | UI polish — TASK_CANCELLED, PROPOSAL_SENT etc. shown as raw enums instead of human labels in categories. |

## Cycle 1 Summary

**Results file**: `qa_cycle/checkpoint-results/automation-notif-cycle1.md`

**Tracks tested**: T1 (CRUD), T2.3 (TASK_STATUS_CHANGED), T3.1 (CREATE_TASK action), T5 (notifications), T6.1 (preferences view), T7 (vertical templates)

**What works**:
- Backend automation engine fires rules correctly on domain events
- Variable resolution in actions (`{{task.name}}`, `{{project.name}}`)
- CREATE_TASK and SEND_NOTIFICATION actions execute successfully
- Execution log page displays history accurately
- Notification preferences page renders with categories and toggles
- Notifications page shows all notifications with resolved content
- 11 seeded vertical automation templates present and configured

**What doesn't work (UI only)**:
- Cannot create, edit, or toggle automation rules via the UI (GAP-AN-001/002/003)
- "View Execution Log" link doesn't navigate (GAP-AN-004)
- All backend APIs work correctly — the issue is purely frontend wiring

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-25T00:00Z | Setup | Automation & Notification Verification QA cycle initialized on branch bugfix_cycle_2026-03-25. Scenario: qa/testplan/automation-notification-verification.md. Reusing seed data from previous cycles. |
| 2026-03-25T00:05Z | Infra | Dev stack verified READY. All Docker infra (Postgres, Keycloak, Mailpit, LocalStack) healthy. All app services (Backend:8080, Gateway:8443, Frontend:3000, Portal:3002) running and healthy. Keycloak realm 'docteams' active, padmin user present. No restarts needed. |
| 2026-03-25T23:10Z | QA | Cycle 1 executed. Tested T1 (CRUD), T2.3 (task trigger), T3.1 (CREATE_TASK action), T5 (notifications), T6.1 (preferences view), T7 (vertical templates). Found 5 gaps (3 HIGH, 1 MEDIUM, 1 LOW). All UI interaction on automations page is broken; backend API and automation engine work correctly. Results: qa_cycle/checkpoint-results/automation-notif-cycle1.md |
