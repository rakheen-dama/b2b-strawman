# R3 — Activity feed / audit cluster: root-cause dossier

Scope: G-B8, G-D9, G-B2, G-D6, G-D8, G-D10 (audit-label part), G-B13. Read-only code archaeology on `main` (HEAD 50b88ed5c). Every claim is tagged CONFIRMED (read at file:line) or HYPOTHESIS (inferred, not executed). Paths are relative to `/Users/rakheendama/Projects/2026/b2b-strawman/`; `B=backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `F=frontend`.

Constraint honoured throughout: audit `entity_type` literals (`project`, `customer`, `invoice`, …) are storage keys pinned by ADR-342 (`adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md:22,33,54`) and `B/../audit/EntityTypeLiteralStabilityTest.java:18-38`. ADR-342 :54 explicitly says "Users see 'Matter' and 'Client' because the rendering component writes those words" — so a UI-side legal-word→literal map is the sanctioned shape, not a rename.

---

## Q1 — How the Activity tabs query events (G-B8, G-D9, G-B2)

### Matter Activity feed (`/api/matters/{matterId}/activity`)

- CONFIRMED `B/activity/ActivityController.java:17,37-50` → `ActivityService.getProjectActivity` → `AuditEventRepository.findByProjectId` (`B/activity/ActivityService.java:76-78`).
- CONFIRMED `B/audit/AuditEventRepository.java:56-77` — the feed query is **only**
  `WHERE (details->>'project_id') = CAST(:projectId AS TEXT) AND (:entityType IS NULL OR entity_type = :entityType)`.
  It does **not** OR-in `entity_type='project' AND entity_id=:projectId`. (Contrast `findMostRecentByProject` at `:93-101`, which does OR both branches.) Index: `idx_audit_project ON audit_events ((details->>'project_id'))` (`backend/src/main/resources/db/migration/tenant/V1__baseline.sql:2026`).
- CONFIRMED The same `details->>'project_id'` correlation drives Home "recent activity" (`findCrossProjectActivity` / `...ForMember`, `AuditEventRepository.java:~108-156`) and the portal Firm-actions trail (`PortalActivityEventResponse.java:14-21`). So `project_id` in details is the single load-bearing correlation key across three feeds. Publishers that already know this carry comments ("load-bearing", `B/project/ProjectService.java` before `project.updated`; `B/projecttemplate/ProjectTemplateService.java` OBS-8801; `B/verticals/legal/closure/MatterClosureService.java:262-266`).
- CONFIRMED The frontend filter chips send `entityType` in {TASK, DOCUMENT, COMMENT, PROJECT_MEMBER, TIME_ENTRY} (`F/components/activity/activity-filter.tsx:11-16`); the service lowercases it (`ActivityService.java:73`). No chip for fee notes / trust / disbursements.

**Per family: does the event carry `project_id` in details? (reachability of the matter feed)**

| Family | Emit site | `project_id` in details? | Feed reach |
|---|---|---|---|
| `acceptance.created/sent/viewed/accepted/certificate_generated/revoked/reminded/expired` | `B/acceptance/AcceptanceService.java:213-246,377-389,402,488,566`; `AcceptanceNotificationService.java:243` | **No.** details = generated_document_id, portal_contact_id, customer_id, contact_name/email, document_file_name; accepted = acceptor_name, ip_address | CONFIRMED unreachable → G-B8 "acceptance lifecycle omitted" |
| `trust_deposit.recorded`, `trust_payment.recorded`, `trust_transfer.recorded` | `TrustTransactionService.java:258-268, 502-512, 418-` | **No** (amount, customer_id, customer_name, reference) even though `TrustTransaction.project_id` column exists (`TrustTransaction.java:35,144`) and `request.matterId()` is passed to the entity (`:231,:490`) | CONFIRMED unreachable → G-D9 (R 50 000 deposit, R 12 000 payment absent) |
| `trust_<type>.approved / .rejected / .first_approved` | `:896-905, 956-, 770-779` | **No** (transaction_type, amount, approved_by) | unreachable |
| `trust_fee_transfer.recorded`, `trust_refund.recorded` | `:598-607, 668-676` | **Yes** (`"project_id"`, may be `""`) | reachable, but no formatter template → raw fallback |
| `trust_transaction.reversed` | `:1164-1174, 1297-` | No | unreachable |
| `invoice.created` | `B/invoice/InvoiceCreationService.java:222-236` | **No** (customer_id/name, currency, line_count…) | unreachable → G-D9 "no created" |
| `invoice.approved` | `B/invoice/InvoiceTransitionService.java:165-180` | **No** (invoice_number, issue_date, total, time_entry_count…) | unreachable → G-D9 "no approved" |
| `invoice.sent` | `InvoiceTransitionService.java:242-248` | **Yes** (`sentProjectId`) | reachable → shows as raw "performed invoice.sent on invoice" (G-D9, G-D10) |
| `invoice.paid` (manual) / payment events | `:360-375`; helper `:726-738` adds `project_id` only when non-null | partial | reachable when project known; no template |
| `budget.created/updated/deleted` | `B/budget/ProjectBudgetService.java:118-137` | **Yes** | reachable → raw "performed budget.created on project_budget" (G-D9) |
| `disbursement.created/updated/submitted/approved/rejected/written_off/billed/unmarked_billed/receipt_attached` | `B/verticals/legal/disbursement/DisbursementService.java:156,235,257,283,314,345,376,425,478` | **Yes** (all nine) | reachable → raw "performed disbursement.approved on legal_disbursement" (G-B8) |
| `project.created/created_from_template/updated/completed/archived/reopened/deleted` | `B/project/ProjectService.java:731,995,1042,1094`; `ProjectTemplateService.java` (OBS-8801 comment) | **Yes** (`details.put("project_id", …)` — "load-bearing" comment) | reachable → raw "performed project.updated on project" / "project.created_from_template" (G-B8, G-D9, G-D10) |
| `matter_closure.closed/reopened`, `matter.closure.override_used` | `MatterClosureService.java:267-298, 513-521` | Yes | reachable, templated |
| `checklist.item.completed/reopened/skipped`, `checklist.instance.created/completed` | `B/checklist/ChecklistInstanceService.java` (item details: itemName, instanceId, completedBy, notes, documentId; instance.completed: `customerId`) | No project_id (checklists are client-scoped; `ChecklistInstance.customer_id` `ChecklistInstance.java:23`) | n/a for matter feed — see client tab below |
| Also reachable but **untemplated**: `expense.*` (`ExpenseService.java:143,242,282,328,365`), `court_date.*` (`CourtCalendarService.java:190`), `prescription_tracker.*`, `adverse_party.*`, `task.completed/cancelled/reopened/deleted/recurrence_created`, `task_item.*`, `project_member.role_changed`, `time_entry.rate_re_snapshot`, `information_request.reminder_sent`, `statement.generated` (templated), `payment.*` | | Yes | raw fallback |

### Client Activity tab (`?tab=audit`, `entityType="customer"`)

- CONFIRMED `F/app/(app)/org/[slug]/clients/[id]/audit-tab.tsx:11` → `<AuditTimelineTab entityType="customer" entityId={customerId}>` → `AuditTimeline` → `GET /api/audit-events/{entityType}/{entityId}` (`F/components/audit/audit-timeline.tsx:33,66`) → `AuditEventController.listAuditEventsByEntity` (`B/audit/AuditEventController.java:99-115`) → `AuditEventFilter(entityType, entityId, …)` → `findByFilter` (`AuditEventRepository.java:32-50`): strict `e.entityType = :entityType AND e.entityId = :entityId`.
- CONFIRMED Checklist events are `entity_type='checklist_instance_item'` / `'checklist_instance'` with the client only in `details.customerId` (instance.completed) or not at all (item.completed only has `instanceId`). So they can never match the customer-entity query → G-B2. This is **not** the matter feed; it is the generic by-entity audit endpoint. Note: the client's lifecycle rows render as "Customer Lifecycle Transitioned" with no from/to because the label is registry-derived (Q3) and the timeline shows `row.label` only (`audit-timeline.tsx:162`), details are behind an expander (`:192`).
- CONFIRMED A precedent for a child-join already exists: `streamForCustomer` for DSAR (`AuditEventRepository.java:523-548`) ORs (a) customer entity, (b) `entity_type IN childTypes AND entity_id = ANY(childIds)`, (c) `details->>'customerId'`. The client Activity tab does not use it.

**Root cause (Q1):** one correlation key (`details->>'project_id'`) with inconsistent population across publishers; the matter feed has no entity_type/entity_id branch and no child-entity join; the client tab uses the strict by-entity endpoint with no child-type join.

---

## Q2 — `ActivityMessageFormatter` templates vs raw fallback (G-B8, G-D9, G-D10)

- CONFIRMED `B/activity/ActivityMessageFormatter.java:44-154`: switch with ~45 keys; `default -> "%s performed %s on %s"` at `:153`. Test pins the fallback for `customer.updated` (`ActivityMessageFormatterTest.java:250-255`, `unknownEventTypeProducesFallbackMessage`) and `doesNotContain("performed")` for several LZKC-019 keys (`:561,577,593`).
- CONFIRMED Home "recent activity" uses a **second, separate** formatter `DashboardService.buildActivityDescription` (`B/dashboard/DashboardService.java:920-938`) with only created/updated/deleted/uploaded verbs and `default -> actor + " performed " + eventType` (`:936`) — produces "performed invoice.sent / project.reopened / project.completed" (G-D10). Entity word is `parts[0]` of the event key, so "created a project".
- CONFIRMED `AuditEventTypeRegistry` (`B/audit/AuditEventTypeRegistry.java:33-188`) is the label/severity catalogue (≈29 entries incl. prefixes), **not** an enumeration of emitted keys. The emitted set is 274 distinct `.eventType("…")` literals in `src/main/java` plus a few computed (`trust_<type>.approved/.rejected/.first_approved`, `budget.created|updated`, `integration.enabled|disabled`, `automation_rule.enabled|disabled`). Full list captured at `scratchpad/eventtypes.txt`.

**Templated today (have a human sentence):** task.created/updated/claimed/released; document.uploaded/updated/deleted/generated/generated_with_clauses/created/accessed/visibility_changed; docx_document.generated; comment.created/updated/deleted/visibility_changed; time_entry.created/updated/deleted; project_member.added/removed; information_request.created/sent/cancelled/completed/item_submitted/item_accepted/item_rejected/updated; portal.request_item.submitted; mcp.write.*(4); collections.*(5); portal.document.downloaded/upload_initiated/acknowledged; portal.invoice.paid; statement.generated; matter_closure.closed/reopened; matter.closure.override_used.

**Missing templates — only keys that can actually reach the matter feed today or after the Q1 fix (reachable = details has project_id now; "after Q1" = would reach once project_id/entity branch added). Legal wording proposals:**

| Event key | Reach | Proposed message (actor = `%s`) |
|---|---|---|
| project.created | now | `%s opened the matter "%s"` (details.name / project_name) |
| project.created_from_template | now | `%s opened the matter from template "%s"` (template_name) |
| project.updated | now | `%s updated the matter` (+ ` — <field> changed` from AuditDeltaBuilder keys if present) |
| project.completed | now | `%s completed the matter` |
| project.reopened | now | `%s reopened the matter` |
| project.archived | now | `%s archived the matter` |
| project.deleted | now | `%s deleted the matter` |
| project_member.role_changed | now | `%s changed %s's role to %s` |
| budget.created / budget.updated / budget.deleted | now | `%s set a matter budget` / `updated the matter budget` / `removed the matter budget` (append `of R %s` from budget_amount when present) |
| disbursement.created | now | `%s recorded disbursement "%s"` (description if present, else `a disbursement`) |
| disbursement.updated | now | `%s updated a disbursement` |
| disbursement.submitted | now | `%s submitted a disbursement for approval` |
| disbursement.approved | now | `%s approved a disbursement` |
| disbursement.rejected | now | `%s rejected a disbursement` (+ reason) |
| disbursement.written_off | now | `%s wrote off a disbursement` (+ reason) |
| disbursement.billed / unmarked_billed | now | `%s billed a disbursement to %s` (invoice_number) / `%s unbilled a disbursement` |
| disbursement.receipt_attached | now | `%s attached a receipt to a disbursement` |
| invoice.sent | now | `%s sent fee note %s` (invoice_number) |
| invoice.paid | now (when project known) | `%s recorded payment on fee note %s` |
| invoice.payment_reversed / payment_partially_reversed | now | `%s reversed a payment on fee note %s` / `partially reversed …` |
| invoice.created | after Q1 | `%s created fee note %s` (number may be absent pre-approval → `a draft fee note`) |
| invoice.approved | after Q1 | `%s approved fee note %s` |
| invoice.voided | after Q1 | `%s voided fee note %s` |
| acceptance.created / sent | after Q1 | `%s sent "%s" to %s for acceptance` (document_file_name, contact_name) |
| acceptance.viewed | after Q1 | `%s viewed "%s"` (portal actor) |
| acceptance.accepted | after Q1 | `%s accepted "%s"` (acceptor_name; actor_type PORTAL_CONTACT already resolves via portalContactMap) |
| acceptance.reminded / revoked / expired / certificate_generated | after Q1 | `%s sent a reminder for "%s"` / `%s revoked the acceptance request for "%s"` / `The acceptance request for "%s" expired` / `Certificate of acceptance generated for "%s"` |
| trust_deposit.recorded | after Q1 | `%s recorded a trust deposit of R %s (%s)` (amount, reference) |
| trust_payment.recorded | after Q1 | `%s recorded a trust payment of R %s (%s)` |
| trust_transfer.recorded / trust_fee_transfer.recorded / trust_refund.recorded | now (fee/refund) / after Q1 | `%s recorded a trust transfer of R %s` / `transferred R %s from trust to business` / `recorded a trust refund of R %s` |
| trust_<type>.approved / rejected / first_approved | after Q1 | `%s approved the trust %s of R %s` (type lower-cased: deposit/payment/transfer) etc. |
| trust_transaction.reversed | after Q1 | `%s reversed a trust %s of R %s` |
| expense.created / updated / deleted / restored / written_off | now | `%s recorded an expense` … |
| court_date.created / updated / postponed / cancelled / outcome_recorded | now | `%s diarised a court date` / `postponed a court date` / `recorded the outcome of a court date` |
| prescription_tracker.created / updated / interrupted | now | `%s started tracking prescription` / `interrupted prescription` |
| adverse_party.linked / unlinked (/created/updated/deleted) | now | `%s linked adverse party "%s"` |
| task.completed / reopened / cancelled / deleted / recurrence_created | now | `%s completed task "%s"` … |
| task_item.created / toggled / updated / deleted / reordered | now | `%s ticked an item on task "%s"` … |
| time_entry.rate_re_snapshot | now | `%s refreshed the rate on a time entry` |
| information_request.reminder_sent | now | `Reminder sent for information request %s` |
| payment.completed / failed / session.* | now | `Online payment received for fee note %s` / `Online payment failed for fee note %s` |
| checklist.item.completed / skipped / reopened, checklist.instance.created / completed | client tab only | `%s completed FICA item "%s"` / `skipped …` / `reopened …`; `FICA checklist started` / `FICA checklist completed` (note: feed formatter isn't used by the audit timeline — those rows show registry labels, see Q3) |
| customer.created / updated / archived / unarchived / lifecycle.transitioned / linked / unlinked | client tab only | registry labels, see Q3 |

Reusable wording already in the codebase: `PortalActivityEventResponse.summary` (`B/portal/PortalActivityEventResponse.java:80-104`) has "Fee note sent to you", "Trust transaction recorded", "Matter closed" — the firm-side feed should mirror those nouns.

**Recommended fix shape (Q2):**
1. Extend the `switch` in `ActivityMessageFormatter.formatMessage` with the table above; add a `resolveEntityName` branch for `invoice` (invoice_number), `legal_disbursement`, `project_budget`, `trust_transaction`, `acceptance_request`, `project`.
2. Replace `default` with a *readable* generic: `"%s %s %s"` built from a small verb map (`created`→"created", `approved`→"approved", …) + a legal noun map for entity_type (`project`→"the matter", `invoice`→"a fee note", `legal_disbursement`→"a disbursement", `trust_transaction`→"a trust transaction", `customer`→"the client"). That removes the "performed x on y" class for any future key. Update `ActivityMessageFormatterTest:250-255` to the new generic wording.
3. Make `DashboardService.buildActivityDescription` (`:920-938`) delegate to the same formatter (or the same verb/noun maps) so Home stops saying "performed invoice.sent" (G-D10 cluster).

---

## Q3 — Where "Project Updated", "Customer Lifecycle Transitioned", "Invoice" labels come from (G-D10 audit-label part, G-B8 Audit sub-tab, G-B2)

- CONFIRMED Labels are computed server-side at read time by `AuditEventTypeRegistry.resolve` (`B/audit/AuditEventTypeRegistry.java:257-277`): exact → longest `.*` prefix → `defaultFor` = `titleCase(eventType)` (`:299-329`), splitting on `.` and `_`. So:
  - `project.updated` → no entry → "Project Updated"; `project.created_from_template` → "Project Created From Template"; `project.reopened` → "Project Reopened".
  - `customer.lifecycle.transitioned` → no entry → "Customer Lifecycle Transitioned"; `customer.created` → "Customer Created".
  - `invoice.*` → exact prefix entry `"Invoice"` at `AuditEventTypeRegistry.java:109-110` (the one flagged in the gap attribution).
- CONFIRMED Delivered to the UI as `AuditEventResponse.label` (`B/audit/AuditEventResponse.java`, via `AuditEventMetadataResolver.java:37,68`) and rendered verbatim: firm audit trail `audit-log-client.tsx:679` (`{event.label}`), entity timelines `audit-timeline.tsx:162` (`{row.label}`). Facet labels: event-type facets use the registry; entity-type facets use `titleCase(entity_type)` in `DatabaseAuditService.facets` (`B/audit/DatabaseAuditService.java:519-523`) → "Project", "Customer", "Legal Disbursement".
- CONFIRMED Entity column prints the raw literal: `EntityCell` (`F/components/audit/entity-cell.tsx:23-25,73`) renders `{entityType} <shortId>` / `entityType:shortId` → "project 3f2a…", "customer …", "invoice …" (G-D6 last sentence). It already has a per-type route map (`:3-14`) — the natural home for a label map.
- CONFIRMED No frontend label map exists for audit entity/event types (grep for `Customer Lifecycle|Project Updated|ENTITY_TYPE_LABELS` hits only unrelated components: `SyncLogTable.tsx:37`, `prerequisite-violation-list.tsx:32`, `custom-fields-content.tsx:37`).

**All registry label strings still saying Project/Customer/Invoice:** only `"Invoice"` (`:110`). Everything else "Project …"/"Customer …" is synthesised by `titleCase` from the pinned keys — there is no string to edit; the fix is either (a) register exact legal labels for the `project.*`, `customer.*`, `invoice.*`, `trust_*`, `checklist.*`, `disbursement.*`, `budget.*`, `acceptance.*` families (registry is the ADR-261 catalogue, labels are explicitly derived at read time so historical rows relabel automatically), or (b) a `legalise()` step in `titleCase`/`defaultFor` that maps the first segment `project→Matter`, `customer→Client`, `invoice→Fee Note`, `legal_disbursement→Disbursement`, `project_member→Matter Member`, `project_budget→Matter Budget`. (a) is the registry's design intent; (b) is one function and covers all 274 keys. Both leave stored keys untouched (ADR-342-safe). `AuditEventTypeRegistryTest` / `AuditEventMetadataEndpointIntegrationTest` pin some labels — check before changing `"Invoice"`.

---

## Q4 — Audit trail filter UI and backend API (G-D6)

- CONFIRMED `F/app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx`:
  - `FilterTextInput` (`:74-110`) is a plain `<Input type="text">` committing on **`onBlur` only** (`:103-108`); no `onKeyDown`/Enter handler.
  - Actor filter = "Actor ID", placeholder "UUID" (`:480-486`); entity type = free text, placeholder "e.g. customer" (`:496-502`), passed verbatim via `setParam("entityType", v)`; event type free text (`:488-494`); entity ID (`:504-510`).
  - Vitest pin: `F/__tests__/settings/audit-trail.test.tsx:353-362` uses `getByLabelText("Actor ID")`, `fireEvent.change/blur`, expects `actorId=actor-xyz` in the pushed URL — must be updated if the input becomes a picker.
- CONFIRMED Backend `GET /api/audit-events` (`B/audit/AuditEventController.java:45-63`) accepts `entityType` (exact literal), `entityId` (UUID), `actorId` (UUID), `eventType` (**prefix** match via `LIKE :prefix%`, `AuditEventRepository.java:38`), `from`, `to` (Instant), `severities` (Set), `page`, `size≤200`. Export CSV/PDF take the same filter (`:65-91`). By-entity route `/api/audit-events/{entityType}/{entityId}` (`:99-115`). So typing "matter" sends `entityType=matter` → 0 rows (G-D6 screens 85.3a). `eventType=matter_closure.closed` works because it's a literal prefix.
- CONFIRMED Actor picker data already exists server-side: `GET /api/audit-events/facets/actors` → `ActorFacet(actorId, actorDisplayName, actorType, eventCount)` (`AuditEventController.java:117-123`, `DatabaseAuditService.facets:483-503` with "Former member (uuid)" fallback); `facets/entity-types` → `EntityTypeFacet(entityType, label, count)`; `facets/event-types` → `EventTypeFacet(eventType, label, severity, group, count)`. Frontend clients exist (`F/lib/api/audit-events.ts:171-192` `listFacetActors/EventTypes/EntityTypes`) and are imported by `dashboard/page.tsx`, but **not** by `audit-log-client.tsx`. There is also `GET /api/members` (`B/member/OrgMemberController.java:19,16-17`, used by `F/lib/dashboard-home-fetch.ts:93`) if a full member list is preferred over "actors with events in range".
- CONFIRMED Severity/preset selects already exist in the same component (`:365-391`, `:515`), so Select-based filters are an established pattern there.

**Complete entity_type literal inventory written by the backend** (68 distinct, from `.entityType("…")` across `src/main/java`; count = emit sites):
invoice 22 · org_settings 14 · task 11 · document_template 11 · document 11 · trust_transaction 10 · project 10 · customer 10 · legal_disbursement 9 · acceptance_request 9 · time_entry 7 · org_integration 7 · information_request 7 · field_group 6 · data_subject_request 6 · checklist_instance_item 6 · ai_execution_gate 6 · tax_rate 5 · project_template 5 · generated_document 5 · expense 5 · court_date 5 · comment 5 · clause 5 · billing_run 5 · task_item 4 · tariff_schedule 4 · project_member 4 · interest_run 4 · field_definition 4 · checklist_template 4 · trust_reconciliation 3 · trust_investment 3 · trust_account 3 · tariff_item 3 · tag 3 · saved_view 3 · request_item 3 · prescription_tracker 3 · pack_install 3 · org_role 3 · member 3 · cost_rate 3 · collection_activity 3 · billing_rate 3 · bank_statement_line 3 · adverse_party 3 · security 2 · retention_policy 2 · project_budget 2 · pending_invitation 2 · mcp_tool 2 · conflict_check 2 · checklist_instance 2 · bank_statement 2 · audit_export 2 · adverse_party_link 2 · accounting_xero_connection 2 · subscription 1 · mcp_session 1 · matter_closure 1 · lpff_rate 1 · email_delivery_log 1 · email 1 · correspondence 1 · compliance_audit_report 1 · compliance_audit_finding 1. Plus 3 computed sites: `AiExecutionPersistenceService.java:208,226` (`execution.getEntityType()`), `CashDigestService.java:439` (`AUDIT_ENTITY_TYPE` constant = collection_activity).

**Proposed legal-word → literal map (frontend, single module, e.g. `F/components/audit/entity-type-labels.ts`, consumed by the Entity-type Select, `EntityCell`, and the facet label):**

| Legal label | literal |
|---|---|
| Matter | `project` |
| Client | `customer` |
| Fee note | `invoice` |
| Fee run | `billing_run` |
| Disbursement | `legal_disbursement` |
| Expense | `expense` |
| Matter budget | `project_budget` |
| Matter team member | `project_member` |
| Matter template | `project_template` |
| Matter closure | `matter_closure` |
| Engagement letter / acceptance | `acceptance_request` |
| Trust transaction | `trust_transaction` |
| Trust account | `trust_account` |
| Trust investment | `trust_investment` |
| Trust reconciliation | `trust_reconciliation` |
| Bank statement / line | `bank_statement`, `bank_statement_line` |
| Interest run | `interest_run` |
| LPFF rate | `lpff_rate` |
| Task / Task item | `task`, `task_item` |
| Time entry | `time_entry` |
| Document / Generated document / Document template / Clause | `document`, `generated_document`, `document_template`, `clause` |
| Comment | `comment` |
| Information request / item | `information_request`, `request_item` |
| FICA checklist / item / template | `checklist_instance`, `checklist_instance_item`, `checklist_template` |
| Court date / Prescription tracker | `court_date`, `prescription_tracker` |
| Adverse party / link / Conflict check | `adverse_party`, `adverse_party_link`, `conflict_check` |
| Tariff schedule / item; Billing rate; Cost rate; Tax rate | `tariff_schedule`, `tariff_item`, `billing_rate`, `cost_rate`, `tax_rate` |
| Collections activity | `collection_activity` |
| Correspondence / Email | `correspondence`, `email`, `email_delivery_log` |
| Member / Invitation / Role | `member`, `pending_invitation`, `org_role` |
| Firm settings / Integration / Xero connection / Subscription | `org_settings`, `org_integration`, `accounting_xero_connection`, `subscription` |
| Tag / Saved view / Custom field / Field group / Pack | `tag`, `saved_view`, `field_definition`, `field_group`, `pack_install` |
| Data subject request / Retention policy / Audit export / Security | `data_subject_request`, `retention_policy`, `audit_export`, `security` |
| AI gate / MCP tool / MCP session | `ai_execution_gate`, `mcp_tool`, `mcp_session` |
| Compliance report / finding | `compliance_audit_report`, `compliance_audit_finding` |

Minimum viable: Select populated from `listFacetEntityTypes` (literal as value, label from the map with `titleCase` fallback). Full map above makes the EntityCell column legal-worded too. Keep the raw text input as an "advanced" escape hatch or drop it; either way add Enter-to-commit.

**Root cause (Q4):** Epic 506A built free-text inputs against stored keys; facet endpoints (502A/507A) were added later but never wired into the filter bar; no legal label layer for entity literals exists because ADR-338 deleted `t()` and nobody wrote the per-component words for the audit surface.

---

## Q5 — G-D8: closure override justification split

- CONFIRMED `B/verticals/legal/closure/MatterClosureService.java:266-278` emits `matter_closure.closed` with `entity_type="project"`, `entity_id=projectId`, details `{reason, override_used, closure_log_id, project_id}` — **no justification**.
- CONFIRMED `:285-298` additionally emits `matter.closure.override_used` (only when `req.override()`), `entity_type="matter_closure"`, `entity_id=closureLogId`, details `{project_id, justification, reason}`; registry classifies it CRITICAL/COMPLIANCE (`AuditEventTypeRegistry.java:50-54`). The comment at `:282-285` says the closed event is "preserved unchanged for portal compatibility (PortalActivityEventTypes whitelist + ~11 dependent tests)".
- CONFIRMED The justification is persisted on `MatterClosureLog.override_justification` (`MatterClosureLog.java:65-66`, DB CHECK ≥ 20 chars `:88`).
- CONFIRMED Portal exposure: `matter_closure.closed` is on the portal whitelist (`B/portal/PortalActivityEventTypes.java:42`), but `PortalActivityEventResponse` (`:18-27`) exposes only id/eventType/actorType/actorName/entityId/entityType/matterId/summary/occurredAt — **details are not sent to the portal**, so adding `justification` to the closed event's details would not leak to clients via the portal activity API. It would appear in the firm audit trail details viewer, CSV/PDF exports and the matter Activity › Audit tab (TEAM_OVERSIGHT-gated) — which is what the scenario step asks for.
- CONFIRMED One-line change: add `"justification", Objects.requireNonNullElse(req.overrideJustification(), "")` to the `Map.of` at `:273-278` (Map.of forbids null; non-override path has null justification → `""`, or use a LinkedHashMap and only put when override). Test pin: `MatterClosureServiceIntegrationTest.java:823` uses `containsEntry("override_used", true)` (not `containsExactly`), so it does not break; the `~11 dependent tests` referenced are portal whitelist tests keyed on event type, unaffected. `ActivityMessageFormatter:149` template "closed the matter" ignores details.
- Alternative (no code): amend the scenario step to filter `entityType=matter_closure` / event `matter.closure.override_used`. That is a product/scenario decision (Quality Gate 6) — flagged in Product questions.

---

## Q6 — G-B13: "Document accepted" automation fires on plain upload

- CONFIRMED Rule: `backend/src/main/resources/automation-templates/common.json:105-125` slug `document-review-notification`, `triggerType: "DOCUMENT_ACCEPTED"`, `conditions: []`, action SEND_NOTIFICATION to `PROJECT_OWNER`, title `"Document accepted: {{document.name}}"`. No `defaultEnabled:false` on this rule (only line 10, a different rule), so `AutomationTemplateDefinition.seedEnabled()` (`:35-37`) returns true and the seeder creates it **enabled** for every tenant (`AutomationTemplateSeeder.java:83-100`).
- CONFIRMED Trigger mapping: `B/automation/TriggerTypeMapping.java:35-37` — `Map.entry(DocumentUploadedEvent.class, TriggerType.DOCUMENT_ACCEPTED)` with the comment "Document upload is treated as acceptance in this domain — there is no separate approval step". `AutomationContext.java:63,278-283` builds the `document` context from `DocumentUploadedEvent`. There is **no** `DocumentAccepted*` domain event class anywhere (`B/event/` grep). `DocumentUploadedEvent` is published for every project-scoped upload at `B/document/DocumentService.java:400-414` (event type string `document.uploaded`).
- CONFIRMED Pinned by tests: `AutomationEventListenerTest.java:608-623` (`DocumentUploadedEvent -> DOCUMENT_ACCEPTED`), `AutomationEntityTest.java:254,265`.
- CONFIRMED Why Thandi **and** Bob: `SendNotificationActionExecutor.java:99-112` resolves PROJECT_OWNER as the first member with `Roles.PROJECT_LEAD` ("lead"); template-instantiated matters store `"LEAD"` (G-D11/G-D13 case mismatch), so `leads.isEmpty()` → fallback to all org admins/owners (`:109-111`). Same root as G-D11 a.
- Genuine "acceptance" events that exist and are not wired to automation: `portal.document.acknowledged` (portal), `information_request.item_accepted` (firm), `acceptance.accepted` (engagement letter) — these publish Spring events (`AcceptanceRequestSentEvent`, `InformationRequestCompletedEvent`) but only `InformationRequestCompletedEvent` is in `TriggerTypeMapping`.

**Root cause (Q6):** a deliberate shortcut (upload ≡ accepted) baked into the trigger map plus a default-enabled template whose copy says "accepted". Fix options: (1) rename rule copy to "Document uploaded" and TriggerType display name (honest, zero behaviour change); (2) retarget `DOCUMENT_ACCEPTED` to a real acceptance event (`portal.document.acknowledged` / `information_request.item_accepted`) and add a separate `DOCUMENT_UPLOADED` trigger — product decision; (3) seed the rule `defaultEnabled:false` like `task-completion-chain` (LZKC-013 precedent).

---

## Q7 — Blast radius / tests to run

Backend (`backend/src/test/java/io/b2mash/b2b/b2bstrawman/…`):
- Activity: `activity/ActivityMessageFormatterTest.java` (pins raw fallback at :250-255; `doesNotContain("performed")` :561,577,593), `activity/ActivityServiceIntegrationTest.java` (seeds `project_id` in details :98-155; `crossProjectFilteringReturnsCorrectResults` :243), `activity/ActivityControllerTest.java`.
- Audit: `audit/AuditEventTypeRegistryTest.java`, `audit/AuditEventMetadataEndpointIntegrationTest.java` (label pins), `audit/AuditEventControllerTest.java`, `audit/AuditEventControllerFacetsIntegrationTest.java`, `audit/AuditServiceFacetsIntegrationTest.java`, `audit/AuditServiceIntegrationTest.java`, `audit/AuditService{,Controller}SeverityFilterIntegrationTest.java`, `audit/AuditServiceActorDisplayIntegrationTest.java`, `audit/EntityTypeLiteralStabilityTest.java` (source scan — any new `"project"`/`"customer"` call site must carry `// ADR-342: storage key, do not rename`), `audit/AuditTenantIsolationTest.java`, `audit/export/AuditPdfExporterIntegrationTest.java`, `audit/AuditExportReflexiveAuditEventTest.java`.
- Closure: `verticals/legal/closure/MatterClosureServiceIntegrationTest.java` (:812-847 override assertions), `MatterClosureControllerIntegrationTest`, `MatterClosureEmailIntegrationTest`, `MatterClosureLogRepositoryTest`, `closure/event/MatterClosureNotificationHandlerTest`.
- Portal feed (shares `details->>'project_id'`): `portal/PortalActivityControllerIntegrationTest.java`, `portal/PortalActivityServiceFirmFilterTest.java`.
- Dashboard (if `buildActivityDescription` changes): `dashboard/*DashboardService*Test` (grep) + `F/__tests__/dashboard/recent-activity-widget.test.tsx`.
- Automation: `automation/AutomationEventListenerTest.java` (:608-623), `AutomationEntityTest.java`, `AutomationEndToEndTest.java`, `automation/template/AutomationTemplateSeederTest.java`, `AutomationTemplateControllerTest.java`, `packs/LegalZaAutomationPackTest.java`, `notification/NotificationServiceIntegrationTest.java`, `event/EventPublicationTest.java`.
- Trust/invoice/acceptance publishers (if `project_id` is added to details): `verticals/legal/trustaccounting/**`, `invoice/InvoiceTransitionService*Test`, `acceptance/*Test` — `Map.of` details are rarely asserted exactly, but run the packages.
- Merge bar: full `./mvnw verify` via `backend/scripts/verify.sh` regardless.

Frontend (`frontend/`):
- `__tests__/settings/audit-trail.test.tsx` (:353 "Actor ID" label + blur commit), `__tests__/activity/activity-feed.test.tsx`, `__tests__/customers/customer-tabs-audit-capability.test.tsx`, `components/audit/__tests__/{audit-timeline,audit-timeline-tab,actor-display,audit-details-viewer,presets,severity-pill,export-dropdown}.test.tsx`, `__tests__/dashboard/{home-matrix,recent-activity-widget}.test.tsx`. Full `pnpm lint && pnpm build && pnpm test` + `format:check`.
- Playwright (UI change → suite run per lesson): `e2e/tests/audit-log/audit-log-smoke.spec.ts`, `e2e/tests/audit-log/matter-closure-audit-tab.spec.ts`, `e2e/tests/admin-audit-30day/admin-audit-lifecycle.spec.ts`, `e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts`, `e2e/tests/projects/project-crud.spec.ts`; portal `portal/app/(authenticated)/activity/__tests__/page.test.tsx`, `portal/e2e/tests/portal-client-90day/day-85-90.spec.ts`.

---

## Root cause statements

1. **Entity scoping (G-B8 acceptance, G-D9 trust/invoice, G-B2 checklist):** the matter feed correlates solely on `details->>'project_id'` (`AuditEventRepository.java:56-77`), and `acceptance.*`, `trust_deposit/payment/transfer.recorded`, `trust_*.approved`, `invoice.created/approved` never write that key even though the entities know their matter (`TrustTransaction.project_id`, invoice lines, acceptance→generated_document→document→project). The client tab uses the strict by-entity endpoint with no child-type join; checklist events only carry the client in `details.customerId` (instance) or not at all (item).
2. **Formatter coverage (G-B8, G-D9, G-D10):** `ActivityMessageFormatter:153` raw fallback for ~40 reachable keys (project.*, budget.*, disbursement.*, invoice.sent/paid, expense.*, court_date.*, …) and a second, cruder formatter in `DashboardService:920-938` for Home.
3. **Labels (G-D10, G-B8 audit sub-tab, G-B2):** `AuditEventTypeRegistry.titleCase` synthesises "Project …"/"Customer …" from pinned keys; only `"Invoice"` (:110) is a literal label; `EntityCell` prints raw literals.
4. **Filter UI (G-D6):** free-text inputs (506A) against storage keys, commit on blur; facet endpoints and `/api/members` exist but are unused by the filter bar; no legal label map for entity literals.
5. **Closure (G-D8):** justification deliberately lives only on the additive CRITICAL event (508A) and the closure log; adding it to `matter_closure.closed` details is one Map entry; portal does not expose details.
6. **Automation (G-B13):** `DocumentUploadedEvent → DOCUMENT_ACCEPTED` by design (`TriggerTypeMapping.java:35-37`) + default-enabled template with "accepted" copy; double recipient via the LEAD/lead role mismatch fallback.

## Recommended fix shapes (ordered by leverage)

A. **Backend, publishers:** add `"project_id"` to details for `acceptance.*` (resolve via generated document → document.project_id or customer's matter; HYPOTHESIS on the exact join, `GeneratedDocument.document_id` `:36`), `trust_deposit/payment/transfer.recorded`, `trust_*.approved/rejected/first_approved`, `trust_transaction.reversed` (from `transaction.getProjectId()`), `invoice.created/approved/voided` (from invoice lines' `project_id`). Keep entity_type literals untouched. This alone fixes the omissions in G-B8/G-D9 for new events; historical rows stay missing (option: also OR `entity_type='project' AND entity_id=:projectId` into `findByProjectId`, mirroring `findMostRecentByProject:97-98`).
B. **Backend, client tab:** either a dedicated `/api/clients/{id}/activity` reusing the `streamForCustomer` three-branch shape (`AuditEventRepository.java:523-548`) with checklist child ids, or add `customer_id` to checklist item/instance details and extend `findByFilter` with a `details->>'customer_id'` branch when `entityType='customer'`.
C. **Backend, formatter:** templates per table in Q2 + legal verb/noun generic default; make `DashboardService.buildActivityDescription` delegate.
D. **Backend, registry:** legal-word `titleCase` (project→Matter, customer→Client, invoice→Fee Note, legal_disbursement→Disbursement) or explicit entries; fix `"Invoice"` → `"Fee note"` at `:110`.
E. **Frontend, filter bar:** Actor Select from `listFacetActors` (fallback `/api/members`), Entity-type Select from `listFacetEntityTypes` + label map, Event-type Select/combobox from `listFacetEventTypes`, Enter-to-commit; `EntityCell` label map. Update `audit-trail.test.tsx:353`.
F. **Closure:** one Map entry at `MatterClosureService.java:273-278` (if authorised) — else scenario amendment.
G. **Automation:** rename copy to "Document uploaded" (+ `defaultEnabled:false` optional); real acceptance trigger is a separate product decision. The double-recipient symptom is fixed by the G-D11 LEAD/lead fix.

## Related gaps
- G-D11 a / G-D13 (LEAD vs lead) → explains G-B13's two recipients (`SendNotificationActionExecutor.java:104-111`).
- G-C5 / portal links: `PortalActivityEventResponse` depends on the same `details->>'project_id'`; fix A improves the portal Firm-actions trail too (`MatterClosureService.java:262-266` comment).
- Aside (not in scope, HYPOTHESIS): portal whitelist has `"trust_transaction.approved"` (`PortalActivityEventTypes.java:34`) but the backend emits `trust_<type>.approved` (`TrustTransactionService.java:896`) — the portal Firm tab may never show approvals; worth a separate look.
- G-D10 copy clusters: Home activity verbs live in `DashboardService:920-938`, not the formatter.

## Product questions (need authorisation — Quality Gate 6/7)
1. G-D8: copy `justification` into `matter_closure.closed` details (firm-visible only; portal unaffected) **or** amend step 85.17 to filter on `matter.closure.override_used`?
2. G-B13: is "upload = accepted" the intended semantic (then rename the rule copy) or should DOCUMENT_ACCEPTED bind to a real acceptance (`portal.document.acknowledged` / `information_request.item_accepted`)? Should `document-review-notification` seed disabled?
3. Audit labels: change the registry label `"Invoice"` → `"Fee note"` and legal-case the synthesised labels (affects CSV/PDF exports that compliance may have archived)?
4. Should the matter feed backfill history by also matching `entity_type='project' AND entity_id` (changes which rows appear for existing tenants)?
5. Which actor source for the picker: audit actor facets (actors with events in range, incl. Portal Contact/System) or the full `/api/members` list?
6. Client Activity tab: should it show FICA checklist events (child join) or is "Firm › Audit trail" the intended home (scenario line 241 says the client view)?

## Tests to run (summary)
Backend full `./mvnw verify` (via `backend/scripts/verify.sh`) — targeted inner loop: `activity/*`, `audit/*`, `verticals/legal/closure/*`, `portal/PortalActivity*`, `automation/*`, `dashboard/*`, plus any publisher package touched. Frontend `pnpm lint && pnpm build && pnpm test && pnpm format:check`; Playwright `audit-log/*`, `admin-audit-30day/*`, `legal-depth-ii/day-75-85-closure`, `projects/project-crud`; portal unit + `portal-client-90day/day-85-90`.
