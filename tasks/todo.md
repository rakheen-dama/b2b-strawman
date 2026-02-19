# Phase 14 — Handoff Notes for Next Agent

## Current State (2026-02-18 ~21:35 SAST)

**Phase 14 is paused.** Resume from **103B**.

### Completed Slices (7/17)
| Slice | PR | Status |
|-------|----|--------|
| 100A | #208 | Merged |
| 100B | #209 | Merged |
| 101A | #210 | Merged |
| 101B | #211 | Merged |
| 102A | #212 | Merged |
| 102B | #213 | Merged |
| 103A | #214 | Merged |

### Remaining (10 slices)
103B → 104A → 104B → 105A → 105B → 106A → 106B → 107A → 107B → 108A

### Resume Command
```bash
./scripts/run-phase.sh 14 103B
```

## Build Optimizations Applied This Session

1. **Tiered build strategy** in `epic_v2` skill:
   - Tier 1: `./mvnw compile test-compile -q` (~30s) — compile check
   - Tier 2: `./mvnw test -Dtest={NEW_TESTS} -q` (~2-3min) — targeted tests
   - Tier 3: `./mvnw clean verify -q` (~10-15min) — full verify once before commit

2. **Test log level set to ERROR** in `application-test.yml`:
   - Suppresses Flyway/Hibernate INFO/DEBUG noise (was 2-4MB per build)
   - To debug failures, pass `-Dlogging.level.root=INFO` on that specific run

## Known Issues & Fixes

### 1. API 500 errors kill agents mid-execution
- Happened on 101B — agent died during review phase
- **Fix**: Check `gh pr list --state open`, review with sonnet agent, merge, mark Done, resume

### 2. Done marker inconsistency
- Phase script checks the **Implementation Order table** rows for `**Done**`
- Agents sometimes mark Done in other tables but NOT the Implementation Order table
- **Fix**: `grep '^\| \*\*{SLICE}\*\*' tasks/phase14-customer-compliance-lifecycle.md` — if last column is empty, add `**Done** (PR #{N})`

### 3. Stale processes from previous runs
- If the phase script dies mid-slice, the `claude -p` child process becomes an orphan and keeps running
- Before restarting, **always check** `ps aux | grep "claude -p" | grep -v grep`
- Kill stale agents before launching a new run, otherwise two agents fight over the same worktree
- 103A took ~83 min (vs ~45 min typical) partly because a stale process from a previous run was competing

### 4. Flyway "Connection is closed" during tests
- Transient Testcontainers + HikariCP issue — connections time out under pool pressure when many tenant schemas are provisioned
- Agent retries the build and it passes on the next attempt
- Not a code bug — just test infrastructure flakiness

### 5. Monitoring a running agent
```bash
# Is agent alive?
ps aux | grep "claude -p" | grep -v grep

# Build progress?
wc -l /tmp/mvn-epic-{SLICE}.log

# Git progress?
git -C /Users/rakheendama/Projects/2026/worktree-epic-{SLICE} log --oneline -3
git -C /Users/rakheendama/Projects/2026/worktree-epic-{SLICE} status --short

# Phase log?
tail -20 tasks/.phase-14-progress.log
```

## What 103A Delivered
- **New package**: `compliance/` (CompliancePackDefinition, CompliancePackSeeder, RetentionPolicy, RetentionPolicyRepository)
- **3 shipped packs**: `generic-onboarding`, `sa-fica-company`, `sa-fica-individual` (JSON in `compliance-packs/`)
- **Integration**: CompliancePackSeeder called during tenant provisioning (TenantProvisioningService)
- **Tests**: CompliancePackSeederTest, updated FieldPackSeederIntegrationTest and TenantProvisioningServiceTest
- **+1014 / -13 lines**

## Task File
`tasks/phase14-customer-compliance-lifecycle.md`

---

# URGENT: Fix PDF Generation for Document Templates

## Problem Summary

Document template **preview** now works (renders HTML with placeholder `________` for unresolvable variables), but **Download PDF** and **Save to Documents** still fail because the PDF conversion step (`OpenHTMLToPDF`) crashes.

## Root Causes

### 1. SpEL cannot resolve Map properties with dot notation
The Thymeleaf context builders (`ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder`, `TemplateContextHelper`) populate the rendering context using `LinkedHashMap<String, Object>`. Templates use `${project.name}`, `${org.name}`, `${customer.name}` etc.

**Problem**: Spring's `ReflectivePropertyAccessor` (used by SpEL in Thymeleaf) does NOT support Map property access via dot notation. It looks for `getName()` getter methods, which `LinkedHashMap` doesn't have. This causes `SpelEvaluationException: EL1008E`.

**Current workaround**: `LenientSpELEvaluator` catches all SpEL errors and returns `________` placeholder. This works for preview but produces broken HTML for PDF generation.

**Proper fix options** (pick ONE):
- **Option A (Recommended)**: Convert all context `LinkedHashMap` values to Java **records**. SpEL in Spring 6+ supports record-style accessors (`name()` instead of `getName()`). Create: `OrgContext`, `ProjectContext`, `CustomerContext`, `InvoiceContext`, `MemberContext` records. Change each context builder to return the record instead of a Map. The `extractEntityName()` method in `PdfRenderingService` also reads Maps — update it too.
- **Option B**: Register Spring's `MapAccessor` on the Thymeleaf evaluation context. This requires creating a custom `IDialect` or finding a hook into `ThymeleafEvaluationContext`. The manually-created `SpringTemplateEngine` in `PdfRenderingService` doesn't have an `ApplicationContext`, which limits the hooks available.
- **Option C**: Use a plain Thymeleaf `TemplateEngine` (OGNL) instead of `SpringTemplateEngine` (SpEL). OGNL natively supports `map.key` syntax. But OGNL has different expression syntax and security characteristics.

### 2. OpenHTMLToPDF SAX parser rejects placeholder HTML
When `LenientSpELEvaluator` inserts `________` into attributes (e.g., `th:src="${org.logoUrl}"` becomes `src="________"`), the resulting HTML may not be well-formed XHTML. OpenHTMLToPDF uses a strict XML parser (`SAXParser`) that rejects malformed input with `SAXException: Scanner State 24 not Recognized`.

**Fix**: Once Option A is implemented (records with real values or nulls), the placeholders go away. For truly missing optional fields, templates should use `th:if` guards (which the seeded templates already do, e.g., `<img th:if="${org.logoUrl}" ...>`). With proper null handling (records return null for missing fields), `th:if` will correctly skip the block.

### 3. Org name was missing from context (FIXED)
`TemplateContextHelper.buildOrgContext()` didn't include the organization name. **Fixed** — it now resolves: tenant schema → `OrgSchemaMapping` (public) → `Organization.name` (public).

## Key Files

- `backend/.../template/PdfRenderingService.java` — `generatePdf()` (full pipeline), `previewHtml()` (HTML only), `renderThymeleaf()`, `htmlToPdf()`
- `backend/.../template/LenientSpELEvaluator.java` — Catches SpEL errors, returns placeholder
- `backend/.../template/LenientSpringDialect.java` — Plugs lenient evaluator into Thymeleaf
- `backend/.../template/TemplateContextHelper.java` — Builds org context (with name fix)
- `backend/.../template/ProjectContextBuilder.java` — Builds project/customer/member/tags context
- `backend/.../template/CustomerContextBuilder.java` — Builds customer context
- `backend/.../template/InvoiceContextBuilder.java` — Builds invoice context
- `backend/.../template/DocumentTemplateController.java` — Preview uses `previewHtml()`, generate uses `generatePdf()`
- `frontend/components/templates/GenerateDocumentDropdown.tsx` — Reads `?generateTemplate=<id>` URL param to auto-open dialog
- `frontend/components/templates/GenerateDocumentDialog.tsx` — Preview + Download PDF + Save to Documents

## What Works Now
- Template preview (HTML rendering with lenient placeholders)
- `?generateTemplate=<id>` URL param auto-opens dialog from setup cards
- Org name appears in template context

## What's Broken
- **Download PDF button** — 500 from OpenHTMLToPDF SAX parser (the `generatePdf()` path still goes through `htmlToPdf()`)
- **Save to Documents button** — same PDF pipeline failure
- Both call `generateDocumentAction` → backend `POST /{id}/generate` → `PdfRenderingService.generatePdf()` → crashes at `htmlToPdf()`
