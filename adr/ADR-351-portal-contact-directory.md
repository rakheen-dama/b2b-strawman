# ADR-351: Global `portal_contact_directory` Keyed by Lower-Cased Email; Email-Only Magic-Link Request; N Firms → N Branded Links; Every Client Email Link Carries Org Context

**Status**: Accepted

**Context**:

A first-time client cannot sign in to the portal unaided (G-D1, HIGH, and the 68.x DEFERREDs). `portal_contacts` is a per-tenant table, `UNIQUE (email, customer_id)`, with no global email index (`tenant/V1__baseline.sql:1400-1413`); the global `portal` schema holds only id-keyed projections. `POST /portal/auth/request-link` requires `orgId` (`@NotBlank`, `PortalAuthController.java:47-50`) because resolution is `org_schema_mapping → schema → findByEmailAndOrgId`; `login/page.tsx:99-106` therefore short-circuits with "We couldn't determine which organization…" unless `?orgId=` or the `portal_last_org_id` localStorage hint (written only after a successful exchange, GAP-L-66) exists. Only 2 of 11 email link builders carry org context (`PortalEmailService:98`, `InformationRequestEmailService:63`); the other nine (`InvoiceEmailService:80,145`, collections reminder, payment returns, `PortalDocumentNotificationHandler:300` — still `/projects/`, digest, trust, deadlines) deep-link to authenticated routes that bounce to a refusing `/login` (R5 §Q1). One email can legitimately be a contact in N firms. Next free global migration is V2, and `BaselineSchemaParityTest` still compares `public`/`portal` as migrated at startup — the first schema-changing global V2 must pin the global comparison to V1 (`backend/db/BASELINE.md`, `.arch-context.md` §10.1). The e2e driver masks the gap with `/login?orgId=<slug>`. Founder decision D6: directory first, links second, N branded links, generic response, `?orgId=` kept as a fast path.

**Options Considered**:

1. **Global directory `portal.portal_contact_directory(email_lower, tenant_schema, org_id, org_name, updated_at)` maintained by the contact lifecycle and backfilled by the app; email-only request-link (CHOSEN).**
   - Pros:
     - O(1) resolution.
     - Bare `/login` works in a fresh browser.
     - Multi-firm is a natural N-row result.
     - Stores no PII beyond the email and the firm's public identity (slug + display name).
     - No contact id, no customer id, no name.
     - Maintained at the single write choke point (`PortalContactService` create/update/archive/email change + `PortalContactAutoProvisioner`).
   - Cons:
     - Global V2 and the parity-test re-pin.
     - A backfill pass per tenant.
     - A second place that must stay in sync (guarded by sync tests).
2. **Tenant fan-out scan: loop `org_schema_mapping`, `callForTenantOnShard` per tenant, query `portal_contacts` by email.**
   - Pros:
     - Zero schema change.
     - Always consistent with the tenant tables.
   - Cons:
     - A loop over every tenant schema per login attempt on an unauthenticated endpoint — an enumeration and load amplifier that needs per-IP/email throttling just to be safe.
     - Cost grows with tenants and shards.
     - The one cross-tenant query path the isolation model forbids by design ([ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md)).
3. **`orgId` in every email link only; bare `/login` keeps refusing.**
   - Pros:
     - Cheapest.
     - Nine builder edits and a layout forward.
   - Cons:
     - A client who types the portal URL from memory, or whose email client strips the query, still cannot sign in.
     - Every future builder is a regression risk.
     - Does not fix the exchange page's hard dependency. Kept as the *second* half of D6, not as the fix.

**Decision**: Option 1 plus the link sweep — global V2 creates `portal.portal_contact_directory` with primary key `(email_lower, org_id)` and no foreign keys into tenant schemas; the directory is written by the portal-contact lifecycle and backfilled per tenant from each tenant's `portal_contacts` by an `ApplicationRunner` with a ShedLock lease, recorded in `portal_contact_directory_backfill` (`tenant_schema` PK, `backfilled_at`, `row_count`); `POST /portal/auth/request-link` accepts `email` alone, resolves zero, one or many orgs, generates one token per resolved org inside that org's tenant scope and sends one branded link per org, and always returns the existing generic "If an account exists, a link has been sent."; `?orgId=` short-circuits the directory lookup; the nine builders carry `orgId` and `(authenticated)/layout.tsx` forwards it from the incoming URL.

**Rationale**:

1. **Isolation guarantees are kept by construction.** The directory holds nothing a tenant would not print on a magic-link email (the email itself and the firm's public slug/name); it never joins to tenant tables; resolution still ends in the tenant's own `findByEmailAndOrgId` inside `RequestScopes.callForTenantOnShard`, so a stale directory row can only produce a no-op, never a cross-tenant token. Tenant A cannot resolve tenant B because the token is minted, stored and exchanged in tenant A's schema and the directory is only a routing hint. A dedicated isolation test pins this.
2. **No enumeration leak.** The response is identical for unknown, single and multi-firm emails; rate limiting stays per contact (`MagicLinkService:29,112-118`) with a new per-email cap across orgs.
3. **Multi-firm is real in SA practice** (a conveyancing client of one firm is a litigant with another); N branded links from N firms is the honest outcome and needs no chooser UI.
4. **Schema-per-tenant forces a global index** for any email-first lookup; a directory is the smallest global surface — the alternative (fan-out) is the exact query shape ADR-T001 exists to prevent.
5. **Demo-partner timeline:** step 68.1 (fresh browser, email, Mailpit link, `/matters`) is the portal's first impression; it must work without a QA-supplied `?orgId=`.

**Consequences**:

- Positive:
  - G-D1 and the 68.x DEFERREDs close.
  - The fee-note email link lands on the fee note after exchange.
  - [ADR-350](ADR-350-client-emails-explicit-intent.md)'s "Document ready" link works.
  - `tasks/lessons.md:252`'s `/login?orgId=` workaround and the e2e `global-setup.ts` direct exchange remain valid fast paths.
  - A new bare-`/login` Playwright spec in the portal suite proves the real path.
- Negative:
  - Global V2 is the first schema-changing global migration since the squash.
  - The PR first pins `BaselineSchemaParityTest`'s global comparison to `.target("1")`, then adds V2 — never re-records the snapshot.
  - `portal/app/login/__tests__/page.test.tsx:179` (short-circuit copy) and `:118-159` (orgId in body) are replaced.
  - `lib/__tests__/auth.test.ts:94-98` (`portal_last_org_id`) stays as a hint test.
  - New backend tests: directory sync on create/update/email-change/delete, backfill idempotency, multi-org fan-out sends N emails (GreenMail dynamic port), isolation (contact in A cannot exchange in B), rate limit across orgs. `PortalAuthIntegrationTest` and `MagicLinkTokenIntegrationTest` extend.
  - The directory must be deleted with the contact and rewritten on email change.
  - Archiving a contact removes the row (a contact who cannot log in should not be routable).
- Related:
  - [ADR-T005](ADR-T005-magic-links-over-customer-accounts.md) (magic links, no portal accounts), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md), [ADR-350](ADR-350-client-emails-explicit-intent.md), [ADR-352](ADR-352-acceptance-pdf-frame-ancestors.md) (the other portal unit), `backend/db/BASELINE.md`, `tasks/phase88-gap-register.md` D6.
