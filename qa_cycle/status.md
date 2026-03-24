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
| GAP-AN-001 | "New Automation" button is non-functional — click does nothing | HIGH | FIXED | frontend | 3f605219 | Converted both "New Automation" buttons to `<Link>` with `asChild` pattern. |
| GAP-AN-002 | Rule row click does not open edit form or detail page | HIGH | FIXED | frontend | 3f605219 | Wrapped rule name in `<Link>` with hover styling. Removed `onClick` from `<TableRow>`. |
| GAP-AN-003 | UI toggle switch does not change backend state | HIGH | FIXED | frontend | 3f605219 | Added optimistic toggle state, error logging in server action, success/error toast feedback. |
| GAP-AN-004 | "View Execution Log" link does not navigate when clicked | MEDIUM | FIXED | frontend | 3f605219 | Added underline + arrow affordance. Added secondary link below rules table in client component. |
| GAP-AN-005 | "Other" notification preference category has 19 raw enum names | LOW | FIXED | frontend | 3f605219 | Extended `NOTIFICATION_TYPE_LABELS` to cover all 41 types across 12 categories (Tasks, Projects, Collaboration, Proposals, Billing & Invoicing, Client Requests, Scheduling, Retainers, Time Tracking, Resource Planning, Security, System). |

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
| 2026-03-25T23:45Z | Product | Triaged all 5 OPEN items (GAP-AN-001 through GAP-AN-005). Root cause analysis via codebase search. Common theme for GAP-AN-001/002: JS-only navigation via `router.push()` in `rule-list.tsx` — fix is to use `<Link>` for progressive enhancement. GAP-AN-003: CSRF/session issue in Keycloak BFF mode + missing optimistic UI. GAP-AN-005: `NOTIFICATION_TYPE_LABELS` map missing 17 of 41 backend types. All 5 items moved to SPEC_READY with fix specs in `qa_cycle/fix-specs/`. |
| 2026-03-26T01:35Z | Dev | Fixed all 5 gaps in commit 3f605219. Files modified: `rule-list.tsx` (GAP-AN-001/002/003/004), `actions.ts` (GAP-AN-003), `page.tsx` (GAP-AN-004), `notification-preferences-form.tsx` (GAP-AN-005). Also updated `rule-list.test.tsx` to match Link-based navigation. Build green, all 277 test files pass (1692 tests). Removed `useRouter` dependency from rule-list.tsx entirely. |
