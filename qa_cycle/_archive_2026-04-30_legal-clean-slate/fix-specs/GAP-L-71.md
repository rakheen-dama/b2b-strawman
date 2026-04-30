# Fix Spec: GAP-L-71 — Statement of Account generation crashes with ClassCastException

## Problem (verbatim from QA evidence)

Day 60 cycle-1 CLOSURE-EXECUTE (2026-04-25 ~20:46 SAST,
`qa_cycle/checkpoint-results/day-60.md` §"Day 60 — CLOSURE-EXECUTE",
Step CE3a). Matter `e788a51b-3a73-…` was successfully closed
(`matter_closure_log` row inserted, retention clock started, closure
letter PDF generated). Thandi then clicked the "Generate Statement of
Account" toolbar button on the closed matter, accepted the default
period `2026-04-01 .. 2026-04-25`, and clicked **Preview & Save**.

Frontend rendered alert "An unexpected error occurred". Backend logged:

```
java.lang.ClassCastException: class io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto
    cannot be cast to class java.util.Map
    at io.b2mash.b2b.b2bstrawman.template.TiptapRenderer.renderLoopTable(TiptapRenderer.java:309)
    at io.b2mash.b2b.b2bstrawman.template.TiptapRenderer.render(TiptapRenderer.java:...)
    at io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementService.renderHtml(StatementService.java:276)
    at io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementService.generate(StatementService.java:110)
    at io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementController.generate(StatementController.java:42)
```

(requestId `e9fc7a75-d2ae-452f-94b2-e85426e6c4bc`,
log timestamp `2026-04-25T20:46:08.033`.)

**Net effect**: SoA generation is unconditionally broken for any matter
that has at least one disbursement (the loop-table on the disbursements
section is the trigger). Closure letter generation is unaffected (it
uses a different template that does not exercise the loop-table path).

**Blocks**:
- Exit checkpoint **E.13** (SoA half — Day 60 closure deliverable).
- **Day 61** Sipho portal SoA download (no SoA artifact exists for the
  portal `/statements` page to render).

## Root Cause (grep-confirmed)

The Tiptap loop-table renderer assumes every iterable element is a
`Map<String, Object>`, but the Statement-of-Account context builder
puts typed Java records into the loop's data sources.

### The renderer (the consumer)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java`:

- **Line 297** — `renderLoopTable(...)` calls
  `List<Map<String, Object>> rows = resolveDataSource(dataSource, context);`
- **Line 309** — `for (var row : rows)` then **line 314**
  `Object val = colKey != null ? row.get(colKey) : null;` — i.e. uses
  `Map.get(String)`. The cast that produces the `ClassCastException`
  happens earlier inside `resolveDataSource`:
- **Lines 334–337** —
  ```java
  if (current instanceof List<?> list) {
    // Safe unchecked cast — JSONB deserialization produces List<Map<String,Object>>
    return (List<Map<String, Object>>) list;
  }
  ```
  The comment is **wrong for the SoA path** — the SoA context is built
  in Java (not deserialized from JSONB), and the lists contain typed
  records. The unchecked cast compiles, but the per-element implicit
  cast inside the `for (var row : rows)` enhanced-for triggers the CCE
  at line 309 once iteration hits the first DTO.

### The producer (the source of typed DTOs)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`:

- **Line 145** — `Aggregates agg = aggregate(project, customer, projectId, periodStart, periodEnd);`
- **Line 148** — `context.put("fees", Map.of("entries", agg.feeLines(), ...))` — `agg.feeLines()` is `List<FeeLineDto>`.
- **Line 157** — `context.put("disbursements", Map.of("entries", agg.disbursementLines(), ...))` — `agg.disbursementLines()` is `List<DisbursementStatementDto>`. **This is the one that blew up in QA's run** (Sheriff Fees R 1 250 disbursement on the Dlamini matter).
- **Lines 160–166** — `context.put("trust", Map.of("deposits", agg.trust().deposits, "payments", agg.trust().payments, ...))` — both are `List<TrustTxDto>`.

So three loop-table data sources hand typed records into a renderer
that expects Maps. Today only `disbursements.entries` triggers in QA's
trace because the SoA template only references the disbursement loop
in the path QA exercises, but the same crash will fire on
`fees.entries` or the trust deposits/payments tables once a matter
with billable time entries / trust activity in the period also
generates an SoA.

`StatementSummary` (line 168) is converted to a Map already via
`toSummaryMap(...)` — that path is correct and is the pattern this
fix extends to the three list-shaped sources.

### Why the unit tests didn't catch this

`backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilderTest.java`
asserts the **shape of the context Map** (keys, totals) but doesn't
invoke `TiptapRenderer.render(...)` end-to-end on a template that
exercises `loopTable`. `TiptapRendererTest.java` exercises the
loop-table renderer with hand-built `List<Map<String,Object>>` test
fixtures, so it sees the format the renderer expects, not the format
the SoA builder actually produces.

## Chosen Fix — Option C (DTO→Map adapter at model-build, contained to SoA)

Convert each typed-record list to `List<Map<String, Object>>` inside
`StatementOfAccountContextBuilder.build(...)` before putting it into
the context map. Use Jackson `ObjectMapper.convertValue(...)` (already
on the classpath via Spring Boot) so the conversion is record-aware,
respects `@JsonProperty` annotations if any are added later, and
honours the existing serialization config.

**Smallest + safest** of the three options considered:

- **Option A (template syntax change)** — would require the SoA
  template (a JSONB document in `document_templates` table installed
  at tenant provisioning + reconciled by `PackReconciliationRunner`)
  to migrate to a different field-access syntax that the renderer
  could resolve via reflection. Affects already-installed tenant data,
  needs a Flyway/data-migration coordinated with template content
  changes. **Rejected** — too much surface for a v1 fix.

- **Option B (extend renderer to handle typed objects via
  reflection)** — would broaden `TiptapRenderer.renderLoopTable` to
  reflectively introspect non-Map elements, e.g. via
  `objectMapper.convertValue(item, Map.class)` per row inside the
  renderer. **Rejected** — broader blast radius (any future template +
  any future report uses the renderer; reflective per-row conversion
  also adds per-row CPU cost). Worth doing in Sprint 2 as a generic
  hardening, but not in this verify cycle.

- **Option C (chosen)** — convert at the source, in
  `StatementOfAccountContextBuilder`, before the typed list ever
  reaches the renderer. The renderer keeps its original contract
  ("rows must be Maps"); only the SoA builder is touched. Closure
  letter and any other report still working today are unaffected.

## Fix — step-by-step

### 1. Add ObjectMapper dependency to StatementOfAccountContextBuilder

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
```

Add `private final ObjectMapper objectMapper;` field. Add it as a
constructor parameter (Spring auto-wires the autoconfigured
`ObjectMapper` bean — the same instance Spring MVC uses for JSON
serialization, so any config customisations apply).

### 2. Add a private helper to convert any typed list to List<Map>

```java
/**
 * Converts a list of typed DTOs (records) to the List<Map<String,Object>> shape
 * required by TiptapRenderer.renderLoopTable. The renderer assumes JSONB-like
 * map shape per row; SoA's typed-record DTOs would otherwise blow up with
 * ClassCastException at TiptapRenderer:309 on the first iteration.
 *
 * <p>Uses the autowired Spring ObjectMapper so any tenant-wide serialization
 * customisations (date format, BigDecimal encoding, naming) apply consistently
 * with the rest of the JSON layer.
 */
private List<Map<String, Object>> toMapList(List<?> typedRows) {
  if (typedRows == null || typedRows.isEmpty()) return List.of();
  return objectMapper.convertValue(
      typedRows, new TypeReference<List<Map<String, Object>>>() {});
}
```

### 3. Wrap the three loop-table data sources

In `build(...)` (line 145 onwards), replace:

```java
context.put(
    "fees",
    Map.of(
        "entries", agg.feeLines(),
        ...));

context.put(
    "disbursements",
    Map.of("entries", agg.disbursementLines(), "total", agg.disbursementsTotal()));

context.put(
    "trust",
    Map.of(
        "opening_balance", agg.trust().openingBalance,
        "deposits", agg.trust().deposits,
        "payments", agg.trust().payments,
        "closing_balance", agg.trust().closingBalance));
```

with:

```java
context.put(
    "fees",
    Map.of(
        "entries", toMapList(agg.feeLines()),
        ...));

context.put(
    "disbursements",
    Map.of("entries", toMapList(agg.disbursementLines()),
           "total", agg.disbursementsTotal()));

context.put(
    "trust",
    Map.of(
        "opening_balance", agg.trust().openingBalance,
        "deposits", toMapList(agg.trust().deposits),
        "payments", toMapList(agg.trust().payments),
        "closing_balance", agg.trust().closingBalance));
```

The scalar fields (`total_hours`, `total_amount_excl_vat`,
`vat_amount`, `total_amount_incl_vat`, `total`, `opening_balance`,
`closing_balance`) stay as their native `BigDecimal`/`String` types —
those are resolved by `TiptapRenderer.resolveVariable`'s scalar path
(line 280 onwards), which already calls `VariableFormatter.format` on
any `Object`, so they don't need conversion.

### 4. Add a renderer-level integration test

To prevent regression, extend
`StatementOfAccountContextBuilderTest.java` (or add a new
`StatementServiceRenderIntegrationTest.java` if the existing test
class is purely shape-based) with a test that:

1. Seeds a project with at least one disbursement (Sheriff Fees R 1 250 — same shape as QA's Day 60 trace).
2. Builds the context via `contextBuilder.build(...)`.
3. Asserts `((Map) context.get("disbursements")).get("entries") instanceof List<Map>` and that the **first row is a `Map`, not a `DisbursementStatementDto`**.
4. **Critical**: round-trips through `tiptapRenderer.render(template.getContent(), context, Map.of(), template.getCss(), Map.of())` using the actual installed SoA template and asserts no exception is thrown + the rendered HTML contains the disbursement description text.

The unit-only assertion (step 3) catches the type drift; the
renderer-roundtrip assertion (step 4) catches any future template
that adds another loop-table on a typed-DTO list.

### 5. Update the misleading comment in TiptapRenderer.resolveDataSource

`TiptapRenderer.java` lines 334–337:

```java
// Before:
// Safe unchecked cast — JSONB deserialization produces List<Map<String,Object>>

// After:
// Caller contract: rows must be List<Map<String,Object>>. JSONB-deserialized
// content lists meet this naturally; programmatic builders (e.g.
// StatementOfAccountContextBuilder) MUST convert typed records via
// objectMapper.convertValue(...) before putting the list into the context map,
// otherwise per-row iteration at line 309 throws ClassCastException.
```

Comment-only change documenting the renderer contract; no behaviour change.

## Scope

**Backend-only.** No frontend, no DB migration, no template content
change.

**Files modified:**

1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java` — add `ObjectMapper` dep, add `toMapList(...)` helper, wrap 3 loop-table list assignments.
2. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java` — comment-only update on lines 334–337 to document the caller contract.

**Files added:**

3. `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/` — new test (or extension to existing
`StatementOfAccountContextBuilderTest`) covering: (a) entries-are-Map shape assertion, (b) end-to-end render roundtrip through the real installed SoA template.

**Lines touched (estimate):** ~25 production + ~50 test.

**NEEDS_REBUILD:** true (backend Java change — Spring restart required after `./mvnw spring-boot:run`).

## Verification (per QA — re-walk after fix lands)

1. Backend rebuild + restart on Keycloak dev stack (port 8080).
2. Re-trigger SoA generation from the **closed** RAF matter `e788a51b-3a73-…` toolbar (the matter is already CLOSED — toolbar button remains available on closed matters per scenario step 60.6).
3. Default period `2026-04-01 .. 2026-04-25`, click **Preview & Save**.
4. **Expected**: backend returns 200, frontend renders the preview HTML, "Save" persists a `generated_documents` row + `documents` row + S3 object. PDF download via `/api/documents/{id}/pdf` returns a non-empty PDF.
5. **Content check**: rendered HTML/PDF contains the disbursement section with row "Sheriff Fees ... R 1 250,00" (the disbursement that triggered the original CCE).
6. Backend log: 0 ClassCastException entries during the full request.
7. Frontend console: 0 errors.
8. Subsequently, **Day 61** can proceed — Sipho's portal `/statements` (or matter-trust statement-documents) section can list and download the SoA PDF.

## Estimated Effort

**S–M (~2–3 hr)** all-in:

- ~30 min — backend code change (DTO→Map adapter + 3 wrappings).
- ~45 min — test extension (round-trip render assertion against the real installed template).
- ~10 min — restart + manual smoke (browser-driven SoA generation on closed matter).
- ~30 min — buffer for any test setup quirks (template installation in test profile, PackReconciliationRunner timing).

Fits the SPEC_READY <2 hr lower bar at the implementation level; the
upper bound covers the test setup if installing the SoA template into
an integration-test tenant requires extra work.
