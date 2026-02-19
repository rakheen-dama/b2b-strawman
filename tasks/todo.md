# RESOLVED: PDF Generation for Document Templates

## Status: FIXED

Both **Download PDF** and **Save to Documents** now work correctly.

## Root Causes & Fixes

### 1. SpEL cannot resolve Map properties with dot notation (FIXED)

**Problem**: `SpringTemplateEngine` uses SpEL, whose `ReflectivePropertyAccessor` resolves `${project.name}` by looking for a `getName()` getter on `LinkedHashMap` — which doesn't exist. The `LenientSpELEvaluator` masked this by returning `"________"` placeholders.

**Fix**: Switched from `SpringTemplateEngine` (SpEL) to standard Thymeleaf `TemplateEngine` (OGNL). OGNL natively treats `map.key` as `map.get("key")`. Since the PDF pipeline is offline rendering with no Spring MVC context, Spring integration was unnecessary.

- Added `ognl:ognl:3.3.4` dependency (version matched to Thymeleaf 3.1.3)
- Replaced `SpringTemplateEngine` → `TemplateEngine` in `PdfRenderingService`
- Created `LenientStandardDialect` (extends `StandardDialect`) + `LenientOGNLEvaluator` (wraps `OGNLVariableExpressionEvaluator`)
- Deleted `LenientSpringDialect` and `LenientSpELEvaluator` (SpEL-based, no longer needed)

### 2. HTML double-wrapping caused invalid XHTML for SAX parser (FIXED)

**Problem**: Seeded templates (e.g., `engagement-letter.html`) are full HTML documents (`<!DOCTYPE html><html>...<body>...</body></html>`). After Thymeleaf rendering, `wrapHtml()` wrapped the result AGAIN in another `<!DOCTYPE html><html><body>...` — producing nested documents that OpenHTMLToPDF's strict XML/SAX parser rejected with `SAXException: Scanner State 24 not Recognized`.

**Fix**: Made `wrapHtml()` smart — detects `</head>` in the rendered output. If present (full document), injects the `<style>` block into the existing `<head>`. If absent (bare fragment), wraps in a new HTML document as before.

### 3. Org name missing from context (previously fixed)

Already resolved before this session — `TemplateContextHelper.buildOrgContext()` now resolves org name via `OrgSchemaMapping` → `Organization.name`.

## Failed Approaches (for posterity)

- **Option B (MapAccessor on SpEL context)**: `ThymeleafEvaluationContext` with `MapAccessor` set as context variable — Thymeleaf's `SpringStandardExpressionObjectFactory` creates its own evaluation context internally, overriding the one set via the variable. MapAccessor never reached compound expressions.
- **Option C first attempt (OGNL without dependency)**: Switched to standard `TemplateEngine` but got `NoClassDefFoundError: ognl/ClassResolver` because `spring-boot-starter-thymeleaf` only includes `thymeleaf-spring6` (SpEL). Fixed by adding explicit `ognl:ognl:3.3.4`.

## Files Changed

- `backend/pom.xml` — added `ognl:ognl:3.3.4`
- `backend/.../template/PdfRenderingService.java` — `SpringTemplateEngine` → `TemplateEngine`, smart `wrapHtml()`
- `backend/.../template/LenientOGNLEvaluator.java` — NEW (wraps OGNL evaluator with lenient error handling)
- `backend/.../template/LenientStandardDialect.java` — NEW (plugs lenient OGNL evaluator into standard dialect)
- `backend/.../template/LenientSpELEvaluator.java` — DELETED
- `backend/.../template/LenientSpringDialect.java` — DELETED
- `backend/.../template/PdfRenderingServiceTest.java` — added full-document PDF generation test
