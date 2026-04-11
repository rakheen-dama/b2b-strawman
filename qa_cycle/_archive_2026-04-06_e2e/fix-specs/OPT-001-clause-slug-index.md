# OPT-001 — Slug-based clause lookup optimization

**Severity**: Low (performance — current data volumes are fine)
**Effort**: S (15 min)
**File**: `backend/src/main/java/.../template/TiptapRenderer.java:157-161`

## Problem

The slug fallback in `renderNode()` iterates all clause values with `.stream().filter().findFirst()` for each `clauseBlock` node. With 4-7 clause blocks per engagement letter, this performs 4-7 linear scans over the clause map. Currently O(n*k) where n = clauses in map, k = clause blocks in template. Fine for n < 20, problematic beyond that.

## Fix

Build a `Map<String, Clause>` slug index at the top of the `renderDocument()` or `renderNode()` entry point, before any clauseBlock processing. Pass it alongside the existing `Map<UUID, Clause>` parameter.

### Change

```java
// At the call site (or at the top of the recursive renderNode chain):
Map<String, Clause> slugIndex = clauses.values().stream()
    .filter(c -> c.getSlug() != null)
    .collect(Collectors.toMap(Clause::getSlug, Function.identity(), (a, b) -> a));

// In the clauseBlock case (lines 156-162):
if (clause == null && slug != null && !"unknown".equals(slug)) {
    clause = slugIndex.get(slug);  // O(1) instead of O(n)
}
```

### Impact

- Eliminates linear scans
- No behavioral change — same clause resolution semantics
- Thread-safe (map is built per-render, never shared)

### Tests

Existing clause rendering tests cover this path. No new tests needed — just verify existing tests pass.
