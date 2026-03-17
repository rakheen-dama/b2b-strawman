# Phase 49 — Code Review Notes for Architect

From PR #735 code review. These items were deferred as acceptable for now but warrant architectural attention.

## 1. Slug-based clause lookup is O(n) per clause block

**File**: `backend/src/main/java/.../template/TiptapRenderer.java` (lines 157-161)

The slug fallback iterates all clause values with `.stream().filter().findFirst()`. For engagement letters with 4-7 clause blocks, this performs 4-7 linear scans over the clause map. Currently fine, but if clause libraries grow beyond ~20 clauses per template, this becomes quadratic.

**Recommendation**: Build a `Map<String, Clause>` slug-to-clause index at the call site before passing to the renderer. Simple change, worth doing proactively.

## 2. resolveDropdownLabels queries DB on every call

**File**: `backend/src/main/java/.../template/TemplateContextHelper.java` (line 142)

Each call to `resolveDropdownLabels` hits `fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder()`. A single template render can invoke this 3-4 times (project fields, customer fields, nested customer in project context). Fine for single doc generation but will be a problem for batch operations (e.g., generating all customer statements).

**Recommendation**: Cache field definitions per entity type within a request scope, or pre-fetch all needed entity types at the start of rendering.

## 3. VariableFormatter hardcoded to ZA locale

**File**: `backend/src/main/java/.../template/VariableFormatter.java` (line 43)

Currency formatting uses `Locale.of("en", "ZA")` with an existing TODO about multi-currency. The new loopTable `format: "currency"` attribute routes through this same formatter, meaning all table cells render as ZAR regardless of the invoice's actual currency. Correct for accounting-za vertical, needs attention for multi-currency/multi-locale support.

**Recommendation**: Pass locale/currency from the rendering context (org settings or invoice currency) to `VariableFormatter`. This aligns with the existing `defaultCurrency` field on `OrgSettings`.

## 4. InvoiceContextBuilder customerVatNumber uses raw value

**File**: `backend/src/main/java/.../template/InvoiceContextBuilder.java` (lines 114-118)

The top-level `customerVatNumber` alias reads from `customer.getCustomFields()` (raw storage value) while `customer.customFields` in the context map has been through `resolveDropdownLabels`. Since `vat_number` is a TEXT field (not DROPDOWN), this doesn't cause a bug today, but the inconsistency could bite if field types change.

**Recommendation**: Read the alias from the already-resolved context map instead of from the raw entity.
