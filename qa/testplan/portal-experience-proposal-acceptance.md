# Test Plan: Portal Experience & Proposal Acceptance
## DocTeams Platform — Client-Side Verification

**Version**: 1.0
**Date**: 2026-03-17
**Author**: Product + QA
**Vertical**: accounting-za (Thornton & Associates)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup.
**Depends on**: Phase 49 test plan T0 (seed data) — run that first or share the same seeded environment

---

## 1. Purpose

The Phase 49 test plan verifies document content from the **firm's perspective**. This plan
verifies the platform from the **client's perspective** — what a portal user (Kgosi's Thabo,
Vukani's Sipho) actually sees and can do.

The portal is the only client-facing surface. If a firm sends a proposal and the client can't
view it, accept it, or see their project status, the product doesn't work. This plan also
covers the most important sales conversion loop: proposal → acceptance → auto-created project.

**Core question**: Can Thabo Kgosi receive a magic link, log into the portal, view his projects
and documents, accept an engagement letter, and see the resulting project — all with correct
branding and complete data isolation from other clients?

## 2. Scope

### In Scope

| Track | Description | Method |
|-------|-------------|--------|
| T1 | Magic link authentication (request → email → exchange → portal session) | Automated (Playwright + Mailpit) |
| T2 | Portal home experience (what the client sees on first login) | Automated |
| T3 | Portal project & document viewing | Automated |
| T4 | Proposal lifecycle: create → send → portal view → accept → project conversion | Automated |
| T5 | Portal document download & preview | Automated |
| T6 | Org branding in portal (logo, colours, firm name) | Automated |
| T7 | Cross-customer data isolation (Client A cannot see Client B's data) | Automated |

### Out of Scope

- Firm-side CRUD (tested in Phase 47-48)
- Document content verification (tested in Phase 49)
- Information request portal flow (tested in Phase 49 T4)
- Document acceptance e-signing flow (tested in Phase 49 T6)
- Clerk auth (this tests Keycloak dev stack)

## 3. Prerequisites

### 3.1 Shared Seed Data

This plan assumes Phase 49 T0 has been executed — the Thornton & Associates 90-day lifecycle
is seeded with 4 customers, projects, invoices, time entries, custom fields, etc.

**Additional prerequisites for this plan:**

| Requirement | How to verify |
|-------------|---------------|
| Portal contacts exist for Kgosi (Thabo) and Vukani (Sipho) | Phase 49 T0.8 |
| At least 1 generated document exists for Kgosi | Phase 49 T1.1 (engagement letter) |
| At least 1 proposal exists (DRAFT or SENT) | Phase 49 T3.1 |
| Mailpit is accessible at localhost:8025 | `curl http://localhost:8025/api/v1/messages` |

### 3.2 Notation

- [ ] **PASS** — behaviour is correct
- [ ] **FAIL** — behaviour is wrong or broken
- [ ] **PARTIAL** — works but with friction or missing polish
- [ ] **BLOCKED** — cannot proceed (dependency missing)

---

## 4. Test Tracks

---

### Track 1 — Magic Link Authentication

**Goal**: Verify the complete portal authentication flow — from requesting a magic link
to having a valid portal session. This is the front door for every client interaction.

#### T1.1 — Request Magic Link

**Actor**: System (triggered by firm action or client-initiated)

- [ ] **T1.1.1** Identify the portal auth endpoint: `POST /portal/auth/request-link`
- [ ] **T1.1.2** Request a magic link for Kgosi's portal contact (thabo@kgosiconstruction.co.za)
- [ ] **T1.1.3** Check Mailpit → magic link email received
- [ ] **T1.1.4** Email contains:
  - Recipient: thabo@kgosiconstruction.co.za
  - Subject: references the firm name or "portal access"
  - Body: contains a clickable link with a token parameter
- [ ] **T1.1.5** Extract the magic link URL from the email body
- [ ] **T1.1.6** Link URL points to the portal (not the firm app)

#### T1.2 — Exchange Token for Session

- [ ] **T1.2.1** Navigate to the magic link URL in the browser
- [ ] **T1.2.2** Verify the token is exchanged for a portal JWT (check cookies or local storage)
- [ ] **T1.2.3** Verify redirect to portal home/dashboard (not an error page)
- [ ] **T1.2.4** Verify the portal header shows the customer name ("Kgosi Construction (Pty) Ltd" or "Thabo Kgosi")
- [ ] **T1.2.5** Verify no "unauthorized" or "invalid token" errors in console

#### T1.3 — Token Expiry and Reuse

- [ ] **T1.3.1** Use the same magic link a second time → should fail or redirect to "link expired" page
- [ ] **T1.3.2** Verify the error message is user-friendly (not a raw 401 or stack trace)
- [ ] **T1.3.3** Note: testing actual time-based expiry may not be feasible in E2E — log as observation

#### T1.4 — Invalid Token

- [ ] **T1.4.1** Navigate to magic link URL with a tampered/random token
- [ ] **T1.4.2** Verify the system rejects it gracefully (error page, not 500)
- [ ] **T1.4.3** Verify no portal content is accessible without valid auth

---

### Track 2 — Portal Home Experience

**Goal**: Verify what the client sees on first login — is there a dashboard, a welcome
message, or a direct navigation to their content? First impressions matter.

**Actor**: Portal user (Thabo Kgosi, authenticated via T1)

#### T2.1 — Landing Page After Login

- [ ] **T2.1.1** After magic link auth, what page does the client land on?
  - Dashboard with summary? → note what's shown
  - Project list? → note
  - Empty state with guidance? → note
- [ ] **T2.1.2** Verify the page loads without errors (no React crashes, no 500s)
- [ ] **T2.1.3** Verify navigation is available (sidebar, header links, or tabs)
- [ ] **T2.1.4** Note available nav items — what sections can the client access?

#### T2.2 — Portal Navigation Structure

- [ ] **T2.2.1** List all navigation links visible to the portal user:
  - Projects? Documents? Requests? Proposals? Invoices?
- [ ] **T2.2.2** Click each nav item → verify page loads without error
- [ ] **T2.2.3** Verify no firm-side navigation leaks through (no "Settings", "Team", "Reports", "Profitability")
- [ ] **T2.2.4** Verify logout/sign-out option exists and works

---

### Track 3 — Portal Project & Document Viewing

**Goal**: Verify the client can see their projects and associated documents — with correct
data, not another client's data.

#### T3.1 — Project List

- [ ] **T3.1.1** Navigate to portal projects page
- [ ] **T3.1.2** Verify Kgosi's projects are listed:
  - "Monthly Bookkeeping — Kgosi" (expected)
  - "Annual Tax Return 2026 — Kgosi" (if created in seed)
- [ ] **T3.1.3** Verify NO other customer's projects appear (no Naledi, Vukani, Moroka projects)
- [ ] **T3.1.4** Each project shows: name, status (or progress indicator)

#### T3.2 — Project Detail

- [ ] **T3.2.1** Click into "Monthly Bookkeeping — Kgosi" project
- [ ] **T3.2.2** Verify project name displays correctly
- [ ] **T3.2.3** Verify project has some visible content:
  - Status/progress indicator?
  - Document list?
  - Task summary (read-only)?
  - Time summary (hours logged)?
- [ ] **T3.2.4** Note what information is visible vs. hidden from the client
- [ ] **T3.2.5** Verify the client CANNOT:
  - Edit the project
  - Create tasks
  - Log time
  - Delete anything

#### T3.3 — Document List

- [ ] **T3.3.1** Navigate to portal documents page (or documents section within project)
- [ ] **T3.3.2** Verify at least one document is visible (from Phase 49 T1 generation)
- [ ] **T3.3.3** Each document shows: title/name, date generated, type/category
- [ ] **T3.3.4** Verify no documents from other customers are visible

---

### Track 4 — Proposal Lifecycle (Create → Send → Accept → Project)

**Goal**: Test the most important sales conversion loop end-to-end. A firm creates a
proposal, sends it to the client, the client views and accepts it, and the system
auto-creates a project with the proposal's milestones and team.

#### T4.1 — Create Proposal (Firm Side)

**Actor**: Alice (Owner)

- [ ] **T4.1.1** Navigate to Proposals → New Proposal
- [ ] **T4.1.2** Fill:
  - Title: "Annual Tax Return Services — Kgosi Construction"
  - Customer: Kgosi Construction (Pty) Ltd
  - Fee Model: FIXED_FEE
  - Amount: R12,000
  - Description: "Preparation and submission of annual corporate tax return (ITR14) for the financial year ending 28 February 2026"
  - Expiry: 14 days from today
- [ ] **T4.1.3** Add milestones (if supported):
  - "Data gathering and review" — R3,000
  - "Trial balance preparation" — R4,000
  - "ITR14 submission to SARS" — R5,000
- [ ] **T4.1.4** Add team members (if supported):
  - Alice (Lead)
  - Carol (Support)
- [ ] **T4.1.5** Save as DRAFT → verify in proposal list

#### T4.2 — Review Proposal Detail

- [ ] **T4.2.1** Open proposal detail page
- [ ] **T4.2.2** Verify all entered data displays correctly:
  - Title, customer name, amount (R12,000), fee model, description, expiry
- [ ] **T4.2.3** Verify milestones listed with amounts (if created)
- [ ] **T4.2.4** Verify team members listed (if created)
- [ ] **T4.2.5** Verify proposal number is assigned (e.g., PROP-001)

#### T4.3 — Send Proposal

- [ ] **T4.3.1** Click "Send" on the proposal
- [ ] **T4.3.2** Verify status changes to SENT
- [ ] **T4.3.3** Verify sent date is recorded
- [ ] **T4.3.4** Check Mailpit → email sent to Kgosi portal contact
- [ ] **T4.3.5** Email contains:
  - Firm name in sender or subject
  - Proposal title or reference
  - A link to view the proposal in the portal
- [ ] **T4.3.6** Extract the portal proposal link from the email

#### T4.4 — Portal: Client Views Proposal

**Actor**: Thabo Kgosi (portal user)

- [ ] **T4.4.1** Navigate to the proposal link (from email or portal proposals page)
- [ ] **T4.4.2** Verify proposal displays:
  - Title: "Annual Tax Return Services — Kgosi Construction"
  - Firm name (org name)
  - Amount: R12,000 (in ZAR, not USD)
  - Description of services
  - Expiry date
- [ ] **T4.4.3** Verify milestones visible (if created):
  - 3 milestones with descriptions and amounts
  - Amounts sum to R12,000
- [ ] **T4.4.4** Verify team members visible (if shown to client)
- [ ] **T4.4.5** Verify "Accept" and "Decline" buttons are present
- [ ] **T4.4.6** No unresolved variables or placeholder text in the proposal view

#### T4.5 — Portal: Client Accepts Proposal

- [ ] **T4.5.1** Click "Accept" (or equivalent acceptance action)
- [ ] **T4.5.2** If confirmation dialog appears → confirm acceptance
- [ ] **T4.5.3** Verify confirmation screen shows to the client:
  - "Proposal accepted" or similar
  - Reference to what was accepted
- [ ] **T4.5.4** Verify proposal status updates to ACCEPTED in the portal view

#### T4.6 — Firm: Verify Acceptance + Auto-Created Project

**Actor**: Alice (Owner)

- [ ] **T4.6.1** Navigate to Proposals → open the accepted proposal
- [ ] **T4.6.2** Verify status = ACCEPTED
- [ ] **T4.6.3** Verify acceptance metadata:
  - Accepted by: Thabo Kgosi (or portal contact name)
  - Acceptance date/time
- [ ] **T4.6.4** Check Mailpit → acceptance notification email sent to Alice
- [ ] **T4.6.5** Navigate to Projects → verify a new project was auto-created:
  - Project name references the proposal title or customer
  - Customer = Kgosi Construction (Pty) Ltd
- [ ] **T4.6.6** Open the auto-created project → verify:
  - Customer is linked correctly
  - Milestones converted to tasks (if applicable)
  - Team members assigned (Alice, Carol — if applicable)
- [ ] **T4.6.7** Check audit trail for the proposal acceptance event

#### T4.7 — Proposal Rejection Flow

**Setup**: Create a second proposal for Naledi (or use an existing DRAFT).

**Actor**: Alice sends, then Naledi's portal contact declines.

- [ ] **T4.7.1** Create proposal: "Quarterly VAT Filing — Naledi", Amount: R2,500, Expiry: 7 days
- [ ] **T4.7.2** Send to Naledi portal contact
- [ ] **T4.7.3** Portal: navigate to proposal → click "Decline" (or "Reject")
- [ ] **T4.7.4** If reason field appears → enter: "Fee too high, will reconsider next quarter"
- [ ] **T4.7.5** Verify proposal status = DECLINED (or REJECTED)
- [ ] **T4.7.6** Firm side: verify status updated, no project auto-created
- [ ] **T4.7.7** Check Mailpit → rejection notification email sent to Alice
- [ ] **T4.7.8** Verify the rejection reason is visible to the firm

#### T4.8 — Proposal Expiry (Observation)

- [ ] **T4.8.1** Note: expiry requires time to pass. If the `ExpiryProcessor` runs on a schedule,
  check the cron config to understand when expired proposals are marked.
- [ ] **T4.8.2** If there's a way to manually trigger expiry processing → test it
- [ ] **T4.8.3** Otherwise: note as untestable in E2E, verify via backend integration tests

---

### Track 5 — Portal Document Download & Preview

**Goal**: Verify the client can actually view and download documents shared with them.
A document generated by the firm should be accessible in the portal.

**Precondition**: Phase 49 T1 generated at least one document for Kgosi (engagement letter PDF).

#### T5.1 — Document Visibility in Portal

- [ ] **T5.1.1** Login as Kgosi portal user
- [ ] **T5.1.2** Navigate to portal documents page
- [ ] **T5.1.3** Verify generated documents are listed:
  - Engagement letter (from Phase 49 T1.1)
  - Any other generated documents
- [ ] **T5.1.4** Each document shows: name, type, date, size or page count

#### T5.2 — Document Preview

- [ ] **T5.2.1** Click on the engagement letter document
- [ ] **T5.2.2** Verify in-browser preview loads (PDF viewer or HTML preview)
- [ ] **T5.2.3** Preview contains correct content:
  - Customer name: "Kgosi Construction (Pty) Ltd"
  - Firm name visible
  - Not blank, not error page
- [ ] **T5.2.4** No download-only fallback without preview (clients expect to view before downloading)

#### T5.3 — Document Download

- [ ] **T5.3.1** Find and click the download button/link
- [ ] **T5.3.2** Verify PDF downloads successfully (non-zero file size)
- [ ] **T5.3.3** Read downloaded PDF → verify content matches what was generated
- [ ] **T5.3.4** Filename is descriptive (not "download.pdf" or random UUID)

#### T5.4 — Document Not Shared = Not Visible

- [ ] **T5.4.1** Note: are ALL generated documents automatically shared to the portal,
  or does the firm explicitly share specific documents?
- [ ] **T5.4.2** If explicit sharing: verify unshared documents are NOT visible in the portal
- [ ] **T5.4.3** If automatic sharing: verify only the customer's own documents appear
  (no Naledi or Vukani documents visible to Kgosi)

---

### Track 6 — Org Branding in Portal

**Goal**: Verify the firm's branding (name, colour, logo) carries through to the portal
experience. The portal should feel like the firm's own client-facing tool.

**Precondition**: Phase 49 T0.6 set org branding (brand colour #1B5E20, document footer text).

#### T6.1 — Brand Colour

- [ ] **T6.1.1** Login to portal as Kgosi
- [ ] **T6.1.2** Check if the brand colour (#1B5E20, green) appears anywhere:
  - Header/navbar background or accent?
  - Buttons or links?
  - Page accents?
- [ ] **T6.1.3** If brand colour is visible → **PASS**
- [ ] **T6.1.4** If portal uses default/generic styling with no firm branding → log as GAP

#### T6.2 — Firm Name

- [ ] **T6.2.1** Verify the firm/org name appears in the portal:
  - Header?
  - Welcome message?
  - Footer?
- [ ] **T6.2.2** If the portal shows "DocTeams" or generic branding instead of the firm name → log as GAP

#### T6.3 — Logo

- [ ] **T6.3.1** If a logo was uploaded in org settings → verify it appears in the portal header
- [ ] **T6.3.2** If no logo uploaded → verify a fallback is shown (firm name text, not broken image)
- [ ] **T6.3.3** Note: logo upload may depend on LocalStack/S3 being healthy in E2E

#### T6.4 — Branding API

- [ ] **T6.4.1** Check the portal branding endpoint: `GET /portal/branding`
- [ ] **T6.4.2** Verify response includes: org name, brand colour, logo URL (if uploaded)
- [ ] **T6.4.3** Verify the portal frontend consumes this endpoint to render branding

---

### Track 7 — Cross-Customer Data Isolation

**Goal**: The most critical security test. Client A must NEVER see Client B's data in the
portal. This tests tenant + customer scoping in the portal read model.

#### T7.1 — Kgosi Cannot See Naledi's Data

- [ ] **T7.1.1** Login to portal as Kgosi's portal contact
- [ ] **T7.1.2** Navigate to projects → verify ONLY Kgosi projects are listed
- [ ] **T7.1.3** Navigate to documents → verify ONLY Kgosi documents are listed
- [ ] **T7.1.4** If proposals are visible → verify ONLY Kgosi proposals are listed
- [ ] **T7.1.5** If requests are visible → verify ONLY Kgosi requests are listed
- [ ] **T7.1.6** Search page content for "Naledi" → expect zero matches
- [ ] **T7.1.7** Search page content for "Vukani" → expect zero matches
- [ ] **T7.1.8** Search page content for "Moroka" → expect zero matches

#### T7.2 — Vukani Cannot See Kgosi's Data

- [ ] **T7.2.1** Login to portal as Vukani's portal contact (Sipho)
- [ ] **T7.2.2** Navigate to projects → verify ONLY Vukani projects are listed
- [ ] **T7.2.3** Navigate to documents → verify ONLY Vukani documents are listed
- [ ] **T7.2.4** Search page content for "Kgosi" → expect zero matches
- [ ] **T7.2.5** Search page content for "Naledi" → expect zero matches

#### T7.3 — Direct URL Access Blocked

- [ ] **T7.3.1** While logged in as Kgosi's portal contact, attempt to access a Vukani project
  by manually changing the project ID in the URL
- [ ] **T7.3.2** Verify the system returns 404 or "access denied" — NOT Vukani's data
- [ ] **T7.3.3** Attempt to access a Naledi document by direct URL → verify blocked
- [ ] **T7.3.4** Attempt to access a Moroka proposal by direct URL → verify blocked

#### T7.4 — API-Level Isolation

- [ ] **T7.4.1** Using Kgosi's portal JWT, call `GET /portal/projects` → verify only Kgosi projects returned
- [ ] **T7.4.2** Using Kgosi's portal JWT, call `GET /portal/documents` → verify only Kgosi documents returned
- [ ] **T7.4.3** Using Kgosi's portal JWT, attempt `GET /portal/projects/{vukani-project-id}` → verify 404 or 403
- [ ] **T7.4.4** Note: these API-level checks can be done via Playwright's `page.evaluate(fetch(...))` or direct HTTP calls

---

## 5. Gap Reporting Format

Same format as Phase 49 test plan. Categories specific to this plan:

| Category | Examples |
|----------|---------|
| portal-auth | Magic link failures, token issues, session problems |
| portal-ux | Missing navigation, broken pages, confusing flows |
| data-isolation | Cross-customer data leaks (ALWAYS blocker severity) |
| proposal-lifecycle | Create/send/accept/reject failures, conversion bugs |
| branding | Missing firm identity in portal |
| portal-content | Wrong data shown, missing fields, stale read model |

**Severity override**: Any data isolation failure (T7) is automatically **blocker** severity,
regardless of how minor the leaked data appears.

---

## 6. Success Criteria

| Criterion | Target |
|-----------|--------|
| Magic link auth completes end-to-end | PASS |
| Portal shows only the authenticated customer's data | 100% isolation |
| Proposal acceptance auto-creates project | PASS |
| Proposal rejection records reason and notifies firm | PASS |
| Client can preview and download documents in portal | PASS |
| Org branding visible in portal (name + colour minimum) | PASS |
| Direct URL access to other customers' data is blocked | 100% |
| API-level isolation holds | 100% |
| Zero data isolation failures | 0 |

---

## 7. Execution Notes

### Execution Order

1. **T1 — Magic Link Auth**: Must succeed first — all other tracks depend on portal access.
   If T1 fails, STOP and investigate auth infrastructure.
2. **T2 — Portal Home**: Quick orientation — understand what the client sees.
3. **T7 — Data Isolation**: Run early. If isolation is broken, everything else is moot.
4. **T3 — Projects & Documents**: Core portal content.
5. **T5 — Document Download**: Depends on documents being visible (T3).
6. **T4 — Proposal Lifecycle**: Most complex track, run after portal basics are confirmed.
7. **T6 — Branding**: Lowest priority, run last.

### Portal Auth in E2E

The E2E stack uses a mock IDP. Portal magic links may work differently than production:
- If the mock IDP handles portal tokens → magic link flow should work
- If portal auth is separate from org member auth → verify the portal JWT pipeline
- The existing `lifecycle-portal.spec.ts` may have auth patterns to follow

### Multiple Portal Sessions

Tracks T7.1 and T7.2 require logging into the portal as different customers' contacts.
Between sessions:
- Clear cookies/session before switching to a different portal contact
- Or use separate browser contexts in Playwright
