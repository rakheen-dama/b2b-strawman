# DocTeams — Acceptance Test Scenarios

Comprehensive acceptance test plan covering both **UI (Playwright browser tests)** and **API (direct HTTP tests)** for the DocTeams multi-tenant B2B SaaS application.

---

## Table of Contents

- [Test Environment & Constraints](#test-environment--constraints)
- [1. Authentication & Session Management](#1-authentication--session-management)
- [2. Organization Onboarding](#2-organization-onboarding)
- [3. Tenant Provisioning (Webhooks)](#3-tenant-provisioning-webhooks)
- [4. Dashboard](#4-dashboard)
- [5. Projects — API](#5-projects--api)
- [6. Projects — UI](#6-projects--ui)
- [7. Documents — API](#7-documents--api)
- [8. Documents — UI](#8-documents--ui)
- [9. Team Management — UI](#9-team-management--ui)
- [10. RBAC Enforcement — API](#10-rbac-enforcement--api)
- [11. RBAC Enforcement — UI](#11-rbac-enforcement--ui)
- [12. Multi-Tenancy & Tenant Isolation](#12-multi-tenancy--tenant-isolation)
- [13. Webhook Security & Idempotency](#13-webhook-security--idempotency)
- [14. Internal Provisioning API Security](#14-internal-provisioning-api-security)
- [15. Navigation & Routing](#15-navigation--routing)
- [16. Error Handling & Edge Cases](#16-error-handling--edge-cases)
- [17. Responsive Layout](#17-responsive-layout)

---

## Test Environment & Constraints

### Prerequisites
- Frontend running on `http://localhost:3000`
- Backend running on `http://localhost:8080`
- PostgreSQL (Docker Compose or Testcontainers)
- LocalStack S3 on port `4566` with `docteams-dev` bucket
- Clerk development instance configured

### Authentication Strategy for Tests

Clerk uses **Cloudflare Turnstile CAPTCHA** on sign-up/sign-in pages, which headless browsers cannot complete. Tests must use one of these strategies:

| Strategy | Use Case |
|----------|----------|
| **Clerk Testing Tokens** | Playwright UI tests — bypass CAPTCHA via Clerk's `__clerk_testing_token` mechanism |
| **Pre-seeded Clerk users** | Create test users via Clerk Backend API in `globalSetup`, authenticate via Clerk Frontend API, store `storageState` |
| **Direct JWT minting** | API-only tests — call backend directly with a valid JWT (obtained from Clerk's Backend API or a test token endpoint) |
| **Saved auth state** | Reuse Playwright `storageState` from a single authenticated session across multiple test files |

### Test User Personas

| Persona | Clerk Role | Purpose |
|---------|------------|---------|
| `owner@test.docteams.io` | `org:owner` | Full control — delete projects, manage org |
| `admin@test.docteams.io` | `org:admin` | Create/edit projects, invite members |
| `member@test.docteams.io` | `org:member` | Read-only projects, upload/view documents |
| `outsider@test.docteams.io` | No org membership | Cross-tenant isolation testing |

---

## 1. Authentication & Session Management

### UI Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| AUTH-UI-01 | Unauthenticated user visits protected page | Navigate to `/org/test-org/dashboard` without session | Redirected to `/sign-in` |
| AUTH-UI-02 | Sign-in page renders | Navigate to `/sign-in` | Clerk sign-in component visible with email/password fields |
| AUTH-UI-03 | Sign-up page renders | Navigate to `/sign-up` | Clerk sign-up component visible |
| AUTH-UI-04 | Authenticated user accesses app | Sign in with test user, navigate to `/dashboard` | Dashboard renders without redirect to sign-in |
| AUTH-UI-05 | Sign out | Click user button → sign out | Redirected to `/` (landing page), session cleared |
| AUTH-UI-06 | Session expiry | Wait for token expiry or invalidate session | Next protected navigation redirects to sign-in |
| AUTH-UI-07 | Landing page accessible without auth | Navigate to `/` | Landing page renders, no redirect |

### API Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| AUTH-API-01 | Request without token | `GET /api/projects` with no `Authorization` header | `401 Unauthorized` |
| AUTH-API-02 | Request with invalid JWT | `GET /api/projects` with malformed JWT | `401 Unauthorized` |
| AUTH-API-03 | Request with expired JWT | `GET /api/projects` with expired token | `401 Unauthorized` |
| AUTH-API-04 | Request with valid JWT | `GET /api/projects` with valid Clerk JWT | `200 OK` with project list |
| AUTH-API-05 | JWT without org claim | `GET /api/projects` with JWT missing `org_id` | `403 Forbidden` (no tenant context) |

---

## 2. Organization Onboarding

### UI Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| ONBOARD-UI-01 | New user with no orgs sees create-org | Sign in as user with no organizations, navigate to `/dashboard` | Redirected to `/create-org` |
| ONBOARD-UI-02 | Create organization form renders | Navigate to `/create-org` | Clerk `<CreateOrganization />` component visible |
| ONBOARD-UI-03 | Create organization success | Fill org name, submit creation form | Org created, redirected to `/org/<slug>/dashboard` |
| ONBOARD-UI-04 | User with existing org goes to dashboard | Sign in as user with active org, navigate to `/dashboard` | Redirected to `/org/<slug>/dashboard` (active org) |
| ONBOARD-UI-05 | Org switcher is visible | Navigate to any org-scoped page | `<OrganizationSwitcher />` present in header |
| ONBOARD-UI-06 | Switch organization | Use org switcher to select a different org | URL updates to new org slug, dashboard reloads with new org context |

---

## 3. Tenant Provisioning (Webhooks)

### API Tests

These tests simulate the Clerk → Next.js → Spring Boot provisioning pipeline by calling the internal provisioning endpoint directly.

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROV-API-01 | Provision new tenant | `POST /internal/orgs/provision` with valid `{ clerkOrgId, orgName }` and `X-API-KEY` | `201 Created` with `{ schemaName, message, status: "COMPLETED" }` |
| PROV-API-02 | Idempotent re-provision | Call `POST /internal/orgs/provision` twice with same `clerkOrgId` | First: `201`, Second: `409 Conflict` |
| PROV-API-03 | Schema actually created | After provisioning, query `information_schema.schemata` | Tenant schema `tenant_<hash>` exists |
| PROV-API-04 | Tenant tables exist | After provisioning, query `information_schema.tables` for tenant schema | `projects` and `documents` tables exist |
| PROV-API-05 | Org-schema mapping persisted | After provisioning, query `public.org_schema_mapping` | Row exists mapping `clerkOrgId` → `tenant_<hash>` |
| PROV-API-06 | Organization record created | After provisioning, query `public.organizations` | Row exists with `provisioning_status = 'COMPLETED'` |

### Webhook Integration Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROV-WH-01 | Valid `organization.created` webhook | `POST /api/webhooks/clerk` with valid Svix signature and `organization.created` payload | `200 OK`, tenant provisioned in backend |
| PROV-WH-02 | Invalid webhook signature | `POST /api/webhooks/clerk` with tampered signature headers | `400 Bad Request`, no provisioning triggered |
| PROV-WH-03 | Duplicate webhook delivery | Send same `organization.created` event with same `svix-id` twice | Both return `200`, provisioning only happens once (idempotent) |
| PROV-WH-04 | `organization.updated` webhook | `POST /api/webhooks/clerk` with `organization.updated` payload | `200 OK`, org metadata updated |
| PROV-WH-05 | `organization.deleted` webhook | `POST /api/webhooks/clerk` with `organization.deleted` payload | `200 OK` (no-op for MVP, no errors) |
| PROV-WH-06 | Membership event webhooks | Send `organizationMembership.created/updated/deleted` | `200 OK` (no-op stubs, no errors) |

---

## 4. Dashboard

### UI Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DASH-UI-01 | Dashboard renders for authenticated user | Sign in, navigate to `/org/<slug>/dashboard` | Page title, project count, recent projects section visible |
| DASH-UI-02 | Project count displays correctly | Create N projects, navigate to dashboard | "N projects" label matches actual count |
| DASH-UI-03 | Recent projects shows up to 5 | Create 7 projects, navigate to dashboard | Only 5 most recent projects displayed |
| DASH-UI-04 | Empty state — no projects | Use org with zero projects | Empty state message with CTA to create first project |
| DASH-UI-05 | Quick action links work | Click "View all projects" or "Create project" quick action | Navigates to `/org/<slug>/projects` or opens create dialog |
| DASH-UI-06 | Dashboard shows correct org context | Switch orgs, check dashboard | Project count and recent projects reflect the active org only |

---

## 5. Projects — API

### CRUD Operations

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-API-01 | List projects (empty) | `GET /api/projects` for new tenant | `200 OK`, empty array `[]` |
| PROJ-API-02 | Create project | `POST /api/projects` with `{ name: "Acme Docs", description: "..." }` | `201 Created` with `ProjectResponse` including UUID, timestamps |
| PROJ-API-03 | Get project by ID | `GET /api/projects/{id}` with valid UUID | `200 OK` with matching project data |
| PROJ-API-04 | Get non-existent project | `GET /api/projects/{random-uuid}` | `404 Not Found` with RFC 9457 ProblemDetail body |
| PROJ-API-05 | Update project | `PUT /api/projects/{id}` with updated `{ name, description }` | `200 OK` with updated fields, `updatedAt` changed |
| PROJ-API-06 | Update non-existent project | `PUT /api/projects/{random-uuid}` | `404 Not Found` |
| PROJ-API-07 | Delete project | `DELETE /api/projects/{id}` | `204 No Content` |
| PROJ-API-08 | Delete non-existent project | `DELETE /api/projects/{random-uuid}` | `404 Not Found` |
| PROJ-API-09 | List projects after CRUD | Create 3, delete 1, list | `200 OK` with 2 projects |

### Validation

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-API-10 | Create project — missing name | `POST /api/projects` with `{ name: "" }` | `400 Bad Request` |
| PROJ-API-11 | Create project — name too long | `POST /api/projects` with name > 255 chars | `400 Bad Request` |
| PROJ-API-12 | Create project — description too long | `POST /api/projects` with description > 2000 chars | `400 Bad Request` |
| PROJ-API-13 | Create project — name only (no description) | `POST /api/projects` with `{ name: "No Desc" }` | `201 Created`, description is null/empty |
| PROJ-API-14 | Update project — blank name | `PUT /api/projects/{id}` with `{ name: "" }` | `400 Bad Request` |

---

## 6. Projects — UI

### List View

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-UI-01 | Empty project list | Navigate to `/org/<slug>/projects` with no projects | Empty state illustration, "Create your first project" CTA |
| PROJ-UI-02 | Project cards render | Create 3 projects, navigate to projects page | 3 project cards visible with name, description, date |
| PROJ-UI-03 | Responsive grid layout | Resize viewport from desktop → tablet → mobile | Grid adjusts: 3 cols → 2 cols → 1 col |

### Create Project

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-UI-04 | Open create dialog | Click "New Project" button | Dialog opens with name and description fields |
| PROJ-UI-05 | Create project — valid input | Fill name + description, click create | Dialog closes, new project card appears in list, success toast |
| PROJ-UI-06 | Create project — empty name validation | Leave name blank, submit | Inline validation error, form not submitted |
| PROJ-UI-07 | Create project — cancel | Open dialog, fill fields, click cancel | Dialog closes, no project created |

### Project Detail

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-UI-08 | Navigate to project detail | Click a project card | Navigated to `/org/<slug>/projects/<id>`, project name/description/date shown |
| PROJ-UI-09 | Documents panel visible | Open project detail | Documents section visible (empty or with docs) |

### Edit Project

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-UI-10 | Open edit dialog | On project detail, click "Edit" button (admin+) | Dialog opens pre-populated with current name/description |
| PROJ-UI-11 | Edit project — save changes | Modify name, click save | Dialog closes, page reflects updated name |
| PROJ-UI-12 | Edit project — cancel | Modify fields, click cancel | No changes persisted |

### Delete Project

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| PROJ-UI-13 | Open delete confirmation | On project detail, click "Delete" button (owner only) | AlertDialog with destructive confirmation prompt |
| PROJ-UI-14 | Confirm delete | Click "Delete" in confirmation dialog | Redirected to projects list, project removed |
| PROJ-UI-15 | Cancel delete | Click "Cancel" in confirmation dialog | Dialog closes, project still exists |

---

## 7. Documents — API

### Upload Flow (3-Phase: Init → S3 PUT → Confirm)

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-API-01 | Initiate upload | `POST /api/projects/{id}/documents/upload-init` with `{ fileName, contentType, size }` | `201 Created` with `{ documentId, presignedUrl, expiresInSeconds }` |
| DOC-API-02 | Upload to presigned URL | `PUT` file bytes to `presignedUrl` from DOC-API-01 | `200 OK` from S3/LocalStack |
| DOC-API-03 | Confirm upload | `POST /api/documents/{documentId}/confirm` | `200 OK` with `DocumentResponse`, `status: "UPLOADED"` |
| DOC-API-04 | Full upload lifecycle | Init → PUT → Confirm → List documents | Document appears in list with status `UPLOADED` |
| DOC-API-05 | Init for non-existent project | `POST /api/projects/{bad-uuid}/documents/upload-init` | `404 Not Found` |
| DOC-API-06 | Confirm non-existent document | `POST /api/documents/{bad-uuid}/confirm` | `404 Not Found` |
| DOC-API-07 | Confirm already-confirmed document | Confirm same document ID twice | Idempotent `200` or `400` (implementation-dependent) |

### Download Flow

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-API-08 | Get download URL for UPLOADED doc | `GET /api/documents/{id}/presign-download` | `200 OK` with `{ presignedUrl, expiresInSeconds }` |
| DOC-API-09 | Download via presigned URL | `GET` the `presignedUrl` from DOC-API-08 | File content returned from S3 |
| DOC-API-10 | Download URL for PENDING doc | `GET /api/documents/{id}/presign-download` for PENDING document | `400 Bad Request` (not yet uploaded) |
| DOC-API-11 | Download non-existent document | `GET /api/documents/{bad-uuid}/presign-download` | `404 Not Found` |

### List Documents

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-API-12 | List documents for project | `GET /api/projects/{id}/documents` | `200 OK` with document array |
| DOC-API-13 | List documents — empty | `GET /api/projects/{id}/documents` for project with no docs | `200 OK`, empty array `[]` |
| DOC-API-14 | List documents — non-existent project | `GET /api/projects/{bad-uuid}/documents` | `404 Not Found` |

### Validation

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-API-15 | Init upload — missing fileName | Omit `fileName` in request body | `400 Bad Request` |
| DOC-API-16 | Init upload — missing contentType | Omit `contentType` in request body | `400 Bad Request` |
| DOC-API-17 | Init upload — zero or negative size | Send `{ size: 0 }` or `{ size: -1 }` | `400 Bad Request` |
| DOC-API-18 | Init upload — fileName too long | `fileName` > 500 chars | `400 Bad Request` |

---

## 8. Documents — UI

### Upload

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-UI-01 | Upload zone visible | Open project detail page | Drag-and-drop upload zone visible with "click to browse" option |
| DOC-UI-02 | Upload file via click | Click upload zone, select a PDF file | Upload progress bar appears, transitions: initiating → uploading → confirming → complete |
| DOC-UI-03 | Upload file via drag-and-drop | Drag a file onto the upload zone | Same progress flow as DOC-UI-02 |
| DOC-UI-04 | Upload progress indicator | Upload a large-ish file (1MB+) | Progress percentage updates in real time |
| DOC-UI-05 | Upload completes successfully | Complete a file upload | Success state shown, document appears in documents table |
| DOC-UI-06 | Multiple concurrent uploads | Drag multiple files at once | Each file shows independent progress, all complete |
| DOC-UI-07 | Reject invalid file type | Attempt to upload a disallowed file type (e.g., `.exe`) | Validation error shown, upload not initiated |
| DOC-UI-08 | Reject oversized file | Attempt to upload a file > 100MB | Validation error shown before upload starts |

### Document List

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-UI-09 | Documents table renders | Upload 3 files, view project detail | Table with 3 rows showing fileName, type icon, size (human-readable), status badge, date |
| DOC-UI-10 | File type icons | Upload PDF, DOCX, PNG files | Each row shows correct file-type icon |
| DOC-UI-11 | Status badges | View documents in PENDING and UPLOADED states | Appropriate badge styling per status |
| DOC-UI-12 | File size formatting | Upload files of varying sizes | Sizes displayed as "1.2 KB", "3.5 MB" etc. |

### Download

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| DOC-UI-13 | Download button for UPLOADED doc | View document table with UPLOADED document | Download button visible and enabled |
| DOC-UI-14 | Click download | Click download button on UPLOADED document | Loading spinner appears, browser initiates file download |
| DOC-UI-15 | Download button hidden for PENDING doc | View document table with PENDING document | Download button absent or disabled |
| DOC-UI-16 | Download error handling | Simulate download failure (e.g., expired URL) | Error message shown, auto-clears after timeout |

---

## 9. Team Management — UI

### Member List

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| TEAM-UI-01 | Team page renders | Navigate to `/org/<slug>/team` | Member list table visible with at least the current user |
| TEAM-UI-02 | Members display with roles | View team page with owner, admin, member | Each row shows name/email and role badge (Owner/Admin/Member) |
| TEAM-UI-03 | Pagination / load more | Org with many members | "Load more" button or pagination works correctly |

### Invitations (Admin+ Only)

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| TEAM-UI-04 | Invite form visible for admin | Sign in as admin, navigate to team page | Email input + role dropdown + "Invite" button visible |
| TEAM-UI-05 | Invite form hidden for member | Sign in as member, navigate to team page | Invite form not rendered |
| TEAM-UI-06 | Send invitation | Enter valid email, select role "Member", click Invite | Success feedback, invitation appears in pending list |
| TEAM-UI-07 | Invite with role selection | Invite as "Admin" vs "Member" | Correct role assigned to invitation |
| TEAM-UI-08 | Invalid email validation | Enter malformed email, submit | Validation error, invitation not sent |

### Pending Invitations

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| TEAM-UI-09 | Pending invitations list | Send an invitation, check team page | Pending invitations section shows the invitation |
| TEAM-UI-10 | Revoke invitation (admin+) | Click revoke button on a pending invitation | Invitation removed from list |
| TEAM-UI-11 | Revoke button hidden for member | Sign in as member | Revoke buttons not visible on pending invitations |

---

## 10. RBAC Enforcement — API

### Project Operations by Role

| ID | Scenario | Role | Action | Expected |
|----|----------|------|--------|----------|
| RBAC-API-01 | Member lists projects | `org:member` | `GET /api/projects` | `200 OK` |
| RBAC-API-02 | Member reads a project | `org:member` | `GET /api/projects/{id}` | `200 OK` |
| RBAC-API-03 | Member creates project — denied | `org:member` | `POST /api/projects` | `403 Forbidden` |
| RBAC-API-04 | Member updates project — denied | `org:member` | `PUT /api/projects/{id}` | `403 Forbidden` |
| RBAC-API-05 | Member deletes project — denied | `org:member` | `DELETE /api/projects/{id}` | `403 Forbidden` |
| RBAC-API-06 | Admin creates project | `org:admin` | `POST /api/projects` | `201 Created` |
| RBAC-API-07 | Admin updates project | `org:admin` | `PUT /api/projects/{id}` | `200 OK` |
| RBAC-API-08 | Admin deletes project — denied | `org:admin` | `DELETE /api/projects/{id}` | `403 Forbidden` |
| RBAC-API-09 | Owner creates project | `org:owner` | `POST /api/projects` | `201 Created` |
| RBAC-API-10 | Owner updates project | `org:owner` | `PUT /api/projects/{id}` | `200 OK` |
| RBAC-API-11 | Owner deletes project | `org:owner` | `DELETE /api/projects/{id}` | `204 No Content` |

### Document Operations by Role

| ID | Scenario | Role | Action | Expected |
|----|----------|------|--------|----------|
| RBAC-API-12 | Member initiates upload | `org:member` | `POST .../upload-init` | `201 Created` |
| RBAC-API-13 | Member confirms upload | `org:member` | `POST .../confirm` | `200 OK` |
| RBAC-API-14 | Member lists documents | `org:member` | `GET .../documents` | `200 OK` |
| RBAC-API-15 | Member downloads document | `org:member` | `GET .../presign-download` | `200 OK` |

---

## 11. RBAC Enforcement — UI

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| RBAC-UI-01 | Edit button visibility — member | Sign in as member, open project detail | "Edit" button not visible |
| RBAC-UI-02 | Edit button visibility — admin | Sign in as admin, open project detail | "Edit" button visible |
| RBAC-UI-03 | Delete button visibility — admin | Sign in as admin, open project detail | "Delete" button not visible |
| RBAC-UI-04 | Delete button visibility — owner | Sign in as owner, open project detail | "Delete" button visible |
| RBAC-UI-05 | New Project button — member | Sign in as member, go to projects list | "New Project" button not visible or disabled |
| RBAC-UI-06 | New Project button — admin | Sign in as admin, go to projects list | "New Project" button visible |
| RBAC-UI-07 | Invite section — member | Sign in as member, go to team page | Invite form not rendered |
| RBAC-UI-08 | Invite section — admin | Sign in as admin, go to team page | Invite form visible |

---

## 12. Multi-Tenancy & Tenant Isolation

These are critical security tests ensuring one organization's data is never visible to another.

### API Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| ISO-API-01 | Org A cannot see Org B's projects | Create project in Org A, list projects with Org B JWT | Org B's list does not include Org A's project |
| ISO-API-02 | Org A cannot read Org B's project by ID | Create project in Org A, `GET /api/projects/{orgA-project-id}` with Org B JWT | `404 Not Found` (not 403 — no information leakage) |
| ISO-API-03 | Org A cannot update Org B's project | `PUT /api/projects/{orgA-project-id}` with Org B admin JWT | `404 Not Found` |
| ISO-API-04 | Org A cannot delete Org B's project | `DELETE /api/projects/{orgA-project-id}` with Org B owner JWT | `404 Not Found` |
| ISO-API-05 | Org A cannot list Org B's documents | `GET /api/projects/{orgA-project-id}/documents` with Org B JWT | `404 Not Found` |
| ISO-API-06 | Org A cannot download Org B's document | `GET /api/documents/{orgA-doc-id}/presign-download` with Org B JWT | `404 Not Found` |
| ISO-API-07 | Org A cannot initiate upload in Org B's project | `POST /api/projects/{orgA-project-id}/documents/upload-init` with Org B JWT | `404 Not Found` |
| ISO-API-08 | S3 key prefix isolation | Upload doc in Org A, verify S3 key | Key starts with `org/{orgA-clerkId}/project/...` — different from Org B's prefix |

### Data Isolation Verification

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| ISO-DB-01 | Separate schemas per tenant | Provision 2 orgs, inspect PostgreSQL schemas | Two distinct `tenant_<hash>` schemas exist |
| ISO-DB-02 | Project data in correct schema | Create project in Org A, query Org A's tenant schema directly | Row exists in `tenant_<orgA_hash>.projects` |
| ISO-DB-03 | No cross-schema data leakage | Create project in Org A, query Org B's tenant schema directly | No row in `tenant_<orgB_hash>.projects` |

---

## 13. Webhook Security & Idempotency

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| WH-SEC-01 | Missing Svix headers | `POST /api/webhooks/clerk` without `svix-id`, `svix-timestamp`, `svix-signature` headers | `400 Bad Request` |
| WH-SEC-02 | Tampered body | Send valid headers but altered request body | `400 Bad Request` (signature mismatch) |
| WH-SEC-03 | Replayed webhook (stale timestamp) | Send webhook with `svix-timestamp` far in the past | `400 Bad Request` |
| WH-SEC-04 | Duplicate `svix-id` | Send two different events with the same `svix-id` | Second event is a no-op (deduplicated via `processed_webhooks`) |
| WH-SEC-05 | Unknown event type | Send webhook with `type: "user.created"` (not handled) | `200 OK` (graceful ignore, no error) |

---

## 14. Internal Provisioning API Security

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| INT-SEC-01 | No API key | `POST /internal/orgs/provision` without `X-API-KEY` | `401 Unauthorized` |
| INT-SEC-02 | Invalid API key | `POST /internal/orgs/provision` with wrong `X-API-KEY` | `401 Unauthorized` |
| INT-SEC-03 | Valid API key | `POST /internal/orgs/provision` with correct `X-API-KEY` | `201 Created` (provisioning succeeds) |
| INT-SEC-04 | JWT bearer does not grant access | `POST /internal/orgs/provision` with valid Clerk JWT (no API key) | `401 Unauthorized` |
| INT-SEC-05 | Internal endpoint not reachable via public paths | Attempt to access `/internal/orgs/provision` without API key from browser | `401` — endpoint secured regardless of origin |

---

## 15. Navigation & Routing

### UI Tests

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| NAV-UI-01 | Org slug validation — mismatch | Navigate to `/org/wrong-slug/dashboard` when active org has different slug | Redirected to correct org slug URL |
| NAV-UI-02 | Deep link to project detail | Directly navigate to `/org/<slug>/projects/<id>` | Project detail renders correctly |
| NAV-UI-03 | Deep link to team page | Directly navigate to `/org/<slug>/team` | Team page renders correctly |
| NAV-UI-04 | Back navigation from project detail | Click browser back from project detail | Returns to project list |
| NAV-UI-05 | Sidebar/header navigation | Click "Projects" in navigation | Navigates to `/org/<slug>/projects` |
| NAV-UI-06 | Sidebar/header navigation — Team | Click "Team" in navigation | Navigates to `/org/<slug>/team` |
| NAV-UI-07 | Sidebar/header navigation — Dashboard | Click "Dashboard" in navigation | Navigates to `/org/<slug>/dashboard` |
| NAV-UI-08 | 404 — non-existent org route | Navigate to `/org/<slug>/nonexistent` | 404 page or redirect |

---

## 16. Error Handling & Edge Cases

### API Error Responses

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| ERR-API-01 | RFC 9457 ProblemDetail format | `GET /api/projects/{bad-uuid}` | Response body has `type`, `title`, `status`, `detail` fields |
| ERR-API-02 | Malformed UUID path param | `GET /api/projects/not-a-uuid` | `400 Bad Request` |
| ERR-API-03 | Malformed JSON request body | `POST /api/projects` with invalid JSON | `400 Bad Request` |
| ERR-API-04 | Wrong HTTP method | `PATCH /api/projects/{id}` (not supported) | `405 Method Not Allowed` |
| ERR-API-05 | Missing Content-Type header | `POST /api/projects` without `Content-Type: application/json` | `415 Unsupported Media Type` |

### UI Error Handling

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| ERR-UI-01 | Network error during project creation | Simulate backend down, attempt create project | Error toast/message, dialog remains open |
| ERR-UI-02 | Upload failure — S3 unreachable | Simulate S3 down, attempt file upload | Upload error state shown, user can retry |
| ERR-UI-03 | Stale project — already deleted | Delete project in another tab, try to edit in current tab | Error message indicating project not found |

### Edge Cases

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| EDGE-01 | Project name with special characters | Create project named `Test <script>alert('xss')</script>` | Name stored and displayed safely (no XSS), HTML escaped |
| EDGE-02 | Project description with unicode | Create project with emoji/CJK characters in description | Stored and displayed correctly |
| EDGE-03 | Upload file with special characters in name | Upload `report (final) - copy [2].pdf` | File name preserved, no encoding issues |
| EDGE-04 | Concurrent project creation | Rapidly create multiple projects | All created successfully, no duplicates |
| EDGE-05 | Delete project with documents | Delete a project that has uploaded documents | Project and all associated documents deleted (CASCADE) |

---

## 17. Responsive Layout

### UI Tests

| ID | Scenario | Viewport | Expected Result |
|----|----------|----------|-----------------|
| RESP-01 | Desktop layout | 1280x720 | Full sidebar, 3-column project grid |
| RESP-02 | Tablet layout | 768x1024 | Collapsible sidebar, 2-column project grid |
| RESP-03 | Mobile layout | 375x812 | Hamburger menu, single-column project grid |
| RESP-04 | Dashboard responsive | 375x812 | Dashboard stats and recent projects stack vertically |
| RESP-05 | Team page responsive | 375x812 | Member table scrollable or cards layout |
| RESP-06 | Document upload zone responsive | 375x812 | Upload zone fits mobile width, still functional |

---

## Summary

| Category | UI Scenarios | API Scenarios | Total |
|----------|-------------|---------------|-------|
| Authentication | 7 | 5 | 12 |
| Onboarding | 6 | — | 6 |
| Provisioning | — | 6 + 6 | 12 |
| Dashboard | 6 | — | 6 |
| Projects | 15 | 14 | 29 |
| Documents | 16 | 18 | 34 |
| Team Management | 11 | — | 11 |
| RBAC — API | — | 15 | 15 |
| RBAC — UI | 8 | — | 8 |
| Tenant Isolation | — | 8 + 3 | 11 |
| Webhook Security | — | 5 | 5 |
| Internal API Security | — | 5 | 5 |
| Navigation | 8 | — | 8 |
| Error Handling | 3 | 5 | 8 |
| Edge Cases | — | — | 5 |
| Responsive | 6 | — | 6 |
| **Total** | **86** | **90** | **181** |
