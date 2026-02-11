# ADR-033: Local-Only Thymeleaf Test Harness

**Status**: Accepted

**Context**: Phase 7 introduces backend-focused portal features (read-model schema, event projections, upgraded magic-link auth) that need a quick way to exercise and validate without starting the full Next.js frontend. The existing Next.js portal frontend (`/portal/*`) works but requires `pnpm dev` to be running and involves client-side state management that makes backend iteration slower.

The team plans to eventually build the production customer portal as a **separate frontend application** (not part of the staff Next.js app). In the interim, a lightweight test harness is needed for backend developers to validate the portal APIs and flows.

**Options Considered**:

1. **Use the existing Next.js portal frontend for all testing**
   - Pros:
     - Already implemented (Phase 4, Epic 43)
     - Production-realistic testing
     - No additional code needed
   - Cons:
     - Requires running Next.js dev server alongside Spring Boot
     - Frontend changes needed for every new backend endpoint
     - Adds portal code to the staff frontend (increasing entropy for eventual separation)
     - Slower iteration cycle for backend-focused development

2. **Thymeleaf-based test harness (dev/local profile only)**
   - Pros:
     - Server-rendered — no separate frontend process needed
     - Backend developers can test without Next.js knowledge
     - `@Profile({"local", "dev"})` ensures zero production surface
     - Exercises the same services and security filters as the real portal
     - Fast iteration — edit template, refresh browser
   - Cons:
     - Additional code (controllers + templates) that has no production use
     - Thymeleaf adds a dependency to the Spring Boot app
     - Risk of accidentally exposing dev harness in production (mitigated by profile guard)
     - Minimal styling — not representative of final portal UX

3. **Swagger/OpenAPI UI for manual API testing**
   - Pros:
     - Auto-generated from controller annotations
     - No custom UI code needed
     - Documents the API at the same time
   - Cons:
     - Cannot exercise the full magic-link flow (multi-step with token exchange)
     - No session state — must manually copy JWTs between requests
     - Raw JSON responses are harder to validate visually
     - Does not test the customer-facing data presentation

4. **HTTP test scripts (curl/httpie/Bruno)**
   - Pros:
     - No UI code at all
     - Scriptable and repeatable
     - Can be committed alongside the code
   - Cons:
     - No visual validation of data
     - Manual token management between steps
     - Higher barrier for non-backend developers
     - Cannot demonstrate the portal experience to stakeholders

**Decision**: Use a Thymeleaf-based test harness gated by `@Profile({"local", "dev"})` (Option 2).

**Rationale**: The test harness serves a specific purpose — letting backend developers exercise the portal auth flow, read-model projections, and API endpoints without running the Next.js frontend. Thymeleaf is the natural choice for Spring Boot server-rendered views: it's a first-party integration, requires no build step, and templates are plain HTML files that any developer can edit.

The profile guard (`@Profile({"local", "dev"})`) provides a hard boundary against production exposure. In production profiles, the Thymeleaf controllers, template resolver, and static resources are not instantiated — there is no URL that serves them.

The existing Next.js portal frontend (Option 1) remains the primary way to test the portal UX, but it should not be extended with new Phase 7 endpoints. The plan is to build the production portal as a separate application; adding more code to the staff frontend increases the entropy of eventual separation.

Swagger (Option 3) and HTTP scripts (Option 4) are complementary tools but cannot replace a visual harness that exercises the full multi-step magic-link flow.

**Consequences**:
- `spring-boot-starter-thymeleaf` added to `pom.xml`
- `DevPortalConfig` class with `@Profile({"local", "dev"})` configures template resolution
- `DevPortalController` handles `/portal/dev/*` routes — magic link generator + dashboard
- Templates in `src/main/resources/templates/portal/` — `generate-link.html`, `dashboard.html`, `project-detail.html`
- Security config updated to permit `/portal/dev/**` only in dev/local profile
- The harness exercises the same `MagicLinkService`, `PortalQueryService`, and `PortalReadModelService` as the real portal
- The harness is explicitly **not** a prototype of the production portal UI — it is a developer tool
- When the production portal UI ships, the Thymeleaf harness may be retained for internal testing or removed
