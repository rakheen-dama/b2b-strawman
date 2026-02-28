# API Surface Reference

The new frontend must consume the existing REST API unchanged. All endpoints require `Authorization: Bearer <JWT>` (Clerk JWT for main app, custom JWT for portal).

## Conventions
- **JSON** request/response bodies
- **RFC 7807 ProblemDetail** for errors
- **Pagination**: Spring Data format with `page`, `size`, `sort` query params where applicable
- **UUIDs** for all entity IDs
- **ISO 8601** for dates and timestamps

---

## Projects
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/projects` | List all projects |
| POST | `/api/projects` | Create project |
| GET | `/api/projects/{id}` | Get project detail |
| PUT | `/api/projects/{id}` | Update project |
| DELETE | `/api/projects/{id}` | Delete project |
| PATCH | `/api/projects/{id}/complete` | Complete project |
| PATCH | `/api/projects/{id}/archive` | Archive project |
| PATCH | `/api/projects/{id}/reopen` | Reopen project |
| GET | `/api/projects/{id}/members` | List project members |
| POST | `/api/projects/{id}/members` | Add member to project |
| DELETE | `/api/projects/{id}/members/{memberId}` | Remove member |
| PUT | `/api/projects/{id}/members/{memberId}/role` | Change member role |
| GET | `/api/projects/{id}/tasks` | List project tasks (filterable) |
| POST | `/api/projects/{id}/tasks` | Create task in project |
| GET | `/api/projects/{id}/profitability` | Project profitability |
| GET | `/api/projects/{id}/activity` | Activity feed (paginated) |
| GET | `/api/projects/{id}/budget` | Budget status |
| PUT | `/api/projects/{id}/budget` | Configure budget |
| GET | `/api/projects/{id}/time-summary` | Time tracking summary |

## Tasks
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/tasks/{id}` | Get task detail |
| PUT | `/api/tasks/{id}` | Update task |
| DELETE | `/api/tasks/{id}` | Delete task |
| POST | `/api/tasks/{id}/claim` | Claim task (assign to self) |
| POST | `/api/tasks/{id}/release` | Release task |
| PATCH | `/api/tasks/{id}/complete` | Complete task |
| PATCH | `/api/tasks/{id}/cancel` | Cancel task |
| PATCH | `/api/tasks/{id}/reopen` | Reopen task |
| GET | `/api/tasks/{id}/time-entries` | Time entries on task |
| POST | `/api/tasks/{id}/time-entries` | Log time on task |
| GET | `/api/tasks/{id}/items` | Subtask items |
| POST | `/api/tasks/{id}/items` | Create subtask |
| PUT | `/api/tasks/{id}/items/{itemId}` | Update subtask |
| DELETE | `/api/tasks/{id}/items/{itemId}` | Delete subtask |
| POST | `/api/tasks/{id}/items/{itemId}/toggle` | Toggle subtask completion |

## Time Entries
| Method | Endpoint | Purpose |
|--------|----------|---------|
| PUT | `/api/time-entries/{id}` | Update time entry |
| DELETE | `/api/time-entries/{id}` | Delete time entry |
| GET | `/api/billing-rates/resolve` | Resolve applicable rate |

## My Work
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/my-work/tasks` | My assigned/available tasks |
| GET | `/api/my-work/time-entries` | My time entries |
| GET | `/api/my-work/time-summary` | My time summary |

## Customers
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/customers` | List customers (filterable) |
| POST | `/api/customers` | Create customer |
| GET | `/api/customers/{id}` | Get customer detail |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |
| GET | `/api/customers/{id}/projects` | Customer's projects |
| POST | `/api/customers/{id}/projects/{projectId}` | Link project |
| DELETE | `/api/customers/{id}/projects/{projectId}` | Unlink project |
| GET | `/api/customers/{id}/checklists` | Customer's checklists |
| POST | `/api/customers/{id}/checklists` | Create checklist instance |
| GET | `/api/customers/{id}/unbilled-time` | Unbilled time summary |
| GET | `/api/customers/{id}/profitability` | Customer profitability |
| POST | `/api/customers/{id}/transition` | Lifecycle transition |
| GET | `/api/customers/{id}/lifecycle` | Lifecycle history |
| GET | `/api/customers/lifecycle-summary` | Distribution by status |
| POST | `/api/customers/dormancy-check` | Check for dormant customers |

## Invoices
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/invoices` | List invoices (filterable) |
| POST | `/api/invoices` | Create invoice draft |
| GET | `/api/invoices/{id}` | Get invoice detail |
| PUT | `/api/invoices/{id}` | Update draft invoice |
| DELETE | `/api/invoices/{id}` | Delete draft invoice |
| GET | `/api/invoices/{id}/preview` | HTML preview |
| POST | `/api/invoices/{id}/approve` | Approve invoice |
| POST | `/api/invoices/{id}/send` | Send to customer |
| POST | `/api/invoices/{id}/payment` | Record payment |
| POST | `/api/invoices/{id}/void` | Void invoice |
| POST | `/api/invoices/{id}/lines` | Add line item |
| PUT | `/api/invoices/{id}/lines/{lineId}` | Update line item |
| DELETE | `/api/invoices/{id}/lines/{lineId}` | Delete line item |
| POST | `/api/invoices/{id}/refresh-payment-link` | Refresh payment link |
| POST | `/api/invoices/validate-generation` | Validate before generation |

## Billing Rates
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/billing-rates` | Create billing rate |
| PUT | `/api/billing-rates/{id}` | Update rate |
| DELETE | `/api/billing-rates/{id}` | Delete rate |
| POST | `/api/cost-rates` | Create cost rate |
| PUT | `/api/cost-rates/{id}` | Update cost rate |
| DELETE | `/api/cost-rates/{id}` | Delete cost rate |

## Documents & Templates
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/documents` | List documents (filtered by scope) |
| POST | `/api/documents/upload-init` | Initialize upload |
| POST | `/api/documents/{id}/confirm` | Confirm upload complete |
| DELETE | `/api/documents/{id}/cancel` | Cancel upload |
| PATCH | `/api/documents/{id}/visibility` | Change visibility |
| GET | `/api/documents/{id}/download` | Download file |
| GET | `/api/templates` | List templates |
| POST | `/api/templates` | Create template |
| GET | `/api/templates/{id}` | Get template detail |
| PUT | `/api/templates/{id}` | Update template |
| DELETE | `/api/templates/{id}` | Delete template |
| POST | `/api/templates/{id}/clone` | Clone template |
| POST | `/api/templates/{id}/reset` | Reset to original |
| POST | `/api/templates/{id}/preview` | Preview with data |
| POST | `/api/templates/{id}/generate` | Generate document |
| GET | `/api/generated-documents` | List generated docs |
| DELETE | `/api/generated-documents/{id}` | Delete generated doc |
| GET | `/api/generated-documents/{id}/download` | Download PDF |

## Clauses
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/clauses` | List clauses |
| POST | `/api/clauses` | Create clause |
| GET | `/api/clauses/{id}` | Get clause detail |
| PUT | `/api/clauses/{id}` | Update clause |
| DELETE | `/api/clauses/{id}` | Delete clause |
| POST | `/api/clauses/{id}/clone` | Clone clause |
| GET | `/api/templates/{id}/clauses` | Template's clauses |
| PUT | `/api/templates/{id}/clauses` | Update clause associations |

## Settings
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/settings` | Get org settings |
| PUT | `/api/settings` | Update org settings |
| POST | `/api/settings/logo` | Upload logo |
| DELETE | `/api/settings/logo` | Remove logo |
| GET | `/api/settings/tax` | Get tax settings |
| PUT | `/api/settings/tax` | Update tax settings |
| GET | `/api/settings/compliance` | Compliance settings |
| GET | `/api/settings/acceptance` | Acceptance settings |
| PUT | `/api/settings/acceptance` | Update acceptance settings |

## Tags & Custom Fields
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/tags` | List tags |
| POST | `/api/tags` | Create tag |
| PUT | `/api/tags/{id}` | Update tag |
| DELETE | `/api/tags/{id}` | Delete tag |
| GET | `/api/{entity}/{id}/tags` | Get entity's tags |
| POST | `/api/{entity}/{id}/tags` | Set entity's tags |
| GET | `/api/field-definitions` | List field definitions |
| POST | `/api/field-definitions` | Create field definition |
| PUT | `/api/field-definitions/{id}` | Update field definition |
| DELETE | `/api/field-definitions/{id}` | Delete field definition |
| GET | `/api/field-groups` | List field groups |
| POST | `/api/field-groups` | Create field group |
| PUT | `/api/field-groups/{id}` | Update field group |
| DELETE | `/api/field-groups/{id}` | Delete field group |

## Saved Views
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/views` | List views |
| POST | `/api/views` | Create view |
| PUT | `/api/views/{id}` | Update view |
| DELETE | `/api/views/{id}` | Delete view |

## Checklists
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/checklist-templates` | List templates |
| POST | `/api/checklist-templates` | Create template |
| PUT | `/api/checklist-templates/{id}` | Update template |
| DELETE | `/api/checklist-templates/{id}` | Delete template |
| POST | `/api/checklist-templates/{id}/clone` | Clone template |
| GET | `/api/checklist-instances/{id}` | Get instance |
| PUT | `/api/checklist-items/{id}/complete` | Complete item |
| PUT | `/api/checklist-items/{id}/skip` | Skip item |
| PUT | `/api/checklist-items/{id}/reopen` | Reopen item |

## Data Requests (Compliance)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/data-requests` | List requests |
| POST | `/api/data-requests` | Create request |
| GET | `/api/data-requests/{id}` | Get request detail |
| PUT | `/api/data-requests/{id}/status` | Update status |
| POST | `/api/data-requests/{id}/export` | Generate export |
| GET | `/api/data-requests/{id}/export/download` | Download export |
| POST | `/api/data-requests/{id}/execute-deletion` | Execute deletion |

## Acceptance Requests
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/acceptance-requests/{id}/certificate` | Download certificate |

## Reports
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/reports/utilization` | Utilization report |
| GET | `/api/reports/profitability` | Profitability report |

## Members
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/members` | List org members |

## Notifications
Notification endpoints consumed by the frontend (bell, page, preferences).

## Retainers & Schedules
CRUD + lifecycle endpoints for retainer agreements and recurring schedules (see domain-specific files for flows).
