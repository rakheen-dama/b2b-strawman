# Entity Model & Relationships

## Entity Relationship Summary

```
Organization (1 per tenant)
│
├── Member (synced from Clerk)
│   ├── BillingRate (3-level: default / customer / project)
│   ├── CostRate (internal cost tracking)
│   └── NotificationPreference
│
├── OrgSettings (branding, defaults)
│
├── Customer
│   ├── CustomerProject (M:N link to Project)
│   ├── Invoice
│   │   ├── InvoiceLine (with optional TimeEntry / RetainerPeriod links)
│   │   └── PaymentEvent
│   ├── ChecklistInstance
│   │   └── ChecklistInstanceItem
│   ├── PortalContact (portal access)
│   ├── RetainerAgreement
│   │   └── RetainerPeriod
│   └── Document (scope=CUSTOMER)
│
├── Project
│   ├── ProjectMember (team assignment)
│   ├── ProjectBudget
│   ├── Task
│   │   ├── TaskItem (subtask)
│   │   ├── TimeEntry
│   │   └── Comment (on task)
│   ├── Document (scope=PROJECT)
│   ├── Comment (on project)
│   └── GeneratedDocument
│
├── DocumentTemplate
│   ├── TemplateClause (M:N with Clause, ordered)
│   └── GeneratedDocument
│
├── Clause
│
├── Tag
│   └── EntityTag (polymorphic: project/customer/task/invoice)
│
├── FieldGroup
│   └── FieldDefinition
│
├── SavedView
│
├── AuditEvent (append-only)
│
├── Notification
│
├── ChecklistTemplate
│   └── ChecklistTemplateItem
│
├── ProjectTemplate (for recurring creation)
│
├── RecurringSchedule
│
├── TaxRate
│
├── DataRequest (GDPR/POPIA)
│
├── AcceptanceRequest
│
├── OrgIntegration (BYOAK config)
│
└── Document (scope=ORG)
```

## State Machines

### Customer Lifecycle
```
PROSPECT ──→ ONBOARDING ──→ ACTIVE ←──→ DORMANT
                              │                │
                              └──→ OFFBOARDING ←┘
                                       │
                                       ↓
                                  OFFBOARDED ──→ ACTIVE (reactivate)
```

### Project Status
```
ACTIVE ←──→ COMPLETED ──→ ARCHIVED
  ↑                           │
  └───────────────────────────┘ (reopen from archived)
```

### Task Status
```
OPEN ←──→ IN_PROGRESS ──→ DONE
  │           │              │
  │           ↓              └──→ OPEN (reopen)
  └──→ CANCELLED ──→ OPEN (reopen)
```

### Invoice Status
```
DRAFT ──→ APPROVED ──→ SENT ──→ PAID
              │          │
              └──→ VOID ←┘
```

### Document Upload Status
```
PENDING ──→ UPLOADED
    │
    └──→ FAILED
```

### Acceptance Request Status
```
PENDING ──→ ACCEPTED
    │
    ├──→ EXPIRED
    └──→ REVOKED
```

### Retainer Status
```
ACTIVE ←──→ PAUSED
  │
  ├──→ CANCELLED
  └──→ EXPIRED (past end date)
```

## Key Business Rules

1. **PROSPECT customers cannot have projects, tasks, time entries, or invoices** — must transition through ONBOARDING to ACTIVE first
2. **Invoice numbers are assigned at APPROVED, not at DRAFT** — prevents gaps in numbering sequence
3. **Only DRAFT invoices can be edited** — approved/sent/paid are immutable
4. **Time entry rate snapshots are immutable** — changing a rate card doesn't affect past entries
5. **Archived projects are read-only** — no new tasks, time entries, or documents
6. **Checklist completion auto-transitions customer** — all items done → ONBOARDING → ACTIVE
7. **Audit events are append-only** — database trigger blocks updates and deletes
8. **Documents have a two-phase upload** — create record, upload to S3, confirm — prevents orphaned records
9. **Delete protection**: projects with tasks, customers with invoices, etc. — cascading delete is blocked, must clean up dependencies first
