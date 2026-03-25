# QA Cycle Status — Automation & Notification Verification / Keycloak Dev Stack (2026-03-25)

## Current State

- **QA Position**: Cycle 3 fixes applied. OBS-AN-006 (gateway 302), OBS-AN-007 (trigger labels), GAP-AN-003 (toggle switch) all FIXED. Gateway restarted. Ready for verification.
- **Cycle**: 3 (fixes applied, awaiting verification)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
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
| GAP-AN-001 | "New Automation" button is non-functional — click does nothing | HIGH | VERIFIED | frontend | 3f605219 | Cycle 2: Link navigates to `/new` page. Form loads with Name, Description, Trigger, Conditions, Actions. |
| GAP-AN-002 | Rule row click does not open edit form or detail page | HIGH | VERIFIED | frontend | 3f605219 | Cycle 2: Rule names are `<a>` links to `/settings/automations/{id}`. Detail page loads with full config form. |
| GAP-AN-003 | UI toggle switch does not change backend state | HIGH | FIXED | frontend + gateway | d6643210 | Cycle 3: Resolves with OBS-AN-006 fix. Gateway now returns 401 for /api/** instead of 302. Frontend detects 3xx as auth failures. |
| GAP-AN-004 | "View Execution Log" link does not navigate when clicked | MEDIUM | VERIFIED | frontend | 3f605219 | Cycle 2: Link has correct href, target page loads with execution history. Two links present (header + footer). |
| GAP-AN-005 | "Other" notification preference category has 19 raw enum names | LOW | VERIFIED | frontend | 3f605219 | Cycle 2: All 46 types properly labeled across 12 categories. Zero raw enum names. No "Other" category. |
| OBS-AN-006 | Gateway BFF returns 302 for all server action mutations | HIGH | FIXED | gateway + frontend | d6643210 | Cycle 3: Added `.exceptionHandling()` with `HttpStatusEntryPoint(401)` for `/api/**` in GatewaySecurityConfig. Frontend `apiRequest` uses `redirect:"manual"` and detects 3xx as auth failures. Uses `PathPatternRequestMatcher` (Spring Security 7.0). |
| OBS-AN-007 | Trigger type badge shows raw enum for some types | LOW | FIXED | frontend | d6643210 | Cycle 3: Added PROPOSAL_SENT and FIELD_DATE_APPROACHING to TriggerType union, TRIGGER_TYPE_CONFIG, rule-form options, and trigger-config-form simple triggers. |

## Cycle 2 Summary

**Results file**: `qa_cycle/checkpoint-results/automation-notif-cycle2.md`

**Fix verification**: 4 of 5 VERIFIED (GAP-AN-001, 002, 004, 005). GAP-AN-003 REOPENED (frontend code correct but gateway BFF blocks mutations).

**Tracks tested**: T2.2 (INVOICE_STATUS_CHANGED), T2.4 (TIME_ENTRY_CREATED), T6.2 (preference save/persistence via API)

**New findings**:
- INVOICE_STATUS_CHANGED trigger fires correctly (InvoicePaidEvent). Action failed on ORG_ADMINS recipient resolution (data issue, not engine bug).
- TIME_ENTRY_CREATED trigger fires correctly (TimeEntryChangedEvent). SEND_NOTIFICATION with TRIGGER_ACTOR works end-to-end.
- Notification preference save/persistence works via backend API. UI save blocked by gateway BFF mutation issue.
- Gateway BFF returns HTTP 302 for all mutation requests from server actions (OBS-AN-006). This is the root cause of GAP-AN-003 remaining broken despite correct frontend code.

**Deferred**: T4 (email content verification) -- no SEND_EMAIL actions triggered in this cycle.

---

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
| 2026-03-25T23:50Z | QA | Cycle 2 executed. Verified 4 of 5 fixes (GAP-AN-001/002/004/005 VERIFIED, GAP-AN-003 REOPENED). Tested T2.2 (INVOICE_STATUS_CHANGED -- trigger fires, action failed on ORG_ADMINS resolution), T2.4 (TIME_ENTRY_CREATED -- full pipeline works), T6.2 (preference save works via API). Discovered root cause of GAP-AN-003: gateway BFF returns 302 for all server action mutations. Filed OBS-AN-006 (gateway mutation issue) and OBS-AN-007 (trigger type badge inconsistency). T4 (email content) deferred. Results: qa_cycle/checkpoint-results/automation-notif-cycle2.md |
| 2026-03-26T02:00Z | Product | Triaged 3 items (GAP-AN-003 reopened, OBS-AN-006, OBS-AN-007). Deep investigation of gateway BFF 302 root cause: traced full auth flow from browser SESSION cookie through Next.js server action to gateway SecurityConfig. Root cause: `oauth2Login()` default AuthenticationEntryPoint returns 302 for unauthenticated /api/** requests instead of 401; Node.js fetch follows redirects silently. GAP-AN-003 blocked by OBS-AN-006 (no additional frontend changes needed). OBS-AN-007: TriggerType union and TRIGGER_TYPE_CONFIG missing 2 of 10 backend enum values. All 3 items moved to SPEC_READY. Fix specs: `OBS-AN-006.md`, `GAP-AN-003-v2.md`, `OBS-AN-007.md`. |
| 2026-03-26T02:15Z | Dev | Fixed OBS-AN-006, OBS-AN-007, GAP-AN-003 in commit d6643210. Gateway: added `.exceptionHandling()` with `HttpStatusEntryPoint(UNAUTHORIZED)` for `/api/**` using `PathPatternRequestMatcher` (Spring Security 7.0 removed `AntPathRequestMatcher`). Frontend: added `redirect:"manual"` to fetch and 3xx detection in `apiRequest`. Added PROPOSAL_SENT and FIELD_DATE_APPROACHING to TriggerType union, TRIGGER_TYPE_CONFIG, rule-form options, trigger-config-form simple triggers. Gateway compile green. Frontend build green, 277 test files pass (1692 tests). Gateway restarted. NEEDS_REBUILD=false. |
