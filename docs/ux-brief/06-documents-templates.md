# Documents & Templates

## Document Uploads

### What Documents Are
Files uploaded to the platform, scoped to an entity. Used for contracts, deliverables, reference materials, etc.

### Document Scopes
| Scope | Meaning | Where Visible |
|-------|---------|--------------|
| ORG | Organization-wide document | Documents page |
| PROJECT | Specific to a project | Project detail → Documents tab |
| CUSTOMER | Specific to a customer | Customer detail → Documents tab |

### Document Visibility
| Visibility | Meaning |
|-----------|---------|
| INTERNAL | Firm members only |
| SHARED | Also visible in customer portal |

### Upload Flow
1. Drag-and-drop or click to upload
2. System creates document record (status: PENDING)
3. Frontend uploads file directly to S3 (presigned URL)
4. Frontend confirms upload → status: UPLOADED
5. Document appears in relevant list

### Document Fields
| Field | Type |
|-------|------|
| File Name | Text |
| Content Type | MIME type |
| Size | Bytes |
| Scope | ORG / PROJECT / CUSTOMER |
| Visibility | INTERNAL / SHARED |
| Uploaded By | Member |
| Upload Date | Timestamp |

---

## Document Templates

### What Templates Are
Reusable document formats for generating professional PDFs — engagement letters, proposals, NDAs, invoices, reports. Templates contain variables that get replaced with real data at generation time.

### Template Fields
| Field | Type | Notes |
|-------|------|-------|
| Name | Text | Required |
| Slug | Text | Auto-generated, unique |
| Description | Text | Optional |
| Category | Enum | INVOICE, PROPOSAL, QUOTE, CUSTOM |
| Primary Entity Type | Enum | PROJECT, CUSTOMER, INVOICE |
| Content | Text/JSON | Template body (currently HTML, moving to Tiptap JSON) |
| CSS | Text | Optional custom styling |
| Source | Enum | TEMPLATE_PACK (system), ORG_CUSTOM (user-created) |
| Active | Boolean | Whether available for generation |
| Required Context Fields | JSON | Custom fields the entity must have filled |

### Template Categories
| Category | Typical Use |
|----------|------------|
| INVOICE | Invoice document formatting |
| PROPOSAL | Project proposals, engagement letters |
| QUOTE | Fee quotes, estimates |
| CUSTOM | Anything else (NDAs, reports, letters) |

### Entity Types
Templates are scoped to an entity type, which determines:
- What variables are available (project fields vs customer fields vs invoice fields)
- Where the "Generate Document" button appears
- What context data is fetched for rendering

### Template Variables
Variables are placeholders in the template that get replaced with real data:

| Group | Example Variables |
|-------|------------------|
| Project | project.name, project.description, project.startDate, project.dueDate |
| Customer | customer.name, customer.email, customer.phone, customer.idNumber |
| Invoice | invoice.number, invoice.issueDate, invoice.dueDate, invoice.total |
| Organization | org.name, org.brandColor, org.documentFooterText, org.logoUrl |
| Generated | generatedAt, generatedBy.name |

### Loop Data Sources (for tables)
| Source | Entity Types | Fields |
|--------|-------------|--------|
| invoice.lines | INVOICE | description, quantity, unitPrice, amount |
| invoice.taxBreakdown | INVOICE | taxRateName, taxRatePercent, taxAmount |
| members | PROJECT | name, email, role |
| rateCards | PROJECT | memberName, hourlyRate, currency |
| tags | PROJECT, CUSTOMER | name, color |

### Template Management (Settings → Templates)
- List of all templates with source badges (System/Custom)
- Search and filter by category, entity type
- Actions per template:
  - **System templates**: Clone, Preview, Reset to original
  - **Custom templates**: Edit, Clone, Preview, Delete
- "New Template" → create form

### Template Editor Page
- Name, category, entity type, description fields
- Content editor (current: HTML textarea; future: WYSIWYG rich editor)
- CSS field (for custom styling)
- Clauses tab (select which clauses to include)
- Preview: pick a real entity → see rendered output
- Save button

---

## Clauses

### What Clauses Are
Reusable text blocks that can be inserted into document templates. Useful for standard terms, confidentiality notices, payment terms, limitation of liability, etc.

### Clause Fields
| Field | Type | Notes |
|-------|------|-------|
| Title | Text | Required (e.g., "Confidentiality") |
| Slug | Text | Auto-generated |
| Description | Text | What this clause covers |
| Category | Text | Grouping (e.g., "Terms", "Legal", "Financial") |
| Body | Text/JSON | Clause content with variables |
| Source | Enum | SYSTEM (from pack), CUSTOM, CLONED |
| Active | Boolean | Available for use |
| Pack ID | Text | If from a clause pack |

### Clause Sources
| Source | Meaning | Editable? |
|--------|---------|-----------|
| SYSTEM | From platform clause pack | No — must clone to customize |
| CLONED | Copy of a system clause | Yes |
| CUSTOM | User-created from scratch | Yes |

### System Clauses (12 clauses in "standard-clauses" pack)
Categories include: Terms, Legal, Financial, General. Examples:
- Confidentiality clause
- Limitation of liability
- Payment terms
- Dispute resolution
- Force majeure
- Intellectual property

### Template-Clause Association
- Templates can include any number of clauses
- Each association has: ordering (sort position), required flag
- **Required clauses**: must be included when generating a document
- **Optional clauses**: can be deselected at generation time

### Clause Library (Settings → Clauses)
- List grouped by category
- Each clause shows: title, source badge, description
- Content visibility (current pain point — system clause content not visible without cloning)
- Actions: Clone (system), Edit/Delete (custom/cloned), Preview

---

## Document Generation

### Generation Flow
1. User clicks "Generate Document" on a project, customer, or invoice detail page
2. **Generation Dialog** opens:
   - **Step 1 — Template**: Select template (filtered by entity type)
   - **Step 2 — Clauses**: Review clauses associated with template
     - Required clauses: locked, cannot uncheck
     - Optional clauses: can uncheck
     - Can add more clauses from library
     - Reorder clauses
   - **Step 3 — Preview**: Rendered HTML preview
   - **Step 4 — Generate**: Confirm and generate
3. Backend renders template + clauses with entity data → PDF
4. PDF uploaded to S3, tracked as GeneratedDocument
5. Optionally saved as a Document (visible in documents list)

### Generated Documents
- Tracked separately from uploaded documents
- Fields: template used, entity, generated by, generated at, context snapshot, clause snapshots
- Download PDF action
- Delete action
- Listed on entity detail pages (project/customer/invoice documents tab)

---

## Document Acceptance (Lightweight E-Signing)

### What It Is
Send a generated document to a customer contact for review and acceptance. Not legally binding e-signatures — a lightweight "I accept this document" flow.

### Acceptance Flow
1. Firm generates a document (e.g., engagement letter)
2. Firm clicks "Send for Acceptance" on the generated document
3. System creates AcceptanceRequest, sends email with magic link to customer contact
4. Customer clicks link → sees PDF + acceptance form (name confirmation)
5. Customer clicks "Accept" → acceptance recorded, certificate generated
6. Firm sees status change: PENDING → ACCEPTED

### Acceptance States
| Status | Meaning |
|--------|---------|
| PENDING | Sent, awaiting customer response |
| ACCEPTED | Customer accepted the document |
| EXPIRED | Link expired (configurable expiry period) |
| REVOKED | Firm cancelled the request |

### Acceptance Settings (Settings → Acceptance)
- Expiry period (days)
- Custom acceptance message
- Auto-revoke on document regeneration (boolean)

### Where Acceptance Appears
- Generated document list: acceptance status badge
- Customer portal: pending acceptances banner on projects page
- Portal acceptance page: PDF viewer + accept button (unauthenticated, token-based)
