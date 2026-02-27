# ADR-107: Acceptance Token Strategy

**Status**: Accepted

**Context**:

The acceptance workflow needs a URL that authenticates the portal contact and routes them to the acceptance page for a specific document. When a firm sends a generated document for acceptance, the client receives an email with a link. Clicking that link must land the client on the acceptance page where they can view the PDF, type their name, and click "I Accept."

The platform has an existing magic-link system (`MagicLinkToken`) for portal authentication. Magic links generate a SHA-256-hashed token, authenticate the portal contact, create a portal session, and redirect to the portal dashboard. The question is whether to reuse this system, create a separate acceptance-specific token, or build a composite approach that does both.

Key constraints: acceptance requests live for up to 30 days (configurable), magic-link tokens expire in ~24 hours. The acceptance page is a single-purpose transactional flow — the contact needs to see one PDF and click one button. The portal contact does not need a full portal session to complete acceptance.

**Options Considered**:

1. **Reuse magic-link tokens** -- The acceptance email contains a magic-link token that authenticates the contact and creates a portal session, then redirects to `/portal/accept/{requestId}`. The portal contact must have an active session to access the acceptance page.
   - Pros:
     - Reuses existing token generation, storage, and verification infrastructure (`MagicLinkService`, `MagicLinkToken` entity).
     - Consistent authentication model — all portal access goes through magic links.
     - Portal session grants access to other portal features (project list, invoices) after acceptance.
   - Cons:
     - Magic-link tokens have a ~24 hour expiry, but acceptance requests live for 30 days. If the magic link expires, the client cannot accept even though the acceptance request is still valid. This creates a confusing UX where the email link stops working long before the acceptance deadline.
     - Two-step flow: click link → authenticate → redirect to acceptance page. If the session expires between opening the email and clicking accept, the client must re-authenticate.
     - The magic-link token is consumed on first use (`markUsed()`). Subsequent clicks on the same email link fail, requiring the firm to re-send or the client to log in separately.
     - Tightly couples acceptance to portal session management — any changes to portal auth (session duration, token format) affect the acceptance flow.

2. **Separate acceptance-specific token (chosen)** -- `AcceptanceRequest` has its own `requestToken` field, a cryptographically random string. The URL `/portal/accept/{requestToken}` authenticates access to that specific acceptance page only. No portal session is created or required.
   - Pros:
     - Single-step flow: email link goes directly to the acceptance page. No login page, no redirect chain.
     - Token expiry aligns perfectly with acceptance request expiry (30 days default). One expiry to reason about.
     - Simpler UX: client clicks the link, sees the PDF, types their name, clicks accept. No intermediate authentication screens.
     - Each token is scoped to exactly one action — viewing and accepting one specific document. No broader access granted.
     - Independent of magic-link system changes. Acceptance flow is self-contained.
     - Token can be clicked multiple times (view, leave, come back later) without being consumed. Only the acceptance action is one-time.
   - Cons:
     - New token type to generate and manage, though the generation logic is trivial (same `SecureRandom` + hex encoding as magic links).
     - Does not grant access to the broader portal (project list, invoices). The client sees only the acceptance page.
     - A parallel authentication mechanism alongside magic links — conceptually there are now two ways a portal contact accesses portal pages.

3. **Composite token** -- A new token type that both authenticates the portal contact (creates a portal session) and routes to the acceptance page. Essentially a magic-link token with embedded routing metadata.
   - Pros:
     - Single click achieves both authentication and routing. Client lands on the acceptance page with a full portal session.
     - After accepting, the client can browse other portal features without re-authenticating.
     - Feels like one integrated system from the user's perspective.
   - Cons:
     - Couples acceptance to portal session lifecycle. If the portal session expires before the client clicks accept, the acceptance page becomes inaccessible — the client must request a new link.
     - Complex token format: must encode routing metadata (acceptance request ID) alongside the authentication payload. Token parsing becomes more complex.
     - Session creation is a side effect of accessing the acceptance page. If the client shares the link (e.g., forwards the email to a colleague), clicking the link creates a session for whoever clicks it.
     - Token reuse concerns: a token that creates a session should ideally be single-use (like magic links), but acceptance tokens need to be re-clickable for the "view, leave, come back" pattern.
     - Inherits the expiry mismatch problem from Option 1 if the composite token uses magic-link expiry durations.

**Decision**: Option 2 -- Separate acceptance-specific token.

**Rationale**:

Acceptance requests live for 30+ days while magic links expire in ~24 hours. This expiry mismatch is the decisive factor: a client who receives an engagement letter on Monday and wants to review it with their partner over the weekend should not find the link broken. Reusing magic links (Option 1) forces either shortening the acceptance window to match magic-link expiry or extending magic-link expiry to match acceptance — both compromise the design of the other system.

A separate token keeps the acceptance flow self-contained: one URL, one action, no intermediate login pages. The portal contact does not need a full portal session to accept a document — they need to see the PDF, type their name, and click accept. The token is generated using the same cryptographic approach as `MagicLinkToken` (`SecureRandom`, hex-encoded) but with independent lifecycle management. Unlike magic links, the acceptance token is not consumed on first use — the client can click the link multiple times to re-view the document before accepting.

The composite approach (Option 3) adds the most complexity for the least benefit. Creating a portal session as a side effect of viewing an acceptance page is architecturally concerning — it means anyone with the acceptance URL gets a full portal session, and the session lifecycle becomes entangled with the acceptance lifecycle.

This also means the acceptance flow is unaffected by any future changes to the portal's authentication system — if magic links are replaced with OAuth or passkeys, acceptance tokens continue to work.

**Consequences**:

- Positive:
  - `AcceptanceRequest` entity has its own `requestToken` field (unique, indexed). Token lookup is a simple `findByRequestToken()` — no session state needed.
  - One expiry to manage: `AcceptanceRequest.expiresAt` controls both the token validity and the acceptance deadline. No expiry mismatch.
  - The acceptance URL pattern is `{portalBaseUrl}/accept/{requestToken}` — simple, bookmarkable, and re-clickable.
  - Acceptance flow works independently of portal session management. No coupling to `MagicLinkService` or `MagicLinkToken`.

- Negative:
  - Two token-based access patterns in the portal: magic links for general portal access, acceptance tokens for the acceptance page. Developers must understand both.
  - Portal contacts who want to access other portal features after accepting must use a separate magic-link login. Acceptance does not "log them in."
  - Sharing the acceptance URL grants acceptance ability to anyone who clicks it — the token authenticates access to the acceptance action, not to a specific person. Rate limiting and brute-force protection on the token endpoint are important.

- Neutral:
  - Token generation uses the same `SecureRandom` approach as `MagicLinkToken` — 32 bytes, hex-encoded, yielding a 64-character token. This provides 256 bits of entropy, sufficient for brute-force resistance.
  - The `requestToken` column has a unique index for fast lookups. Combined with rate limiting, this prevents enumeration attacks.
  - If a future phase requires acceptance tokens to also grant portal access (e.g., the client wants to review project details before accepting), the token lookup can be extended to create a portal session on first use without changing the URL pattern.

- Related: [ADR-030](ADR-030-portal-magic-link-auth.md) (magic-link authentication for portal contacts), [ADR-108](ADR-108-certificate-storage-and-integrity.md) (certificate storage), [ADR-109](ADR-109-portal-read-model-sync-granularity.md) (portal read-model sync for acceptance data).
