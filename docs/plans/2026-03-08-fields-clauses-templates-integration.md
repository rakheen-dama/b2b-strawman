# Fields–Clauses–Templates Integration Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Resolve all 9 gaps (GAP-1 through GAP-9) and 5 UX gaps (UX-1 through UX-5) identified in `architecture/findings-fields-clauses-templates-gaps.md`, making custom fields fully discoverable, clause authoring usable, previews accurate, and rendered documents professional.

**Architecture:** The core fix is making `VariableMetadataRegistry` dynamic (querying `FieldDefinition` records per-tenant) instead of static. This unblocks the variable picker for custom fields (GAP-1), enables the clause editor variable picker (GAP-2), and provides type metadata for value formatting (GAP-4). Remaining gaps are UI fixes (GAP-3, GAP-6, GAP-9), a new field pack (GAP-5), and feature additions (GAP-7, GAP-8, UX-1 through UX-5).

**Tech Stack:** Spring Boot 4 / Java 25, Next.js 16 / React 19 / TypeScript, Tiptap editor, PostgreSQL JSONB, Vitest (frontend), JUnit 5 + Testcontainers (backend)

---

## Task 1: Dynamic Variable Metadata Registry (GAP-1 — P0)

**Problem:** `VariableMetadataRegistry` is entirely static/hardcoded. Custom fields are renderable via dot-path (`customer.customFields.tax_number`) but invisible in the variable picker because the metadata endpoint never queries `FieldDefinition` records.

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java`
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataEndpointTest.java`

**Step 1: Write the failing test**

Add a test to `VariableMetadataEndpointTest.java` that creates custom field definitions for a tenant, then asserts the `/api/templates/variables` endpoint includes them in the response.

```java
@Test
void getVariables_project_includesCustomFields() throws Exception {
    // Create a custom field definition for PROJECT entity type
    ScopedValue.where(RequestScopes.TENANT_ID, SchemaNameGenerator.toSchemaName(ORG_ID)).run(() -> {
        var field = new FieldDefinition(EntityType.PROJECT, "Tax Reference", "tax_reference", FieldType.TEXT);
        field.setDescription("Project tax reference number");
        fieldDefinitionRepository.save(field);
    });

    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[?(@.prefix == 'project.customFields')].variables[0].key")
            .value("project.customFields.tax_reference"))
        .andExpect(jsonPath("$.groups[?(@.prefix == 'project.customFields')].variables[0].label")
            .value("Tax Reference"))
        .andExpect(jsonPath("$.groups[?(@.prefix == 'project.customFields')].variables[0].type")
            .value("string"));
}
```

You'll need to `@Autowired FieldDefinitionRepository fieldDefinitionRepository;` in the test class.

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=VariableMetadataEndpointTest#getVariables_project_includesCustomFields -q 2>&1 | tail -20`
Expected: FAIL — the custom field group won't appear in the response.

**Step 3: Inject `FieldDefinitionRepository` into `VariableMetadataRegistry` and make `getVariables` dynamic**

Currently `VariableMetadataRegistry` is a `@Component` with a no-arg constructor that hardcodes everything. Modify it to:

1. Accept `FieldDefinitionRepository` via constructor injection
2. In `getVariables(TemplateEntityType entityType)`, after building the static groups, query `fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(mapEntityType(entityType))` to get tenant custom fields
3. Build a `VariableGroup` with label "Custom Fields ({EntityType})", prefix `"{entity}.customFields"`, and map each `FieldDefinition` to a `VariableInfo`:
   - key: `"{entity}.customFields.{slug}"` (e.g., `"project.customFields.tax_reference"`)
   - label: `field.getName()` (e.g., `"Tax Reference"`)
   - type: map `FieldType` to metadata type string (`TEXT→"string"`, `NUMBER→"number"`, `DATE→"date"`, `CURRENCY→"currency"`, `BOOLEAN→"boolean"`, `DROPDOWN→"string"`, `URL→"string"`, `EMAIL→"string"`, `PHONE→"string"`)
4. Append this group to the static groups list (only if non-empty)

**Entity type mapping helper** (inside VariableMetadataRegistry):
```java
private EntityType mapTemplateEntityType(TemplateEntityType type) {
    return switch (type) {
        case PROJECT -> EntityType.PROJECT;
        case CUSTOMER -> EntityType.CUSTOMER;
        case INVOICE -> EntityType.INVOICE;
    };
}

private String entityPrefix(TemplateEntityType type) {
    return switch (type) {
        case PROJECT -> "project";
        case CUSTOMER -> "customer";
        case INVOICE -> "invoice";
    };
}
```

**For PROJECT and INVOICE templates**, also include CUSTOMER custom fields (since customer context is always available in those templates). For INVOICE templates, also include PROJECT custom fields.

The logic for which custom field entity types to include per template entity type:
- `PROJECT` template → PROJECT fields + CUSTOMER fields
- `CUSTOMER` template → CUSTOMER fields only
- `INVOICE` template → INVOICE fields + CUSTOMER fields + PROJECT fields

**Step 4: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=VariableMetadataEndpointTest -q 2>&1 | tail -20`
Expected: ALL PASS (including existing tests — the static groups haven't changed, they just have extra groups appended)

**Step 5: Write additional edge-case tests**

```java
@Test
void getVariables_customer_includesCustomerCustomFields() throws Exception {
    // Create customer custom field
    ScopedValue.where(RequestScopes.TENANT_ID, SchemaNameGenerator.toSchemaName(ORG_ID)).run(() -> {
        var field = new FieldDefinition(EntityType.CUSTOMER, "VAT Number", "vat_number", FieldType.TEXT);
        fieldDefinitionRepository.save(field);
    });

    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[?(@.prefix == 'customer.customFields')].variables[0].key")
            .value("customer.customFields.vat_number"));
}

@Test
void getVariables_invoice_includesInvoiceAndCustomerCustomFields() throws Exception {
    // Invoice templates should show invoice, customer, and project custom fields
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Should have groups for invoice.customFields, customer.customFields, project.customFields
        .andExpect(jsonPath("$.groups[?(@.prefix == 'invoice.customFields')]").exists());
}

@Test
void getVariables_excludesInactiveFields() throws Exception {
    ScopedValue.where(RequestScopes.TENANT_ID, SchemaNameGenerator.toSchemaName(ORG_ID)).run(() -> {
        var field = new FieldDefinition(EntityType.PROJECT, "Inactive Field", "inactive_field", FieldType.TEXT);
        field.deactivate();
        fieldDefinitionRepository.save(field);
    });

    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[?(@.prefix == 'project.customFields')].variables[?(@.key == 'project.customFields.inactive_field')]")
            .doesNotExist());
}
```

**Step 6: Run all tests**

Run: `./mvnw test -pl backend -Dtest=VariableMetadataEndpointTest -q 2>&1 | tail -20`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java \
        backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataEndpointTest.java
git commit -m "feat: make variable metadata registry dynamic with custom fields (GAP-1)"
```

---

## Task 2: Variable Picker in Clause Editor (GAP-2 — P0)

**Problem:** The clause editor uses `scope="clause"` in `DocumentEditor.tsx`, which includes `VariableExtension` but the `EditorToolbar` conditionally hides the variable picker button when no `entityType` prop is provided. Since clauses are entity-type-agnostic, no `entityType` is passed.

**Files:**
- Modify: `frontend/components/editor/EditorToolbar.tsx` (line 186 — conditional visibility)
- Modify: `frontend/components/editor/VariablePicker.tsx` (needs multi-entity support)
- Modify: `frontend/components/editor/actions.ts` (new fetch function for all variables)
- Modify: `frontend/components/editor/DocumentEditor.tsx` (pass scope to toolbar)

**Step 1: Add a `fetchAllVariableMetadata` server action**

In `frontend/components/editor/actions.ts`, add a function that fetches variables for ALL entity types and merges them into a single response with de-duplicated groups:

```typescript
export async function fetchAllVariableMetadata(): Promise<VariableMetadataResponse> {
  const entityTypes: TemplateEntityType[] = ["PROJECT", "CUSTOMER", "INVOICE"];
  const responses = await Promise.all(
    entityTypes.map((et) => fetchVariableMetadata(et)),
  );

  // Merge groups, de-duplicating by prefix (keep first occurrence, merge variables)
  const groupMap = new Map<string, VariableGroup>();
  for (const response of responses) {
    for (const group of response.groups) {
      const existing = groupMap.get(group.prefix);
      if (existing) {
        // Merge variables, de-duplicate by key
        const existingKeys = new Set(existing.variables.map((v) => v.key));
        const newVars = group.variables.filter((v) => !existingKeys.has(v.key));
        groupMap.set(group.prefix, {
          ...existing,
          variables: [...existing.variables, ...newVars],
        });
      } else {
        groupMap.set(group.prefix, group);
      }
    }
  }

  return {
    groups: Array.from(groupMap.values()),
    loopSources: [], // Clauses don't use loop tables
  };
}
```

**Step 2: Update `VariablePicker` to support clause mode**

Modify `VariablePicker.tsx` props to accept an optional `entityType` (when undefined, fetches all):

```typescript
interface VariablePickerProps {
  entityType?: TemplateEntityType; // Optional — if omitted, shows all entity types
  onSelect: (key: string) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}
```

In the `useEffect`, change the fetch logic:
```typescript
useEffect(() => {
  if (open) {
    const fetchFn = entityType
      ? () => fetchVariableMetadata(entityType)
      : fetchAllVariableMetadata;
    fetchFn()
      .then(setMetadata)
      .catch(() => setMetadata(null));
  }
}, [entityType, open]);
```

**Step 3: Update `EditorToolbar` to show variable picker in clause scope**

Change the conditional at line 186 from:
```typescript
{entityType && (
```
to:
```typescript
{(entityType || scope === "clause") && (
```

Add `scope` to `EditorToolbarProps`:
```typescript
interface EditorToolbarProps {
  editor: Editor | null;
  entityType?: TemplateEntityType;
  scope?: "template" | "clause";
}
```

When `scope === "clause"`, render the `VariablePicker` without `entityType` (which triggers the all-entity fetch). Hide the clause picker button when in clause scope (clauses can't contain other clauses).

```typescript
{/* Variable picker — always available */}
<VariablePicker
  entityType={entityType}
  onSelect={handleVariableSelect}
  open={variablePickerOpen}
  onOpenChange={setVariablePickerOpen}
/>

{/* Clause picker — only in template scope */}
{scope !== "clause" && entityType && (
  <ClausePicker ... />
)}
```

**Step 4: Pass `scope` from `DocumentEditor` to `EditorToolbar`**

In `DocumentEditor.tsx`, pass the `scope` prop through to `EditorToolbar`:
```typescript
<EditorToolbar editor={editor} entityType={entityType} scope={scope} />
```

**Step 5: Run frontend tests**

Run: `cd frontend && pnpm test 2>&1 | tail -30`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add frontend/components/editor/EditorToolbar.tsx \
        frontend/components/editor/VariablePicker.tsx \
        frontend/components/editor/actions.ts \
        frontend/components/editor/DocumentEditor.tsx
git commit -m "feat: enable variable picker in clause editor with all-entity variables (GAP-2)"
```

---

## Task 3: Fix `extractTextFromBody` for Variable Nodes (GAP-3 — P1)

**Problem:** `extractTextFromBody()` in `tiptap-utils.ts` walks the Tiptap JSON tree and only extracts `text` nodes. Variable nodes (`type: "variable"`) are silently skipped, producing garbled preview text like "All invoices issued by  to  are payable..."

**Files:**
- Modify: `frontend/lib/tiptap-utils.ts` (lines 5-21)
- Create: `frontend/lib/__tests__/tiptap-utils.test.ts`

**Step 1: Write the failing test**

```typescript
import { describe, expect, it } from "vitest";
import { extractTextFromBody } from "../tiptap-utils";

describe("extractTextFromBody", () => {
  it("extracts text from simple paragraph", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello world" }],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe("Hello world");
  });

  it("renders variable nodes as {key} placeholders", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Issued by " },
            { type: "variable", attrs: { key: "org.name" } },
            { type: "text", text: " to " },
            { type: "variable", attrs: { key: "customer.name" } },
          ],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe(
      "Issued by {org.name} to {customer.name}",
    );
  });

  it("handles mixed text and variable nodes across paragraphs", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Project: " },
            { type: "variable", attrs: { key: "project.name" } },
          ],
        },
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Budget: " },
            { type: "variable", attrs: { key: "budget.amount" } },
          ],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe(
      "Project: {project.name}\nBudget: {budget.amount}",
    );
  });

  it("returns null for empty content", () => {
    expect(extractTextFromBody({ type: "doc", content: [] })).toBeNull();
  });

  it("returns null for no content key", () => {
    expect(extractTextFromBody({ type: "doc" })).toBeNull();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test -- tiptap-utils 2>&1 | tail -20`
Expected: FAIL — "renders variable nodes as {key} placeholders" fails because variables produce empty strings.

**Step 3: Fix `extractTextFromBody` to handle variable nodes**

In `tiptap-utils.ts`, change the child mapping logic (around line 14) from:
```typescript
return children.map((child) => (child.text as string) ?? "").join("");
```
to:
```typescript
return children
  .map((child) => {
    if (child.type === "variable") {
      const attrs = child.attrs as Record<string, unknown> | undefined;
      const key = attrs?.key as string | undefined;
      return key ? `{${key}}` : "";
    }
    return (child.text as string) ?? "";
  })
  .join("");
```

**Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm test -- tiptap-utils 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add frontend/lib/tiptap-utils.ts frontend/lib/__tests__/tiptap-utils.test.ts
git commit -m "fix: render variable nodes as {key} placeholders in text extraction (GAP-3)"
```

---

## Task 4: Value Formatting in Renderers (GAP-4 — P1)

**Problem:** `resolveVariable()` in both `TiptapRenderer.java` and `client-renderer.ts` performs raw `String.valueOf()` / `String()` with no type-aware formatting. Currency renders as `50000.00`, dates as ISO timestamps.

**Architecture Decision:** Infer formatting from `FieldDefinition.fieldType` via the variable metadata type hints already carried in `VariableInfo.type`. The renderer will use a format map built from the variable metadata rather than requiring format attrs on each variable node. This keeps the Tiptap schema unchanged.

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java` (lines 220-230)
- Modify: `frontend/components/editor/client-renderer.ts` (lines 79-92)
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` (expose type map)
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TiptapRendererTest.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableFormatter.java`

### Step 1: Write failing tests for backend formatting

Add to `TiptapRendererTest.java`:

```java
@Test
void variable_currency_value_formatted() {
    var doc = doc(
        Map.<String, Object>of(
            "type", "paragraph",
            "content", List.of(
                Map.<String, Object>of("type", "variable", "attrs", Map.of("key", "invoice.total")))));

    var context = Map.<String, Object>of(
        "invoice", Map.<String, Object>of("total", 50000.00));

    var formatHints = Map.of("invoice.total", "currency");
    String html = renderer.render(doc, context, Map.of(), null, formatHints);

    // Should contain formatted currency (at minimum, thousands separator)
    assertThat(html).containsPattern("50[,.]000");
}

@Test
void variable_date_value_formatted() {
    var doc = doc(
        Map.<String, Object>of(
            "type", "paragraph",
            "content", List.of(
                Map.<String, Object>of("type", "variable", "attrs", Map.of("key", "invoice.issueDate")))));

    var context = Map.<String, Object>of(
        "invoice", Map.<String, Object>of("issueDate", "2026-03-08"));

    var formatHints = Map.of("invoice.issueDate", "date");
    String html = renderer.render(doc, context, Map.of(), null, formatHints);

    // Should contain human-readable date format
    assertThat(html).containsAnyOf("8 March 2026", "March 8, 2026", "2026-03-08");
}
```

### Step 2: Create `VariableFormatter.java`

```java
package io.b2mash.b2b.b2bstrawman.template;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

public final class VariableFormatter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private VariableFormatter() {}

    public static String format(Object value, String typeHint) {
        if (value == null) return "";
        if (typeHint == null) return HtmlUtils.htmlEscape(String.valueOf(value));

        return switch (typeHint) {
            case "currency" -> formatCurrency(value);
            case "date" -> formatDate(value);
            case "number" -> formatNumber(value);
            default -> HtmlUtils.htmlEscape(String.valueOf(value));
        };
    }

    private static String formatCurrency(Object value) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            return HtmlUtils.htmlEscape(CURRENCY_FORMAT.format(amount));
        } catch (NumberFormatException e) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }
    }

    private static String formatDate(Object value) {
        try {
            String str = String.valueOf(value);
            if (str.contains("T")) {
                // ISO instant → extract date part
                Instant instant = Instant.parse(str);
                LocalDate date = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
                return date.format(DATE_FORMAT);
            } else {
                LocalDate date = LocalDate.parse(str);
                return date.format(DATE_FORMAT);
            }
        } catch (Exception e) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }
    }

    private static String formatNumber(Object value) {
        try {
            BigDecimal num = new BigDecimal(String.valueOf(value));
            return HtmlUtils.htmlEscape(NumberFormat.getInstance(Locale.US).format(num));
        } catch (NumberFormatException e) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }
    }
}
```

### Step 3: Update `TiptapRenderer.render()` to accept and use format hints

Add a `formatHints` parameter (`Map<String, String>` — variable key → type hint) to the `render` method. Update `resolveVariable` to accept the format hints map:

```java
private String resolveVariable(String key, Map<String, Object> context, Map<String, String> formatHints) {
    if (key == null || key.isBlank()) return "";
    String[] segments = key.split("\\.");
    Object current = context;
    for (String segment : segments) {
        if (!(current instanceof Map)) return "";
        current = ((Map<?, ?>) current).get(segment);
        if (current == null) return "";
    }
    String typeHint = formatHints != null ? formatHints.get(key) : null;
    return VariableFormatter.format(current, typeHint);
}
```

Keep the existing `render(doc, context, clauses, css)` signature as an overload that passes `Map.of()` for format hints (backward compatible):

```java
public String render(Map<String, Object> document, Map<String, Object> context,
                     Map<UUID, Clause> clauses, String templateCss) {
    return render(document, context, clauses, templateCss, Map.of());
}

public String render(Map<String, Object> document, Map<String, Object> context,
                     Map<UUID, Clause> clauses, String templateCss, Map<String, String> formatHints) {
    // ... existing logic, pass formatHints through to resolveVariable
}
```

### Step 4: Build format hints in `PdfRenderingService`

In `PdfRenderingService`, after getting the variable metadata from `VariableMetadataRegistry`, build a `Map<String, String>` from the `VariableInfo` records:

```java
Map<String, String> formatHints = variableMetadataRegistry.getVariables(template.getPrimaryEntityType())
    .groups().stream()
    .flatMap(g -> g.variables().stream())
    .filter(v -> v.type() != null && !v.type().equals("string"))
    .collect(Collectors.toMap(VariableInfo::key, VariableInfo::type, (a, b) -> a));
```

Pass this to `tiptapRenderer.render(content, context, clauseMap, css, formatHints)`.

### Step 5: Mirror formatting in `client-renderer.ts`

Add a `formatValue` function:

```typescript
function formatValue(value: unknown, typeHint?: string): string {
  if (value == null) return "";
  const str = String(value);
  if (!typeHint || typeHint === "string") return escapeHtml(str);

  switch (typeHint) {
    case "currency": {
      const num = Number(str);
      if (isNaN(num)) return escapeHtml(str);
      return escapeHtml(
        new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(num),
      );
    }
    case "date": {
      try {
        const date = new Date(str);
        if (isNaN(date.getTime())) return escapeHtml(str);
        return escapeHtml(
          date.toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" }),
        );
      } catch {
        return escapeHtml(str);
      }
    }
    case "number": {
      const num = Number(str);
      if (isNaN(num)) return escapeHtml(str);
      return escapeHtml(new Intl.NumberFormat("en-US").format(num));
    }
    default:
      return escapeHtml(str);
  }
}
```

Update `resolveVariable` to accept and use format hints, and update `renderTiptapToHtml` to accept a `formatHints` parameter.

### Step 6: Run all tests

Run: `./mvnw test -pl backend -Dtest=TiptapRendererTest -q 2>&1 | tail -20`
Run: `cd frontend && pnpm test 2>&1 | tail -20`
Expected: ALL PASS

### Step 7: Commit

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableFormatter.java \
        backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java \
        backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java \
        backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TiptapRendererTest.java \
        frontend/components/editor/client-renderer.ts
git commit -m "feat: add type-aware value formatting for currency, date, number variables (GAP-4)"
```

---

## Task 5: Invoice Custom Fields — Field Pack + Context Builder (GAP-5 — P2)

**Problem:** No `common-invoice.json` field pack exists. InvoiceContextBuilder already includes `invoice.customFields` in the context, but there are no seeded field definitions for invoices, and the variable metadata registry has no invoice custom field entries (fixed by Task 1).

**Files:**
- Create: `backend/src/main/resources/field-packs/common-invoice.json`
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackJsonValidationTest.java` (or similar validation test)

### Step 1: Create `common-invoice.json` field pack

```json
{
  "packId": "common-invoice",
  "version": 1,
  "entityType": "INVOICE",
  "group": {
    "slug": "invoice_info",
    "name": "Invoice Info"
  },
  "autoApply": true,
  "fields": [
    {
      "slug": "purchase_order_number",
      "name": "Purchase Order Number",
      "fieldType": "TEXT",
      "description": "Client's PO number for this invoice",
      "required": false,
      "sortOrder": 1,
      "requiredForContexts": []
    },
    {
      "slug": "payment_reference",
      "name": "Payment Reference",
      "fieldType": "TEXT",
      "description": "Payment reference for bank transfers",
      "required": false,
      "sortOrder": 2,
      "requiredForContexts": []
    },
    {
      "slug": "tax_type",
      "name": "Tax Type",
      "fieldType": "DROPDOWN",
      "description": "Type of tax applied",
      "required": false,
      "sortOrder": 3,
      "options": [
        {"value": "vat", "label": "VAT"},
        {"value": "gst", "label": "GST"},
        {"value": "sales_tax", "label": "Sales Tax"},
        {"value": "none", "label": "No Tax"}
      ],
      "requiredForContexts": []
    },
    {
      "slug": "billing_period_start",
      "name": "Billing Period Start",
      "fieldType": "DATE",
      "description": "Start date of the billing period",
      "required": false,
      "sortOrder": 4,
      "requiredForContexts": []
    },
    {
      "slug": "billing_period_end",
      "name": "Billing Period End",
      "fieldType": "DATE",
      "description": "End date of the billing period",
      "required": false,
      "sortOrder": 5,
      "requiredForContexts": []
    }
  ]
}
```

### Step 2: Write test validating the new pack loads

Add or extend a test in `TemplatePackJsonValidationTest` (or create a `FieldPackJsonValidationTest`) that verifies the JSON parses correctly:

```java
@Test
void commonInvoicePackIsValid() throws Exception {
    var resource = new ClassPathResource("field-packs/common-invoice.json");
    assertThat(resource.exists()).isTrue();
    var pack = objectMapper.readValue(resource.getInputStream(), FieldPackDefinition.class);
    assertThat(pack.packId()).isEqualTo("common-invoice");
    assertThat(pack.entityType()).isEqualTo("INVOICE");
    assertThat(pack.fields()).hasSize(5);
}
```

### Step 3: Run tests

Run: `./mvnw test -pl backend -q 2>&1 | tail -20`
Expected: ALL PASS

### Step 4: Commit

```bash
git add backend/src/main/resources/field-packs/common-invoice.json \
        backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/FieldPackJsonValidationTest.java
git commit -m "feat: add common-invoice field pack with PO number, payment ref, tax type, billing period (GAP-5)"
```

---

## Task 6: Clause Preview Variable Rendering (GAP-6 — P2)

**Problem:** This is already fixed by Task 3. The `extractTextFromBody()` fix renders variable nodes as `{key}` placeholders, which are used by both `ClauseBlockNodeView` and `ClausePicker` for previews. No additional work needed.

**Verification:** After Task 3 is complete, verify:
- `ClauseBlockNodeView.tsx:32-34` — uses `extractTextFromBody(body)` ✓ (will now show `{org.name}`)
- `ClausePicker.tsx:110-112` — uses `extractTextFromBody(selectedClause.body)` ✓ (will now show `{customer.name}`)

---

## Task 7: Stale Clause Titles in Template JSON (GAP-9 — P3)

**Problem:** When a `clauseBlock` node is inserted, the clause title is snapshotted in `attrs.title`. If the clause title is later edited in the library, the template retains the old title.

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseSync.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` (or wherever template content is returned)

### Step 1: Write failing test

In a new or existing test class, test that when a clause title is updated, templates referencing that clause return the updated title:

```java
@Test
void templateContent_returnsUpdatedClauseTitle_afterClauseTitleChange() {
    // 1. Create clause with title "Original Title"
    // 2. Create template with clauseBlock referencing that clause
    // 3. Update clause title to "Updated Title"
    // 4. Fetch template — clauseBlock.attrs.title should be "Updated Title"
}
```

### Step 2: Implement title refresh

**Option A (recommended — lightweight):** When loading a template for the editor, enrich the response by cross-referencing clauseBlock IDs with current clause titles. Add a method to `DocumentTemplateService`:

```java
public Map<String, Object> enrichContentWithCurrentClauseTitles(
        Map<String, Object> content, Map<UUID, String> currentTitles) {
    // Deep-walk content, find clauseBlock nodes, update attrs.title from currentTitles map
    return walkAndUpdateTitles(content, currentTitles);
}
```

Call this when returning template content from the API (GET endpoint), not when saving. This ensures:
- Stored JSON is the source of truth for what was inserted
- Display always reflects current clause titles
- No write amplification on clause title changes

### Step 3: Run tests, commit

```bash
git commit -m "fix: refresh clause titles from library when loading template content (GAP-9)"
```

---

## Task 8: Project Auto-Naming Patterns (GAP-7 — P2)

**Problem:** Projects have a freeform `name` string. No auto-naming pattern system exists to generate consistent project names like `{reference_number} - {customer.name} - Tax Return 2026`.

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgsettings/OrgSettings.java` (add `projectNamingPattern` field)
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` (apply pattern on create)
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectNameResolver.java`
- Create: `backend/src/main/resources/db/migration/tenant/V{next}__add_project_naming_pattern.sql`
- Modify: `frontend/...` (settings UI for naming pattern)

### Step 1: Add `projectNamingPattern` to OrgSettings

Add a nullable `String` column to `OrgSettings`:
```java
@Column(name = "project_naming_pattern")
private String projectNamingPattern; // e.g., "{reference_number} - {customer.name} - {name}"
```

Migration:
```sql
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS project_naming_pattern VARCHAR(500);
```

### Step 2: Create `ProjectNameResolver`

A service that takes a naming pattern and context map, replaces `{...}` tokens with values:

```java
@Service
public class ProjectNameResolver {

    public String resolve(String pattern, String projectName, Map<String, Object> customFields,
                          String customerName) {
        if (pattern == null || pattern.isBlank()) return projectName;

        String result = pattern;
        result = result.replace("{name}", projectName != null ? projectName : "");
        result = result.replace("{customer.name}", customerName != null ? customerName : "");

        // Replace custom field references: {reference_number}, {category}, etc.
        if (customFields != null) {
            for (var entry : customFields.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
            }
        }

        return result.trim().replaceAll("\\s+-\\s*$", "").trim(); // Clean trailing separators
    }
}
```

### Step 3: Integrate into project creation

In `ProjectService.createProject()`, after setting the name, check if `OrgSettings.projectNamingPattern` is set. If so, resolve and apply.

### Step 4: Add settings UI

Add a "Project Naming" section to the org settings page with a text input for the pattern and a preview showing an example resolved name.

### Step 5: Tests and commit

Write unit tests for `ProjectNameResolver` covering:
- Null/blank pattern returns original name
- Pattern with `{name}` only
- Pattern with custom field references
- Pattern with missing values (clean output)

```bash
git commit -m "feat: project auto-naming patterns from org settings (GAP-7)"
```

---

## Task 9: Loop Table Custom Field Sources (GAP-8 — P3)

**Problem:** `LoopTableExtension` and `renderLoopTable()` support data sources like `members`, `tags`, `lines` — but custom field collections aren't exposed as loop sources.

**Scope:** This is a niche feature. Only relevant if tenants define multi-value custom fields (arrays). Given the current field types (TEXT, NUMBER, DATE, DROPDOWN, BOOLEAN, CURRENCY, URL, EMAIL, PHONE), none naturally produce arrays. This gap is **deferred** until a multi-value field type is introduced.

**Action:** Document this as a future enhancement. No implementation now.

---

## Task 10: Inline Missing-Data Indicators (UX-1 — P2)

**Problem:** `requiredContextFields` validation only runs at generation time. The template editor has no inline indicator showing which variables reference missing or empty data.

**Files:**
- Modify: `frontend/components/editor/node-views/VariableNodeView.tsx`
- Modify: `frontend/components/editor/DocumentEditor.tsx` (add validation context)

### Step 1: Add a `missingVariables` context to the editor

Create a React context that provides a `Set<string>` of variable keys that are required but missing:

```typescript
// In DocumentEditor.tsx or a new context file
const MissingVariablesContext = createContext<Set<string>>(new Set());
```

### Step 2: Consume in `VariableNodeView`

```typescript
export function VariableNodeView({ node }: NodeViewProps) {
  const missingVars = useContext(MissingVariablesContext);
  const key = node.attrs.key as string;
  const isMissing = missingVars.has(key);

  return (
    <NodeViewWrapper
      as="span"
      className={cn(
        "inline-flex items-center rounded-md px-1.5 py-0.5 font-mono text-xs",
        isMissing
          ? "border border-amber-300 bg-amber-50 text-amber-700"
          : "border border-teal-200 bg-teal-50 text-teal-700",
      )}
      title={isMissing ? "This field has no value for the current entity" : undefined}
    >
      {"{"}
      {key}
      {"}"}
      {isMissing && <AlertTriangle className="ml-1 size-3" />}
    </NodeViewWrapper>
  );
}
```

### Step 3: Populate missing variables from `requiredContextFields`

When the template editor loads, cross-reference `template.requiredContextFields` with the metadata endpoint to identify which are required but have no default. This is a UI-only indicator — it doesn't validate actual entity data (that requires selecting a specific entity).

### Step 4: Tests and commit

```bash
git commit -m "feat: inline missing-data indicators on variable nodes in template editor (UX-1)"
```

---

## Task 11: Template Editor Live Preview with Real Data (UX-5 — P2)

**Problem:** The template editor has no mechanism to pick a sample entity and render a live preview with actual data substituted.

**Files:**
- Create: `frontend/components/editor/TemplatePreviewPanel.tsx`
- Modify: Template editor page (add preview toggle)
- Modify: `frontend/components/editor/actions.ts` (add preview fetch)

### Step 1: Add an entity picker to the template editor

Add a combobox that lists entities of the template's `primaryEntityType` (e.g., projects for a PROJECT template). When an entity is selected, fetch its data and render the template client-side using `renderTiptapToHtml` from `client-renderer.ts`.

### Step 2: Create `TemplatePreviewPanel`

```typescript
interface TemplatePreviewPanelProps {
  content: Record<string, unknown> | null;
  entityType: TemplateEntityType;
  css?: string;
}

export function TemplatePreviewPanel({ content, entityType, css }: TemplatePreviewPanelProps) {
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);

  // Fetch entity data when selected
  // Build context using buildPreviewContext()
  // Render using renderTiptapToHtml()

  return (
    <div className="flex flex-col gap-4">
      <EntityPicker entityType={entityType} onSelect={setSelectedEntityId} />
      {previewHtml && (
        <iframe
          srcDoc={previewHtml}
          className="h-[600px] w-full rounded border"
          sandbox=""
        />
      )}
    </div>
  );
}
```

### Step 3: Add a "Preview" tab or split-view toggle to the template editor

### Step 4: Tests and commit

```bash
git commit -m "feat: template editor live preview with real entity data (UX-5)"
```

---

## Task 12: Field Pack → Template Pack Linkage (UX-2 — P3)

**Problem:** Field packs and template packs are provisioned independently. No visible connection shows which field packs a template expects.

**Implementation:** Add a `requiredFieldPacks` JSONB column to `DocumentTemplate` that lists field pack IDs the template depends on. Display these as badges in the template editor with warnings if the tenant hasn't applied the required pack.

**This is a low-priority polish item — defer if timeline is tight.**

---

## Task 13: "Used In" Indicator for Fields (UX-3 — P3)

**Problem:** When editing a field definition, there's no indicator showing which templates or clauses reference it.

**Implementation:** Add a backend endpoint that searches template and clause content JSON for variable keys containing the field's slug (e.g., `project.customFields.{slug}`). This is a JSONB text search:

```sql
SELECT id, name FROM document_templates
WHERE content::text LIKE '%' || :fieldSlug || '%' AND active = true;
```

Display results as a "Used in" section on the field definition edit page.

**This is a low-priority polish item — defer if timeline is tight.**

---

## Task 14: Conditional Content Blocks (UX-4 — P3)

**Problem:** No `if/else` block node in Tiptap. Can't conditionally show sections based on field values.

**This is the largest feature addition and warrants its own architecture doc and ADR.** It requires:
1. A new `conditionalBlock` Tiptap extension (block node with `predicate` attrs)
2. Predicate evaluation in both renderers (backend + frontend)
3. A predicate editor UI (field picker + operator + value)

**Estimated effort:** 2 slices. **Recommend deferring to a dedicated phase.**

---

## Execution Order & Dependencies

```
Task 1 (GAP-1: Dynamic metadata) ← MUST BE FIRST — everything else depends on this
  ↓
Task 2 (GAP-2: Clause editor picker) ← depends on Task 1 (uses fetchAllVariableMetadata)
  ↓
Task 3 (GAP-3: Text extraction fix) ← independent, can parallel with Task 2
  ↓
Task 4 (GAP-4: Value formatting) ← depends on Task 1 (uses type metadata)
  ↓
Task 5 (GAP-5: Invoice field pack) ← independent, can parallel with Task 4
  ↓
Task 7 (GAP-9: Stale clause titles) ← independent
  ↓
Task 8 (GAP-7: Project auto-naming) ← independent
  ↓
Task 10 (UX-1: Missing-data indicators) ← independent
  ↓
Task 11 (UX-5: Live preview) ← depends on Task 4 (uses formatted rendering)
  ↓
Tasks 12-14 (UX-2, UX-3, UX-4) ← P3 polish, defer to later phase
```

**Parallelization opportunities:**
- Tasks 2 + 3 can run in parallel after Task 1
- Tasks 5 + 7 can run in parallel after Task 4
- Task 10 is fully independent

**Total estimated effort:** ~6-7 slices for P0+P1+P2 (Tasks 1-5, 7-8, 10-11), plus 3-4 slices for P3 items (Tasks 12-14) if pursued.
