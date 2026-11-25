# OBS re-run: PR #1859 at head fe4e3491d ("revoke always wins"), Step 5 + Step 3 re-check

- **Head:** worktree `/Users/rakheendama/Projects/2026/wt-invite-fixes` at `fe4e3491dbf7a6f0b06b611231f1eac451ddb3df`. The head did not move, and the tree was clean at the end.
- **When:** 2026-11-25, about 07:15–07:32 UTC.
- **Stack setup:**
  - Infra came from the worktree's `dev-up.sh`, with `compose/.env.keycloak` exported in the shell. No files were written to the worktree.
  - Backend and gateway were started with `svc.sh`, with `JWT_ISSUER_URI` and `JWT_JWK_SET_URI` unset.
  - Frontend and portal were started with `frontend/.env.keycloak` exported.
- **Backend runs the new code:**
  - `MemberFilter.class` contains "Access revoked".
  - No source file is newer than the compiled classes.
  - The log line "Starting BackendApplication … wt-invite-fixes/backend/target/classes".
- **How it was driven:**
  - The browser was a clean headless Chromium, driven by the Playwright library script `obs.cjs` (kept byte-for-byte). It is the first run's script plus two new steps, `step5-revoke-reload` and `step5-bc`.
  - SQL ran read-only through `sql.sh`, which wraps every query in `BEGIN READ ONLY`.
  - Keycloak state was read only with admin GETs (`kc-read.sh`).
  - Emails were read through the Mailpit API.
- **Firm:** Mathebula & Partners (`tenant_5039f2d497cf`), owner Thandi. The run timestamp is `TS=1795591208`.

| Step | Status | Observation | Evidence |
|---|---|---|---|
| 3 re-check: custom-role invitee | **PASS** | Invited `obs-partner2-…` into Partner OBS. Mailpit message `9bFUhmgkKjt9qCNiqRABM7`. After registering, the person landed on `/org/mathebula-partners/dashboard` with no 5xx. `/api/me/capabilities` returned `role:"Partner OBS"` with `[PROJECT_MANAGEMENT, SIGN_OFF_TRUST_TAKE_ON, VIEW_TRUST]`. SQL: the member has the Partner OBS role id (f4813c78…), and the invitation is ACCEPTED. | `s3-*.png`, `step3.log.txt`, `sql-s3.txt` |
| 5a: link used, then revoke → succeeds | **PASS** | The setup held before the revoke. KC user e223d2a5… was an org member (`MANAGED`), and its KC invitations were `[]`. Kazi had 0 member rows, and the invitation was PENDING. The owner clicked Revoke. The row went 1 → 0 and stayed 0 after a reload. The alert count was **0**. DB: invitation 4e2e88b2… is **REVOKED**. Backend: "No live Keycloak invitation for email=… (link used or never sent) — revoked in Kazi; sign-in is refused by MemberFilter". | `s5a-*.png`, `step5-revoke-reload.log.txt`, `kc-s5-after-kc-accept.txt`, `sql-s5.txt`, `backend-s5a-revoke.log`, Mailpit `HbZjL9VkzamYDpaqMvC9Tm` |
| 5b: revoked person opens Kazi → refused | **PASS** | Home (`/org/mathebula-partners/dashboard`) shows `data-testid="access-revoked"` with `role="alert"`, the title **"Access revoked"**, and the text **"Your invitation to this firm was revoked. Ask the firm to invite you again."** `/api/me/capabilities` returns **403** `{"title":"Access revoked","detail":"Your invitation to this firm was revoked. Ask the firm to invite you again."}`. Backend: 15 × WARN "Refused member creation for user e223d2a5…". No ERROR and no 500 came from the backend while the person was refused. No member row was created: the person's only member row has `created_at` 07:30:03, after the re-invite at 07:29:58, and "Lazy-created member" appears only after the re-invite. **/matters opened directly** shows the app's error screen "Something went wrong / Unable to load this page. Please try again. / Try again" inside the app shell. It does not crash. | `s5b-invitee-home.png`, `s5b-invitee-matters.png`, `step5-bc.log.txt`, `backend-s5bc-summary.txt` |
| 5c: re-invite → admitted with new role | **PASS** (note F-2) | The owner re-invited the same email as **Member**, and the UI said "Invitation sent to obs-revoke-c-….". In the same browser context, the person reloaded Home and got the normal Home page, with access-revoked count 0. `/api/me/capabilities` returned `role:"Member"`. Backend: "Found pending invitation … with role member", then "Lazy-created member f5e818d2…". SQL: member f5e818d2… with role **Member**. Invitations: 4e2e88b2… (Partner OBS) REVOKED, and bd4acfe5… (Member) ACCEPTED. | `s5c-*.png`, `sql-s5.txt`, `backend-s5bc.log` |

## Findings

- **F-1 (LOW, frontend): the revoked user's notification bell produces a stream of 500s.**
  - What happens: while the person sits on the Access-revoked Home or on /matters, the bell's `fetchUnreadCount` server action rethrows the backend's 403 "Access revoked".
  - What Next.js shows: `POST /org/mathebula-partners/dashboard 500` and `POST …/matters 500`, repeated on every poll. In dev, the Next overlay also shows "2 Issues".
  - Origin: these are frontend 500s; the backend answered 403 each time. The page itself renders correctly.
  - Evidence: `svc-logs/frontend.log` around lines 236–480, and `step5-bc.log.txt`.
- **F-2 (MEDIUM, product): re-inviting a revoked person who already used the link sends no email, but the UI says it did.**
  - Why it happens: Kazi does not remove the person from the Keycloak org on revoke, so Keycloak `invite-user` returns `409 "User already a member of the organization"`.
  - What the backend does: it logs **ERROR** `KeycloakAdminClient … 409 CONFLICT` and WARN "Failed to send Keycloak invitation", then still creates the Kazi invitation.
  - What the owner sees: "Invitation sent to …".
  - Mailpit: only the original 07:25 email exists for that address; nothing was sent for the re-invite.
  - Effect: the person is admitted on their next sign-in, as observed here, but nobody tells them they were re-invited.
  - This is the only backend ERROR in the window: 47 INFO, 16 WARN (15 of them the expected refusals), 1 ERROR. `backend-obs2-window-summary.txt` has the counts.
- Carried over from the first run: pending rows for a custom role still show `org:partner-obs` instead of the role name.

## Teardown

- `svc.sh stop all` and `dev-down.sh`, without `--clean`. Infra was down before the run, and the volumes are preserved.
- Test data left in the dev DB and Keycloak: `obs-partner2-…` (Partner OBS) and `obs-revoke-c-…` (Member).
