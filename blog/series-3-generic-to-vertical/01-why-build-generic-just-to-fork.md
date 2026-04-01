# Why I'm Building a Generic SaaS Just to Fork It

*This is Part 1 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries.*

---

There's a pattern in SaaS that nobody talks about honestly: generic platforms are commodities. Project management? There are 500 of them. Time tracking? Another 300. Document management? Don't even count.

The money is in vertical SaaS — software built for a specific industry. Clio for law firms. Karbon for accounting practices. Procore for construction. These companies charge 3-5x what horizontal tools charge, because they speak the customer's language, solve their specific compliance problems, and embed themselves into workflows that a generic tool can't touch.

But vertical SaaS has a cold start problem: you're building a complete platform (auth, multi-tenancy, billing, projects, documents, time tracking) *plus* the industry-specific features. That's 18 months of development before you can show anything to a customer.

Unless you build the generic platform first, and fork it.

## The 80/20 Architecture

DocTeams is a multi-tenant B2B platform with 83 entities. About 80% of them are generic: projects, documents, customers, tasks, time entries, invoices, rates, budgets, notifications, comments, audit logs. Every professional services firm needs these, regardless of industry.

The other 20% is what makes the software *valuable*: FICA compliance checklists for South African accounting firms. Court date tracking for law firms. LSSA tariff calculations. SARS submission deadlines. Engagement letter templates with SAICA-compliant liability clauses.

My approach: build the 80% as a shared foundation, then customize the 20% through a **pack system** — pluggable configuration that gets seeded into each tenant based on their industry profile.

```
Generic Foundation (80%)          Vertical Packs (20%)
┌──────────────────────┐         ┌─────────────────────┐
│ Auth & Multi-tenancy │         │ accounting-za:       │
│ Projects & Tasks     │         │   FICA checklists    │
│ Documents & Storage  │    +    │   VAT/SARS fields    │
│ Time Tracking        │         │   Engagement letters │
│ Invoicing & Billing  │         │   Fee schedules      │
│ Notifications        │         │   SARS automations   │
│ Audit & Compliance   │         ├─────────────────────┤
│ Templates & Reports  │         │ legal-za:            │
│ Custom Fields        │         │   Court calendar     │
│ Automations          │         │   Conflict checks    │
└──────────────────────┘         │   Trust accounting   │
                                 │   LSSA tariffs       │
                                 └─────────────────────┘
```

The fork isn't a Git branch. It's a configuration profile.

## Vertical Profiles

Each industry gets a JSON profile file that declares what gets seeded into a tenant:

```json
{
  "profileId": "accounting-za",
  "name": "South African Accounting Firm",
  "currency": "ZAR",
  "packs": {
    "field": ["accounting-za-customer", "accounting-za-project"],
    "compliance": ["fica-kyc-za"],
    "template": ["accounting-za"],
    "clause": ["accounting-za-clauses"],
    "automation": ["automation-accounting-za"],
    "request": ["year-end-info-request-za"]
  },
  "rateCardDefaults": {
    "currency": "ZAR",
    "billingRates": [
      { "roleName": "Owner", "hourlyRate": 1500 },
      { "roleName": "Admin", "hourlyRate": 850 },
      { "roleName": "Member", "hourlyRate": 450 }
    ]
  },
  "terminologyOverrides": "en-ZA-accounting"
}
```

When a tenant is provisioned with `verticalProfile: "accounting-za"`, the provisioning pipeline reads this profile and seeds:
- **Custom fields**: Company registration number, VAT number, SARS tax reference, entity type (Pty Ltd, Sole Proprietor, CC, Trust), financial year-end
- **Compliance checklists**: FICA/KYC with 10+ items, some conditional on entity type
- **Document templates**: Engagement letters, SA tax invoices, FICA confirmation letters
- **Clauses**: SAICA-aligned liability limitation, fee escalation, POPIA confidentiality
- **Automation rules**: FICA reminders, SARS deadline alerts, budget warnings
- **Request templates**: Year-end info requests (trial balance, bank statements, payroll summary)
- **Rate card defaults**: ZAR-denominated billing rates

A tenant provisioned without a vertical profile gets the generic packs only — basic templates, standard fields, generic automations. Still functional, just not industry-specific.

## Why Not Just Build the Vertical Product?

Three reasons:

**1. You don't know which vertical wins first.** I initially assumed law firms would be the best target market. After analysis, accounting firms turned out to have the smallest gap — similar time/billing/profitability workflows, simpler compliance requirements (FICA vs. trust accounting), and a larger addressable market in South Africa. If I'd built a law-firm-only product from day one, I'd have wasted months on trust accounting before discovering that accounting firms were the easier first customer.

**2. The generic foundation is reusable.** Multi-tenancy, auth, invoicing, time tracking — these don't change between verticals. Building them once and well (schema-per-tenant, ScopedValue, capability RBAC) means every vertical fork starts from a mature base. The alternative — building a law firm tool and then extracting the generic parts later — is the kind of refactoring that never actually happens.

**3. Multiple verticals multiply revenue.** One codebase serving accounting firms *and* law firms *and* consulting practices isn't 3x the maintenance. The generic foundation is shared. Only the pack files and a few vertical-specific entities differ. Three revenue streams from one engineering investment.

## The Fork Gap Analysis

Not all verticals are equally close to the generic platform. Here's the gap analysis I ran:

**Accounting firms (smallest gap, ~2-3 phases):**
- Same time/billing/profitability model
- FICA compliance maps cleanly to the checklist system
- Custom fields handle entity-type-specific data
- Missing: recurring engagement scheduling (built in Phase 16), engagement letter workflow
- Assessment: "A 3-person accounting firm could run ~75% of daily practice today"

**Law firms (biggest gap, 5-8 phases):**
- **Trust accounting** is the blocker — 3-5 phases on its own. Law firms in SA must maintain separate trust accounts per matter with strict reconciliation rules (Legal Practice Act). This is an entire sub-system.
- Court calendar with deadline tracking and prescription monitoring
- Conflict of interest checks across matters and parties
- LSSA tariff calculations for fee assessments
- Matter-specific billing (different from project billing — matter numbers, party-and-party costs)

**Consulting practices (medium gap, ~3-4 phases):**
- Resource planning and utilization tracking
- Proposal/SOW workflow with approval chains
- Multi-phase project structures (not just flat task lists)
- Client deliverable tracking with acceptance workflows

The order of attack is accounting first (smallest gap, fastest to revenue), then consulting (moderate gap, large market), then law (largest gap, highest revenue per customer once built).

## The Economics

Here's the math that makes this work:

**Generic platform development**: ~55 phases over 8 weeks. This is a sunk cost regardless of which vertical you target.

**Accounting vertical**: ~3 additional phases. Mostly pack seeding (JSON files), a few custom entities (engagement scheduling), and QA validation. Time investment: days, not weeks.

**Each additional vertical**: ~3-8 phases depending on the gap. But each vertical shares the same generic foundation, test infrastructure, deployment pipeline, and operational tooling.

**Revenue multiplication**: An accounting-specific SaaS in South Africa can charge R500-R2,000/month per firm. A law firm tool can charge R2,000-R5,000/month (higher willingness to pay due to compliance requirements). Two verticals from one codebase = 2x the addressable market with <50% additional development cost.

The generic platform is the leverage. The vertical packs are the margin.

---

*Next in this series: [Compliance Packs: Making Regulatory Requirements a First-Class Feature](02-compliance-packs.md)*

*If you're building vertical SaaS, I'm extracting the generic foundation into an open-source template with pluggable vertical packs. [Subscribe](#) for updates.*
