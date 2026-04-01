# FICA Compliance Without the Filing Cabinet

*Part 3 of "Run a Leaner Practice with Kazi."*

---

Somewhere in your office — or in a box under someone's desk — there's a filing cabinet full of photocopied IDs, proof-of-address letters, and company registration certificates. Each folder represents a client. Each client represents a FICA obligation you hope you've met.

When the IRBA inspector arrives, you pull the folders. If the certified ID copy is there and it's less than 3 months old, you're fine. If it's expired, or missing, or the client is a trust and you forgot the Letters of Authority — that's a finding.

The filing cabinet isn't the problem. The problem is that nobody knows which folders are complete until the inspector asks.

## What FICA Actually Requires

The Financial Intelligence Centre Act requires accountable institutions to verify client identity before transacting. For accounting firms, this means collecting specific documents depending on the **entity type** of the client:

**Every client:**
- Certified ID copy of the responsible individual
- Proof of residential address (not older than 3 months)
- Tax clearance certificate (SARS TCS pin)

**Companies (Pty Ltd, CC, NPC) — additionally:**
- Company registration certificate (CIPC)
- Proof of registered address
- Beneficial ownership declaration (owners holding >25%)

**Trusts — additionally:**
- Trust deed
- Letters of Authority from the Master of the High Court
- Identification of all trustees

**Close Corporations — additionally:**
- CK1/CK2 registration documents
- Member interests schedule

A sole proprietor needs 3 documents. A trust needs 7+. And if you're serving 50 clients across different entity types, tracking which documents are outstanding for which clients is a full-time job.

## How Kazi Handles This

When you add a new client to Kazi, you select their entity type: Pty Ltd, Sole Proprietor, Close Corporation, Trust, NPC, or Partnership.

Kazi automatically creates a **compliance checklist** for that client — and the checklist items change based on the entity type.

Select "Sole Proprietor" and you see:
```
☐ Certified ID Copy
☐ Proof of Address (< 3 months)
☐ Tax Clearance Certificate
```

Select "Trust" and you see:
```
☐ Certified ID Copy (of lead trustee)
☐ Proof of Address (< 3 months)
☐ Tax Clearance Certificate
☐ Trust Deed
☐ Letters of Authority (Master's Office)
☐ Identification of all trustees
☐ Beneficial Ownership Declaration
```

Same checklist template. Different items. Driven by the entity type selection.

### Document Attachment

Each checklist item can require a document upload. "Certified ID Copy" has a document requirement — the team member uploads the scanned copy directly to the checklist item. The document is linked, timestamped, and stored securely.

No filing cabinet. No wondering "did we get this?" The checklist shows a green tick if the document is uploaded, a red indicator if it's missing.

### Automatic Client Activation

Here's where it gets interesting. In Kazi, a new client starts in **Onboarding** status. While onboarding, you can add their details, note their engagement terms, and start preparing — but you **cannot** create engagements, log time, or generate invoices for them.

Why? Because billing a client you haven't verified is a FICA violation.

When the last required checklist item is completed — the last document uploaded, the last verification ticked — the client automatically transitions to **Active** status. Now you can create engagements, log time, and bill.

No manual status change. No "I'll update it later." The compliance checklist gates the client lifecycle. Complete the verification, and the system opens up. Skip it, and the system holds firm.

### The IRBA Inspection Scenario

When the inspector arrives, you don't pull folders. You open Kazi and show them:

- **Client list filtered by verification status**: All clients, sorted by compliance completeness. Green = complete. Amber = in progress. Red = outstanding items.
- **Per-client checklist**: Every required document, with upload dates and the team member who verified it.
- **Audit trail**: When each item was completed, by whom, and when the client was activated.

The inspector sees a structured, timestamped record — not a box of photocopies. And because the system blocks work for unverified clients, there are no gaps to find.

## The 7-Day Nudge

A client enters onboarding, and the team starts collecting documents. But sometimes things stall — the client hasn't sent their tax clearance, the certified ID is being re-done, the trust deed is with the attorneys.

Kazi sends a notification 7 days after onboarding starts if FICA verification hasn't progressed. The firm's admins get an alert: "Thornton Properties entered onboarding 7 days ago and FICA verification has not been started."

Another nudge at 14 days. And another when SARS submission deadlines approach — because you can't file a return for a client you haven't verified.

These aren't aggressive reminders. They're safety nets. The firm that verifies all clients in the first week doesn't see them. The firm that occasionally lets one slip through does.

## Beyond FICA: The Compliance Pattern

The checklist system isn't limited to FICA. The same pattern works for:

- **Annual compliance reviews**: "Re-verify proof of address (expired > 3 months)" — triggered annually per client
- **SARS registration verification**: VAT registration, income tax reference, PAYE registration
- **Engagement prerequisites**: "Has the engagement letter been signed?" as a gate before work begins
- **Document completeness**: Custom checklists for specific engagement types (audit readiness, due diligence requirements)

Each checklist is a template. Templates can be assigned to client types, engagement types, or created custom for specific needs. The firm defines what compliance means for their practice — Kazi enforces it.

## What This Replaces

| Before Kazi | With Kazi |
|------------|-----------|
| Filing cabinet with photocopied IDs | Digital document storage linked to checklist items |
| Spreadsheet tracking verification status | Live compliance dashboard with status indicators |
| Manual status update when docs are collected | Auto-activation when checklist is complete |
| Hope that nothing was missed before inspection | Structured, timestamped, auditable record |
| One-time verification with no follow-up | Automated nudges and annual re-verification reminders |
| Same checklist for every client type | Entity-type-specific checklists (3 items for sole proprietor, 7+ for trust) |

The filing cabinet isn't going away tomorrow — you'll still need physical copies for some purposes. But the *tracking* — knowing which clients are verified, which are outstanding, and which documents are expiring — that's what Kazi replaces.

---

*[Request early access to Kazi →](#)*

*Next: [Your Clients Can Help Themselves (And They Prefer It)](04-client-portal.md)*
*Previous: [Stop Billing Late](02-stop-billing-late.md)*
