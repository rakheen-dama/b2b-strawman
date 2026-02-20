You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with dedicated schema-per-tenant isolation (Phase 13 eliminated shared schema).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal backend** (Phase 7): `PortalContact` entity with magic link authentication (15-min TTL, SHA-256 hashed tokens, rate-limited), portal JWT issuance (HS256, 1-hour TTL), `CustomerAuthFilter` with `ScopedValue` bindings. Dedicated portal read-model schema with `portal_projects`, `portal_documents`, `portal_comments`, `portal_project_summaries` tables synced via domain events. REST API: `/portal/auth/*` (request-link, exchange), `/portal/projects`, `/portal/documents`, `/portal/projects/{id}/comments`, `/portal/projects/{id}/summary`, `/portal/me`. Thymeleaf dev harness (local/dev profiles only).
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default -> project-override -> customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency, logo, brand_color, footer text). Profitability reports.
- **Operational dashboards** (Phase 9): company dashboard, project overview, personal dashboard, health scoring.
- **Invoicing & billing from time** (Phase 10): `Invoice`/`InvoiceLine` entities, draft-to-paid lifecycle, unbilled time management, HTML invoice preview via Thymeleaf, PDF generation via OpenHTMLToPDF.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `Tag`, `SavedView` entities.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline. Org branding.
- **Customer compliance & lifecycle** (Phase 14): Customer lifecycle state machine, checklist engine, compliance packs.
- **Contextual actions & setup guidance** (Phase 15): Setup status aggregation, contextual action cards.
- **Project templates & recurring schedules** (Phase 16): `ProjectTemplate`, `RecurringSchedule` entities with daily scheduler.
- **Retainer agreements & billing** (Phase 17): `RetainerAgreement`, `RetainerPeriod` entities with hour banks and rollover.

For **Phase 18**, I want to add the **Customer Portal Frontend** — a separate Next.js application that gives clients a branded, read-only portal to view their projects, documents, invoices, and comment threads. The backend APIs already exist (Phase 7); this phase builds the client-facing experience and fills backend gaps (invoice data in the read-model).

***

## Objective of Phase 18

Design and specify:

1. **Separate Next.js portal application** (`portal/`) — a new Next.js 16 app within the monorepo, sharing Tailwind CSS v4 and Shadcn UI components with the main frontend, but with its own layout, routing, and deployment configuration.
2. **Magic link authentication flow** — request magic link → exchange for portal JWT → session management via HTTP-only cookies or localStorage. Built on the existing Phase 7 `/portal/auth/*` endpoints.
3. **Portal shell and navigation** — a simplified, client-focused layout with org branding (logo, brand color from `OrgSettings`). No sidebar — minimal header navigation between projects, invoices, and profile.
4. **Project list and detail pages** — home page showing the customer's projects; detail page with project overview, task list (read-only status), documents, and comment thread.
5. **Document viewing and download** — document list per project, S3 presigned URL downloads via the existing `/portal/documents/{id}/presign-download` endpoint.
6. **Invoice list and detail pages** — list of all invoices for this customer with status indicators; detail page with line items, totals, and PDF download. **Requires new backend work**: syncing invoice data to the portal read-model and adding portal invoice endpoints.
7. **Comment threads** — view and post comments on projects, using the existing `/portal/projects/{id}/comments` endpoints.
8. **Profile page** — contact info and display name from `/portal/me`.
9. **Responsive design** — works well on mobile browsers (clients rarely use native apps for portals; web is the industry standard).
10. **Infrastructure and deployment** — Docker configuration, monorepo integration, portal-specific environment variables, deployment alongside the existing frontend and backend.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- The portal is a **separate Next.js 16 app** in the monorepo at `portal/`. It shares the Tailwind CSS v4 configuration and Shadcn UI component library with the main `frontend/` app, but has its own `package.json`, routing, and build configuration.
- **No Clerk integration** in the portal. Authentication is via magic link tokens exchanged for portal JWTs (Phase 7). Clerk is only used in the internal admin frontend.
- **No SSR for authenticated pages.** The portal JWT is a client-side concern (stored in localStorage or a cookie). Pages fetch data client-side via the portal REST API. The login/magic-link-request page can be SSR.
- The portal uses the **same Shadcn UI components** (buttons, cards, tables, dialogs, etc.) but with a different layout — no sidebar, simplified header, org branding applied via CSS variables.
- **Org branding**: The portal renders the organization's logo, brand color, and name. These come from `OrgSettings` (Phase 8). The portal needs a way to fetch branding without authentication (it's shown on the login page). This requires a new **public endpoint**: `GET /portal/branding?orgId={orgId}` → returns `{ orgName, logoUrl, brandColor }`.
- Do not introduce any new database tables in the main tenant schemas for this phase. The portal frontend reads from the existing portal read-model schema. The only backend additions are: (a) invoice sync to the portal read-model, (b) portal invoice endpoints, (c) the public branding endpoint.

2. **Tenancy**

- The portal app serves multiple organizations. The `orgId` is part of the URL structure (e.g., `/o/{orgSlug}/projects`) or derived from the magic link/JWT claims.
- The portal read-model schema is shared across all tenants but data is partitioned by `org_id`. All queries filter by `org_id` from the portal JWT.
- The portal app needs the backend base URL as an environment variable (`NEXT_PUBLIC_PORTAL_API_URL`).

3. **Permissions model**

- **All portal pages require authentication** (valid portal JWT) except:
  - Login page (magic link request form)
  - Magic link exchange page (token → JWT)
  - Branding endpoint (public)
- **Portal contacts see only their customer's data.** The `customer_id` in the portal JWT is the access boundary. The backend already enforces this — the portal frontend just displays what the API returns.
- **Comment posting**: Portal contacts can post comments on projects linked to their customer. Comments from portal contacts are marked with `source = PORTAL` (the backend already supports this).

4. **Relationship to existing features**

- **Phase 7 backend** is the foundation — magic links, read-model, all existing portal API endpoints.
- **Phase 8 OrgSettings** provides branding data (logo, brand_color).
- **Phase 10 invoicing** provides invoice data that needs to be synced to the portal read-model.
- **Phase 12 document templates** — generated documents (PDFs) are already visible via the portal document endpoints if their visibility is set to SHARED.

5. **Out of scope for Phase 18**

- Document approval workflows (client approves/rejects deliverables).
- Client file uploads (clients uploading documents to the portal).
- Online invoice payment (payment gateway integration). The portal shows invoice status but clients cannot pay online yet.
- Retainer visibility (retainer agreements, hour banks, period status). Can be added in a future phase once Phase 17 is fully complete.
- Real-time updates (WebSockets, SSE). Polling or manual refresh is fine for v1.
- Email notification delivery (email stubs exist from Phase 6.5 but actual sending is not implemented).
- Native mobile application. The portal is a responsive web app.
- Notification inbox in the portal (portal contacts don't receive in-app notifications yet).

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase18-customer-portal-frontend.md`, plus ADRs for key decisions.

### 1. Portal application scaffolding

Design the **new Next.js app** structure:

1. **Monorepo integration**

    - New directory `portal/` at the repo root, alongside `frontend/` and `backend/`.
    - Own `package.json` with Next.js 16, React 19, Tailwind CSS v4, Shadcn UI dependencies.
    - Shared Tailwind configuration via a common config file or package. The existing `frontend/` design tokens (colors, fonts, spacing) should be reusable.
    - Shadcn components can be duplicated into `portal/components/ui/` (same pattern as `frontend/`) — no need for a shared package at this stage. Keep it simple.
    - Own `tsconfig.json`, `next.config.ts`, `Dockerfile`.

2. **Directory structure**

    ```
    portal/
    ├── app/
    │   ├── layout.tsx              # Root layout (minimal, applies org branding)
    │   ├── page.tsx                # Redirect to login or dashboard
    │   ├── login/
    │   │   └── page.tsx            # Magic link request form
    │   ├── auth/
    │   │   └── exchange/
    │   │       └── page.tsx        # Token exchange (from magic link URL)
    │   ├── (authenticated)/        # Route group — all pages requiring portal JWT
    │   │   ├── layout.tsx          # Portal shell: header, nav, branding
    │   │   ├── projects/
    │   │   │   ├── page.tsx        # Project list (home/dashboard)
    │   │   │   └── [id]/
    │   │   │       └── page.tsx    # Project detail
    │   │   ├── invoices/
    │   │   │   ├── page.tsx        # Invoice list
    │   │   │   └── [id]/
    │   │   │       └── page.tsx    # Invoice detail
    │   │   └── profile/
    │   │       └── page.tsx        # Contact profile
    │   └── not-found.tsx
    ├── components/
    │   ├── ui/                     # Shadcn UI components (duplicated from frontend)
    │   ├── portal-header.tsx       # Header with logo, nav, contact name
    │   ├── project-card.tsx        # Project summary card
    │   ├── document-list.tsx       # Document table with download links
    │   ├── comment-section.tsx     # Comment thread with reply form
    │   ├── invoice-card.tsx        # Invoice summary card
    │   ├── invoice-line-table.tsx  # Invoice line items table
    │   ├── status-badge.tsx        # Reusable status indicator
    │   └── branding-provider.tsx   # Context provider for org branding
    ├── lib/
    │   ├── api-client.ts           # Portal API client (fetch wrapper with JWT)
    │   ├── auth.ts                 # Auth utilities (store/retrieve JWT, check expiry)
    │   ├── format.ts               # Date, currency, duration formatting
    │   └── types.ts                # TypeScript types matching portal API responses
    ├── hooks/
    │   ├── use-auth.ts             # Auth state hook (JWT, customer info, logout)
    │   └── use-branding.ts         # Org branding hook
    ├── public/
    ├── Dockerfile
    ├── package.json
    ├── tailwind.config.ts
    ├── tsconfig.json
    └── next.config.ts
    ```

3. **Docker configuration**

    - Standalone Next.js output (same as existing `frontend/`).
    - Multi-stage Docker build: install → build → production.
    - Environment variables: `NEXT_PUBLIC_PORTAL_API_URL` (backend base URL for portal APIs).
    - Port: 3001 (distinct from frontend's 3000).
    - Add to `compose/docker-compose.yml` as a new service.

### 2. Authentication flow

Design the **magic link authentication** implementation:

1. **Login page** (`/login`)

    - Shows org logo and name (fetched from `GET /portal/branding?orgId={orgId}`).
    - Email input form.
    - "Send Magic Link" button → calls `POST /portal/auth/request-link`.
    - Success state: "Check your email for a login link" message.
    - Error handling: rate limit exceeded, invalid org, network errors.
    - The `orgId` is derived from the URL. Portal URLs include the org identifier: the portal is deployed per-org or uses a path/subdomain strategy (see ADR below).

2. **Token exchange page** (`/auth/exchange`)

    - Receives `token` and `orgId` from the magic link URL query params.
    - Automatically calls `POST /portal/auth/exchange` on mount.
    - On success: stores JWT + customer info, redirects to `/projects`.
    - On failure: shows "Link expired or invalid" with option to request a new link.

3. **Session management**

    - Store portal JWT in `localStorage` (simple, sufficient for 1-hour TTL).
    - `useAuth()` hook provides: `token`, `customerId`, `customerName`, `isAuthenticated`, `logout()`.
    - API client reads token from `useAuth()` and includes `Authorization: Bearer {token}` header.
    - On 401 response from any API call: clear token, redirect to login.
    - No refresh token mechanism — when JWT expires after 1 hour, user requests a new magic link. This is acceptable for portal usage patterns (infrequent, short sessions).

4. **Route protection**

    - `(authenticated)/layout.tsx` checks for valid JWT. If missing or expired, redirects to `/login`.
    - No middleware-based protection (JWT is client-side). The layout acts as the auth guard.

### 3. Portal shell and branding

Design the **portal layout** and branding system:

1. **Header**

    - Left: org logo (from branding) + org name.
    - Center: navigation links — Projects, Invoices.
    - Right: contact display name + Profile link + Logout button.
    - Mobile: hamburger menu for navigation links.
    - Brand color applied as CSS custom property: `--portal-brand-color`. Used for primary buttons, links, and accent elements.

2. **Branding provider**

    - `BrandingProvider` context wraps the authenticated layout.
    - Fetches branding on mount (or reads from auth response if we extend the exchange endpoint).
    - Provides: `orgName`, `logoUrl`, `brandColor` to all child components.
    - Fallback: if no logo is set, show org name in text. If no brand color, use a default blue.

3. **Layout principles**

    - **No sidebar.** Clients don't need a sidebar — they have 3 sections (projects, invoices, profile). Header navigation is sufficient.
    - **Content-centered layout.** Max-width container (similar to the admin app's content area but without the sidebar offset).
    - **Clean, professional aesthetic.** Clients may be from any industry — the portal should feel modern and trustworthy without being flashy.
    - **Footer**: "Powered by DocTeams" + org's footer text from `OrgSettings` (if set).

### 4. Project list page (portal home)

Design the **project list / dashboard** page:

1. **URL**: `/(authenticated)/projects` (also the default authenticated landing page)

2. **Data source**: `GET /portal/projects` → `List<PortalProjectResponse>`

3. **Layout**:
    - Page title: "Your Projects" or "Projects" (branded with org name).
    - Grid of project cards (responsive: 1 column mobile, 2 columns tablet, 3 columns desktop).
    - Each card shows: project name, status badge, description excerpt (2 lines), document count, last activity date.
    - Click card → navigate to project detail.
    - Empty state: "No projects yet. Your {orgName} team will share projects with you here."

4. **No filtering or search in v1.** Clients typically have 5-20 projects — a simple grid is sufficient.

### 5. Project detail page

Design the **project detail** page:

1. **URL**: `/(authenticated)/projects/[id]`

2. **Data sources**:
    - `GET /portal/projects/{id}` → project detail
    - `GET /portal/projects/{id}/documents` → document list (SHARED visibility only)
    - `GET /portal/projects/{id}/comments` → comment thread
    - `GET /portal/projects/{id}/summary` → time/billing summary

3. **Layout** (single page with sections):

    a. **Project header**: Name, status badge, description.

    b. **Summary card**: Total hours, Billable hours, Last activity (from `/summary` endpoint). Displayed as a clean stat row. Only shown if hours > 0.

    c. **Tasks section**: Read-only task list showing task name, status, and assignee (if any). **Note**: The current portal API does NOT have a task endpoint. This requires a new endpoint: `GET /portal/projects/{id}/tasks` → `List<PortalTaskResponse>`. The read-model also needs a `portal_tasks` table synced via domain events. Design this as part of the backend additions.

    d. **Documents section**: Table with file name, type icon, size, uploaded date. Download button per row (calls presign-download endpoint, then opens the presigned URL). Empty state: "No documents shared yet."

    e. **Comments section**: Chronological comment list with author name, date, and content. "Add a comment" text area + submit button at the bottom. Comments from portal contacts show the contact's display name. Comments from internal team members show the member's name.

### 6. Invoice pages (NEW — requires backend additions)

Design the **invoice list and detail** pages, including the required backend work:

#### 6a. Backend additions — Invoice sync to portal read-model

The portal read-model does not currently include invoice data. Add:

1. **`portal_invoices` table** (in portal read-model schema):

    - `id` (UUID PK — invoice ID).
    - `org_id` (VARCHAR — Clerk org ID).
    - `customer_id` (UUID — customer FK).
    - `invoice_number` (VARCHAR — display number, e.g., "INV-2026-0001").
    - `status` (VARCHAR — DRAFT, SENT, VIEWED, PAID, OVERDUE, CANCELLED, VOID).
    - `issue_date` (DATE).
    - `due_date` (DATE).
    - `subtotal` (DECIMAL).
    - `tax_amount` (DECIMAL).
    - `total` (DECIMAL).
    - `currency` (VARCHAR(3) — ISO 4217).
    - `notes` (TEXT, nullable — customer-facing notes on the invoice).
    - `synced_at` (TIMESTAMP).

2. **`portal_invoice_lines` table** (in portal read-model schema):

    - `id` (UUID PK — invoice line ID).
    - `portal_invoice_id` (UUID FK → portal_invoices).
    - `description` (TEXT — line item description).
    - `quantity` (DECIMAL — hours or units).
    - `unit_price` (DECIMAL — rate per unit).
    - `amount` (DECIMAL — quantity × unit_price).
    - `sort_order` (INTEGER).
    - `synced_at` (TIMESTAMP).

3. **Event handlers** (in `PortalEventHandler`):

    - Listen for invoice domain events (InvoiceCreated, InvoiceUpdated, InvoiceStatusChanged, InvoiceDeleted).
    - Sync to `portal_invoices` and `portal_invoice_lines` on each event.
    - **Only sync invoices with status SENT or later** — DRAFT invoices are internal and should not be visible to clients. When an invoice transitions from DRAFT to SENT, sync it. If it transitions back (edge case), remove it.

4. **Portal invoice endpoints**:

    - `GET /portal/invoices` — list all invoices for the authenticated customer. Returns: id, invoice_number, status, issue_date, due_date, total, currency.
    - `GET /portal/invoices/{id}` — invoice detail with line items. Returns: full invoice data + line items array.
    - `GET /portal/invoices/{id}/download` — returns a presigned URL for the invoice PDF (generated via Phase 12's rendering pipeline). If no PDF exists yet, generate on-the-fly.

#### 6b. Frontend — Invoice list page

1. **URL**: `/(authenticated)/invoices`

2. **Data source**: `GET /portal/invoices`

3. **Layout**:
    - Page title: "Invoices".
    - Table: Invoice #, Status (badge), Issue Date, Due Date, Total (formatted with currency), Actions (View, Download PDF).
    - Status badge colors: SENT (blue), VIEWED (yellow), PAID (green), OVERDUE (red), CANCELLED/VOID (gray).
    - Sort by issue date descending (newest first).
    - Empty state: "No invoices yet."

#### 6c. Frontend — Invoice detail page

1. **URL**: `/(authenticated)/invoices/[id]`

2. **Data source**: `GET /portal/invoices/{id}`

3. **Layout**:
    - Invoice header: Invoice number, status badge, issue date, due date.
    - "Download PDF" button (prominent).
    - Customer info section: customer name (from portal JWT context).
    - Line items table: Description, Quantity, Rate, Amount.
    - Totals section: Subtotal, Tax, Total (formatted with currency).
    - Notes section (if present).
    - No payment action in v1 — just a clear display of the invoice.

### 7. Backend additions — Task sync to portal read-model

The portal backend currently does not expose task data. For the project detail page to show tasks, add:

1. **`portal_tasks` table** (in portal read-model schema):

    - `id` (UUID PK — task ID).
    - `org_id` (VARCHAR — Clerk org ID).
    - `portal_project_id` (UUID FK → portal_projects).
    - `name` (VARCHAR — task name).
    - `status` (VARCHAR — TODO, IN_PROGRESS, DONE, etc.).
    - `assignee_name` (VARCHAR, nullable — display name of assigned member, for read-only display).
    - `sort_order` (INTEGER).
    - `synced_at` (TIMESTAMP).

    Note: Only minimal task data is synced — no description, estimated hours, or billable flag. Clients see task name + status + assignee. Internal details stay internal.

2. **Event handlers** — listen for task domain events (TaskCreated, TaskUpdated, TaskDeleted, TaskAssigned). Sync to `portal_tasks`.

3. **Portal task endpoint**:

    - `GET /portal/projects/{projectId}/tasks` — list tasks for a project. Returns: id, name, status, assigneeName, sortOrder.

### 8. Backend addition — Public branding endpoint

Add a public (unauthenticated) endpoint for portal branding:

- `GET /portal/branding?orgId={orgId}` — returns `{ orgName, logoUrl, brandColor }`.
- Reads from `OrgSettings` (logo, brand_color) and the org schema mapping (org name).
- **No authentication required** — this is shown on the login page before the client authenticates.
- **Cache-friendly**: include `Cache-Control: public, max-age=3600` header. Branding changes infrequently.
- If org has no branding configured, return defaults (no logo, default brand color, org name from schema mapping).

### 9. Comment posting from portal

The existing `/portal/projects/{id}/comments` endpoint supports GET (list comments). Extend it:

- `POST /portal/projects/{projectId}/comments` — post a new comment from a portal contact.
- Request body: `{ content: string }`.
- The comment is created with `source = PORTAL` and the contact's display name as the author.
- The comment triggers the existing notification system (notify project team members).
- **Content limits**: max 2000 characters per comment.

Verify whether this endpoint already exists in the Phase 7 implementation. If it does, no new work is needed. If not, add it.

### 10. ADRs for key decisions

Add ADR-style sections for:

1. **Separate Next.js app vs. embedded in main frontend**:
    - Why the portal is a separate application (`portal/`) rather than a route within the existing `frontend/` app.
    - Separate auth systems (Clerk vs. magic links) would create complex middleware. Separate apps keep each auth flow clean and isolated.
    - Deployment independence: portal can be deployed/scaled separately from the admin app.
    - Security boundary: the portal app cannot accidentally expose admin-only routes or data.
    - Trade-off: some component duplication (Shadcn UI files copied). But components are small and stable — duplication is cheaper than a shared package at this stage.

2. **Client-side JWT storage (localStorage) vs. HTTP-only cookies**:
    - Why `localStorage` is acceptable for portal JWTs despite being less secure than HTTP-only cookies.
    - Portal JWTs have a 1-hour TTL — short-lived tokens limit the XSS attack window.
    - The portal is read-only (v1) — no destructive operations. The risk profile is lower than the admin app.
    - HTTP-only cookies would require a BFF (backend-for-frontend) layer to set cookies from the Spring Boot backend, adding infrastructure complexity.
    - Trade-off: XSS can steal the JWT. Mitigation: standard XSS prevention (CSP headers, input sanitization). In a future phase, migrating to HTTP-only cookies via a BFF is straightforward.

3. **Portal read-model extension (invoice/task sync) vs. direct tenant schema queries**:
    - Why invoice and task data is synced to the portal read-model schema rather than having portal endpoints query the main tenant schema directly.
    - The portal read-model was designed in Phase 7 as a security boundary — portal queries never touch tenant schemas. This prevents a portal bug from leaking tenant data.
    - Event-driven sync means the portal schema has exactly the data clients should see (e.g., only SENT+ invoices, only SHARED documents). Filtering happens at write-time, not query-time.
    - Trade-off: eventual consistency (there's a brief delay between main schema write and read-model sync). Acceptable for portal use cases.

4. **Org identification strategy — path-based vs. subdomain-based**:
    - How the portal identifies which org a client belongs to.
    - Option A: Subdomain-based (`acme.portal.docteams.com`) — cleaner URLs, but requires wildcard DNS and SSL.
    - Option B: Path-based (`portal.docteams.com/o/acme`) — simpler infrastructure, single deployment.
    - Option C: Derived from magic link — the magic link URL includes the org ID, and after auth the org is stored in the JWT. No org in the URL at all.
    - Recommend Option C for v1: the portal JWT contains `org_id`, and all API calls use it. Portal URLs don't need the org identifier — the client is always scoped to one org. Login page URL includes the org identifier as a query param (from the magic link email).
    - Trade-off: clients can't bookmark the login page for "their" portal without the org param. Mitigated by the magic link flow — they always enter via email, not a bookmarked URL.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- **The portal is the client's window into the firm.** It should feel professional, branded, and trustworthy. No admin UI complexity should leak through — simpler is better.
- **Read-only first, interactive later.** v1 is about visibility (view projects, documents, invoices) with minimal interactivity (comments only). Approval workflows, uploads, and payments come in future phases.
- **Magic links are the only auth.** Clients should never need to remember a password. Request link → click email → you're in. The 1-hour JWT TTL matches the expected session length (check a document, review an invoice, leave a comment, done).
- **Org branding makes each portal feel custom.** A client of "Smith & Associates" should see the firm's logo and colors, not DocTeams branding. The "Powered by DocTeams" footer is small and unobtrusive.
- **Invoice visibility is status-gated.** Clients only see invoices that have been explicitly sent (status SENT or later). Draft invoices are internal. This is a firm-level expectation — you don't show clients work-in-progress billing.
- **Task visibility is minimal.** Clients see task names and statuses. They don't see time estimates, billable flags, or internal descriptions. The portal provides just enough transparency without revealing internal operations.
- **Mobile-friendly, not mobile-first.** The portal should work well on phone browsers (responsive layout, touch-friendly buttons), but the primary use case is desktop/laptop. Industry standard for client portals is responsive web, not native apps.
- **Infrastructure should be boring.** The portal app is another Next.js app with a Dockerfile. Same deployment pattern as the existing frontend. No new infrastructure primitives (no Redis, no WebSockets, no CDN requirements beyond what already exists).
- All backend additions follow the dedicated-schema-per-tenant model (Phase 13). Portal read-model additions follow the existing portal schema patterns from Phase 7.
- Frontend uses Shadcn UI components with the olive design system from Phase 3. Layout is different (no sidebar, simplified header) but the component language is the same.
- Keep the phase achievable in 7-8 epics (~14-16 slices). The backend additions (invoice sync, task sync, branding endpoint) are ~3 slices. The frontend is ~11-13 slices.

Return a single markdown document as your answer, ready to be added as `architecture/phase18-customer-portal-frontend.md` and ADRs.
