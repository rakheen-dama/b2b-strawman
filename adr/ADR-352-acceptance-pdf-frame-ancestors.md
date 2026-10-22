# ADR-352: `frame-ancestors <portal origin>` on `/api/portal/acceptance/*/pdf` Only; Every Other Response Keeps `X-Frame-Options: DENY`

**Status**: Accepted

**Context**:

The acceptance page (`portal/app/accept/[token]/acceptance-page.tsx:209-213`) renders `<iframe src={NEXT_PUBLIC_PORTAL_API_URL + /api/portal/acceptance/{token}/pdf}>`. The endpoint (`PortalAcceptanceController.java:38-49`, `Content-Disposition: inline`) lives under `/api/`, so it is served by the **main `@Order(2)` staff chain** (`SecurityConfig.java:130`, `permitAll` at `:144-145`), not the `/portal/**` chain. No chain calls `.headers(...)` — `grep headers\(|frameOptions|contentSecurityPolicy|frame-ancestors` over `backend/src/main/java` is empty — so Spring Security's defaults emit `X-Frame-Options: DENY` and the browser refuses the frame. The client accepts an engagement letter they cannot see (G-B7, HIGH; wow moment 2). The topology facts (R5 §Q3) make this a production bug on both deployments: on the VPS the portal (`portal-dev.heykazi.com`) iframes `api-dev.heykazi.com` (`compose/caddy/Caddyfile:17-23`, `deploy-vps.yml:73`) — cross-origin; on AWS staging the API is same-origin by path (`deploy-staging.yml:201`) — and `DENY` refuses even same-origin framing. No backend test asserts frame headers. `.arch-context.md` §10.6 notes the two public portal surfaces sit on two chains.

**Options Considered**:

1. **Global `X-Frame-Options: SAMEORIGIN`.**
   - Pros:
     - One line.
   - Cons:
     - Does not fix the VPS topology (cross-origin) at all.
     - Weakens clickjacking protection for every staff and portal response to buy nothing on the deployment that matters.
2. **Request-matcher-scoped headers on the existing main chain: for `/api/portal/acceptance/*/pdf` disable `X-Frame-Options` and set `Content-Security-Policy: frame-ancestors <PORTAL_BASE_URL origin>`; everything else unchanged (CHOSEN).**
   - Pros:
     - Exactly one path is frameable, and only by the configured portal origin (`docteams.app.portal-base-url`, env `PORTAL_BASE_URL`).
     - Works in both topologies because `frame-ancestors` is origin-based, not same-origin-based.
     - The server states its intent.
     - Third-party framing of the PDF is still refused.
     - Testable: one integration test asserts the two headers on the path and `DENY` on a sibling path in both chains.
   - Cons:
     - A `DelegatingRequestMatcherHeaderWriter` plus a frame-options exclusion in the chain config — small but easy to get subtly wrong (order of writers, `*` path variable matching), hence the header test.
3. **Frontend blob-URL rendering: `fetch(pdfUrl)` → `URL.createObjectURL(blob)` → `<iframe src={blobUrl}>`.**
   - Pros:
     - No backend change.
     - A `blob:` URL is same-origin to the portal and carries no response headers.
   - Cons:
     - Hides the server's policy instead of declaring it.
     - Depends on CORS allowing the portal origin for the PDF GET (local allowlist is `:3000/:3001` only — a hypothesis to verify).
     - Doubles memory for large PDFs.
     - A workaround in the sense memory `feedback_no_workarounds_in_qa` warns about.
4. **Open-in-new-tab only; no inline viewer.**
   - Pros:
     - Zero header work.
   - Cons:
     - Loses wow moment 2 (read and accept on one page).
     - Mobile Safari pops a download.
     - The scenario step observes an inline PDF.

**Decision**: Option 2, plus the "Open in new tab" link from option 4 as a fallback affordance — `SecurityConfig`'s main chain adds a request-matcher-scoped header writer for `/api/portal/acceptance/*/pdf` that omits `X-Frame-Options` and writes `Content-Security-Policy: frame-ancestors <portal origin>` derived from `docteams.app.portal-base-url`; all other responses on both chains keep `X-Frame-Options: DENY`; an integration test pins both.

**Rationale**:

1. **Least privilege on one path.** The acceptance PDF is the only response Kazi ever intends to frame; scoping the exception to that request matcher keeps the default-deny posture for the staff API, the portal API and every other document endpoint.
2. **`frame-ancestors` is the only directive that fits both deployments.** `SAMEORIGIN` cannot express "framed by `portal-dev.heykazi.com` from `api-dev.heykazi.com`"; a CSP origin list can, and the origin is already configuration (`PORTAL_BASE_URL`), so the policy follows the deployment without code change.
3. **Prod-affecting, independent, one config class** — the register orders it first; it has no dependency on V4 or any other unit, and the fix is observable with `curl -I` plus a clean Chrome console on `/accept/<token>`.
4. **Declared policy over client-side workaround.** Option 3 would make a later security review conclude the API forbids framing while the portal does it anyway; the header is the audit trail.

**Consequences**:

- Positive:
  - `/accept/<token>` on :3002 renders the PDF inline on the e2e stack, the VPS and AWS.
  - An unrelated API response still carries `X-Frame-Options: DENY` (G-B7, wow moment 2).
  - A `FrameHeadersIntegrationTest` (new) is the first test of frame headers in the codebase.
  - It asserts the PDF path, a sibling `/api/portal/acceptance/{token}` JSON path, a staff `/api/matters` path and a `/portal/**` path.
- Negative:
  - The portal origin must be exact (scheme + host + port).
  - A malformed `docteams.app.portal-base-url` fails startup; a well-formed but wrong origin fails closed (frame refused) — both are the safe failure. Local profiles set it to `http://localhost:3002`.
  - The e2e profile to the :3002 mock stack.
  - If a second portal origin is ever needed (custom firm domains), the directive accepts a list — a configuration change, not a new ADR.
  - `acceptance-page.tsx` gains the "Open in new tab" link.
  - `app/accept/[token]/__tests__/page.test.tsx` gains the link assertion.
- Related:
  - [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (the acceptance flow this makes visible), [ADR-T004](ADR-T004-gateway-bff-over-direct-api-access.md) (the gateway is the firm app's BFF and has no `/portal` route — the portal talks to the API directly, which is why the header lives on the API), [ADR-351](ADR-351-portal-contact-directory.md), `tasks/phase88-research/R5-portal.md` §Q3, `tasks/phase88-gap-register.md` U12.
