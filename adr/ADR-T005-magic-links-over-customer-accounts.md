# ADR-T005: Magic Links Over Customer Accounts

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Customers (external parties, not team members) need to access a portal to view their projects and add comments. The portal must authenticate them without creating full Keycloak accounts, as customers are managed within tenant schemas and should not pollute the identity provider.

**Options Considered:**

1. **Keycloak accounts for customers** — Create Keycloak users for each customer, use standard OIDC login
   - Pros: Unified auth mechanism, password-based login, Keycloak-managed password reset
   - Cons: Keycloak user count bloats (potentially thousands of customers across tenants), customers must remember passwords, provisioning/deprovisioning complexity, Keycloak becomes a scaling bottleneck

2. **Magic link authentication** — Backend generates time-limited, single-use tokens; customer clicks email link to get a short-lived portal JWT
   - Pros: Zero-friction access (no password, no registration), clean identity separation (customers are not Keycloak users), scoped access (JWT tied to specific customer + tenant), simple to implement and reason about
   - Cons: Requires email access for every session, no persistent login state, email delivery dependency

3. **Shared secret / PIN-based access** — Customer receives a static PIN or shared URL that grants ongoing access
   - Pros: Simplest implementation, no email dependency per session
   - Cons: No revocation without changing the PIN for everyone, no audit trail of who accessed, shared credentials are a security anti-pattern, no individual accountability

**Decision:** Use magic links with backend-issued HS256 portal JWTs.

**Rationale:**
1. **No password management:** Customers never create or remember a password. The magic link is the authentication mechanism — click the link, you're in.
2. **No Keycloak account bloat:** Customers are entities in the tenant schema's `customers` table, not Keycloak users. This keeps the identity provider focused on internal team members and avoids scaling concerns.
3. **Scoped access:** Portal JWTs contain the customer ID and org ID. The backend controls exactly what each customer can see — only their projects, only their comments.
4. **Security by design:** Tokens are cryptographically random (32 bytes), SHA-256 hashed before storage, single-use, and expire in 15 minutes. The exchanged portal JWT has a 1-hour TTL. Rate limiting prevents brute-force attempts.

**Consequences:**
- Positive: Zero-friction customer access (no registration, no password)
- Positive: Clean separation of internal identity (Keycloak) and external identity (portal JWT)
- Positive: Natural audit trail — every magic link request and exchange is a logged event
- Negative: Customers must request a new magic link for each session (mitigated by 1-hour JWT TTL — sufficient for a portal visit)
- Negative: Depends on email delivery — if email is delayed, customer access is delayed (mitigated by showing token directly in dev/test mode)
- Negative: No persistent login — customers cannot "stay logged in" across browser sessions (acceptable for a portal use case where visits are infrequent)
