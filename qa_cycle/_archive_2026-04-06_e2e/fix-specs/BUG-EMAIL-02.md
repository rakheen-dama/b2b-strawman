# Fix Spec: BUG-EMAIL-02 — AutomationContext missing event detail fields for template variable resolution

## Problem

Three automation template variables advertised in the frontend UI resolve to `null` at runtime, causing `{{variable}}` placeholders to remain unresolved in email bodies and notification text:

1. `{{project.name}}` is null on task completed events
2. `{{invoice.totalAmount}}` is null on all invoice status change events
3. `{{customer.name}}` is null on task completed events

Users who create automation rules with "Send Email" actions using these variables receive emails with raw `{{project.name}}` text instead of the actual values.

## Root Cause (confirmed)

Three independent gaps between what `AutomationContext` tries to read from event details and what the event publishers actually put into the details map:

### Gap 1: `{{project.name}}` null on TaskCompletedEvent

`AutomationContext.buildTaskCompleted()` (line 122) reads:
```java
project.put("name", detailValue(event, "project_name"));
```

But `TaskService` (line 749) publishes `TaskCompletedEvent` with details:
```java
Map.of("title", task.getTitle())
```

The details map contains only `"title"` -- no `"project_name"` key. The context builder looks for `"project_name"` and gets null.

For comparison, `TaskStatusChangedEvent` has the same problem -- its details map (line 506-512) contains `"title"`, `"old_status"`, `"new_status"` but no `"project_name"` or `"customer_name"`.

### Gap 2: `{{invoice.totalAmount}}` null on all invoice events

`AutomationContext.buildInvoiceStatusChanged()` (lines 187-210) never adds a `totalAmount` field to the invoice context map. The invoice map only contains: `id`, `invoiceNumber`, `status`, `customerId`.

The frontend `variable-inserter.tsx` (line 44) and `condition-builder.tsx` (line 58) both expose `invoice.totalAmount` as an available variable, but the context builder never populates it.

The total is available on the `Invoice` entity (`invoice.getTotal()`) and is already included in some invoice event details maps (e.g., `InvoicePaidEvent` details include `"total"` at `InvoiceService` line 1095), but `AutomationContext` doesn't read it from either the details map or the event record fields.

### Gap 3: `{{customer.name}}` null on TaskCompletedEvent

Same root cause as Gap 1. `AutomationContext.buildTaskCompleted()` (line 127) reads:
```java
customer.put("name", detailValue(event, "customer_name"));
```

But the `TaskCompletedEvent` details map only has `"title"`. No `"customer_name"` key exists.

The `TaskStatusChangedEvent` has the same gap -- its details also lack `"customer_name"` and `"customer_id"`.

## Fix

### Approach: Enrich event details at publish time

The `AutomationContext` Javadoc states: "All data is extracted from the DomainEvent -- no additional database queries are performed." This is the correct design -- the context builder should not reach back to the database. Instead, the event publishers must include all fields that downstream consumers need.

### Change 1: Enrich TaskCompletedEvent details in TaskService

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` (~line 749)

Current:
```java
Map.of("title", task.getTitle())
```

Add project name and customer info. The project name requires a lookup (project is not loaded in `completeTask`). Customer info requires looking up the customer linked to the project via `CustomerProjectRepository`.

```java
// Look up project name
var project = projectRepository.findById(task.getProjectId()).orElse(null);
String projectName = project != null ? project.getName() : null;

// Look up customer name (if project is customer-linked)
String customerName = null;
String customerId = null;
if (project != null) {
    var customerProject = customerProjectRepository.findByProjectId(project.getId()).orElse(null);
    if (customerProject != null) {
        customerName = customerProject.getCustomer().getName();
        customerId = customerProject.getCustomer().getId().toString();
    }
}

// Build enriched details map
var details = new LinkedHashMap<String, Object>();
details.put("title", task.getTitle());
if (projectName != null) details.put("project_name", projectName);
if (customerName != null) details.put("customer_name", customerName);
if (customerId != null) details.put("customer_id", customerId);
```

### Change 2: Enrich TaskStatusChangedEvent details in TaskService

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` (~line 506)

Same enrichment as Change 1: add `project_name`, `customer_name`, `customer_id` to the details map of `TaskStatusChangedEvent`.

### Change 3: Add totalAmount to AutomationContext for invoice events

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java` (~line 193)

The invoice events already carry `total` in their details map (for `InvoicePaidEvent` at InvoiceService line 1095). For `InvoiceSentEvent` and `InvoiceVoidedEvent`, the total is not in the details but should be added at publish time.

**Option A (preferred):** Add `total` to all invoice event details maps at publish time, then read it in AutomationContext:

In `InvoiceService`, for `InvoiceSentEvent` details (~line 980):
```java
Map.of(
    "invoice_number", invoice.getInvoiceNumber(),
    "customer_name", invoice.getCustomerName(),
    "total", invoice.getTotal().toString())
```

For `InvoiceVoidedEvent` details (~line 1353), add `"total"`:
```java
Map.of(
    "invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
    "customer_name", invoice.getCustomerName(),
    "total", invoice.getTotal().toString())
```

Then in `AutomationContext.buildInvoiceStatusChanged()`, add after the status line:
```java
invoice.put("totalAmount", detailValue(event, "total"));
```

This maps the backend field name `"total"` to the frontend-expected context key `"totalAmount"`.

## Scope

- `TaskService.java` -- enrich details maps for `TaskCompletedEvent` (1 location) and `TaskStatusChangedEvent` (1 location)
- `InvoiceService.java` -- add `"total"` to details maps for `InvoiceSentEvent` and `InvoiceVoidedEvent` (2 locations; `InvoicePaidEvent` already has it)
- `AutomationContext.java` -- add `invoice.put("totalAmount", ...)` in `buildInvoiceStatusChanged()` (1 line)
- May need to inject `ProjectRepository` into the `completeTask` flow if not already available (check existing dependencies)
- No frontend changes needed
- No database migration needed

## Verification

1. Create automation rule: trigger = "Task Status Changed" (to Done), action = "Send Email" with body containing `{{project.name}}` and `{{customer.name}}`
2. Complete a task on a customer-linked project -- email should contain resolved project and customer names
3. Create automation rule: trigger = "Invoice Status Changed" (to Paid), action = "Send Email" with body containing `{{invoice.totalAmount}}`
4. Mark an invoice as paid -- email should contain the formatted total amount
5. Verify `VariableResolverTest` still passes
6. Add new unit tests for `AutomationContext.buildTaskCompleted()` verifying project.name and customer.name are populated
7. Add unit test for `AutomationContext.buildInvoiceStatusChanged()` verifying totalAmount is populated

## Estimated Effort

Medium -- ~6 files touched, ~30 lines of production code. Requires careful testing of the enriched event details across all task and invoice event paths. ~1-2 hours including tests.
