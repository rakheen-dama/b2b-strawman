# Notifications, Comments & Activity

## Notifications

### Notification Model
Every significant event in the system can generate a notification for relevant users.

### Notification Fields
| Field | Type |
|-------|------|
| Recipient | Member |
| Type | String (event type) |
| Title | Text (human-readable) |
| Body | Text (detail) |
| Reference Entity | Type + ID (what the notification is about) |
| Reference Project | Project ID (for scoping) |
| Read | Boolean |
| Created At | Timestamp |

### Notification Events
| Event | Recipient(s) |
|-------|-------------|
| Task assigned to you | Assignee |
| Task completed | Project members |
| Task comment added | Task assignee + mentioned members |
| Project member added | New member |
| Project status changed | Project members |
| Invoice approved | Owner + admin |
| Invoice paid | Invoice creator |
| Customer lifecycle transition | Admins |
| Document acceptance completed | Document generator |
| Retainer period at threshold | Admins |
| Budget threshold reached | Project lead |

### Notification UI

**Notification Bell (Header)**
- Icon with unread count badge
- Click → dropdown with recent notifications
- Each notification: icon, title, time ago, read/unread indicator
- Click notification → navigate to referenced entity
- "Mark all read" action
- "View all" → full notifications page

**Notifications Page (`/notifications`)**
- Full list of all notifications
- Filter: All / Unread
- Mark individual as read
- Mark all as read
- Preferences link

---

## Comments

### Comment Model
Flat comment threads on entities. Comments support internal visibility (firm-only) or shared visibility (visible in customer portal).

### Comment Fields
| Field | Type | Notes |
|-------|------|-------|
| Entity Type | Enum | TASK, DOCUMENT, PROJECT |
| Entity ID | UUID | What's being commented on |
| Project ID | UUID | For access scoping |
| Author | Member | Who wrote it |
| Body | Text | Comment content |
| Visibility | Enum | INTERNAL, SHARED |
| Parent ID | UUID | Optional (for threaded replies) |
| Created At | Timestamp | |

### Where Comments Appear
| Entity | Location |
|--------|---------|
| Project | Project detail → Comments tab |
| Task | Task detail sheet → Comments section |

### Comment Actions
- Add comment (text input + submit)
- Edit own comment
- Delete own comment (admin can delete any)
- Visibility is set at creation (cannot be changed after)

### Comment Visibility Rules
- **INTERNAL**: visible only to org members
- **SHARED**: visible to org members AND customer portal contacts
- Portal users can add SHARED comments via portal API

---

## Activity Feed

### What It Is
A chronological stream of events that happened on a project. Derived from audit events, formatted for human consumption.

### Activity Feed Content
| Event Category | Examples |
|----------------|---------|
| Project events | Created, status changed, member added/removed |
| Task events | Created, assigned, completed, cancelled |
| Time events | Time logged, time edited |
| Document events | Uploaded, generated, acceptance sent |
| Comment events | Comment added, edited |
| Invoice events | Created, approved, sent, paid |
| Customer events | Linked to project, lifecycle change |

### Activity Item Display
Each activity entry shows:
- Actor avatar + name
- Action description (e.g., "Alice completed task 'Review contract'")
- Timestamp (relative: "2 hours ago")
- Entity link (click to navigate)

### Where Activity Appears
- **Project detail → Activity tab**: all activity for that project
- **Dashboard → Recent Activity widget**: org-wide recent events
- Filter by entity type (tasks, documents, etc.)

---

## Audit Trail (Backend)

### What Gets Audited
Every create, update, delete, and state transition produces an immutable audit event. This is infrastructure — not directly surfaced to end users, but powers the activity feed and compliance features.

### Audit Event Fields
| Field | Type |
|-------|------|
| Event Type | String (e.g., INVOICE_APPROVED) |
| Entity Type | String (e.g., INVOICE) |
| Entity ID | UUID |
| Actor | Member ID or SYSTEM |
| Actor Type | USER or SYSTEM |
| Source | UI, API, WEBHOOK |
| IP Address | Optional |
| Details | JSON (event-specific data) |
| Occurred At | Timestamp (immutable) |

The audit trail is append-only — events cannot be modified or deleted (enforced by database triggers).
