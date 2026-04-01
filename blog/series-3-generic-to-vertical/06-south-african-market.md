# South African Professional Services: A Surprisingly Good First Market

*Part 6 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries. This is the final post.*

---

When people hear "I'm building SaaS for South African accounting firms," the first reaction is: "Isn't that a small market?"

Yes. That's the point.

Small markets have properties that make them excellent for a first vertical: less competition, clearer customer profiles, regulatory requirements that create lock-in, and willingness to pay for software that understands their specific context. Let me explain why I chose this market, and what I've learned about building for it.

## Why South Africa

**FICA compliance is mandatory and poorly served by software.** The Financial Intelligence Centre Act requires every accountable institution (including accounting firms) to verify client identity before transacting. This isn't optional — the penalties are real (up to R100 million for institutions, R50 million for individuals, and criminal liability).

Most small accounting firms manage FICA compliance with:
- Paper folders with photocopied IDs
- Spreadsheets tracking verification status
- Manual reminders in Outlook calendars
- Occasional panic before IRBA inspections

DocTeams replaces all of this with structured checklists (conditional on entity type), document upload requirements linked to checklist items, auto-activation when compliance is complete, and automated reminders. For a firm managing 50+ clients, this alone justifies the subscription.

**SARS deadlines create natural automation demand.** The South African Revenue Service has strict filing deadlines that vary by entity type and financial year-end. An accounting firm tracks dozens of deadlines:
- Income Tax returns (within 12 months of financial year-end for companies)
- Provisional Tax (August and February for most entities)
- VAT returns (monthly or bi-monthly depending on registration)
- EMP201 payroll submissions (monthly)
- Annual Financial Statements (within 6 months for private companies)

Missing a deadline triggers penalties — and the firm, not the client, often absorbs the blame. DocTeams tracks SARS submission deadlines as custom field dates and fires automation reminders as they approach. The firm never "forgets" a filing date because the system doesn't forget.

**Small market = less competition.** Global practice management tools (Karbon, Canopy, Jetpack Workflow) exist but don't handle SA-specific requirements: FICA checklists, SARS deadline types, ZAR-denominated fee schedules, entity types (Pty Ltd, CC, Trust — specific to South African company law). Local competitors exist but are often dated or poorly maintained.

A global tool that doesn't know what FICA is will never be adopted by an SA accounting firm. A local tool that does know FICA but has a poor user experience will be tolerated but not loved. The opportunity is: modern UX + SA-specific compliance.

**Willingness to pay is high for compliance tools.** An accounting firm that gets fined for FICA non-compliance will happily pay R500-R2,000/month for software that prevents it. The ROI is obvious and immediate — not "improved productivity" (hard to measure) but "avoided R100M fine" (very easy to measure).

## The Entity Type System

One of the most important features for the SA accounting vertical is the entity type dropdown on the customer form:

```json
{
  "slug": "acct_entity_type",
  "name": "Entity Type",
  "fieldType": "DROPDOWN",
  "options": [
    { "value": "PTY_LTD", "label": "Pty Ltd" },
    { "value": "SOLE_PROPRIETOR", "label": "Sole Proprietor" },
    { "value": "CC", "label": "Close Corporation (CC)" },
    { "value": "TRUST", "label": "Trust" },
    { "value": "NPC", "label": "Non-Profit Company" },
    { "value": "PARTNERSHIP", "label": "Partnership" }
  ]
}
```

These aren't arbitrary categories — they're South African Companies Act classifications with different compliance requirements:

- **Pty Ltd**: Requires CIPC registration, director IDs, beneficial ownership declaration
- **CC (Close Corporation)**: Legacy entity type (can no longer register new ones), has member interests instead of shareholders
- **Trust**: Requires Letters of Authority from the Master of the High Court, trustees' details
- **Sole Proprietor**: Simplest — just the individual's ID and proof of address
- **NPC (Non-Profit)**: Requires MOI with non-distribution clause, beneficiary details

The entity type drives conditional FICA checklist items (Post 2), different custom field visibility, and eventually different document template selection. A trust's engagement letter references trustees, not directors.

## The Rate Card Defaults

The vertical profile seeds ZAR-denominated billing rates:

```json
{
  "rateCardDefaults": {
    "currency": "ZAR",
    "billingRates": [
      { "roleName": "Owner", "hourlyRate": 1500 },
      { "roleName": "Admin", "hourlyRate": 850 },
      { "roleName": "Member", "hourlyRate": 450 }
    ]
  }
}
```

R1,500/hour for a firm owner, R850 for a senior, R450 for a junior. These are realistic rates for a small SA accounting practice. The firm can adjust them, but starting with market-appropriate defaults communicates that the software was built for them.

By contrast, a generic tool that defaults to `$150/hour` immediately signals "this wasn't built for you." The firm has to change the currency, recalculate all their rates, and hope the tool handles ZAR formatting correctly. Most don't — they show `$` signs with ZAR amounts, or use American decimal formatting (1,500.00) instead of the convention some SA firms prefer.

## What's Next: The Law Firm Angle

The accounting vertical is the "smallest fork gap" from the generic platform. Law firms are the largest — and the most lucrative.

**What's needed for law firms:**

**Trust accounting** is the biggest challenge. The Legal Practice Act requires law firms to maintain a trust account separate from their business account. Every client's trust money must be tracked individually. Withdrawals require specific documentation. Monthly reconciliations must be submitted to the Legal Practice Council. This is an entire sub-system — 3-5 phases of development.

**Court calendar and prescription tracking.** Litigation has hard deadlines: filing dates, court appearances, prescription periods (statutes of limitations). Missing a prescription date can extinguish a client's claim entirely — and expose the firm to a malpractice suit. DocTeams has basic court date tracking (Phase 21), but the full system needs integration with court rolls, automatic prescription calculations based on cause of action, and cascading alerts.

**Conflict of interest checks.** Before a law firm takes on a new matter, they must check whether they've previously represented (or currently represent) the opposing party. This requires searching across all clients, matters, and parties — a cross-cutting query that touches multiple entities.

**LSSA tariff calculations.** The Law Society tariff determines what attorneys can charge for certain work (party-and-party costs). The tariff is published annually and varies by court, matter type, and value. Implementing this requires a tariff engine that's updated each year.

**Estimated gap**: 5-8 additional phases beyond the generic platform. The revenue per customer is higher (R2,000-R5,000/month due to compliance complexity), but the development investment is significant.

## The Template Extraction

Parallel to the vertical work, I'm extracting the generic foundation into an open-source template: `java-keycloak-multitenant-saas`. The template includes:

- Schema-per-tenant multitenancy with ScopedValue
- Keycloak auth with org support and capability RBAC
- Flyway dual-path migrations
- The pack seeder framework (without industry-specific pack content)
- Customer lifecycle state machine
- Time tracking and invoicing foundation
- Notification and audit infrastructure

The vertical packs (accounting-za, legal-za) would be separate add-ons — either open-source community contributions or paid packs.

The goal: anyone building B2B SaaS for professional services can start from a production-tested foundation and add their own vertical packs. Instead of spending 8 weeks building multi-tenancy, auth, and billing, they spend 1 week configuring the template and go straight to the industry-specific features that make their product valuable.

## Lessons for Choosing Your First Market

If you're building vertical SaaS, here's what I've learned from choosing South Africa:

**1. Pick a market where compliance creates demand.** Regulated industries *need* software. Non-regulated industries merely *want* it. "Need" converts better than "want."

**2. Pick a market where you have domain knowledge.** I understand the SA professional services landscape — the regulations, the workflows, the pain points. Building for an industry I don't understand (say, construction or healthcare) would require months of research before I could even define the packs.

**3. Pick a small market first.** It's counterintuitive, but a smaller market lets you iterate faster. Fewer competitors means fewer features to match. Fewer potential customers means you can talk to most of them. Faster feedback loops mean you catch the GAP-001 blocker before building 6 months of features nobody needs.

**4. Pick a market where the 80% foundation is reusable.** Professional services firms (accounting, law, consulting) share 80% of their workflow. The 20% that differs is what makes vertical SaaS valuable. If the 80% weren't shared, you'd be building three separate products.

**5. Pick a market where "good enough" pays.** A 3-person accounting firm doesn't need enterprise features. They need FICA compliance, time tracking, and invoicing — with a clean UI and sensible defaults. The 75% readiness score from the gap report is actually deployable for small firms. Perfection is the enemy of revenue.

South Africa isn't where most SaaS founders look. That's exactly why it works.

---

*This is the final post in "From Generic to Vertical." The series covered:*

1. *[Why Build Generic Just to Fork It](01-why-build-generic-just-to-fork.md) — the 80/20 architecture*
2. *[Compliance Packs](02-compliance-packs.md) — making regulatory requirements first-class*
3. *[Document Templates Per Vertical](03-document-templates-per-vertical.md) — one pipeline, many industries*
4. *[The Gap Report](04-the-gap-report.md) — measuring vertical readiness*
5. *[Terminology Overrides](05-terminology-overrides.md) — Projects → Engagements without forking*
6. *[South African Professional Services](06-south-african-market.md) — why small markets work*

*More series:*
- *["One Dev, 843 PRs"](/blog/series-1-one-dev-843-prs/) — building with AI agents*
- *["Multi-Tenant from Scratch"](/blog/series-2-multi-tenant-from-scratch/) — architecture deep-dives*

*I'm extracting the multi-tenant foundation + pack framework into an open-source template. [Subscribe](#) to know when it launches.*
