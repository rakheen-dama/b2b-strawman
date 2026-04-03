# UX Template-Field Enhancements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement three remaining UX enhancements from the fields-clauses-templates gap analysis: field-pack-to-template linkage (UX-2), "Used In" indicators for fields (UX-3), and conditional content blocks in the Tiptap editor (UX-4).

**Prerequisites:** PR #611 merged (GAP-1 through GAP-9 + UX-1 resolved).

**Tech Stack:** Spring Boot 4 / Java 25, Next.js 16 / React 19 / TypeScript, Tiptap editor, PostgreSQL JSONB, Vitest (frontend), JUnit 5 + Testcontainers (backend)

**ADR:** ADR-167 (conditional block predicate model) — only UX-4 warrants an ADR; UX-2 and UX-3 are straightforward queries.

**Migration:** V64 (add conditional block support columns — if needed)

---

## Task 1: "Used In" Indicator for Fields (UX-3 — P3)

**Problem:** When editing a field definition, there's no indicator showing which templates or clauses reference it. Users may unknowingly deactivate or edit fields that are actively used in documents.

**Why UX-3 before UX-2:** UX-3 provides the core variable-scanning logic that UX-2 also needs. Building UX-3 first avoids duplication.

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateVariableAnalyzer.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java`
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplateVariableAnalyzerTest.java`
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldUsageEndpointTest.java`
- Modify: `frontend/components/field-definitions/FieldDefinitionDialog.tsx`
- Modify: `frontend/components/field-definitions/actions.ts` (or create if needed)

### Step 1: Write the `TemplateVariableAnalyzer` with tests

Create a utility that walks a Tiptap JSON tree and extracts all variable keys. This mirrors the `renderNode` switch in `TiptapRenderer.java` but only collects variable references.

```java
@Component
public class TemplateVariableAnalyzer {

    /**
     * Extract all variable keys from a Tiptap JSON document.
     * Walks the tree recursively, collecting keys from "variable" nodes.
     */
    public Set<String> extractVariableKeys(Map<String, Object> document) {
        var keys = new LinkedHashSet<String>();
        walkNode(document, keys);
        return keys;
    }

    private void walkNode(Map<String, Object> node, Set<String> keys) {
        if (node == null) return;
        String type = (String) node.get("type");

        if ("variable".equals(type)) {
            var attrs = (Map<String, Object>) node.get("attrs");
            if (attrs != null && attrs.get("key") instanceof String key && !key.isBlank()) {
                keys.add(key);
            }
        }

        if (node.get("content") instanceof List<?> children) {
            for (var child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    walkNode((Map<String, Object>) childMap, keys);
                }
            }
        }
    }

    /**
     * Filter variable keys to only custom field references.
     * Pattern: {entity}.customFields.{slug} → returns the slug.
     */
    public Map<String, Set<String>> extractCustomFieldSlugs(Map<String, Object> document) {
        // Returns: entityType → set of slugs
        // e.g. { "project" → ["reference_number", "category"], "customer" → ["vat_number"] }
        var result = new HashMap<String, Set<String>>();
        for (String key : extractVariableKeys(document)) {
            var parts = key.split("\\.");
            if (parts.length == 3 && "customFields".equals(parts[1])) {
                result.computeIfAbsent(parts[0], k -> new LinkedHashSet<>()).add(parts[2]);
            }
        }
        return result;
    }
}
```

**Unit test:** Test with various Tiptap JSON structures (empty doc, text-only, variables, nested clauseBlocks containing variables, loopTable columns).

### Step 2: Add field usage endpoint

Add a service method and endpoint to return where a field is used:

```java
// In FieldDefinitionService or a new FieldUsageService
public record FieldUsageInfo(
    List<TemplateReference> templates,
    List<ClauseReference> clauses
) {
    public record TemplateReference(UUID id, String name, String category) {}
    public record ClauseReference(UUID id, String title) {}
}

public FieldUsageInfo getFieldUsage(UUID fieldId) {
    var field = fieldDefinitionRepository.findById(fieldId)
        .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", fieldId));

    String slug = field.getSlug();
    EntityType entityType = field.getEntityType();
    String prefix = entityPrefix(entityType) + ".customFields." + slug;

    // Scan templates — JSONB text search
    var templates = documentTemplateRepository.findActiveByContentContaining(prefix);
    // Scan clauses — JSONB text search
    var clauses = clauseRepository.findActiveByBodyContaining(prefix);

    return new FieldUsageInfo(
        templates.stream().map(t -> new TemplateReference(t.getId(), t.getName(), t.getCategory().name())).toList(),
        clauses.stream().map(c -> new ClauseReference(c.getId(), c.getTitle())).toList()
    );
}
```

**Repository queries** (native JSONB text search — performant for this use case):

```java
// DocumentTemplateRepository
@Query("SELECT t FROM DocumentTemplate t WHERE t.active = true AND CAST(t.content AS string) LIKE %:variableKey%")
List<DocumentTemplate> findActiveByContentContaining(@Param("variableKey") String variableKey);

// ClauseRepository
@Query("SELECT c FROM Clause c WHERE c.active = true AND CAST(c.body AS string) LIKE %:variableKey%")
List<Clause> findActiveByBodyContaining(@Param("variableKey") String variableKey);
```

**Endpoint:**
```
GET /api/fields/{id}/usage → FieldUsageInfo
```

### Step 3: Add integration test

Test that creating a template with a variable referencing a custom field slug, then calling `GET /api/fields/{id}/usage`, returns the template in the response.

### Step 4: Update `FieldDefinitionDialog.tsx`

Add a "Used In" collapsible section at the bottom of the dialog:

```tsx
// Fetch on dialog open
const [usage, setUsage] = useState<FieldUsageInfo | null>(null);

useEffect(() => {
  if (field?.id) {
    fetchFieldUsage(field.id).then(setUsage);
  }
}, [field?.id]);

// Render below description
{usage && (usage.templates.length > 0 || usage.clauses.length > 0) && (
  <Collapsible>
    <CollapsibleTrigger className="text-sm text-slate-600">
      Used in {usage.templates.length} template{usage.templates.length !== 1 ? 's' : ''}, {usage.clauses.length} clause{usage.clauses.length !== 1 ? 's' : ''}
    </CollapsibleTrigger>
    <CollapsibleContent>
      {/* List templates and clauses */}
    </CollapsibleContent>
  </Collapsible>
)}
```

Show a warning badge on the deactivate button if the field is actively used.

### Step 5: Run tests, commit

```bash
git commit -m "feat: field 'Used In' indicator showing templates and clauses that reference a custom field (UX-3)"
```

---

## Task 2: Field Pack → Template Pack Linkage (UX-2 — P3)

**Problem:** Field packs and template packs are provisioned independently. Templates contain variable references to custom fields, but there's no visible connection showing which field packs a template expects.

**Depends on:** Task 1 (uses `TemplateVariableAnalyzer`)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`
- Modify: `frontend/app/(app)/org/[slug]/settings/templates/[id]/page.tsx` (or template editor)
- Modify: `frontend/components/editor/TemplateEditorClient.tsx`
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplateFieldPackLinkageTest.java`

### Step 1: Compute required field packs on template load

When a template is loaded for editing, use `TemplateVariableAnalyzer` to extract custom field slugs, then cross-reference against `FieldDefinition` records to determine which field packs are needed:

```java
// In DocumentTemplateService — called when loading template for editor
public record FieldPackStatus(String packId, String packName, boolean applied, List<String> missingFields) {}

public List<FieldPackStatus> getRequiredFieldPacks(UUID templateId) {
    var template = findById(templateId);
    var slugsByEntity = templateVariableAnalyzer.extractCustomFieldSlugs(template.getContent());

    // For each slug, find the FieldDefinition and its packId
    var requiredPackIds = new LinkedHashSet<String>();
    var missingByPack = new HashMap<String, List<String>>();

    for (var entry : slugsByEntity.entrySet()) {
        EntityType entityType = EntityType.valueOf(entry.getKey().toUpperCase());
        for (String slug : entry.getValue()) {
            var field = fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, slug);
            if (field.isPresent() && field.get().getPackId() != null) {
                requiredPackIds.add(field.get().getPackId());
            } else if (field.isEmpty()) {
                missingByPack.computeIfAbsent("_missing", k -> new ArrayList<>())
                    .add(entry.getKey() + ".customFields." + slug);
            }
        }
    }
    // Build status list — check OrgSettings.fieldPackStatus to see if packs are applied
    // Return list of FieldPackStatus records
}
```

This is a **computed linkage** — no database column needed. The relationship is derived from the template content at read time.

### Step 2: Add endpoint

```
GET /api/templates/{id}/required-field-packs → List<FieldPackStatus>
```

### Step 3: Show in template editor UI

Add a "Required Field Packs" info section in `TemplateEditorClient.tsx`:

```tsx
// Below the template name/category section
{fieldPacks && fieldPacks.length > 0 && (
  <div className="flex flex-wrap gap-2">
    {fieldPacks.map(pack => (
      <Badge key={pack.packId} variant={pack.applied ? "success" : "warning"}>
        {pack.packId} {pack.applied ? "✓" : "— not applied"}
      </Badge>
    ))}
  </div>
)}
```

If a pack is not applied, show a warning: "This template references fields from {packId} which hasn't been applied to your organisation."

### Step 4: Tests and commit

```bash
git commit -m "feat: show required field packs in template editor with application status (UX-2)"
```

---

## Task 3: Conditional Content Blocks (UX-4 — P3)

**Problem:** No `if/else` block node in Tiptap. Templates can't conditionally show sections based on field values (e.g., show a VAT clause only if `customer.customFields.tax_type` is "vat").

**ADR Required:** ADR-167 — Conditional Block Predicate Model

**Files:**
- Create: `frontend/components/editor/extensions/conditionalBlock.ts`
- Create: `frontend/components/editor/node-views/ConditionalBlockNodeView.tsx`
- Create: `frontend/components/editor/ConditionalBlockConfig.tsx`
- Modify: `frontend/components/editor/DocumentEditor.tsx` (register extension)
- Modify: `frontend/components/editor/EditorToolbar.tsx` (add insert button)
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java` (render conditionals)
- Modify: `frontend/components/editor/client-renderer.ts` (render conditionals)
- Create: `frontend/lib/__tests__/conditional-block.test.ts`
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/ConditionalBlockRenderTest.java`
- Create: `adr/ADR-167-conditional-block-predicate-model.md`

### Step 1: Write ADR-167

**Decision:** Use an attribute-based predicate model (fieldKey + operator + value) stored as Tiptap node attributes. This is simpler than a DSL or expression language and sufficient for field-level conditions.

**Options:**
1. **Attribute-based predicates** — fieldKey, operator, value as node attrs
2. **Expression DSL** — freeform string expression like `project.status == "ACTIVE" && customer.customFields.tier != "free"`
3. **No conditional blocks** — defer entirely, let users manage with separate templates

**Decision:** Option 1. Simple, type-safe, UI-friendly. Covers 90% of use cases (field equality, emptiness checks). Complex boolean logic can be added later via nested blocks.

### Step 2: Create the Tiptap extension

```typescript
// frontend/components/editor/extensions/conditionalBlock.ts
import { mergeAttributes, Node } from "@tiptap/core";
import { ReactNodeViewRenderer } from "@tiptap/react";
import { ConditionalBlockNodeView } from "../node-views/ConditionalBlockNodeView";

export const ConditionalBlockExtension = Node.create({
  name: "conditionalBlock",
  group: "block",
  content: "block+",     // Contains renderable content (paragraphs, tables, etc.)
  defining: true,

  addAttributes() {
    return {
      fieldKey: { default: "" },
      operator: { default: "isNotEmpty" },  // Safe default — show if field has a value
      value: { default: "" },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-conditional-block]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["div", mergeAttributes(HTMLAttributes, { "data-conditional-block": "" }), 0];
  },

  addNodeView() {
    return ReactNodeViewRenderer(ConditionalBlockNodeView);
  },
});
```

**Key design choice:** `content: "block+"` makes this a **wrapping block** (like a container), not an atom. Users type content *inside* the conditional block. This matches how blockquotes work in Tiptap — the block is a wrapper, not a replacement.

### Step 3: Create the NodeView

The NodeView renders a styled container with a header showing the condition and editable content area inside:

```tsx
// Styled container: amber border/header, white content area
// Header: "Show if: {fieldKey} {operator} {value}" with edit button
// Content: <NodeViewContent /> — Tiptap renders the inner blocks here
// Config dialog opens on header click
```

**Operators supported:**
| Operator | Label | Description |
|----------|-------|-------------|
| `eq` | equals | Field value equals the comparison value |
| `neq` | does not equal | Field value does not equal the comparison value |
| `isEmpty` | is empty | Field has no value (null, blank) |
| `isNotEmpty` | has a value | Field has a non-empty value |
| `contains` | contains | Field value contains the substring |
| `in` | is one of | Field value is in a comma-separated list |

### Step 4: Create the Config dialog

`ConditionalBlockConfig.tsx` — a popover or dialog with:
1. **Field picker** — dropdown of available variables (reuse `fetchVariableMetadata` / `fetchAllVariableMetadata`)
2. **Operator picker** — dropdown of operators (filtered by field type — e.g., no "contains" for numbers)
3. **Value input** — text input (or dropdown for DROPDOWN field types if metadata available)
4. **Preview** — "Show this content if {customer.customFields.tax_type} equals vat"

### Step 5: Backend rendering

Add `conditionalBlock` to the `renderNode` switch in `TiptapRenderer.java`:

```java
case "conditionalBlock" -> {
    String fieldKey = attrs != null ? (String) attrs.get("fieldKey") : null;
    String operator = attrs != null ? (String) attrs.getOrDefault("operator", "isNotEmpty") : "isNotEmpty";
    Object condValue = attrs != null ? attrs.get("value") : null;

    if (fieldKey != null && !fieldKey.isBlank()) {
        Object fieldValue = resolveVariableRaw(fieldKey, context);
        if (evaluateCondition(fieldValue, operator, condValue)) {
            renderChildren(node, context, clauses, sb, depth + 1, formatHints);
        }
    }
    // If fieldKey is blank (unconfigured), render children unconditionally (safe default in editor)
}
```

**Condition evaluator:**

```java
private boolean evaluateCondition(Object fieldValue, String operator, Object condValue) {
    return switch (operator) {
        case "eq" -> Objects.equals(asString(fieldValue), asString(condValue));
        case "neq" -> !Objects.equals(asString(fieldValue), asString(condValue));
        case "isEmpty" -> fieldValue == null || String.valueOf(fieldValue).isBlank();
        case "isNotEmpty" -> fieldValue != null && !String.valueOf(fieldValue).isBlank();
        case "contains" -> asString(fieldValue).contains(asString(condValue));
        case "in" -> {
            String csv = asString(condValue);
            Set<String> allowed = Set.of(csv.split("\\s*,\\s*"));
            yield allowed.contains(asString(fieldValue));
        }
        default -> true; // Unknown operator → render (fail-open)
    };
}

private String asString(Object o) {
    return o == null ? "" : String.valueOf(o);
}
```

### Step 6: Frontend rendering

Mirror the backend logic in `client-renderer.ts`:

```typescript
case "conditionalBlock": {
  const fieldKey = attrs?.fieldKey as string;
  const operator = (attrs?.operator as string) ?? "isNotEmpty";
  const condValue = attrs?.value;

  if (!fieldKey) {
    // Unconfigured — render children
    return renderChildren(node, context, clauses, formatHints);
  }

  const fieldValue = resolveVariableRaw(fieldKey, context);
  if (evaluateCondition(fieldValue, operator, condValue)) {
    return renderChildren(node, context, clauses, formatHints);
  }
  return ""; // Condition not met — hide content
}
```

### Step 7: Register extension in DocumentEditor

Add `ConditionalBlockExtension` to the template extensions array in `DocumentEditor.tsx`:

```typescript
scope === "template"
  ? [VariableExtension, LoopTableExtension, ClauseBlockExtension, ConditionalBlockExtension]
  : [VariableExtension]
```

**Do NOT add to clause scope** — clauses are entity-type-agnostic and conditional logic depends on entity context.

### Step 8: Add toolbar button

Add a "Conditional" button to `EditorToolbar.tsx` (next to the variable picker, only in template scope):

```tsx
{entityType && (
  <Button
    variant="ghost"
    size="sm"
    onClick={() => editor?.chain().focus().insertContent({
      type: "conditionalBlock",
      content: [{ type: "paragraph" }],
    }).run()}
    title="Insert conditional block"
  >
    <GitBranch className="size-3.5" />
  </Button>
)}
```

### Step 9: Write tests

**Backend tests** (`ConditionalBlockRenderTest.java`):
- `conditionalBlock_isNotEmpty_rendersWhenValuePresent`
- `conditionalBlock_isEmpty_rendersWhenValueMissing`
- `conditionalBlock_eq_matchesValue`
- `conditionalBlock_neq_excludesValue`
- `conditionalBlock_in_matchesOneOfList`
- `conditionalBlock_unconfigured_rendersContent`
- `conditionalBlock_nested_evaluatesIndependently`

**Frontend tests** (`conditional-block.test.ts`):
- Mirror backend tests for `evaluateCondition` function
- Test `renderTiptapToHtml` with conditional blocks

### Step 10: Commit

```bash
git commit -m "feat: conditional content blocks in template editor with predicate evaluation (UX-4)"
```

---

## Execution Order & Dependencies

```
Task 1 (UX-3: "Used In" indicators) ← FIRST — provides TemplateVariableAnalyzer
  ↓
Task 2 (UX-2: Field pack linkage) ← depends on Task 1 (uses TemplateVariableAnalyzer)

Task 3 (UX-4: Conditional blocks) ← INDEPENDENT — can run in parallel with Tasks 1+2
```

**Parallelisation:** Tasks 1+2 are sequential (shared dependency). Task 3 is fully independent and can be developed on a separate branch.

**Estimated effort:** ~3 slices total (1 slice per task).

---

## ADR Index

| ADR | Title | Status |
|-----|-------|--------|
| ADR-167 | Conditional Block Predicate Model | Proposed (Task 3) |
