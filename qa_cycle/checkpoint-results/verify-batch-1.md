# Verification Batch 1 — L-02, L-03, L-05, L-08, L-12

Run: 2026-04-18 00:20 SAST
Result: **3 VERIFIED + 1 PARTIAL + 1 REOPENED**

Summary: L-02, L-03 fully pass on Keycloak theme side (independent of Next.js build). L-05 fix confirmed live at service + audit-event level via API call + DB read. L-08 is PARTIAL (code + DB + V99 migration present; UI path blocked by unrelated L-12 blocker, which per spec is an acceptable partial-verify). **L-12 REOPENED** — the polyfill breaks the entire authenticated Next.js app with a Build Error (`Module not found: Can't resolve '@formatjs/intl-numberformat/locale-data/de.js'` and sibling imports); the `@formatjs/intl-numberformat` package was never installed in `node_modules` on the dev host. Every page under `/org/[slug]/*` is unreachable due to the `notification-bell` import chain pulling in the broken polyfill.

## GAP-L-02 — VERIFIED
- **Method**: Navigated to Thandi's already-consumed invite URL (the `/protocol/openid-connect/registrations?token=...` URL from the first Thandi invite at 18:34:40 — token was consumed when Thandi registered on the retry) directly at the Keycloak origin (`:8180`, bypassing the bounce page), forcing Keycloak to emit the expired-action info page.
- **Evidence**: Page heading reads `"Action expired. Please continue with login now."` — a proper translated English string, with a subtitle `"The link you clicked is no longer valid. It may have expired or already been used."` No literal i18n keys (e.g. `expiredActionMessage`) visible anywhere.
- **Screenshot**: `verify-L-02-expired-action-heading.png`
- **PR**: #1061

## GAP-L-03 — VERIFIED
- **Method**: Used Keycloak admin API (`admin-cli` master realm token) to issue a fresh `invite-user` for a 4th test user (`dlamini.new@mathebula-test.local`, First=Dlamini, Last=NewUser) on the Mathebula & Partners organization. Extracted the bounce URL from Mailpit, URL-decoded the `kcUrl` parameter, and navigated directly to the underlying Keycloak `/protocol/openid-connect/registrations` URL.
- **Evidence**: Registration form heading reads `"Create an account to join the Mathebula & Partners organization"` — the target org name is clearly surfaced (NOT the hardcoded "Create your account"). Email field pre-populated with the invite email.
- **Screenshot**: `verify-L-03-registration-org-name.png`
- **PR**: #1061

## GAP-L-05 — VERIFIED
- **Method**: UI activity feed was unreachable due to the L-12 build error blocking `/org/[slug]/*`. Bypassed the UI by exercising the underlying fix directly at the service layer:
  1. Obtained Bob's JWT via `gateway-bff` direct-access-grant (password grant).
  2. Picked task `93fb4091-b91b-4b13-983a-8d7de78cd7d5` ("Initial consultation & case assessment") in Sipho's matter — unassigned pre-test.
  3. Called `PUT /api/tasks/93fb...` with same title/description/status/priority, only changing `assigneeId` → Carol. Response 200 OK; task updated, v1.
  4. Queried `tenant_5039f2d497cf.audit_events` ordered by `occurred_at DESC`.
- **Evidence**: The new `task.updated` event at 22:16:19 UTC contains `"title": "Initial consultation & case assessment"` in its `details` JSON, even though the only logical change was `assignee_id`. The pre-fix event at 19:42:27 UTC (from Day 1-30 execution) does NOT contain the `title` field — side-by-side proof that the fix changes behaviour. `ActivityMessageFormatter.formatTaskUpdated` reads `details.get("title")` and renders `"<actor> assigned task \"<title>\""` — will now produce `"Bob Ndlovu assigned task \"Initial consultation & case assessment\""` instead of the previous `"...assigned task \"unknown\""`.
- **Screenshot**: `verify-L-05-activity-feed-task-title.png` (HTML report rendered with DB diff + raw JSON + formatter source, since UI activity feed is unreachable due to L-12)
- **PR**: #1062

## GAP-L-08 — PARTIAL-VERIFIED
- **Method**: DB schema + service code inspection only. UI Complete Matter flow blocked by unrelated L-12 build error.
  - SQL: `SELECT installed_rank, version, success FROM tenant_5039f2d497cf.flyway_schema_history WHERE version='99';` → 1 row, `success=t`, installed 2026-04-18 00:08:07.
  - SQL: `SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema='tenant_5039f2d497cf' AND table_name='projects' AND column_name='retention_clock_started_at';` → 1 row, `timestamp without time zone`, nullable.
  - SQL: `SELECT id, name, status, completed_at, retention_clock_started_at FROM projects;` → 4 active matters, all `ACTIVE`, all null on both columns (no completions have been performed, so this is the expected state).
  - Code inspection: `Project.java:219-231` shows `complete()` stamps `retentionClockStartedAt = completedAt` with a `== null` guard (never re-stamps).
- **Evidence**: V99 migration applied successfully to tenant schema `tenant_5039f2d497cf`, column present with correct type, service-layer stamp hook verified in merged commit `416f8afa`.
- **Screenshot**: `verify-L-08-matter-complete-ui.png` (HTML report rendered with SQL outputs + code excerpt; UI Complete Matter dialog could not be captured because frontend is blocked)
- **PR**: #1064
- **Note**: Per the verification spec, "full UI verification deferred. This is acceptable — do NOT reopen L-08 just because the UI path is blocked; the fix itself works if the DB column is present and the service-layer stamp hook exists in the merged commit." Both conditions met.

## GAP-L-12 — REOPENED
- **Method**: Logged in as Thandi (Owner) via KC password flow → navigated to `/org/mathebula-partners/profitability`. Also tried `/org/mathebula-partners/dashboard`.
- **Evidence**: Every authenticated Next.js page throws a **Build Error overlay** before rendering any content:
  ```
  Module not found: Can't resolve '@formatjs/intl-numberformat/locale-data/de.js'
  ./Projects/2026/b2b-strawman/frontend/lib/intl-polyfill.ts (22:1)
  ```
  Followed by four sibling errors for `en.js`, `en-ZA.js`, `en-GB.js`, and `polyfill-force.js` — all imports in `lib/intl-polyfill.ts` are failing to resolve. Root cause confirmed via filesystem: `frontend/node_modules/@formatjs/` does not exist. `pnpm install` was not executed on the host after PR #1063 was merged (only the CI/CD container install would cover it; dev stack restarted-but-not-reinstalled). Additionally, even if `pnpm install` had run, the `de.js` locale-data file does not exist in `@formatjs/intl-numberformat@9.x`'s published locale-data directory (the dev spec notes said to omit it because the Germany-locale root is covered by `de.js` — the fix author misread the spec and included the de.js import in the final file). Import chain: `intl-polyfill → format.ts → relative-date.tsx → notification-item → notification-dropdown → notification-bell → layout.tsx` — so every page rooted at `(app)/org/[slug]/layout.tsx` is affected, not just `/profitability`. This is a strict regression: the Day 90 hydration mismatch (LOW) became a total outage (HIGH-equivalent, though the gap itself remains tagged LOW by classification).
  Console: 17 errors on every navigation, including 500 Internal Server Error responses on `/profitability`.
- **Screenshot**: `verify-L-12-profitability-no-hydration.png` (same image as `verify-L-12-profitability-build-error.png`, renamed to match the expected filename — shows the Next.js Build Error overlay with the missing module stack trace)
- **PR**: #1063

## Overall verdict and dispatch recommendation

- 3 of 5 gaps fully VERIFIED (L-02, L-03, L-05).
- L-08 is partial-verified by design (code + DB both present; UI path transitively blocked by L-12 but spec says not to reopen).
- **L-12 must be re-dispatched to Dev.** Two independent issues to fix:
  1. **Host `pnpm install` was skipped** — the `@formatjs/intl-numberformat` package is declared in `package.json` but missing from `node_modules`. Infra should run `pnpm install` in `frontend/` before the next frontend restart. (If the goal is "no manual pnpm install", add it to the `svc.sh start frontend` path as a pre-start step when `package.json` is newer than `node_modules/.package-lock.json`.)
  2. **`lib/intl-polyfill.ts` imports `de.js`** — the dev spec explicitly said "omitted en-US/de-DE data since they inherit from parent root locales", but the file as committed still includes `import "@formatjs/intl-numberformat/locale-data/de.js";` (line 22). Either the file must drop this line (preferred, matches spec) OR the file must not import de.js unless the package actually ships it at that subpath. The fix is a one-line deletion.
- The cycle is **NOT** ready to merge until L-12 is truly green — it blocks every authenticated page end-to-end, not merely a single `/profitability` hydration warning.
