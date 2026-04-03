# Settings & Administration

## Organization Settings (`/settings`)

### Branding
| Setting | Type | Purpose |
|---------|------|---------|
| Organization Name | Text | Displayed in app, documents, portal |
| Logo | Image upload | Used in documents, portal header, invoices |
| Brand Color | Hex color | Accent color for portal, document templates |
| Document Footer Text | Text | Footer line on generated PDFs |

### Plan & Billing (`/settings/billing`)
- Current plan: Starter or Pro
- Member count: current / limit
- Upgrade button (Starter → Pro)
- Plan comparison card

---

## Tags (`/settings/tags`)

### What Tags Are
Colored labels that can be attached to projects, customers, tasks, and invoices. Used for ad-hoc categorization beyond the built-in fields.

### Tag Fields
| Field | Type |
|-------|------|
| Name | Text |
| Color | Hex color |

### Tag Management
- Create, edit, delete tags
- Tags are org-wide (shared across all entities)
- Applied to entities via multi-select tag picker in detail pages

### Tag Filtering
- Most list pages support "Filter by tag"
- Saved views can include tag filters

---

## Custom Fields (`/settings/custom-fields`)

### What Custom Fields Are
User-defined fields that extend the data model for projects, customers, tasks, and invoices. Organized into field groups.

### Field Definition
| Property | Type | Notes |
|----------|------|-------|
| Name | Text | Display label |
| Key | Text | Machine-readable identifier |
| Entity Type | Enum | PROJECT, CUSTOMER, TASK, INVOICE |
| Field Type | Enum | TEXT, NUMBER, DATE, SELECT, MULTI_SELECT, BOOLEAN, URL, EMAIL |
| Required | Boolean | Validation enforcement |
| Options | Array | For SELECT/MULTI_SELECT: list of allowed values |
| Default Value | Varies | Pre-filled value |
| Description | Text | Help text shown to users |

### Field Groups
Fields are organized into groups for bulk application:
- Group has: name, entity type, description
- Groups contain ordered field definitions
- Entities "apply" a field group → all fields in the group become available

### How Custom Fields Work
1. Admin creates field definitions and organizes them into groups
2. When viewing/editing an entity (project, customer, etc.), applied field groups determine which custom fields appear
3. Field values stored as JSON in the entity's `customFields` column
4. Custom fields appear in entity detail pages as an additional section
5. Saved views can filter by custom field values

### Field Packs (Auto-Applied)
- System-provided field groups that come pre-configured
- E.g., "Legal Client Fields" (matter number, court, jurisdiction)
- Auto-applied to new entities of the matching type (configurable)

### Conditional Visibility
- Fields can be conditionally shown based on another field's value
- E.g., "Court Name" only shown when "Matter Type" = "Litigation"

### Template Required Fields
- Document templates can declare required custom fields
- Generation dialog validates that required fields are filled before allowing generation

---

## Tax Rates (`/settings/tax`)

### Tax Rate Fields
| Field | Type |
|-------|------|
| Name | Text (e.g., "VAT") |
| Rate | Percentage (e.g., 15.00) |
| Default | Boolean (auto-applied to new invoice lines) |
| Active | Boolean |

### Tax Configuration
- Multiple rates supported simultaneously
- One can be marked as default
- Applied per invoice line item (can be overridden or exempted per line)

---

## Email (`/settings/email`)

### Email Provider
- **SMTP** (default, built-in)
- **SendGrid** (BYOAK — Bring Your Own API Key)
- Configuration: provider selection, API key, from address, from name

### Email Features
- Invoice delivery (send invoice to customer with PDF and payment link)
- Magic link emails (portal access)
- Notification emails (task assigned, comment added, etc.)
- Unsubscribe handling (per-recipient opt-out)

### Delivery Log
- Table of all sent emails
- Fields: recipient, subject, status (delivered/bounced/failed), sent at
- Bounce webhook processing (marks addresses as bounced)

---

## Integrations (`/settings/integrations`)

### Integration Model
- **BYOAK** (Bring Your Own API Key) — org provides their own API credentials
- **Integration Registry** — tracks which integrations are configured per org
- **Feature Flags** — enable/disable integration features

### Available Integrations
| Integration | Category | Purpose |
|------------|----------|---------|
| Stripe | Payment | Online payment collection for invoices |
| PayFast | Payment | South African payment collection |
| SendGrid | Email | Transactional email delivery |
| S3 / LocalStack | Storage | File storage (auto-configured) |

### Integration Card UI
- Each integration shown as a card
- Status: Connected / Not Connected
- "Configure" → API key dialog
- "Test Connection" button
- Enable/disable toggle

---

## Notification Preferences (`/settings/notifications`)

### Per-Event Configuration
Users can configure which events trigger notifications and via which channel:

| Event Type | Examples |
|-----------|----------|
| Task events | Assigned, completed, comment added |
| Project events | Member added, status changed |
| Invoice events | Approved, sent, paid |
| Customer events | Lifecycle transition |
| Document events | Generated, acceptance status changed |

### Channels
| Channel | Status |
|---------|--------|
| In-app | Always available |
| Email | Available if email configured |

### Preference Matrix
- Grid: event types (rows) × channels (columns)
- Toggle on/off per cell

---

## Checklist Templates (`/settings/checklists`)

### Template Management
- List of all checklist templates
- Filter by customer type applicability
- Create/edit/clone/delete templates
- System templates from compliance packs (read-only, can clone)

### Template Editor
- Name, description
- Customer type applicability checkboxes
- Ordered item list (drag to reorder)
- Per item: title, description, required flag, document attachment toggle
- Add/remove items

---

## Project Templates (`/settings/project-templates`)

### Template Management
- List of project templates
- Create from scratch or "Save as Template" from existing project
- View/edit template structure

### Template Detail
- Name, description
- Task list with: title, description, priority, default assignee (by role)
- Tags to auto-apply
