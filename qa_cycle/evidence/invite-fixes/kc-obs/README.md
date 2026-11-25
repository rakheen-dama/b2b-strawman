# OBS — PR #1859 invitation fixes on the Keycloak dev stack

- Branch `fix/invitations-custom-role-revoke-and-no-invite-customisation`, worktree `/Users/rakheendama/Projects/2026/wt-invite-fixes`, head **`fd42dfa6d`**. The head did not move and `git status` was clean at the end.
- Date: 2026-11-25, about 04:20–04:42 UTC.
- Stack:
  - Infra came from the worktree's `dev-up.sh`, using Keycloak 26.5.0, Postgres, Mailpit and LocalStack.
  - Services came from the worktree's `svc.sh start`. The backend log line "Starting BackendApplication … (/Users/rakheendama/Projects/2026/wt-invite-fixes/backend/target/classes …)" confirms the branch code ran.
- Browser: a clean headless Chromium driven by the Playwright library script `obs.cjs`, kept in this folder byte-for-byte as it ran. The script was extended twice during the run (the `invite-only` step, and the probe added to `step5-remove`). No MCP browser and no claude-in-chrome.
- Read-only checks:
  - SQL ran only through `sql.sh`, which wraps every query in `BEGIN READ ONLY`.
  - Keycloak state was read with admin GETs only, through `kc-read.sh`.
  - Mailpit was read through its API.
  - No REST call performed a UI step.
- Firm: the seeded "Mathebula & Partners", tenant `tenant_5039f2d497cf`, Keycloak org `fe76c0d4-2174-4001-ae58-af2175b0fb1d`. Owner `thandi@mathebula-test.local`.
- Run timestamp `TS=1795580847`.

## Result per step

| Step | Status | Key observation | Evidence |
|---|---|---|---|
| 1. Create custom role "Partner OBS" | **PASS** | The role card shows "Partner OBS · Matter Management · Sign off trust take-on · View Trust · 0 members". DB: role `f4813c78-1da7-41e3-a056-6fb53074876b` with caps PROJECT_MANAGEMENT, SIGN_OFF_TRUST_TAKE_ON, VIEW_TRUST. | `s1-role-dialog-filled.png`, `s1-role-created.png`, `step1.log.txt` |
| 2. FR-7: no customisation; invite into Partner OBS | **PASS** | Role options are `["Member","Admin","Partner OBS"]`. "Customize for this user" count = **0**, and the checkbox count in the invite form = **0**. Screen shows "Invitation sent to obs-partner-1795580847@example.com.". DB: `pending_invitations` row 52530224… PENDING with org_role_id = the role id. | `s2-role-options.png`, `s2-partner-obs-selected.png`, `s2-invite-sent.png`, `s2-pending-tab.png`, `step2.log.txt` |
| 3. Invitee accepts → lands with custom role | **PASS** | Mailpit `FqyJVdGzpmF4q47LD93KXw` "Invitation to join the Mathebula & Partners organization". The link goes `/accept-invite` → KC registration → `/accept-invite/complete` → `/org/mathebula-partners/dashboard`, with no 5xx and no error page. `/api/me/capabilities` returns `{"capabilities":["PROJECT_MANAGEMENT","SIGN_OFF_TRUST_TAKE_ON","VIEW_TRUST"],"role":"Partner OBS",...}`. The Team page (both as the invitee and as the owner) shows "Pat Partner … Partner OBS". SQL: member 482f9e9d… `org_role_id = f4813c78… = Partner OBS id` (match = t), and the invitation is ACCEPTED. Backend: "MemberFilter Found pending invitation … with role partner-obs" then "Lazy-created member". No "Member context not available". | `s3-*.png`, `step3.log.txt`, `sql-s3.txt`, `mail-s2-FqyJVdGzpmF4q47LD93KXw.json`, `backend-s2-s3.log` |
| 4. Revoke before use | **PASS** | After clicking Revoke, the row count goes 1 → 0 with no alert. After a reload the tab shows "No pending invitations". DB status is REVOKED. Backend: "Revoked 1 Keycloak invitation(s) for email=obs-revoke-a-…" then "Revoked invitation id=30129bdf…". KC GET `invitations?email=` returns `[]`. Following the emailed link (Mailpit `PVcQvrkiK6jwTLBVZ3LmEN`), Keycloak shows **"Action expired. Please continue with login now." / "The link you clicked is no longer valid. It may have expired or already been used."** with no registration form. KC event `EXECUTE_ACTION_TOKEN_ERROR error="invalid_token" action="ORGIVT"`. No KC user and no Kazi member exist for the address. | `s4-*.png` (`s4-follow-link-landing.png`), `step4-*.log.txt`, `sql-s4.txt`, `kc-s4-after-revoke.txt`, `kc-log-s4.txt`, `backend-s4-revoke.log` |
| 5a. Link used, then revoke → refused | **PASS** | The invitee completed KC registration with Kazi blocked: the gateway kickoff `http://localhost:8443/oauth2/authorization/keycloak` was aborted. KC GET then showed the user as an org member (`membershipType":"MANAGED"`) and KC invitations `[]`, while Kazi had 0 member rows and the invitation was PENDING. When the owner clicks Revoke, `role="alert"` `data-testid="revoke-invitation-error"` shows: *"'obs-revoke-b-1795580847@example.com' has already accepted the invitation email and joins with the invited role on their first sign-in, so it can no longer be revoked. Remove them from the team once they appear."* The row stays (1 → 1) and the DB is still PENDING. | `s5-after-kc-register.png`, `s5-revoke-refused.png`, `step5-*.log.txt`, `kc-s5-after-kc-accept.txt`, `sql-s5.txt`, Mailpit `QKj3EbPMqthE5HaPtKHSPE` |
| 5b. Person opens Kazi → invited role (FR-9) | **PASS** | They land on `/org/mathebula-partners/dashboard` with no 5xx. `/api/me/capabilities` returns `role":"Partner OBS"` with the 3 caps. SQL: member 9e49adc0… has org_role_id f4813c78… (Partner OBS), and the invitation is ACCEPTED at 04:39:30. | `s5-invitee-landing.png`, `step5-invitee-login.log.txt`, `backend-s5-login.log`, `sql-s5.txt` |
| 5c. Owner removes them on the Team page | **FAIL** | There is **no remove control**. The member row has no buttons. Clicking it opens the "Member Detail" sheet, whose only buttons are `["", "Save", "Cancel"]` (close, Save, Cancel: a role and capability editor). No button or menu item matches /remove\|deactivate\|delete/. `git grep` on origin/main and on the branch finds no firm-level member removal in the frontend; only matter-level removal exists (`project-members-panel.tsx`). | `s5-owner-team-before-remove.png`, `s5-owner-member-panel.png`, `step5-remove.log.txt` |
| 6. Keycloak error path | not run | Not required by the brief. | — |

## Defect found

**D-1 (5c): the refusal tells the admin to "Remove them from the team once they appear", but Kazi has no way to remove a firm member.**
- Repro:
  1. Invite X.
  2. X completes Keycloak registration from the email but does not open Kazi.
  3. The owner clicks Revoke and sees the alert.
  4. X signs in and appears on the Team page with the invited role.
  5. The owner opens Team, clicks X's row, and finds no remove action.
- Source of the text: `InvitationService.java:227` (the new FR-8 message).
- The missing removal is a pre-existing gap, not introduced by this PR.
- The PR's new guidance points at it, so an admin who wanted to cancel the invitation is left with a member they cannot remove from the UI. The only lever is a role change to Member in the Member Detail sheet.
- Product call needed: either reword the message, or add firm-member removal.

## Side observations (not caused by this PR)

- **LOW:** pending-invitation rows for a custom role show the raw `org:partner-obs` badge, not "Partner OBS" (`s2-pending-tab.png`, `s5-revoke-refused.png`). Cause: `ROLE_BADGES[inv.role] ?? { label: inv.role }` in `pending-invitations.tsx`. The PR only re-indents those lines.
- The 409 "already accepted" refusal leaves no backend log line. The only traces are the frontend's server-action log and the UI.
- Cold-start note: during the pre-step probe (04:25:55, before step 1), the backend logged one ERROR, "An error occurred while attempting to decode the Jwt: I/O error on GET … /realms/docteams/protocol/openid-connect/certs: Read timed out". This was the first JWKS fetch from a freshly started Keycloak, and the frontend logged "Failed to fetch capabilities … 401" once. It did not recur: the OBS window from step 1 onward has **61 INFO, 0 WARN, 0 ERROR** (`backend-obs-window-summary.txt`).

## Environment notes

- The worktree has no gitignored `compose/.env` (main has `.env -> .env.keycloak`), so the first `dev-up.sh` left Keycloak unable to reach Postgres ("SCRAM … no password").
  - Fix applied in the shell only: `compose/.env.keycloak` was exported for `dev-up.sh`.
  - Backend and gateway were then started with `JWT_ISSUER_URI` and `JWT_JWK_SET_URI` unset, because those values point at the container host `keycloak:8180`.
  - Frontend and portal were started with `frontend/.env.keycloak` exported, since the worktree has no `frontend/.env.local`.
  - No files were written into the worktree.
- In step 5a the Playwright route did not intercept KC's 302 to `/accept-invite/complete` (Playwright does not route redirects). That static bounce page rendered, but its client-side navigation to the gateway was aborted, so Kazi never saw the user. The proof is SQL `members_rows = 0` and the invitation still PENDING before the revoke.
- Teardown: `svc.sh stop all`, then `dev-down.sh` without `--clean` (infra was down before the run, with no containers). Volumes were preserved.
- Test data left in the dev DB and Keycloak: role "Partner OBS"; members Pat Partner and Late Revoke; KC users for obs-partner-, obs-revoke-b-.

## Files

- `obs.cjs`: the Playwright library script. `sql.sh` and `kc-read.sh` are the read-only helpers.
- `*.png`: screenshots. `*.log.txt`: per-step script logs, including network lines and on-screen text.
- `backend-*.log` and `backend-obs-window-summary.txt`: backend log windows. `svc-logs/` holds the full backend, frontend, gateway, portal and Keycloak container logs.
- `sql-s3.txt`, `sql-s4.txt`, `sql-s5.txt`: read-only SQL output. `kc-s4-after-revoke.txt` and `kc-s5-after-kc-accept.txt`: KC admin GETs.
- `mail-*.json`: Mailpit messages `FqyJVdGzpmF4q47LD93KXw` (step 3), `PVcQvrkiK6jwTLBVZ3LmEN` (step 4) and `QKj3EbPMqthE5HaPtKHSPE` (step 5).
