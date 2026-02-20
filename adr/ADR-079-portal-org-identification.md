# ADR-079: Org Identification Strategy for Portal

**Status**: Accepted
**Date**: 2026-02-20

**Context**:

The customer portal serves multiple organizations. Each portal contact belongs to a specific org, and all their data (projects, invoices, documents) is scoped by that org's ID. The portal needs a strategy for identifying which organization a client belongs to -- both for the unauthenticated login page (where org branding is displayed) and for authenticated pages (where API calls are org-scoped).

The existing portal JWT (issued by the Phase 7 backend) contains `org_id` and `customer_id` claims. The question is whether the org identity should also be encoded in the URL structure, and if so, how.

**Options Considered**:

1. **Subdomain-based** -- Each org gets a subdomain: `acme.portal.docteams.com`. The portal app reads the subdomain on each request to determine the org.
   - Pros: Clean, branded URLs (`acme.portal.docteams.com/invoices`); org identity visible in the browser address bar; easy for clients to bookmark "their" portal; professional appearance.
   - Cons: Requires wildcard DNS (`*.portal.docteams.com`) and wildcard SSL certificate; Next.js middleware must parse the hostname on every request; deployment complexity (single app serving all subdomains, or dynamic routing via proxy); local development requires hostname configuration; subdomain parsing is fragile across proxy layers (load balancer, CDN).

2. **Path-based** -- Org identifier in the URL path: `portal.docteams.com/o/acme/invoices`. The portal app reads the org slug from the route parameter.
   - Pros: Simple infrastructure (single domain, standard SSL); works with standard Next.js routing (`/o/[orgSlug]/*`); no DNS or proxy changes needed; easy local development.
   - Cons: Longer, less clean URLs; org slug must be validated on every request; route structure changes if org identification strategy changes; clients must know their org slug to bookmark the login page.

3. **JWT-derived (chosen)** -- The org identity is stored in the portal JWT after magic link exchange. The portal app reads `org_id` from the JWT for all authenticated API calls. The login page receives the org ID as a query parameter from the magic link email. No org identifier in the URL structure for authenticated pages.
   - Pros: Simplest infrastructure (no subdomains, no path segments for org); portal URLs are clean (`/projects`, `/invoices`, not `/o/acme/projects`); org identity is a security property of the token, not a URL parameter that can be tampered with; no route structure dependency on org identification; the portal contact is always scoped to exactly one org -- there is no use case for switching orgs.
   - Cons: Login page needs the org ID as a query parameter (from the magic link email) to display branding; clients cannot bookmark a clean login URL for "their" portal without the query param; if a client navigates to `/login` without `?orgId=...`, the login page cannot show org branding.

**Decision**: Option 3 -- JWT-derived org identification.

**Rationale**:

A portal contact belongs to exactly one organization. There is no org-switching use case -- a contact for "Smith & Associates" will never need to view data for "Jones Legal." This is fundamentally different from the admin frontend, where a Clerk user might belong to multiple orgs and switch between them. The portal's one-org-per-contact model means the org identity is a property of the authenticated session, not a navigational concern.

Encoding the org in the URL (Options 1 and 2) adds infrastructure or routing complexity for no functional benefit. The client already knows their org from the magic link email -- the email says "View your projects at Smith & Associates" and includes a link with the org ID. After authentication, the JWT carries the org ID for all subsequent API calls. The URL `/invoices` is cleaner than `/o/smith-associates/invoices` and communicates the same information.

The login page branding concern is addressed by the magic link flow itself. Clients always enter the portal via a magic link email, which includes `?orgId={clerkOrgId}` in the URL. The login page reads this parameter and fetches branding from `GET /portal/branding?orgId={orgId}`. If a client navigates to `/login` without the parameter (e.g., by typing the URL manually), the page shows a generic DocTeams login with a text prompt: "Enter the email address associated with your portal account." After exchange, branding is available from the JWT claims.

The subdomain approach (Option 1) would be the most polished solution from a branding perspective, but the infrastructure overhead (wildcard DNS, wildcard SSL, hostname parsing) is not justified for v1. If client demand for branded portal URLs emerges, migrating from JWT-derived to subdomain-based is additive -- the JWT still carries the org ID, and the subdomain becomes an additional signal for the login page.

**Consequences**:

- Positive:
  - Simplest infrastructure -- no wildcard DNS, no subdomain routing, no path-based org segments
  - Clean portal URLs (`/projects`, `/invoices`, `/profile`)
  - Org identity is a security property of the token, not a URL parameter that can be manipulated
  - No routing changes needed if org identification strategy evolves
  - Portal contacts are always scoped to one org -- no ambiguity

- Negative:
  - Login page requires `?orgId=` query parameter for org branding (mitigated by magic link emails always including this parameter)
  - Clients cannot bookmark a clean "my portal" URL without the query param (mitigated by the magic link flow -- clients enter via email, not bookmarks; and by the option to extend the exchange endpoint to return a branded portal URL in a future iteration)
  - If multi-org portal contacts are needed in the future, the JWT-derived approach must be extended with an org selector (mitigated by the current one-org-per-contact model being the correct abstraction for the target market)
