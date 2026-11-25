# OBS recheck: PR #1859 Fix 6 (re-invite of someone who already has a login)

- **Requested head:** `aa514362a`. It was confirmed at the start with `git rev-parse HEAD`.
- **Where the run happened:**
  - Worktree: `/Users/rakheendama/Projects/2026/wt-invite-fixes`.
  - Date: 2026-11-25, about 08:33–08:50 UTC.
  - Stack: Keycloak dev stack. Infra came from the worktree's `dev-up.sh` with the env exported in the shell. Services were started with the worktree's `svc.sh`.
- **How it was driven:**
  - Browser: a clean headless Chromium driven by the Playwright library script `obs.cjs`, kept byte-for-byte. It extends the kc-obs2 script: the shared `invite()` now captures `data-testid="invite-success"`, and there is a new `invitee-login` step.
  - SQL ran only through `sql.sh`, which wraps every query in `BEGIN READ ONLY`.
  - Keycloak state was read only with admin GETs (`kc-read.sh`).
  - Emails were read through the Mailpit API.

## Results

| Check | Status | Observation | Evidence |
|---|---|---|---|
| R1 setup | done | The owner invited `obs-revoke-d-1795594678@example.com` into Partner OBS, and the UI said "Invitation sent to …". Mailpit `NvJo4ctztkFApi9hFS7ABF` arrived. The person completed Keycloak registration with Kazi blocked, so the gateway kickoff was aborted. The owner revoked: the row went 1 → 0, stayed 0 after a reload, and the alert count was 0. At that point the state was: invitation `cdf8ec81…` REVOKED; 0 member rows; KC user `7e4b5b44…` is an org member (`MANAGED`). | `run-r1a.out`, `run-r1kc.out`, `run-r1rev.out`, `sql-r1.txt`, `kc-r1-after-revoke.txt`, `mailpit-r1-before-reinvite.txt` |
| R1 re-invite (Member) | **PASS** | `invite-success` read: **"Invitation created for obs-revoke-d-1795594678@example.com. They already have a login, so no email was sent — ask them to sign in to Kazi to join."** Mailpit still had **1** message for the address, the original `NvJo4ctztkFApi9hFS7ABF`, checked 15 s and more after the re-invite. The backend logged only INFO lines: "KeycloakAdminClient Keycloak sent no invitation email: … is already an org member", "InvitationService No Keycloak invitation email for email=…: already has a login in the org", and "Created invitation … with role=member". There was **no ERROR**. SQL: new invitation `949d1af7…` (Member) PENDING. | `r1-reinvite-*.png`, `run-r1-reinvite.out`, `mailpit-r1-after-reinvite.txt`, `backend-r1-reinvite.log`, `sql-r1-after-reinvite.txt` |
| R1 person opens Kazi | **PASS** | The person signed in with their Keycloak password and landed on `/org/mathebula-partners/dashboard`. The `access-revoked` count was 0. `/api/me/capabilities` returned `role:"Member"`. Backend: "Found pending invitation … with role member", then "Lazy-created member e68bf01a…". SQL: member `e68bf01a…` has role **Member**. Invitation `949d1af7…` is ACCEPTED, and `cdf8ec81…` (Partner OBS) is still REVOKED. | `r1-invitee-home.png`, `run-r1-login.out`, `sql-r1-after-login.txt`, `backend-r1-login.log` |
| R2 brand-new invite | **PASS** | Owner invited `obs-fresh-1795594678@example.com` as Member. `invite-success` read **"Invitation sent to obs-fresh-1795594678@example.com."** Mailpit `6hrGd9KuGJro5pkbz4Furk`, from noreply@docteams.local, "Invitation to join the Mathebula & Partners organization", carries the KC registration link. Backend: "Triggered Keycloak invitation …". SQL: the invitation is PENDING. The R1 first invite above is a second sighting of the same behaviour. | `r2-*.png`, `run-r2.out`, `mailpit-r2.txt`, `mail-6hrGd9KuGJro5pkbz4Furk.json`, `sql-r2.txt`, `backend-r2.txt` |

- **Backend log for the whole OBS window:** **51 INFO, 0 WARN, 0 ERROR** (`backend-obs3-window-summary.txt`).
- **Defects:** none found in R1 or R2.

## Caveats (must read)

1. **The worktree moved during the run.**
   - Another agent committed in this worktree while I was observing:
     - `7f0657d73`, 10:37:00 +0200 (08:37 UTC)
     - `259a7daab`, 10:38:34 +0200 (08:38 UTC), "CodeRabbit round on PR #1859"
   - It also left uncommitted `fix7-*` evidence files and README edits. At the end, HEAD was `259a7daab`. I made no change to the worktree.
   - What actually ran:
     - **Backend** = `aa514362a` code. The JVM started at 08:35:12 UTC, before the source and class changes at 08:37:29–37. The Fix 6 backend files (`KeycloakAdminClient`) are unchanged between the two heads anyway.
     - **Invite form** (`invite-member-form.tsx`, the Fix 6 frontend) is identical at both heads. Its mtime is 07:37 UTC.
     - **`pending-invitations.tsx`** changed at 08:37:18 UTC, before my frontend restart at about 08:39. So the revoke clicks in R1's setup ran the newer `259a7daab` version of that component. The revoke itself is not what R1/R2 check.
2. **The first R1 attempt failed for environment reasons.** It is kept in `failed-first-attempt/`.
   - What happened: right after `svc.sh start`, the dev server answered **404** for every `/org/mathebula-partners/*` route, even the owner's dashboard. The backend was healthy.
   - Likely cause: `frontend/.next` held a production `BUILD_ID` from a `pnpm build` at 09:43 +0200, written by the other agent's frontend gate.
   - Fix: restarting the frontend alone cleared it. After that, `/dashboard` and `/team` returned 200. Nothing was changed in the worktree by me.
   - The same email was reused for the retry. The failed attempt created nothing in the DB or Keycloak: 0 rows and no KC user.

## Teardown

- `svc.sh stop all` and `dev-down.sh`, without `--clean`. Infra was down before the run; volumes are preserved.
- Test data left behind: `obs-revoke-d-…` (Member), and `obs-fresh-…` (PENDING invitation).
