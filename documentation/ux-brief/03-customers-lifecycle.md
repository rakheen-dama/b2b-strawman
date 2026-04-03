# Customers & Lifecycle

## Customers

### What a Customer Is
A customer is a client of the firm. Customers can be linked to projects, have invoices raised against them, have documents generated for them, and access the customer portal.

### Customer Fields
| Field | Type | Notes |
|-------|------|-------|
| Name | Text | Required |
| Email | Text | Optional |
| Phone | Text | Optional |
| ID Number | Text | Optional (SA ID, company reg, etc.) |
| Customer Type | Enum | INDIVIDUAL, COMPANY, TRUST |
| Status | Enum | ACTIVE, ARCHIVED (operational flag) |
| Lifecycle Status | Enum | See lifecycle below |
| Custom Fields | Dynamic | Based on applied field groups |
| Tags | Multi-select | From org tag library |

### Customer Lifecycle

```
PROSPECT → ONBOARDING → ACTIVE ↔ DORMANT → OFFBOARDING → OFFBOARDED
                                                              │
                                                              └→ ACTIVE (reactivate)
```

| Status | Meaning | Restrictions |
|--------|---------|-------------|
| PROSPECT | Lead / potential client | Cannot create projects, tasks, time entries, or invoices |
| ONBOARDING | Intake in progress | Checklist must be completed. Auto-transitions to ACTIVE when all items done |
| ACTIVE | Engaged client | Full access to all features |
| DORMANT | Inactive (no activity for N days) | Same access as ACTIVE, flagged for attention |
| OFFBOARDING | Winding down engagement | Preparing for exit |
| OFFBOARDED | Relationship ended | Terminal, but can be reactivated to ACTIVE |

### Valid Transitions
| From | To |
|------|-----|
| PROSPECT | ONBOARDING |
| ONBOARDING | ACTIVE (auto, on checklist completion) |
| ACTIVE | DORMANT |
| DORMANT | ACTIVE |
| ACTIVE | OFFBOARDING |
| DORMANT | OFFBOARDING |
| OFFBOARDING | OFFBOARDED |
| OFFBOARDED | ACTIVE |

### Customer Detail Page (Tabbed)

**Projects Tab**
- Linked projects list
- "Link Project" dialog (select from existing projects)
- Unlink project action
- "Create Project for Customer" shortcut

**Invoices Tab**
- Invoices for this customer
- Create invoice draft
- Invoice status indicators
- Unbilled time summary (hours not yet invoiced)

**Retainers Tab**
- Retainer agreements for this customer
- Utilization progress bars
- Create retainer action

**Financials Tab**
- Customer lifetime profitability
- Revenue vs cost breakdown
- Rate cards applied to this customer

**Documents Tab**
- Documents scoped to this customer
- Upload zone
- Generated documents
- "Generate Document" dropdown

**Compliance Section** (within customer detail or separate tab)
- Lifecycle status badge with transition dropdown
- Active checklists (onboarding checklists in progress)
- Checklist item completion (check off, skip with reason, reopen)
- Lifecycle history timeline

### Customer List Page
- Table of all customers
- Filter by lifecycle status, customer type, tags
- Search by name/email
- "New Customer" button → Create dialog
- Lifecycle distribution chart (how many in each status)

### Create Customer Dialog
- Name (required)
- Email, phone, ID number (optional)
- Customer type dropdown
- Lifecycle starts at PROSPECT

---

## Onboarding Checklists

### What They Are
Configurable step-by-step checklists that guide customer onboarding. When all items in a checklist are completed, the customer auto-transitions from ONBOARDING to ACTIVE.

### Checklist Template (Settings)
- Name, description
- Customer type applicability (INDIVIDUAL, COMPANY, TRUST, or all)
- Ordered list of items, each with: title, description, required (boolean), allows document attachment
- Source: SYSTEM (from compliance packs) or CUSTOM (org-created)

### Checklist Instance (Per Customer)
- Created when customer transitions to ONBOARDING (or manually)
- Each item tracks: completed (boolean), completed by, completed at, notes, attached document
- Items can be: completed, skipped (with reason), or reopened
- Progress bar shows completion percentage

### Checklist Management Flow
1. Org sets up checklist templates in Settings → Checklists
2. Customer transitions to ONBOARDING
3. Checklist instance created from template
4. Team works through items (in any order)
5. All items completed → customer auto-transitions to ACTIVE

---

## Compliance Features

### Compliance Dashboard (`/compliance`)
- **Lifecycle Distribution** — chart showing how many customers are in each lifecycle status
- **Onboarding Pipeline** — customers currently in ONBOARDING with checklist progress
- **Dormancy Detection** — customers with no activity beyond a configurable threshold (default: 90 days)
- **Data Requests** — GDPR/POPIA data subject access and deletion requests

### Data Subject Requests
- Create request for a customer (type: ACCESS or DELETION)
- Request lifecycle: PENDING → IN_PROGRESS → COMPLETED (or REJECTED)
- ACCESS: generates export file (downloadable)
- DELETION: requires confirmation with customer name typed to confirm, anonymizes data
- Timeline view of request processing steps

### Retention Policies (Settings)
- Configure data retention periods per entity type
- Dormancy threshold (days of inactivity before flagging)
