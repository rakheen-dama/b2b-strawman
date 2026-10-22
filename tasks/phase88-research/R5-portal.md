# R5 — Portal cluster root-cause dossier

Read-only research on `main` @ `50b88ed5c` (2026-10-22). Gaps in scope: G-D1, G-B7, G-D19, G-B19, G-C5, G-D14 (implicit-email part). G-D12 excluded. All six are **Pre-existing** per the 617C attribution table (`qa_cycle/evidence/phase87-capstone/gaps.md:82-118`); this dossier confirms that by code and adds the mechanism, topology and fix shapes.

Legend: **CONFIRMED** = read in source at the cited line. **HYPOTHESIS** = inferred, not executed.

---

## Q1 — G-D1: portal bare `/login` cannot resolve the firm

### How the magic-link request works (CONFIRMED)

| Step | File:line | Mechanism |
|---|---|---|
| Form submit | `portal/app/login/page.tsx:99-106` | If `orgId` is null → short-circuit with "We couldn't determine which organization…"; **no request is sent**. |
| orgId sources | `page.tsx:24` (`?orgId=` query) → `page.tsx:38-47` (`getLastOrgId()` localStorage hint) | Nothing else. No email-based resolution exists. |
| Request | `page.tsx:109-112` → `POST /portal/auth/request-link {email, orgId}` | `orgId` is `@NotBlank` on the backend DTO (`PortalAuthController.java:47-50`), so the short-circuit is correct: without it the request is a guaranteed 400. |
| Backend resolution | `PortalAuthService.java:49-104` | `orgSchemaMappingRepository.findByClerkOrgId(orgId)` (global `public.org_schema_mapping`, `global/V1__baseline.sql:280-289`) → schema → `RequestScopes.callForTenantOnShard` → `portalContactRepository.findByEmailAndOrgId(email, orgId)` (`PortalContactRepository.java:11-13`) → `magicLinkService.generateToken` (`MagicLinkService.java:63-84`) which persists the token and sends the email via `PortalEmailService.sendMagicLinkEmail` (`PortalEmailService.java:79-151`). |
| Magic link | `PortalEmailService.java:98` | `portalBaseUrl + "/auth/exchange?token=…&orgId=" + orgId` — the exchange page also requires `orgId` (`portal/app/auth/exchange/page.tsx:23-27`). |
| Exchange | `PortalAuthService.java:110-179` | Same org → schema hop; verifies token; issues portal JWT scoped to `(customerId, orgId)`. |
| Hint written | `portal/lib/auth.ts:52-60` (`storeAuth`) | `portal_last_org_id` is set **only after a successful exchange**; survives `clearAuth()` (`auth.ts:100-105`). Read by `/login` (`page.tsx:40`) and by the `(authenticated)` layout redirect (`layout.tsx:69-72`, which appends `?orgId=` only when the hint exists). |

Consequence (CONFIRMED by code, matches the observed gap): a first-time client in a fresh browser has no hint; the **only** URLs that carry `orgId` are the magic-link email itself and the information-request email (`InformationRequestEmailService.java:63`, which embeds a `generateTokenOnly` token + orgId). Every other client email deep-links to an authenticated portal route with no org context, so `/login?redirectTo=…` refuses.

### Why orgId is needed — data model (CONFIRMED)

- `portal_contacts` is a **per-tenant** table (`tenant/V1__baseline.sql:1400-1413`): `UNIQUE (email, customer_id)`, FK to tenant `customers`. There is **no global email index**. The global `portal` schema (`global/V1__baseline.sql:16, 76-330`) holds read-model projections (`portal_projects`, `portal_invoices`, `portal_acceptance_requests`, …) keyed by `customer_id`/`portal_contact_id`/`org_id` — none stores a contact email.
- Global tables that could anchor a lookup: `public.organizations` (`:65-74`, `external_org_id`, `name`) and `public.org_schema_mapping` (`:280-289`). Neither knows contacts.
- **Can one email belong to several tenants / several customers?** Yes, by schema: uniqueness is only `(email, customer_id)` inside one tenant, so the same email can be a contact of two customers in one firm and of customers in N firms. Backend already handles the intra-tenant case by preferring PRIMARY > BILLING > GENERAL (`PortalContactRepository.java:35-40`). The cross-tenant case is simply unaddressed today because `orgId` is always a required input.
- The `orgId` is the org **slug/external id** (`mathebula-partners` in the walk), public and already used for branding (`page.tsx:64-66`, `/portal/branding?orgId=`). Not a secret.

### Which emails link to the portal, and which carry org context (CONFIRMED)

| Email | Builder | Link | Carries orgId? |
|---|---|---|---|
| Magic link | `PortalEmailService.java:98` | `/auth/exchange?token&orgId` | **yes** |
| Information request (new/reminder) | `InformationRequestEmailService.java:63` (token path), `:66, :84, :102, :123, :142` | `/auth/exchange?token&orgId` (first), then `/requests/{id}` | first yes, follow-ups **no** |
| Fee note sent (INV-xxxx) | `InvoiceEmailService.java:80` | `/invoices/{id}` | **no** |
| Collections reminder | `CollectionReminderSendService.java:145` | `/invoices/{id}` | **no** |
| Payment return URLs | `PaymentLinkService.java:153,157` | `/invoices/{id}/payment-{success,cancelled}` | **no** |
| Document ready (SoA / closure letter) | `PortalDocumentNotificationHandler.java:300` | `/projects/{matterId}` (legacy; 617A redirect → `/matters/{id}`, 308 observed) | **no** |
| Weekly digest | `PortalDigestScheduler.java:304` | `/home` | **no** |
| Trust activity | `PortalEmailNotificationChannel.java:291,322` | `/trust[/matterId]` | **no** |
| Deadline reminder | `PortalEmailNotificationChannel.java:365` | `/deadlines` | **no** |
| Engagement-letter acceptance | `AcceptanceNotificationService.java:87` | `/accept/{token}` | n/a — public token page, no login needed |
| Firm-app notifications (not portal) | `EmailNotificationChannel.java:252,276` | `orgAppUrl + /projects/…` | firm app, covered by the 610D `/projects → /matters` redirect; **not a portal link** (G-C5 attribution note only) |

So 9 builders emit bare authenticated portal paths. All go through `(authenticated)/layout.tsx:60-76` → `/login?redirectTo=<path>` with no `orgId` unless localStorage already has it.

### Root cause

**CONFIRMED.** The portal's identity model is "contact-in-tenant"; the login form has no way to discover the tenant from the email, and the email link builders were written assuming the client already has a session (localStorage hint). GAP-L-66 (2026-04-27, `06d5a6337`) added the hint + the short-circuit as a session-expiry fix, not a first-login fix. The e2e/QA driver masks it with `/login?orgId=<slug>` (`tasks/lessons.md:252`; `portal/e2e/helpers/global-setup.ts` exchanges a magic link directly).

### Fix options

**(a) Email → org resolution on the backend (no user choice).**
- New endpoint `POST /portal/auth/request-link` with `orgId` **optional** (or a sibling `/portal/auth/request-link-by-email`), on the `@Order(1)` portal chain (`SecurityConfig.java:100-125`, already `permitAll` for `/portal/auth/**`).
- Resolution needs a global index because contacts live per schema. Two shapes:
  1. **Global directory table** `portal.portal_contact_directory(email_normalised, org_id, portal_contact_id, status)` in the global series (next free global = **V2**, `backend/db/BASELINE.md §5`; first global V2 must also pin `BaselineSchemaParityTest` to V1). Maintained by `PortalContactService` on create/update/archive and by `PortalContactAutoProvisioner`; backfilled once per tenant at startup/migration (tenant loop over `org_schema_mapping`). Lookup is O(1).
  2. **Fan-out scan** over `org_schema_mapping` rows (`RequestScopes.callForTenantOnShard` per tenant). Zero schema change; O(tenants). Acceptable at Phase 84 scale (single-digit tenants) but it is a loop over every tenant per login attempt and must be rate-limited by email/IP. HYPOTHESIS on cost; not measured.
- Multi-org handling: send **one magic link per org** (each `/auth/exchange?token&orgId`, branded per firm) and keep the generic "If an account exists, a link has been sent." (`PortalAuthService.java:20`) — no enumeration leak, no chooser UI. Rate limit stays per contact (`MagicLinkService.java:29,112-118`); add a per-email cap across orgs.
- Frontend: drop the short-circuit at `page.tsx:99-106`; keep `?orgId=` as a branding hint only. Update `portal/app/login/__tests__/page.test.tsx:179` (pins the short-circuit copy) and `:118-159` (orgId threaded into the body).

**(b) orgId in every client email link + copy.**
- Add `?orgId=<slug>` (or the exchange-token pattern of `InformationRequestEmailService.java:63`) to the 9 builders above; `(authenticated)/layout.tsx:60-76` must then forward `orgId` from the **incoming** URL, not only from localStorage (today it only reads `getLastOrgId()`), and `/login` already honours `?orgId=`. Copy: keep "Please use the link in your email" but only after (a) fails, i.e. unknown email.
- Cheapest, but every future builder is a regression risk, and a client who types `portal.…/login` from memory still fails. Does not fix the exchange page's hard dependency either.

**(c) Both — recommended.** (a) makes bare `/login` work; (b) makes deep links land branded and avoids a lookup when the link already knows the firm. Order: (a) first (unblocks the walk's step 68.1), (b) as a follow-up sweep of the builder table.

Product question (founder): is cross-firm identity acceptable, i.e. one client email receiving N branded links when they are a client of N Kazi firms? If not, (a) must pick one (ambiguous → ask for the link from the email, which is today's behaviour).

---

## Q2 — Portal post-login `/home` vs `/matters`

**CONFIRMED, by design, and already flagged.**
- Post-exchange target is `/matters` (`portal/app/auth/exchange/page.tsx:74`), unless a `redirectTo` was stashed (`:76-83`). 617A D-3 set this ("internal links → /matters directly (incl. post-login target)", `tasks/phase87-legal-native-shell.md:810`).
- `/` → `/home` via `portal/middleware.ts` (Phase 68, 494.13).
- `/home` is a Phase 68 "quick-actions summary" by spec (`tasks/phase68-portal-redesign-vertical-parity.md:235`): info requests, pending acceptances, deadlines, recent invoices (fee notes), trust (`portal/app/(authenticated)/home/page.tsx:46-52`). Matters and documents were never on Home; the sidebar has Home · Matters · … (`portal/lib/nav-items.ts:28-73`).
- The scenario line ("portal /home loads with Sipho's matters, fee notes and documents visible", doc line 480) is therefore a **scenario-wording mismatch**, and 617B explicitly listed "portal post-login `/home`" as needing product authorisation (`tasks/phase87-legal-native-shell.md:811, 1318`). Not a code bug. Options: amend the scenario to `/matters`, or add a "Your matters" card to Home (small, frontend-only; `MatterCard` already exists).

---

## Q3 — G-B7: `X-Frame-Options: deny` blocks the acceptance PDF iframe

### Mechanism (CONFIRMED)
- `portal/app/accept/[token]/acceptance-page.tsx:209-213` renders `<iframe src={pdfUrl}>`; `pdfUrl` = `NEXT_PUBLIC_PORTAL_API_URL + /api/portal/acceptance/{token}/pdf` (`portal/lib/api/acceptance.ts:44-48`).
- Endpoint: `PortalAcceptanceController.java:38-49` (`@RequestMapping("/api/portal/acceptance")`), `Content-Disposition: inline`. Because the path starts with `/api/`, it is served by the **main `@Order(2)` chain** (`SecurityConfig.java:130`), `permitAll` at `:144-145` — **not** the `/portal/**` portal chain.
- Neither chain calls `.headers(...)`: grep for `headers(|frameOptions|contentSecurityPolicy|frame-ancestors` in `backend/src/main/java` returns nothing. Spring Security's default header writers apply → `X-Frame-Options: DENY`. No backend test asserts frame headers (grep of `backend/src/test` empty).
- The acceptance flow is therefore "accept a document you cannot see" (the Accept button still works — `acceptance.ts:23-38`).

### Prod topology (CONFIRMED from deploy artefacts)
- **VPS (current dev-prod)**: Caddy vhosts `portal-dev.heykazi.com → portal:3002` and `api-dev.heykazi.com → backend:8080` (`compose/caddy/Caddyfile:17-23`). The portal image is built with `NEXT_PUBLIC_PORTAL_API_URL=https://api-dev.heykazi.com` (`.github/workflows/deploy-vps.yml:73`; baked at build, `portal/Dockerfile:47-48`). The gateway (`bff-dev`) is the **firm app's** BFF; no `/portal` route exists in gateway config (grep empty). So in prod the portal iframes a **cross-origin** URL — same failure as local.
- **AWS staging workflow**: `NEXT_PUBLIC_PORTAL_API_URL=https://${PORTAL_DOMAIN}/api` (`deploy-staging.yml:201`, `seed-images.yml:110`) — same-origin via path routing. **Still blocked**: `DENY` refuses framing even by the same origin (only `SAMEORIGIN` would allow it).
- Verdict: **prod bug under both topologies**, not local-only.

### Fix shapes
1. **Backend, scoped (recommended):** on the main chain add a request-matcher-scoped header writer for `/api/portal/acceptance/*/pdf` that disables `X-Frame-Options` and sets `Content-Security-Policy: frame-ancestors <portal origin>` (origin derived from `docteams.app.portal-base-url`, `application.yml:108`, env `PORTAL_BASE_URL` = `https://portal-dev.heykazi.com` in `docker-compose.prod.yml:125`). Implementation: `http.headers(h -> h.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(matcher, cspWriter)))` plus `frameOptions` disabled for that matcher (or a dedicated `@Order(0)` chain with `securityMatcher("/api/portal/acceptance/*/pdf")`, `permitAll`, its own `headers`). Keep `DENY` everywhere else. Add an integration test asserting the two headers on that path and `DENY` on a sibling path.
2. **Frontend-only alternative:** `fetch(pdfUrl)` (CORS already allows the portal origin for GET, `docker-compose.prod.yml:122-123`, `application-local.yml:97-100` — note local allowlist is `:3000/:3001` only; `:3002` must be checked at runtime, HYPOTHESIS) → `URL.createObjectURL(blob)` → `<iframe src={blobUrl}>`. A `blob:` URL is same-origin to the portal and carries no response headers, so XFO is moot. No backend change; works in every topology. `<object>`/`<embed>` do **not** help — Chrome applies XFO to them too.
3. **Fallback UX regardless of 1/2:** "Open PDF in a new tab" / download link next to the viewer (the response is already `inline`).

Recommend 1 + 3 (server states intent; the header also protects against third-party framing) — or 2 + 3 if the backend PR must stay untouched this cycle.

---

## Q4 — G-D19: portal matter card shows no status

**CONFIRMED frontend-only.**
- Backend list DTO carries status: `PortalProjectController.java:36-46, 87-93` (`PortalMatterResponse(id, name, status, …)`), fed from `portal.portal_projects.status` (`global/V1__baseline.sql:182-194`), kept in sync on close/reopen by `MatterClosureNotificationHandler.java:91-128` → `PortalReadModelRepository.java:146-158`.
- Portal type already has it (`portal/lib/types.ts:32`, comment "optional until backend list endpoint surfaces it" is stale) and `matters/page.tsx:44-50` already filters Active/Past on `p.status`.
- `portal/components/matter-card.tsx:33-60` just never renders it. Fix: make `status` required in `PortalMatter`, render `<StatusBadge status={matter.status}>` (a `components/status-badge` exists — `components/__tests__/status-badge.test.tsx`) in the card header; update `components/__tests__/matter-card.test.tsx`.

---

## Q5 — G-B19: no Decline on the acceptance page

**CONFIRMED: backend has no DECLINED state or endpoint; this is a feature, not a UI gap.**
- `AcceptanceStatus.java:4-22`: `PENDING, SENT, VIEWED, ACCEPTED, EXPIRED, REVOKED` only. Portal endpoints: `GET /{token}`, `GET /{token}/pdf`, `POST /{token}/accept` (`PortalAcceptanceController.java:31-60`). Firm side has `remind`/`revoke` (`AcceptanceController.java:55-61`). `grep -i declin` across `backend/.../acceptance`, `portal/app`, `portal/components` finds only an unrelated `sticky-action-bar` docstring.
- Scope if authorised: enum value `DECLINED` (+ optional `declineReason`, column on tenant `acceptance_requests` → tenant **V4**), `AcceptanceService.declineFromPortal(token, reason, ip, ua)`, `POST /api/portal/acceptance/{token}/decline`, audit event + firm notification (mirror `acceptFromPortal`; `AcceptanceAuditAndNotificationIntegrationTest`), read-model sync of `portal.portal_acceptance_requests.status` (`AcceptanceReadModelSyncIntegrationTest`), closure gate semantics (`AllAcceptanceRequestsFinalGateTest` — is DECLINED "final"?), firm UI badge/filter, portal page Decline button + confirmation (`acceptance-page.tsx` ~`:300-340`), `app/accept/[token]/__tests__/page.test.tsx`.
- Product decision needed: the scenario (doc line 346) says Accept/Decline; the product never had Decline. Either amend the scenario (authorisation required, CLAUDE.md §6) or build it (~1 backend slice + 1 portal slice).

---

## Q6 — G-C5 / G-D14: implicit "Document ready" client emails

### Trigger chain (CONFIRMED)
1. **Statement of Account** — `StatementService.generate` (`StatementService.java:155-168`) always creates the paired `Document` with `Visibility.PORTAL` ("system-auto-shared per scenario step 61.2") and publishes `DocumentGeneratedEvent` with `details.visibility = "PORTAL"`, `templateName = "statement-of-account"` (`:214-246`). `GenerateStatementRequest.java` has only `periodStart, periodEnd, templateId` — **no notify/visibility flag**. The "Preview & Save" button (`frontend/components/legal/statement-of-account-dialog.tsx:331`) is the only action; the dialog has no email/portal copy at all (grep empty).
2. **Closure** — `MatterClosureService.generateClosureLetterSafely` passes `Document.Visibility.PORTAL` (`MatterClosureService.java:391-401`, "born portal-visible"); `generateSoaSafely` (`:437-452`) calls the same `StatementService.generate`. The closure dialog defaults `generateStatementOfAccount: true` (`matter-closure-dialog.tsx:83,120`) and its checkbox copy says only "will be attached to this matter" (`:317-321`) — no mention that the client is emailed, and no option to generate the letter without notifying.
3. **Handler** — `PortalDocumentNotificationHandler.onDocumentGenerated` (`:117-244`): gates are (i) project-scoped, (ii) template slug in `OrgSettings.portal.portalNotificationDocTypes`, default `["matter-closure-letter","statement-of-account"]` (`OrgSettings.java:30`, `PortalSettings.java:56-73`) — **no settings UI exposes this list** (frontend grep empty), (iii) portal-visible via `details.visibility` or the persisted Document (`:252-287`), (iv) 5-min dedup per (tenant, customer, project, template). Then `PortalEmailService.sendDocumentReadyEmail` (`:195-198`) with the legacy `/projects/{id}` link (`:300`).
4. Any other generated document with `saveToDocuments` + an intended visibility of SHARED/PORTAL and a slug in the allowlist would also notify (`GeneratedDocumentService.java:263-310`) — today only those two slugs are allowlisted, so the blast is limited to SoA + closure letter.

### Root cause
**CONFIRMED.** GAP-L-72 (slice 23) made "generated ⇒ portal-visible ⇒ emailed" an invariant for these two artefacts to fix "client waits a week for the digest". There is no per-generation intent: the firm cannot save a draft SoA for review, nor close a matter quietly. The visibility flip and the email are coupled in the same event.

### Fix shapes
- **Recommended: explicit intent at the dialog, default off.** Add `notifyClient: Boolean` (null → false) to `GenerateStatementRequest` and `ClosureRequest`; thread it into `DocumentGeneratedEvent.details` (e.g. `notify_client`) and gate the handler on it (keeping the allowlist/visibility gates). Dialog copy: "Notify client by email (the document is visible on the portal either way)". Separate concern: whether the document should be PORTAL-visible at all without review — if the founder wants review-before-share, add a `shareWithClient` flag that sets `Visibility.INTERNAL` vs `PORTAL` (the handler then naturally skips INTERNAL), and a later "Share with client" action on the document emits the notification.
- Minimal alternative: keep behaviour, add honest copy to both dialogs ("The client will be emailed immediately") and a tenant setting UI for `portalNotificationDocTypes`. Does not fix the SoA "preview" being irrevocable.
- Independent of either: fix the link at `PortalDocumentNotificationHandler.java:300` to `/matters/` (and the `orgId` question from Q1).

---

## Q7 — Blast radius / tests to run

**Portal (vitest, `pnpm --dir portal test`, full run):** `app/login/__tests__/page.test.tsx` (pins the short-circuit at `:179` and orgId threading `:118-159`), `app/auth/exchange/__tests__/page.test.tsx`, `lib/__tests__/auth.test.ts` (`:94-98` pins `portal_last_org_id`), `lib/__tests__/api-client.test.ts`, `app/accept/[token]/__tests__/page.test.tsx` (iframe/pdfUrl), `components/__tests__/matter-card.test.tsx`, `components/__tests__/status-badge.test.tsx`, `app/(authenticated)/matters/__tests__/page.test.tsx`, `app/(authenticated)/home/__tests__/*`. Plus `pnpm lint && pnpm build` and prettier `format:check`.

**Portal Playwright (`portal/playwright.portal.config.ts`, needs `PLAYWRIGHT_BASE_URL=:3001`-style env + e2e stack):** `e2e/tests/portal-client-90day/day-00-07.spec.ts` (acceptance page), `day-45-75`, `day-85-90`, `e2e/tests/responsive/portal-pages.spec.ts`; `e2e/helpers/global-setup.ts` exchanges a magic link directly — a login-page change must be covered by a new spec that drives bare `/login`.

**Backend (`bash scripts/verify.sh`, full):** `portal/PortalAuthIntegrationTest`, `portal/MagicLinkTokenIntegrationTest`, `portal/PortalEmailServiceIntegrationTest`, `portal/PortalBrandingControllerIntegrationTest`, `portal/PortalContextControllerIntegrationTest`, `portal/PortalDocumentNotificationHandlerIntegrationTest`, `portal/notification/PortalEmailNotificationChannelIntegrationTest`, `verticals/legal/statement/StatementServicePortalDocumentTest`, `verticals/legal/closure/MatterClosureServiceIntegrationTest`, `closure/event/MatterClosureNotificationHandlerTest`, `closure/gates/AllAcceptanceRequestsFinalGateTest`, all `acceptance/*` (9 classes incl. `PortalAcceptanceControllerIntegrationTest`), `customerbackend/AcceptanceReadModelSyncIntegrationTest`. For a global V2 migration: `provisioning/BaselineSchemaParityTest` must be pinned to V1 (BASELINE.md §5) and `OrgSettingsSchemaSnapshotTest` is untouched unless `org_settings` changes. New: a security-headers test for the PDF path (none exists).

**Frontend (`pnpm lint && pnpm build && pnpm test`, full):** `__tests__/legal/statement-of-account-dialog.test.tsx`, `__tests__/legal/matter-closure-dialog.test.tsx`, `__tests__/legal/matter-closure-report.test.tsx`, `__tests__/legal/statements-tab.test.tsx`.

**Observed verification (CLAUDE.md §3):** Mailpit for every email change (link contains orgId / no implicit email); browser on `:3002/login` in a fresh context; Chrome console clean of "Refused to display" on `/accept/<token>`; `curl -I` the PDF path for the headers.

---

## Related gaps
- G-B16 (acceptance email subject), G-B19 share the acceptance surface.
- G-C4 (SoA preview unbounded scale) compounds G-C5: the client is emailed a document the firm could not read.
- G-D8 (closure override event) sits in the same `verticals/legal/closure` package as G-D14.
- G-D1 interacts with every portal email link; fixing Q6's `/projects/` link without Q1 still lands on a refused `/login`.
- 611A.2 / 617A D-2 (template category not reaching the portal) is unrelated to status (Q4) but in the same portal read-model area.

## Product questions for the founder
1. Cross-firm client identity: one email → N firms → N branded magic links, or refuse ambiguity? (Q1)
2. Is Home's composition (no matters card) accepted, i.e. amend scenario line 480 to `/matters`, or add a Matters card? (Q2)
3. Decline on engagement letters: build (enum + endpoint + firm notification + gate semantics) or amend scenario line 346? (Q5)
4. SoA/closure-letter client notification: explicit opt-in checkbox (default off) vs honest copy only; and should SoA be PORTAL-visible before the firm reviews it? (Q6)
5. Should `portalNotificationDocTypes` get a settings UI at all, or be removed in favour of per-action intent?

## Recommended PR shaping (one fix per PR)
1. G-B7: backend scoped `frame-ancestors` + "Open in new tab" (+ header test).
2. G-D19: status badge on `matter-card.tsx` (+ test; make `status` required).
3. G-C5/G-D14: `notifyClient` flag through SoA + closure requests → handler gate; dialog copy (+ fix `:300` link to `/matters/`).
4. G-D1 (a): email-based resolution endpoint + directory table (global V2) + login page change; then (b) builder sweep as a follow-up.
5. G-B19: separate feature slice after the product decision.
