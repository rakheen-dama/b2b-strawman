# Document Templates Per Vertical: One Rendering Pipeline, Many Industries

*Part 3 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries.*

---

An accounting firm sends engagement letters. A law firm sends letters of demand. A consulting practice sends statements of work. The documents look different, say different things, and reference different data — but the *process* is the same: pick a template, populate it with context data, render it, send it.

DocTeams has a single document rendering pipeline that serves all verticals. Templates are packed per-industry, seeded at provisioning, and customizable by tenants. Here's how it works.

## The Template Pack Structure

Each vertical has a template pack — a directory of JSON files defining document templates:

```
template-packs/
├── generic/
│   ├── pack.json                        # 3 templates (Project Summary, Customer Overview, Invoice)
│   ├── project-summary.json             # Template content (ProseMirror document model)
│   ├── customer-overview.json
│   └── invoice.json
├── accounting-za/
│   ├── pack.json                        # 7 templates
│   ├── engagement-letter-bookkeeping.json
│   ├── engagement-letter-tax-return.json
│   ├── invoice-za.json                  # SA tax invoice with VAT
│   ├── fica-confirmation.json
│   └── ...
└── legal-za/
    ├── pack.json
    ├── letter-of-demand.json
    └── ...
```

The pack manifest declares templates with their category, target entity type, and content file:

```json
{
  "packId": "accounting-za",
  "verticalProfile": "accounting-za",
  "templates": [
    {
      "templateKey": "engagement-letter-bookkeeping",
      "name": "Engagement Letter — Monthly Bookkeeping",
      "category": "ENGAGEMENT_LETTER",
      "primaryEntityType": "PROJECT",
      "contentFile": "engagement-letter-bookkeeping.json",
      "description": "SAICA-format engagement letter for monthly bookkeeping"
    },
    {
      "templateKey": "invoice-za",
      "name": "SA Tax Invoice",
      "category": "OTHER",
      "primaryEntityType": "INVOICE",
      "contentFile": "invoice-za.json",
      "description": "SA tax invoice with VAT number and SARS-compliant formatting"
    }
  ]
}
```

The `primaryEntityType` determines which context builder populates the template. A PROJECT template gets project data + customer data + org settings. An INVOICE template gets invoice lines + customer details + banking info. A CUSTOMER template gets the customer's custom fields, compliance status, and linked projects.

## The Rendering Pipeline

```
Template (Handlebars + HTML/CSS)
        │
        ▼
Context Builder (assembles data for entity type)
        │
        ▼
Handlebars Compile + Render → HTML string
        │
        ▼
OpenHTMLToPDF → PDF bytes
        │
        ▼
S3 Upload → Presigned URL
        │
        ▼
GeneratedDocument record (tracking)
```

**Context builders** assemble the data that templates can reference. Each entity type has its own builder:

```java
// ProjectContextBuilder provides:
// {{project.name}}, {{project.status}}, {{project.dueDate}}
// {{customer.name}}, {{customer.email}}, {{customer.customFields.vat_number}}
// {{org.name}}, {{org.logo}}, {{org.brandColor}}, {{org.footer}}
// {{members}} (list of project team members)
// {{today}}, {{formattedDate}}

// InvoiceContextBuilder provides:
// {{invoice.number}}, {{invoice.status}}, {{invoice.dueDate}}
// {{invoice.lines}} (list with description, hours, rate, amount)
// {{invoice.subtotal}}, {{invoice.tax}}, {{invoice.total}}
// {{customer.*}}, {{org.*}}, {{bankDetails}}
```

The template references these variables using Handlebars syntax:

```html
<h1>Engagement Letter</h1>
<p>Dear {{customer.name}},</p>
<p>{{org.name}} is pleased to confirm our engagement to provide
monthly bookkeeping services for the financial year ending
{{customer.customFields.financial_year_end}}.</p>

<h2>Scope of Services</h2>
<ul>
  <li>Monthly processing of financial transactions</li>
  <li>Preparation of monthly management accounts</li>
  <li>VAT return preparation and submission (if applicable)</li>
</ul>

{{#if customer.customFields.vat_number}}
<p>VAT Registration: {{customer.customFields.vat_number}}</p>
{{/if}}
```

The `{{#if}}` block handles conditional content — VAT details only appear for VAT-registered clients. Custom fields are accessible through the `customFields` namespace, so accounting-specific fields work naturally in accounting-specific templates.

## Clause Integration

Templates can include **clauses** — reusable legal text blocks that are also packed per-vertical:

```json
{
  "packId": "accounting-za-clauses",
  "verticalProfile": "accounting-za",
  "clauses": [
    {
      "title": "Limitation of Liability",
      "slug": "accounting-za-limitation-of-liability",
      "body": "The liability of {{org.name}} shall be limited to three (3) times the total fees charged, or R1,000,000, whichever is the lesser."
    },
    {
      "title": "Fee Escalation",
      "slug": "accounting-za-fee-escalation",
      "body": "Fees shall be subject to annual escalation, effective on the anniversary of this engagement, at a rate not exceeding CPI + 2%."
    },
    {
      "title": "Document Retention",
      "slug": "accounting-za-document-retention",
      "body": "{{org.name}} shall retain all working papers and client records for a minimum period of five (5) years in accordance with SARS requirements."
    }
  ],
  "templateAssociations": [
    {
      "templatePackId": "accounting-za",
      "templateKey": "engagement-letter-bookkeeping",
      "clauseSlugs": [
        "accounting-za-limitation-of-liability",
        "accounting-za-fee-escalation",
        "accounting-za-document-retention"
      ],
      "requiredSlugs": [
        "accounting-za-limitation-of-liability",
        "accounting-za-document-retention"
      ]
    }
  ]
}
```

Clauses reference the same Handlebars variables as templates — `{{org.name}}` resolves to the tenant's organization name. The `templateAssociations` section wires clauses to specific templates, with some marked as required (can't be removed by the tenant) and others optional.

When the firm generates an engagement letter, the clauses are appended after the main template content. The firm can:
- **Reorder** optional clauses
- **Remove** non-required clauses
- **Add** their own custom clauses
- **Clone and edit** a system clause to customize the wording

Required clauses (liability limitation, document retention) stay locked — the firm can edit the content of a cloned version, but can't remove the clause entirely.

## Clone-and-Edit Customization

Firms need to customize templates without losing the ability to reset to the original. DocTeams uses a **clone-and-edit** pattern:

1. System templates (from packs) are marked `source: PLATFORM` and are read-only
2. When a firm wants to customize, they clone the template → creates a `source: CUSTOM` copy
3. The custom copy is fully editable
4. A "Reset to default" action deletes the custom copy and the platform original becomes active again

```java
@Transactional
public DocumentTemplate cloneForCustomization(UUID templateId) {
    var original = templateRepository.findById(templateId)
        .orElseThrow(() -> new ResourceNotFoundException("Template", templateId));

    var clone = new DocumentTemplate(
        original.getEntityType(),
        original.getName() + " (Custom)",
        generateSlug(original.getName() + "-custom"),
        original.getCategory(),
        new HashMap<>(original.getContent()));  // Deep copy of content
    clone.setSource(TemplateSource.CUSTOM);
    clone.setClonedFromId(original.getId());
    clone.setCss(original.getCss());

    return templateRepository.save(clone);
}
```

This means vertical packs can update their templates in new versions, and firms that haven't customized get the improvements automatically. Firms that have customized keep their changes.

## Branding Per Tenant

All templates reference `{{org.name}}`, `{{org.logo}}`, `{{org.brandColor}}`, and `{{org.footer}}` — configured in the tenant's OrgSettings. The SA tax invoice template includes:

```html
<div style="border-top: 3px solid {{org.brandColor}}">
  {{#if org.logo}}
  <img src="{{org.logo}}" alt="{{org.name}}" style="max-height: 60px" />
  {{/if}}
  <h1 style="color: {{org.brandColor}}">TAX INVOICE</h1>
</div>
```

A Thornton & Associates engagement letter has their logo, their brand color, their footer with registration details. A different accounting firm gets the same template structure with their own branding. One template, many firms.

## The Universal Pattern

The entire template system — content files, context builders, clause associations, clone-and-edit, branding — is vertical-agnostic. The vertical-specific part is entirely in the pack JSON files.

Adding templates for a new vertical:

1. Create `template-packs/legal-za/pack.json` with `"verticalProfile": "legal-za"`
2. Add template content files: `letter-of-demand.json`, `notice-to-defend.json`
3. Create clause pack: `clause-packs/legal-za-clauses/pack.json`
4. Wire clauses to templates via `templateAssociations`

The rendering pipeline, context builders, PDF generation, S3 upload, and clone-and-edit — all shared. All tested. All production-ready from day one of the new vertical.

---

*Next in this series: [The Gap Report: How I Measured Vertical Readiness](04-the-gap-report.md)*

*Previous: [Compliance Packs](02-compliance-packs.md)*
