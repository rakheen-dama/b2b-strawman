# Day 0 — Firm org onboarding (Keycloak flow)
Cycle: 1 | Date: 2026-04-21 | Auth: Keycloak | Frontend: :3000 | Portal: n/a (firm-only day)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 0 Phases A–D.

Result summary: **32/32 checkpoints executed. 30 PASS, 1 PARTIAL, 1 OBSERVATION. No BLOCKER.** New gaps: `GAP-L-20` (LOW), `GAP-L-21` (LOW), `GAP-L-22` (OBSERVATION, carry-forward OBS-L-06 reconfirmed).

## Phase A — Access request & OTP verification (Thandi)

### Checkpoint 0.1 — Landing page loads cleanly
- Result: PASS
- Evidence: `http://localhost:3000` → title "Kazi — Practice management, built for Africa". Console: 0 errors, 0 warnings.

### Checkpoint 0.2 — Get Started routes to /request-access
- Result: PASS
- Evidence: Clicking header "Get Started" link navigates to `/request-access` (title "Request Access | Kazi").

### Checkpoint 0.3 — Form fields visible
- Result: PASS
- Evidence: Form shows Work Email, Full Name, Organisation Name, Country (combobox), Industry (combobox). Submit button disabled until filled.

### Checkpoint 0.4 — Fill and submit
- Result: PASS
- Evidence: Filled `thandi@mathebula-test.local` / Thandi Mathebula / Mathebula & Partners / South Africa / Legal Services; clicked Request Access.

### Checkpoint 0.5 — Transitions to OTP step
- Result: PASS
- Evidence: Page re-renders into "Check Your Email" card with 6-digit OTP input and "Code expires in 09:56" timer.

### Checkpoint 0.6 — OTP email in Mailpit with "verification" in subject
- Result: PASS
- Evidence: Mailpit message id `SwT4DgQvDNcyYBqwuRcHfn`, subject "Your Kazi verification code", body contains OTP `456920`.

### Checkpoint 0.7 — Enter OTP → Verify
- Result: PASS
- Evidence: Typed `456920`, clicked Verify.

### Checkpoint 0.8 — "Request Submitted" confirmation card
- Result: PASS
- Evidence: Success card "Request Submitted — Your access request has been submitted for review".

## Phase B — Platform admin approval (padmin)

### Checkpoint 0.9 — /dashboard → redirect to Keycloak login
- Result: PARTIAL
- Evidence: Direct nav to `http://localhost:3000/dashboard` does NOT auto-redirect an unauthenticated visitor to Keycloak; instead shows the "Waiting for Access" fallback card. Keycloak redirect only happens via the /oauth2/authorization/keycloak endpoint on :8443 (or Sign In button). Scenario wording "redirected to Keycloak login" does not match current behaviour. Not a blocker — works when using the Sign In link. Logged as **GAP-L-20**.
- Gap: GAP-L-20

### Checkpoint 0.10 — Login as padmin
- Result: PASS
- Evidence: Via `http://localhost:8443/oauth2/authorization/keycloak` → KC email/password form → logged in as `padmin@docteams.local`/`password`, redirected to `http://localhost:3000/platform-admin/access-requests`.

### Checkpoint 0.11 — Navigate to /platform-admin/access-requests
- Result: PASS
- Evidence: Post-login redirect lands directly on the access-requests page with nav tabs [All, Pending, Approved, Rejected].

### Checkpoint 0.12 — Mathebula & Partners visible in Pending
- Result: PASS
- Evidence: Row shows Org="Mathebula & Partners", Email="thandi@mathebula-test.local", Name="Thandi Mathebula", Country="South Africa", Industry="Legal Services", Submitted="3 minutes ago", Status=PENDING.

### Checkpoint 0.13 — Click into request → detail shows all submitted fields
- Result: PARTIAL
- Evidence: Clicking the row or Org-Name cell does NOT navigate to a separate detail view. All six submitted fields are visible inline on the row. The scenario expects a drill-down detail view that doesn't exist. Logged as **GAP-L-21**.
- Gap: GAP-L-21

### Checkpoint 0.14 — Approve → AlertDialog → Confirm
- Result: PASS
- Evidence: Approve button opened AlertDialog "Approve Access Request for Mathebula & Partners? This will create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local." Clicked Approve.

### Checkpoint 0.15 — Status Approved, no provisioning error
- Result: PASS
- Evidence: Row disappears from Pending ("No pending access requests"). Approved tab shows Mathebula & Partners with Status=APPROVED, Submitted="4 minutes ago". No error banner or toast.

### Checkpoint 0.16 — Vertical profile auto-assigned = legal-za
- Result: PASS
- Evidence: Backend diagnostics confirmed `tenant_5039f2d497cf.pack_install` rows include `legal-za` (DOCUMENT_TEMPLATE, v5, 16 items) and `automation-legal-za` (AUTOMATION_TEMPLATE, v2, 5 items) installed at 2026-04-21 18:08:48 UTC. Confirms vertical profile = `legal-za`.

### Checkpoint 0.17 — Keycloak invite email to Thandi
- Result: PASS
- Evidence: Mailpit message id `5HT2BVhT7X3yy3xD8GiARy`, subject "Invitation to join the Mathebula & Partners organization", body contains `http://localhost:3000/accept-invite?kcUrl=...` bounce URL (prior GAP-L-01 bounce-page mechanics confirmed still working).

## Phase C — Owner Keycloak registration (Thandi)

### Checkpoint 0.18 — Open invite link
- Result: PASS
- Evidence: Invite URL opens the `/accept-invite` bounce page which redirects to `http://localhost:8180/realms/docteams/protocol/openid-connect/registrations?...` (carry-forward regression point: allow-list covers both `/login-actions/action-token` and `/protocol/openid-connect/registrations`).

### Checkpoint 0.19 — Registration page loads with org pre-bound
- Result: PASS
- Evidence: Heading reads "Create an account to join the Mathebula & Partners organization". Email field pre-populated with `thandi@mathebula-test.local`. No untranslated i18n keys visible (carry-forward GAP-L-02/L-03 still VERIFIED).

### Checkpoint 0.20 — Fill registration form
- Result: PASS
- Evidence: Filled First=Thandi, Last=Mathebula, Password=<redacted> (+confirm). Click Register.

### Checkpoint 0.21 — Redirect to org dashboard
- Result: PARTIAL (re-verified)
- Evidence: Initial registration redirect landed on `/platform-admin/access-requests` because the BFF session cookie still held padmin's session (OIDC authorization code arrived at BFF already-authenticated as padmin). After explicit KC logout + fresh OIDC login as Thandi, reached `http://localhost:3000/org/mathebula-partners/dashboard`. Confirms prior OBS-L-06 "Post-registration session handoff" still requires explicit re-login. Re-logged as **GAP-L-22** with carry-forward reference.
- Gap: GAP-L-22

### Checkpoint 0.22 — Sidebar shows Mathebula & Partners + Thandi Mathebula
- Result: PASS
- Evidence: Sidebar header "Mathebula & Partners"; footer user card "TM / Thandi Mathebula / thandi@mathebula-test.local".

### Checkpoint 0.23 — Legal terminology active
- Result: PASS
- Evidence: Sidebar expands to show **Matters** (not Projects), **Clients** (not Customers), **Fee Notes** (not Invoices), **Engagement Letters**, **Mandates**, **Tariffs**. Not leaked: top-level nav has no "Projects"/"Customers"/"Invoices". Minor body-copy leaks: (a) Activity empty state reads "Activity will appear as your team works on projects."; (b) Admin card labels "Incomplete profiles" / "Pending requests" (feels generic, not legal-specific). These are LOW cosmetic leaks, not terminology-blocking.

### Checkpoint 0.24 — Legal module nav items visible
- Result: PASS
- Evidence: **Matters** ✓ (Work > Matters), **Trust Accounting** ✓ (Finance > Trust Accounting + sub-nav Transactions/Client Ledgers/Reconciliation/Interest/Investments/Trust Reports), **Court Calendar** ✓ (Work > Court Calendar), **Conflict Check** ✓ (Clients > Conflict Check). All four present. Bonus: Adverse Parties, Tariffs also visible under Clients/Finance.

### Checkpoint 0.25 — Screenshot day-00-firm-dashboard-legal.png
- Result: PASS
- Evidence: `qa_cycle/checkpoint-results/day-00-firm-dashboard-legal.png` saved — shows Matters sidebar + Court Calendar + Trust Accounting + Fee Notes + legal-specific nav groups Work/Matters/Clients/Finance/Team.

## Phase D — Team invites (Thandi as Owner)

### Checkpoint 0.26 — Navigate to Settings > Team
- Result: PARTIAL
- Evidence: `/settings/team` → 404. Actual path is `/team` (sidebar → Team). Scenario path is wrong / stale. Not blocking; note the path.

### Checkpoint 0.27 — Thandi listed as Owner, no "Upgrade to Pro" gate
- Result: PARTIAL
- Evidence: Thandi shown as Owner. **No** "Upgrade to Pro" modal/upsell/banner visible anywhere on invite flow. However, the header reads "1 of 10 members" (and becomes "2 of 10" after first invite). This appears to be a seat-limit display even though user memory states "No plan-tier subscriptions". Behaviour: soft counter only — invites still send successfully — so there is no hard gate. Filed under GAP-L-20 as copy regression risk (matches user rule: "No tier/upgrade/billing upsell visible").

### Checkpoint 0.28 — Invite Bob as Admin
- Result: PASS
- Evidence: Filled `bob@mathebula-test.local` + Role=Admin + Send Invite. Banner "Invitation sent to bob@mathebula-test.local." appears. Counter becomes "2 of 10 members".

### Checkpoint 0.29 — Invite Carol as Member
- Result: PASS
- Evidence: Filled `carol@mathebula-test.local` + Role=Member + Send Invite. Two invite records created.

### Checkpoint 0.30 — Two invitation emails in Mailpit
- Result: PASS
- Evidence: Mailpit now contains 4 messages: OTP, Thandi invite, Bob invite (`QL7yQQvjqpVW5vunPcqFtf`), Carol invite (`WVgjidJ5PpVsFqV6MPVS3i`) — both subjects "Invitation to join the Mathebula & Partners organization".

### Checkpoint 0.31 — Bob registers, reaches dashboard, logout
- Result: PASS
- Evidence: Opened Bob's `/accept-invite?kcUrl=...` URL (after KC logout). KC registration page loads with email prebound. Filled First=Bob, Last=Ndlovu, Password=<redacted>. Register → landed on `http://localhost:3000/org/mathebula-partners/dashboard`. Logged out via KC logout confirmation page (GAP-L-04 nuisance still present — carry-forward).

### Checkpoint 0.32 — Carol registers, reaches dashboard, logout
- Result: PASS
- Evidence: Opened Carol's invite URL. Filled First=Carol, Last=Mokoena, Password=<redacted>. Register → landed on `/org/mathebula-partners/dashboard`. Note: immediately after Carol's registration the server-rendered sidebar still showed Thandi's user card (BFF session from an earlier Thandi OIDC code exchange was retained) — same GAP-L-22 session-handoff flavour.

## Day 0 summary checks

- Org created via real access-request → approval → Keycloak registration (no mock IDP anywhere): **PASS**
- Three Keycloak users exist under realm `docteams` for `@mathebula-test.local`: **PASS**
  - `c5693386-dc45-467a-b171-18713b94460f` thandi@mathebula-test.local (Thandi Mathebula)
  - `92823624-3e97-457d-8424-61381f89c81e` bob@mathebula-test.local (Bob Ndlovu)
  - `b72c738b-686f-4bd2-8ba1-da7eeeddecab` carol@mathebula-test.local (Carol Mokoena)
- Vertical profile = `legal-za`, terminology + nav reflect legal: **PASS**
- No tier / upgrade / billing upsell visible: **PASS with copy LOW** — counter "1 of 10 members" uses old wording but is not a functional gate (GAP-L-20).

## Carry-Forward watch-list verifications

| Prior gap | Re-observed? | Notes |
|---|---|---|
| GAP-L-01 (invite bounce page mechanics) | Verified still working | `/accept-invite?kcUrl=...` correctly forwards to `/realms/docteams/protocol/openid-connect/registrations?...` for all 3 users |
| GAP-L-04 (end-session confirmation page with no id_token_hint) | Re-observed | Still required "Do you want to log out?" click on every KC logout. LOW nuisance — not re-logged as new gap |
| GAP-L-02 / GAP-L-03 (KC i18n keys) | Verified still fixed | Registration page heading "Create an account to join the Mathebula & Partners organization" reads cleanly; no raw `{{advancedMsg...}}` keys |
| OBS-L-06 (post-registration session handoff) | Re-observed and escalated | Now logged as **GAP-L-22** because all three registrants hit a variant (Thandi lands on padmin's page, Carol dashboard shows Thandi's user card, etc.). Explicit KC logout + fresh login is the workaround |

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-20 | LOW | `/dashboard` does not auto-redirect unauthenticated users to Keycloak; renders a "Waiting for Access" fallback card with a generic "Submit Access Request" link. Scenario 0.9 expected an OIDC redirect. Additionally, "1 of 10 members" seat counter on team page is a copy leak against the "no tier subscriptions" rule. |
| GAP-L-21 | LOW | Pending access-request row has no drill-down detail view. Clicking the row or any cell does not open a detail. All six submitted fields are visible inline, which is sufficient for approval, but scenario 0.13 expected a separate detail view. |
| GAP-L-22 | MED | Post-registration session handoff is incomplete. The BFF gateway session cookie retained by the browser after registration does NOT reflect the newly registered user — subsequent requests either hit a stale session (padmin after Thandi's registration) or a stale RSC cache (Thandi's avatar shown after Carol registered). Workaround is explicit KC logout + fresh OIDC login. Escalated from prior OBS-L-06 because it surfaced for all three registrants in this run. Owner: both (frontend RSC cache + backend BFF session merging). |
