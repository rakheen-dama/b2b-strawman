## Observed evidence (orchestrator, e2e stack, backend rebuilt from 0e669d7d8)

**Before** (e2e backend still on the pre-624B build): `POST :8081/portal/auth/request-link {"email":"nobody@example.com"}` returned **400** `fieldErrors:[{"message":"orgId is required","field":"orgId"}]`. This reproduces G-D1 on the backend.

**After** (`e2e-rebuild.sh backend` from the worktree):

| Probe | Result |
|---|---|
| Unknown email, no orgId | **200** `{"message":"If an account exists, a link has been sent.","magicLink":null}`; nothing sent |
| `Contact@Acme.Example.com` (mixed case), no orgId | **200**, same message; Mailpit 11 → **12**: `nbrasCPgLyX5d55s5RfYcm` "Your portal access link from E2E Test Organization" to `contact@acme.example.com`; the link is `http://localhost:3002/auth/exchange?token=REDACTED&orgId=e2e-test-org` |
| `cap-probe-624b@example.com` ×4 (unknown email) | 200, 200, 200, **429** (the per-email cap fires before any lookup) |
| Known email + `orgId=no-such-org` (fast path) | **200**, generic message, nothing sent |

Backend log: `Generated magic link for portal contact a7bc1566-… in org e2e-test-org`. Our own log line has no email.

Notes:
- The e2e profile echoes `magicLink` in the response. That is pre-existing dev-profile behaviour and is unchanged.
- `PortalEmailService` logs `to=<email>` at INFO. That is pre-existing, outside this diff, and listed as a follow-up.
- The browser-level G-D1 PASS belongs to 624D / 624.11.
