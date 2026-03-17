# OPT-002 — resolveDropdownLabels per-request caching

**Severity**: Low (performance — fine for single doc, issue for batch)
**Effort**: S (30 min)
**File**: `backend/src/main/java/.../template/TemplateContextHelper.java:142`

## Problem

Each call to `resolveDropdownLabels()` hits `fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType)`. A single template render invokes this 3-4 times:

1. InvoiceContextBuilder: `resolveDropdownLabels(customer.customFields, CUSTOMER)` (line 107)
2. InvoiceContextBuilder: `resolveDropdownLabels(project.customFields, PROJECT)` (line 139)
3. ProjectContextBuilder: `resolveDropdownLabels(project.customFields, PROJECT)`
4. CustomerContextBuilder: `resolveDropdownLabels(customer.customFields, CUSTOMER)`

For single doc generation this is fine (3-4 small queries). For batch operations (e.g., "generate all customer statements"), this becomes N * 3-4 queries.

**Additional finding**: `GeneratedDocumentService.generateDocument()` calls `buildContext()` **twice** — once for field validation (line ~141) and again inside `generatePdf()` (line ~155). This doubles all DB queries to 6-8 per render. The validation call could reuse the context from the first build.

## Fix

Add a per-invocation cache inside `TemplateContextHelper`. Since the helper is a `@Component` (request-scoped via Spring), we can cache field definitions in a simple `Map<EntityType, List<FieldDefinition>>` field. Two options:

### Option A: Lazy cache on the instance (preferred)

```java
// Add field to TemplateContextHelper:
private final Map<EntityType, List<FieldDefinition>> fieldDefCache = new EnumMap<>(EntityType.class);

// In resolveDropdownLabels:
var fieldDefs = fieldDefCache.computeIfAbsent(entityType,
    et -> fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(et));
```

**Important**: Verify `TemplateContextHelper` scope. If it's a singleton, this would cache across requests (bad). In that case, use Option B.

### Option B: Accept pre-fetched field defs as parameter

```java
public Map<String, Object> resolveDropdownLabels(
    Map<String, Object> customFields,
    EntityType entityType,
    List<FieldDefinition> fieldDefs) { ... }
```

Call sites pre-fetch all needed entity types once and pass them in.

### Impact

- Reduces DB queries from 3-4 per render to 1-2 per render (one per unique entity type)
- For batch of N documents: from N * 3-4 queries to 2 total
- No behavioral change

### Tests

Existing tests cover the resolution logic. Add one test verifying cache hit (second call with same entity type doesn't hit repo).
