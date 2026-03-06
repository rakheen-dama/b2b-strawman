# Test Plan 06: Document Generation & Acceptance

## Overview

This plan covers the full document lifecycle: template management, clause management, document generation with PDF rendering, and the e-signing acceptance flow via the customer portal. Tests verify CRUD operations, clone/reset workflows, context snapshots, branding, RBAC, and the complete acceptance request state machine.

## Test Actors

| Actor | Role | Responsibilities |
|-------|------|------------------|
| Priya Sharma | Admin | Template/clause management, document generation |
| James Chen | Admin | Acceptance request management, reminders, revocation |
| Thandi Nkosi | Owner | Branding settings (logo, brand_color, footer) |
| alice.porter@acmecorp.com | Portal Contact | Acceptance flow (view, accept via token) |
| Lerato Dlamini | Member | RBAC boundary testing (can generate, cannot manage templates) |

---

## 1. Template CRUD

### DOC-001: Create a custom template

**Given** Priya Sharma is logged in as Admin
**And** she navigates to the Templates management page
**When** she creates a new template with:
  - Name: "Project Summary Report"
  - Category: "Reports"
  - Primary Entity Type: PROJECT
  - Content: valid Thymeleaf HTML with `${project.name}` and `${project.status}` variables
  - CSS: custom styling
**Then** the template is created with source = ORG_CUSTOM
**And** a slug is auto-generated as "project-summary-report"
**And** the template appears in the template list

### DOC-002: Slug generation handles duplicates

**Given** a template with slug "project-summary-report" already exists
**When** Priya creates another template named "Project Summary Report"
**Then** the new template receives a unique slug (e.g., "project-summary-report-1")
**And** both templates are independently accessible

### DOC-003: Update an existing template

**Given** the template "Project Summary Report" exists
**When** Priya updates the name to "Project Overview Report" and modifies the content
**Then** the template name and content are updated
**And** the slug remains unchanged
**And** previously generated documents are not affected

### DOC-004: Delete a custom template

**Given** the template "Project Overview Report" exists with source ORG_CUSTOM
**And** no generated documents reference this template
**When** Priya deletes the template
**Then** the template is removed from the list
**And** a 404 is returned when accessing the deleted template ID

### DOC-005: Validate primary entity type

**Given** Priya is creating a new template
**When** she submits with primaryEntityType set to an invalid value (e.g., "TASK")
**Then** a 400 validation error is returned
**And** only PROJECT, CUSTOMER, and INVOICE are accepted

### DOC-006: Validate required fields on create

**Given** Priya is creating a new template
**When** she submits without a name or content
**Then** a 400 validation error is returned listing the missing fields

---

## 2. Template Pack & Clone

### DOC-007: View seeded pack templates

**Given** the organization was provisioned with the common template pack
**When** Priya navigates to the Templates page
**Then** pack templates are listed with source = COMMON_PACK
**And** pack templates are marked as read-only (cannot edit content directly)

### DOC-008: Clone a pack template to custom

**Given** a COMMON_PACK template "Standard Invoice" exists
**When** Priya clicks "Clone" on the template
**Then** a new template is created with source = ORG_CUSTOM
**And** the cloned template has the same content and CSS as the original
**And** the cloned template name is "Standard Invoice (Copy)"
**And** the original pack template remains unchanged

### DOC-009: Customize a cloned template

**Given** Priya has cloned "Standard Invoice" to "Standard Invoice (Copy)"
**When** she edits the content to add a custom header and changes the CSS
**Then** the customized template is saved
**And** the original COMMON_PACK template is unaffected

### DOC-010: Reset a customized template to pack default

**Given** Priya has a cloned and customized template originally from "Standard Invoice"
**When** she clicks "Reset to Default"
**Then** the template content and CSS revert to the COMMON_PACK original
**And** the template source remains ORG_CUSTOM
**And** the template name is preserved

### DOC-011: Cannot edit pack templates directly

**Given** a COMMON_PACK template exists
**When** Priya attempts to PUT an update to that template
**Then** a 403 or 400 error is returned indicating pack templates are read-only

---

## 3. Clause Management

### CLAUSE-001: Create a clause

**Given** Priya is on the Clauses management page
**When** she creates a new clause with:
  - Title: "Payment Terms"
  - Category: "Financial"
  - Body: "Payment is due within 30 days of invoice date..."
**Then** the clause is created and listed under the "Financial" category

### CLAUSE-002: Update a clause

**Given** the clause "Payment Terms" exists
**When** Priya updates the body to change "30 days" to "14 days"
**Then** the clause body is updated
**And** previously generated documents retain the old clause snapshot

### CLAUSE-003: Deactivate a clause

**Given** the clause "Payment Terms" is active
**When** Priya deactivates the clause via POST /deactivate
**Then** the clause is marked as inactive
**And** the clause no longer appears in the active clause list
**And** templates currently using this clause retain the association but it is flagged

### CLAUSE-004: Clone a clause

**Given** the clause "Payment Terms" exists
**When** Priya clones it via POST /clone
**Then** a new clause "Payment Terms (Copy)" is created
**And** it has the same body and category as the original

### CLAUSE-005: Attach clauses to a template

**Given** the template "Standard Invoice (Copy)" exists
**And** clauses "Payment Terms" and "Liability Limitation" exist
**When** Priya updates the template's clauses via PUT /api/templates/{id}/clauses
  with both clause IDs
**Then** the template's clause list contains both clauses in order

### CLAUSE-006: Detach a clause from a template

**Given** the template "Standard Invoice (Copy)" has clauses "Payment Terms" and "Liability Limitation"
**When** Priya updates the clause list to only include "Liability Limitation"
**Then** "Payment Terms" is removed from the template's clause association
**And** the clause itself is not deleted

---

## 4. Template Preview

### DOC-012: Preview a template with sample data

**Given** the template "Project Summary Report" exists with entity type PROJECT
**When** Priya requests a preview via POST /preview with a project ID
**Then** an HTML preview is returned with the project's actual data substituted
**And** variables like `${project.name}` are replaced with real values

### DOC-013: List available template variables

**Given** a template with primaryEntityType = INVOICE
**When** Priya requests the template variables endpoint
**Then** the response lists all available variables (e.g., invoice.number, invoice.total, customer.name, lineItems)
**And** required vs. optional variables are distinguished

### DOC-014: Preview with missing optional fields

**Given** a template references `${project.description}` which is optional
**And** the target project has no description set
**When** Priya previews the template
**Then** the preview renders successfully with the description field blank or showing a placeholder

---

## 5. Document Generation

### GEN-001: Generate a document for a project

**Given** the template "Project Summary Report" exists with entity type PROJECT
**And** project "Website Redesign" exists with complete data
**When** Priya generates a document via POST /generate with the project ID
**Then** a GeneratedDocument record is created
**And** a PDF is uploaded to S3
**And** the document is downloadable via GET /{id}/download

### GEN-002: Generate a document for a customer

**Given** a CUSTOMER-type template exists
**And** customer "Acme Corp" exists with complete data
**When** Priya generates a document for the customer
**Then** the context builder assembles customer-specific variables
**And** a PDF is generated and stored in S3

### GEN-003: Generate a document for an invoice

**Given** an INVOICE-type template exists with clause "Payment Terms" attached
**And** invoice INV-2026-0001 exists
**When** Priya generates a document for the invoice
**Then** the PDF includes invoice line items, totals, and the "Payment Terms" clause text
**And** the GeneratedDocument records the template ID and invoice ID

### GEN-004: Download a generated document

**Given** a GeneratedDocument exists for project "Website Redesign"
**When** Priya requests GET /api/generated-documents/{id}/download
**Then** a PDF file is returned with correct Content-Type and Content-Disposition headers

### GEN-005: Delete a generated document

**Given** a GeneratedDocument exists
**When** Priya deletes it via DELETE /api/generated-documents/{id}
**Then** the record is removed from the database
**And** the S3 object is deleted

### GEN-006: List generated documents

**Given** multiple documents have been generated for various entities
**When** Priya requests GET /api/generated-documents
**Then** all generated documents for the organization are listed
**And** each entry includes template name, entity type, entity reference, and creation timestamp

---

## 6. Context Snapshot & Clause Snapshot

### GEN-007: Context snapshot is frozen at generation time

**Given** a document was generated for project "Website Redesign" with status ACTIVE
**When** the project status is later changed to COMPLETED
**And** Priya views the generated document details
**Then** the context snapshot still shows status = ACTIVE
**And** re-downloading the PDF shows the original data

### GEN-008: Clause snapshot is frozen at generation time

**Given** a document was generated with clause "Payment Terms" reading "30 days"
**When** the clause body is updated to "14 days"
**And** Priya views the generated document's clause snapshots
**Then** the snapshot still shows "30 days"
**And** the PDF content reflects the original clause text

### GEN-009: New generation picks up updated data

**Given** the project status was changed to COMPLETED after the first generation
**When** Priya generates a new document for the same project and template
**Then** the new document's context snapshot shows status = COMPLETED
**And** the previous document's snapshot remains unchanged

---

## 7. Branding

### GEN-010: Generated document includes org branding

**Given** Thandi Nkosi (Owner) has configured OrgSettings with:
  - Logo: uploaded image URL
  - Brand color: "#1E40AF"
  - Footer text: "Acme Corp - Confidential"
**When** Priya generates a document using any template
**Then** the PDF includes the org logo in the header area
**And** the brand color is applied to headings/accents
**And** the footer text appears on each page

### GEN-011: Update branding reflects in new generations

**Given** Thandi changes the brand_color to "#DC2626"
**When** Priya generates a new document
**Then** the new PDF uses the updated brand color
**And** previously generated documents retain the old branding

### GEN-012: Branding settings require Owner role

**Given** Priya (Admin) attempts to update OrgSettings branding
**Then** the request is forbidden (403)
**And** only Thandi (Owner) can modify branding settings

---

## 8. Acceptance Request Lifecycle

### ACC-001: Create an acceptance request

**Given** a GeneratedDocument exists for invoice INV-2026-0001
**When** James creates an acceptance request via POST /api/acceptance-requests with:
  - documentId: the generated document ID
  - recipientEmail: alice.porter@acmecorp.com
  - expiresAt: 7 days from now
**Then** the acceptance request is created with status = PENDING
**And** a unique requestToken is generated

### ACC-002: Send an acceptance request

**Given** an acceptance request exists with status PENDING
**When** James triggers sending (status transitions to SENT)
**Then** the status changes to SENT
**And** sentAt timestamp is recorded
**And** a notification/email stub is triggered for the portal contact

### ACC-003: Portal contact views the document

**Given** the acceptance request is SENT
**And** alice.porter@acmecorp.com has the requestToken
**When** Alice accesses GET /api/portal/acceptance/{token}
**Then** the acceptance request details are returned (document info, expiry)
**And** the status transitions to VIEWED
**And** viewedAt timestamp is recorded

### ACC-004: Portal contact downloads the PDF

**Given** the acceptance request is in VIEWED status
**When** Alice accesses GET /api/portal/acceptance/{token}/pdf
**Then** the PDF is downloaded successfully
**And** the request remains in VIEWED status

### ACC-005: Portal contact accepts the document

**Given** the acceptance request is in VIEWED status
**When** Alice submits POST /api/portal/acceptance/{token}/accept with:
  - acceptorName: "Alice Porter"
**Then** the status transitions to ACCEPTED
**And** acceptedAt timestamp is recorded
**And** acceptor metadata is stored: name, IP address, user agent
**And** an audit event is created with the acceptance details

### ACC-006: Full lifecycle audit trail

**Given** an acceptance request has gone through PENDING -> SENT -> VIEWED -> ACCEPTED
**When** James views the acceptance request details
**Then** all timestamps are present: sentAt, viewedAt, acceptedAt
**And** the acceptor's IP address and user agent are recorded
**And** the full state transition history is auditable

---

## 9. Acceptance Expiry & Revocation

### ACC-007: Expired token is rejected

**Given** an acceptance request was created with expiresAt = yesterday
**When** Alice accesses GET /api/portal/acceptance/{token}
**Then** the status is EXPIRED
**And** a 410 Gone or appropriate error is returned
**And** Alice cannot accept the document

### ACC-008: Revoke an acceptance request

**Given** an acceptance request is in SENT status
**When** James revokes it via POST /api/acceptance-requests/{id}/revoke
**Then** the status transitions to REVOKED
**And** revokedAt timestamp is recorded
**And** Alice can no longer access the token endpoint

### ACC-009: Cannot accept a revoked request

**Given** an acceptance request has been revoked
**When** Alice attempts POST /api/portal/acceptance/{token}/accept
**Then** a 403 or 410 error is returned
**And** the request remains in REVOKED status

### ACC-010: Send a reminder

**Given** an acceptance request is in SENT or VIEWED status
**And** 3 days have passed without acceptance
**When** James sends a reminder via POST /api/acceptance-requests/{id}/remind
**Then** the reminder count is incremented
**And** lastRemindedAt timestamp is updated
**And** a notification/email stub is triggered

### ACC-011: Cannot remind on completed requests

**Given** an acceptance request is in ACCEPTED status
**When** James attempts to send a reminder
**Then** a 400 error is returned indicating the request is already completed

---

## 10. Certificate Generation

### ACC-012: Acceptance generates a certificate PDF

**Given** Alice has accepted an acceptance request
**When** James requests GET /api/acceptance-requests/{id}/certificate
**Then** a certificate PDF is returned
**And** the certificate includes:
  - Document title and reference
  - Acceptor name: "Alice Porter"
  - Acceptance timestamp
  - IP address (masked or full, per policy)
  - User agent summary

### ACC-013: Certificate is stored in S3

**Given** an acceptance request has been accepted
**When** the certificate is generated
**Then** the certificate PDF is uploaded to S3
**And** subsequent requests for the certificate return the stored version
**And** the certificate S3 key is recorded on the acceptance request

### ACC-014: Certificate unavailable before acceptance

**Given** an acceptance request is in VIEWED status (not yet accepted)
**When** James requests the certificate endpoint
**Then** a 404 or 400 error is returned indicating no certificate exists yet

---

## 11. RBAC

### DOC-013: Admin can manage templates

**Given** Priya (Admin) is authenticated
**When** she performs CRUD operations on templates and clauses
**Then** all operations succeed (200/201)

### DOC-014: Owner can manage templates

**Given** Thandi (Owner) is authenticated
**When** she creates, updates, or deletes templates
**Then** all operations succeed

### DOC-015: Member can generate documents but not manage templates

**Given** Lerato Dlamini (Member) is authenticated
**When** she attempts to create a template via POST /api/templates
**Then** a 403 Forbidden is returned
**When** she generates a document via POST /generate with a valid template and entity
**Then** the generation succeeds (201)

### DOC-016: Member cannot manage clauses

**Given** Lerato (Member) is authenticated
**When** she attempts to create, update, or deactivate a clause
**Then** a 403 Forbidden is returned

### ACC-015: Portal contact can only access their own acceptance token

**Given** alice.porter@acmecorp.com has token ABC123
**And** another portal contact has token XYZ789
**When** Alice attempts to access GET /api/portal/acceptance/XYZ789
**Then** a 403 or 404 is returned

### ACC-016: Only Admin/Owner can create acceptance requests

**Given** Lerato (Member) is authenticated
**When** she attempts to create an acceptance request
**Then** a 403 Forbidden is returned

---

## 12. Edge Cases

### GEN-014: Generate with missing required fields produces warnings

**Given** a template declares `project.dueDate` as a required context field
**And** the target project has no dueDate set
**When** Priya generates the document
**Then** the document is generated (with placeholder or blank for the missing field)
**And** the GeneratedDocument record includes a warnings array listing the missing field

### GEN-015: Generate with a template that has no clauses

**Given** a template exists with no clauses attached
**When** Priya generates a document
**Then** the document is generated successfully
**And** the clauseSnapshots field is empty
**And** no clause section appears in the PDF

### ACC-017: Accept an already-accepted request

**Given** an acceptance request is already in ACCEPTED status
**When** Alice submits POST /api/portal/acceptance/{token}/accept again
**Then** a 409 Conflict or 400 error is returned
**And** the original acceptance data is unchanged

### ACC-018: Multiple acceptance requests for the same document

**Given** a GeneratedDocument exists
**And** one acceptance request was REVOKED
**When** James creates a new acceptance request for the same document
**Then** the new request is created successfully with a fresh token
**And** the old revoked request remains in history

### GEN-016: Generate document for entity in another tenant

**Given** Priya is in tenant "acme"
**And** a project exists in tenant "globex"
**When** Priya attempts to generate a document referencing the Globex project ID
**Then** a 404 is returned (tenant isolation enforced)

### DOC-017: Delete template with existing generated documents

**Given** a template has been used to generate documents
**When** Priya attempts to delete the template
**Then** either the deletion is blocked (409 Conflict) with a message about existing documents
**Or** the template is soft-deleted and existing documents retain their snapshot data

### ACC-019: Access acceptance token with invalid format

**Given** a malformed or non-existent token "invalid-token-123"
**When** anyone accesses GET /api/portal/acceptance/invalid-token-123
**Then** a 404 is returned
**And** no internal details are leaked in the response
