# Compliance Packs: Making Regulatory Requirements a First-Class Feature

*Part 2 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries.*

---

Every regulated industry has compliance requirements that software usually handles as an afterthought — a checkbox field here, a manual reminder there, maybe a spreadsheet that someone maintains.

For professional services firms, compliance isn't an afterthought. It's the thing that keeps them out of trouble with regulators. An accounting firm in South Africa must verify every client's identity under FICA (Financial Intelligence Centre Act). A law firm must check for conflicts of interest before taking a case. These aren't nice-to-haves — they're legal obligations with real penalties.

DocTeams treats compliance requirements as first-class data: structured checklist templates with conditional items, automatic instantiation during customer onboarding, and completion-driven lifecycle transitions. And all of it is configured through **compliance packs** — JSON files that define industry-specific requirements without touching application code.

## What a Compliance Pack Looks Like

Here's the FICA/KYC compliance pack for South African accounting firms:

```json
{
  "packId": "fica-kyc-za",
  "name": "FICA KYC — SA Accounting",
  "version": "1.1.0",
  "verticalProfile": "accounting-za",
  "checklistTemplate": {
    "name": "FICA KYC — SA Accounting",
    "slug": "fica-kyc-za-accounting",
    "items": [
      {
        "name": "Certified ID Copy",
        "description": "Certified copy of director/member ID document",
        "required": true,
        "requiresDocument": true,
        "requiredDocumentLabel": "Certified ID (certified within last 3 months)"
      },
      {
        "name": "Proof of Address",
        "description": "Utility bill or bank statement not older than 3 months",
        "required": true,
        "requiresDocument": true
      },
      {
        "name": "Tax Clearance Certificate",
        "description": "SARS tax clearance certificate or Tax Compliance Status pin",
        "required": true
      },
      {
        "name": "Beneficial Ownership Declaration",
        "description": "Declaration identifying all beneficial owners holding >25% interest",
        "required": true,
        "requiresDocument": true,
        "applicableEntityTypes": ["PTY_LTD", "CC", "NPC", "TRUST"]
      },
      {
        "name": "Letters of Authority (Master's Office)",
        "description": "Letters of Authority issued by the Master of the High Court",
        "required": true,
        "applicableEntityTypes": ["TRUST"]
      }
    ]
  }
}
```

Two things to notice:

**`verticalProfile: "accounting-za"`** means this pack only gets seeded into tenants with the accounting-za profile. A law firm tenant never sees these items.

**`applicableEntityTypes`** makes items conditional. The "Beneficial Ownership Declaration" only appears for companies (Pty Ltd, CC, NPC, Trust) — not for sole proprietors. "Letters of Authority" only appears for Trusts. When the firm creates a customer and selects "Sole Proprietor" as the entity type, those items are hidden automatically.

This is critical for usability. A 3-person accounting firm managing sole proprietors doesn't need to see trust-related compliance items. The checklist adapts to the client type.

## The Seeding Pipeline

When a tenant is provisioned with `verticalProfile: "accounting-za"`, the `CompliancePackSeeder` runs:

```java
@Service
public class CompliancePackSeeder extends AbstractPackSeeder<CompliancePackDefinition> {

    @Override
    protected void applyPack(CompliancePackDefinition pack,
                             Resource packResource, String tenantId) {
        // 1. Create the checklist template
        var template = new ChecklistTemplate(
            pack.checklistTemplate().name(),
            pack.checklistTemplate().slug(),
            pack.checklistTemplate().autoInstantiate());
        template.setPackId(pack.packId());
        template = checklistTemplateRepository.save(template);

        // 2. Create items with entity-type filtering
        for (var itemDef : pack.checklistTemplate().items()) {
            var item = new ChecklistTemplateItem(
                template.getId(),
                itemDef.name(),
                itemDef.description());
            item.setRequired(itemDef.required());
            item.setRequiresDocument(itemDef.requiresDocument());
            item.setApplicableEntityTypes(itemDef.applicableEntityTypes());
            checklistTemplateItemRepository.save(item);
        }
    }
}
```

The seeder inherits vertical filtering from `AbstractPackSeeder` — the base class checks `pack.verticalProfile` against the tenant's profile and skips non-matching packs. This means you can drop a new compliance pack JSON file into the `compliance-packs/` directory, and it automatically seeds into the right tenants on the next provisioning.

## Checklists Drive the Customer Lifecycle

Compliance checklists aren't passive records. They drive the customer lifecycle state machine:

```
PROSPECT → ONBOARDING → ACTIVE
              │
              │ (checklist created when entering ONBOARDING)
              │ (auto-transitions to ACTIVE when all items complete)
```

When a customer transitions from PROSPECT to ONBOARDING, the system instantiates a checklist from the template. The firm works through the items — uploading certified IDs, marking tax clearance as verified, completing the beneficial ownership declaration. When the last required item is marked complete, the customer automatically transitions to ACTIVE.

No manual status toggle. No "forgot to activate the client." The compliance checklist *is* the activation gate.

```java
// In ChecklistService — completing the last item triggers activation
if (checklist.allItemsCompleted()) {
    if (customer.getLifecycleStatus() == LifecycleStatus.ONBOARDING) {
        customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId);
        eventPublisher.publishEvent(new CustomerActivatedEvent(customer, actorId));
    }
}
```

And the lifecycle guard prevents premature operations:

```java
// Can't create projects for PROSPECT customers
lifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);
// Throws: "Cannot create project for customer in PROSPECT status"
```

## Custom Fields Extend the Data Model

Compliance packs work alongside **field packs** that add industry-specific data fields to entities. For accounting-za, the customer entity gets:

```json
{
  "packId": "accounting-za-customer",
  "verticalProfile": "accounting-za",
  "entityType": "CUSTOMER",
  "fields": [
    {
      "slug": "acct_company_registration_number",
      "name": "Company Registration Number",
      "fieldType": "TEXT",
      "description": "CIPC registration number (e.g., 2024/123456/07)"
    },
    {
      "slug": "vat_number",
      "name": "VAT Number",
      "fieldType": "TEXT",
      "requiredForContexts": ["INVOICE_GENERATION"]
    },
    {
      "slug": "sars_tax_reference",
      "name": "SARS Tax Reference",
      "fieldType": "TEXT",
      "required": true
    },
    {
      "slug": "financial_year_end",
      "name": "Financial Year-End",
      "fieldType": "DATE",
      "required": true
    },
    {
      "slug": "acct_entity_type",
      "name": "Entity Type",
      "fieldType": "DROPDOWN",
      "required": true,
      "options": [
        { "value": "PTY_LTD", "label": "Pty Ltd" },
        { "value": "SOLE_PROPRIETOR", "label": "Sole Proprietor" },
        { "value": "CC", "label": "Close Corporation" },
        { "value": "TRUST", "label": "Trust" }
      ]
    },
    {
      "slug": "fica_verified",
      "name": "FICA Verified",
      "fieldType": "DROPDOWN",
      "options": [
        { "value": "NOT_STARTED", "label": "Not Started" },
        { "value": "IN_PROGRESS", "label": "In Progress" },
        { "value": "VERIFIED", "label": "Verified" }
      ]
    }
  ]
}
```

These fields appear on the customer form automatically. The `requiredForContexts` feature means VAT number isn't required when creating the customer, but *is* required when generating an invoice — the system blocks invoice generation until the VAT number is filled in.

The `acct_entity_type` dropdown drives the conditional checklist items. Select "Trust" and the checklist shows "Letters of Authority." Select "Sole Proprietor" and it doesn't.

## The Automation Layer

Compliance packs also seed **automation rules** — notifications that fire based on compliance events:

```json
{
  "slug": "fica-reminder",
  "name": "FICA Reminder (7 days)",
  "triggerType": "CUSTOMER_STATUS_CHANGED",
  "triggerConfig": { "toStatus": "ONBOARDING" },
  "actions": [{
    "actionType": "SEND_NOTIFICATION",
    "actionConfig": {
      "recipientType": "ORG_ADMINS",
      "title": "FICA not started: {{customer.name}}",
      "message": "Client {{customer.name}} entered onboarding 7 days ago..."
    },
    "delayDuration": 7,
    "delayUnit": "DAYS"
  }]
}
```

Seven days after a customer enters ONBOARDING, if FICA verification hasn't started, the firm's admins get a notification. Another automation fires when SARS submission deadlines approach.

These automations are seeded from JSON, not hard-coded. A law firm's compliance pack would have different triggers — conflict check reminders, prescription date alerts, court filing deadlines.

## Adding a New Vertical's Compliance

To add compliance packs for a new vertical (say, `consulting-generic`):

1. Create `compliance-packs/client-intake-generic/pack.json` with `"verticalProfile": "consulting-generic"`
2. Define checklist items: NDA signed, service agreement countersigned, billing setup complete
3. Create `field-packs/consulting-generic-customer.json` with relevant custom fields
4. Create the vertical profile at `vertical-profiles/consulting-generic.json`

No application code changes. No migration. No deployment. The pack seeder discovers new JSON files on the classpath and seeds them into matching tenants at provisioning.

For *existing* tenants that adopt a new vertical, a re-seed endpoint applies the packs retroactively — with the same idempotency guarantees (skip already-applied packs).

## The Compliance Advantage

Most horizontal SaaS tools treat compliance as "the customer's problem." DocTeams treats it as a feature:

- **Structured checklists** with conditional items beat spreadsheets
- **Lifecycle gating** prevents the firm from doing work for unverified clients
- **Auto-activation** reduces administrative friction
- **Automation rules** catch compliance gaps before regulators do

For a small accounting firm choosing between a generic project management tool and DocTeams, the FICA checklist alone justifies the switch. It's not a feature — it's risk management.

---

*Next in this series: [Document Templates Per Vertical: One Rendering Pipeline, Many Industries](03-document-templates-per-vertical.md)*

*Previous: [Why I'm Building a Generic SaaS Just to Fork It](01-why-build-generic-just-to-fork.md)*
