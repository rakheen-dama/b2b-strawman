# Fix Spec: GAP-P49-002 — Statement of Account invoice table empty

## Problem
The Statement of Account template (CUSTOMER-scoped, slug `statement-of-account`) renders an empty invoice history table despite Naledi Corp QA having an approved invoice (INV-0001, R8,050). The `loopTable` with `dataSource="invoices"` renders headers correctly but shows zero data rows. `totalOutstanding` is "0", confirming the `invoices` list in the context was empty when the template was rendered.

The `CustomerContextBuilder.buildContext()` calls `invoiceRepository.findByCustomerId(entityId)` (line 96) which uses the JPQL query `SELECT i FROM Invoice i WHERE i.customerId = :customerId ORDER BY i.createdAt DESC`. The SA Tax Invoice template (INVOICE-scoped, T1.5) rendered INV-0001 correctly, proving the invoice exists in the database.

## Root Cause (hypothesis)
Two hypotheses:

**H1 (most likely): The Invoice entity's `customerId` field does not match the Customer entity's `id`.** The `CustomerContextBuilder` receives `entityId` = the Customer UUID. The query `WHERE i.customerId = :customerId` compares against `Invoice.customerId`. If the invoice was created with a different customer ID (e.g., during creation via the API, a different customer UUID was used), the query returns empty.

Evidence supporting H1: The QA agent created the invoice via API with `request.customerId()` pointing to Naledi. If the API used a UUID from a different source (e.g., customerProject ID instead of customer ID), the IDs would not match.

**H2: Hibernate search_path issue.** The query runs within the tenant schema via `search_path`. If there's a caching or session issue causing the query to run against the wrong schema, it would return empty. This is unlikely given that `customer.name` resolves correctly in the same render.

**H3: The `loopTable` data source resolution fails.** The `TiptapRenderer.resolveDataSource("invoices", context)` looks up `context.get("invoices")`. If the builder returned a non-List type or null, the table would be empty. But the builder explicitly creates an `ArrayList` and puts it at `context.put("invoices", invoicesList)`.

## Fix
1. **Add debug logging** in `CustomerContextBuilder.buildContext()` after the invoice loop (after line 128):
   ```java
   log.debug("Customer {} invoice context: invoices.size={}, totalOutstanding={}",
       entityId, invoicesList.size(), totalOutstanding);
   ```
   - File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`

2. **Verify at runtime**: Query the invoice's `customer_id` and compare with the customer's `id`:
   ```sql
   SELECT id, customer_id, invoice_number, status FROM invoices WHERE invoice_number = 'INV-0001';
   SELECT id, name FROM customers WHERE name LIKE 'Naledi%';
   ```
   If the UUIDs don't match, the fix is to recreate the invoice with the correct customer ID, or update the invoice's `customer_id`.

3. **If UUIDs match but query still returns empty**, check if the JPQL `@Query` with `@Param` has a parameter binding issue. Consider replacing with Spring Data derived query method:
   ```java
   List<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
   ```
   File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java`

4. **Also register `invoices` as a loop source for CUSTOMER templates** in `VariableMetadataRegistry.registerCustomerVariables()` (currently missing — the loop sources only list `projects` and `tags`). This is separate from the empty table bug but is needed for the frontend variable picker to show invoice columns as available:
   ```java
   loopSourcesByType.put(
       TemplateEntityType.CUSTOMER,
       List.of(
           new LoopSource("projects", "Projects", List.of("CUSTOMER"), List.of("id", "name")),
           new LoopSource("invoices", "Invoices", List.of("CUSTOMER"),
               List.of("invoiceNumber", "issueDate", "dueDate", "total", "currency", "status", "runningBalance")),
           new LoopSource("tags", "Tags", List.of("PROJECT", "CUSTOMER"), List.of("name", "color"))));
   ```
   File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` (line ~173)

## Scope
Backend
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java` (debug logging)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` (add invoices loop source)
- Possibly `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java` (if JPQL issue)
Migration needed: no

## Verification
- Query database to compare invoice.customer_id with customer.id
- If data mismatch: fix data, regenerate statement, verify invoice rows appear
- If code fix applied: re-run Track T1.6 (Statement of Account variable fidelity)
- Verify `totalOutstanding` shows correct value (R8,050 for a single approved invoice)

## Estimated Effort
M (30 min - 1 hr) — investigation + possible fix of `VariableMetadataRegistry` + data verification
