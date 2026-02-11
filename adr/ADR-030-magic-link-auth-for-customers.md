# ADR-030: Magic Link Auth for Customers

**Status**: Accepted

**Context**: The customer portal needs an authentication mechanism that is separate from the staff Clerk-based auth. Customers are external contacts who access a read-only view of their projects, documents, and comments. They do not have accounts in Clerk and should not need to create passwords or install authenticator apps. The auth mechanism must be production-grade (secure token handling, rate limiting, audit trails) even though the portal UI is currently a prototype.

Phase 4 (Epic 43) introduced a magic-link MVP using stateless JWTs with a Caffeine-based single-use cache. This works for a single instance but does not survive restarts, cannot be audited, and does not support rate limiting.

**Options Considered**:

1. **Stateless JWT magic links with Caffeine single-use cache (current MVP)**
   - Pros:
     - Already implemented and working
     - No database schema changes needed
     - Simple — no token table to manage
   - Cons:
     - Single-use enforcement lost on restart
     - Cannot rate-limit per contact without additional infrastructure
     - No audit trail (who requested what, when, from where)
     - Does not work in multi-instance deployments (each instance has its own cache)

2. **Database-backed magic link tokens with hash storage**
   - Pros:
     - Survives restarts and works across multiple instances
     - Full audit trail (created_at, created_ip, used_at)
     - Rate limiting via simple SQL count query
     - Token hash storage prevents exposure even if DB is compromised
     - Single-use enforcement via `used_at` column (idempotent)
   - Cons:
     - Requires new migration (V14)
     - Slight latency increase for token validation (DB round-trip vs cache lookup)
     - Token cleanup requires periodic batch job

3. **External auth provider (e.g., Clerk customer accounts, Auth0)**
   - Pros:
     - Full-featured auth (password, MFA, social login, session management)
     - Hosted infrastructure — no token management code
     - Proven security posture
   - Cons:
     - Introduces a second auth provider dependency (Clerk is already used for staff)
     - Per-seat pricing for external customers may not scale economically
     - Overkill for read-only portal access
     - Significant integration effort for a prototype phase
     - Tight coupling to a specific provider makes future migration harder

**Decision**: Use database-backed magic link tokens with hash storage (Option 2).

**Rationale**: The current Caffeine-based approach was appropriate for the Phase 4 MVP but does not meet production requirements. Database-backed tokens provide audit trails, multi-instance support, and rate limiting with minimal complexity. The token hash approach (store SHA-256 hash, transmit raw token only in the magic link URL) follows security best practices for credential storage.

External auth providers (Option 3) are overkill for a read-only portal and would introduce unnecessary cost and dependency. Magic links are the right UX for low-frequency, low-friction customer access — customers visit the portal occasionally to check project status or download documents, not daily.

When the portal grows to require richer authentication (e.g., password-based accounts, MFA), the `PortalContact` entity provides a natural extension point. The magic link mechanism can coexist with future auth methods — it becomes one of several login options rather than the only one.

**Consequences**:
- New `portal_contacts` and `magic_link_tokens` tables added to tenant schema (V14 migration)
- `MagicLinkService` refactored from JWT+Caffeine to DB-backed token generation and verification
- `PortalAuthController` updated to use `PortalContact` for email resolution
- A periodic cleanup job should be added (future) to purge expired/used tokens older than 30 days
- Rate limiting is enforced at 3 tokens per contact per 5 minutes
- Magic link tokens expire after 15 minutes and are single-use
