# 5 Architectural Debts Found in Code Review (And When to Fix Them)

*Part 3 of "Lessons from 843 Reviews" — real bugs, real fixes, and the patterns behind them. This is the final post.*

---

Not everything code review catches needs to be fixed immediately. Some findings are debts — they work today but will cause problems at scale. The skill is knowing which debts are load-bearing (fix now) and which are manageable (fix when they hurt).

Here are 5 architectural debts from real code reviews, with honest assessments of when each one actually matters.

## 1. Slug-Based Clause Lookup: O(n) per Block

**File**: `TiptapRenderer.java`

The document renderer finds clauses by slug for each clause block in a template:

```java
// For each clause block in the template (4-7 blocks per engagement letter):
clauses.values().stream()
    .filter(c -> c.getSlug().equals(blockSlug))
    .findFirst()
    .orElse(null);
```

With 4-7 clause blocks and ~20 clauses per template, this is 80-140 comparisons. Technically O(n*m) where n=blocks and m=clauses.

**When it matters**: When clause libraries grow beyond ~50 clauses per template, or when rendering multiple documents in a batch (e.g., generating all client engagement letters for annual renewals). At that point, 7 blocks * 50 clauses * 200 clients = 70,000 comparisons.

**The fix**: Pre-build a `Map<String, Clause>` slug index before passing to the renderer. Takes the loop from O(n*m) to O(n+m). A 10-line change.

**When to fix**: Before building batch document generation. For single-document rendering, it's invisible.

## 2. DB Queries Per Template Render

**File**: `TemplateContextHelper.java`

```java
// Called once per entity type used in the template
public Map<String, String> resolveDropdownLabels(EntityType entityType) {
    var fields = fieldDefinitionRepository
        .findByEntityTypeAndActiveTrueOrderBySortOrder(entityType);
    // ... build label map
}
```

A project engagement letter template triggers:
- `resolveDropdownLabels(PROJECT)` — for project custom fields
- `resolveDropdownLabels(CUSTOMER)` — for customer custom fields
- `resolveDropdownLabels(CUSTOMER)` again — nested in project context builder

Three DB queries for field definitions that don't change within a request. Four queries for an invoice template.

**When it matters**: Batch operations. Generating 50 client statements means 50 * 4 = 200 identical DB queries. Also matters when rendering previews during template editing — each keystroke triggers a re-render with fresh DB queries.

**The fix**: Request-scoped cache. Load field definitions once per entity type per request:

```java
@RequestScope
@Component
public class FieldDefinitionCache {
    private final Map<EntityType, List<FieldDefinition>> cache =
        new EnumMap<>(EntityType.class);

    public List<FieldDefinition> getFields(EntityType entityType) {
        return cache.computeIfAbsent(entityType,
            type -> fieldDefinitionRepository
                .findByEntityTypeAndActiveTrueOrderBySortOrder(type));
    }
}
```

**When to fix**: Before building batch rendering or real-time template preview. For single-document generation, 3-4 queries at 5ms each is 20ms — unnoticeable.

## 3. Hardcoded ZA Locale in Currency Formatting

**File**: `VariableFormatter.java`

```java
private static final Locale DEFAULT_LOCALE = Locale.of("en", "ZA");

public String formatCurrency(Object value) {
    return NumberFormat.getCurrencyInstance(DEFAULT_LOCALE).format(value);
}
```

Every currency value in templates, invoices, and reports renders as ZAR with South African formatting (`R 1 500,00`). This is correct for the `accounting-za` vertical — which is the only vertical in production.

**When it matters**: When a second vertical launches with a different currency (USD for a US consulting practice, GBP for a UK law firm), or when a South African firm has international clients billed in USD.

**The fix**: Pass locale from the rendering context:

```java
public String formatCurrency(Object value, Locale locale, String currencyCode) {
    var format = NumberFormat.getCurrencyInstance(locale);
    format.setCurrency(Currency.getInstance(currencyCode));
    return format.format(value);
}
```

The `OrgSettings` entity already has a `defaultCurrency` field. The rendering context builder already has access to `OrgSettings`. The wiring just needs to flow through to `VariableFormatter`.

**When to fix**: Before launching a second vertical with a different currency. For accounting-za only, it's correct as-is.

## 4. InvoiceContextBuilder Reads Raw Custom Fields

**File**: `InvoiceContextBuilder.java`

```java
// Top-level alias reads from raw entity storage
String customerVatNumber = (String) customer.getCustomFields().get("vat_number");

// But the nested customer context has resolved field values
Map<String, Object> resolvedFields =
    resolveDropdownLabels(customer.getCustomFields());
context.put("customer", Map.of("customFields", resolvedFields));
```

Two different representations of the same data in the same template context. The `customerVatNumber` alias uses the raw storage value; `customer.customFields.vat_number` uses the resolved value.

For a TEXT field like `vat_number`, these are identical. But if someone changes the field type from TEXT to DROPDOWN (say, adding a validation list of known VAT number formats), the alias would return the stored value key while the nested context would return the display label. Inconsistent rendering.

**When it matters**: When field types change. Which shouldn't happen often — but "shouldn't" and "won't" are different words.

**The fix**: Read the alias from the resolved context map instead of from the raw entity:

```java
String customerVatNumber = (String) resolvedFields.get("vat_number");
```

**When to fix**: Honestly, now. It's a one-line change. The risk of leaving it is low, but the fix cost is also low. The kind of debt where the interest payment exceeds the principal.

## 5. Missing Portal Acceptance Page

**Severity**: Blocker for the e-signing workflow

This isn't a code quality debt — it's a feature gap. But it was found in code review and it's the most impactful item on the list.

The backend is fully built:
- `PortalAcceptanceController.java` — view, download PDF, accept with typed signature
- `AcceptanceService.java` — token resolution, expiry, revocation
- `AcceptanceCertificateService.java` — generates Certificate of Acceptance with SHA-256 hash

The firm-side frontend is built:
- `SendForAcceptanceDialog.tsx` — send document for acceptance
- `AcceptanceDetailPanel.tsx` — view status, download certificate
- `AcceptanceStatusBadge.tsx` — visual indicator

What's missing: the client-facing page where the recipient actually views the document and clicks "I Accept." The entire Track 6 of the QA cycle (25 checkpoints) couldn't be tested.

**When it matters**: Now. Without this page, the e-signing workflow doesn't exist. Engagement letters can be generated and sent, but can't be accepted digitally. The firm falls back to email + manual tracking — which is what they're already doing without DocTeams.

**The fix**: One page at `app/portal/accept/[token]/page.tsx`. Token-based auth (outside the authenticated layout). PDF viewer, name input, accept button, confirmation screen. Estimated: half a day.

**When to fix**: Before the first customer demo. This is the kind of gap that makes the difference between "this is a real product" and "this is a prototype."

## The Decision Framework

After seeing hundreds of review findings, I use this framework to decide when to fix:

**Performance debt** (O(n squared), N+1 queries) — invisible now, blocks scale later. Fix before the operation that triggers it at scale. Example: clause lookup before batch rendering ships.

**Data inconsistency** (raw vs resolved values) — silent corruption risk with low probability. Fix now if the fix is trivial. Example: one-line change to read from resolved map.

**Hardcoded values** (locale, currency) — blocks next vertical or customer segment. Fix before the next vertical launches. Example: ZAR formatting before a USD customer.

**Missing features** (portal acceptance page) — blocks core workflow today. Fix before first customer. No debt — it's unfinished work.

**Convention violations** (naming, patterns) — erodes codebase consistency over time. Fix during the review cycle, never defer. A convention violation that ships becomes the new convention.

The key insight: **debt that blocks the next step is urgent. Debt that slows down an operation nobody does yet is not.** Fix the portal page now. Fix the O(n squared) clause lookup before batch rendering ships. Leave the currency formatting until the second vertical is imminent.

Code review's job isn't to make everything perfect. It's to make sure you know what's imperfect and why.

---

*This is the final post in "Lessons from 843 Reviews." The series covered:*

1. *[12 Bugs That Almost Shipped](01-twelve-bugs-that-almost-shipped.md) — null cascades, tenant leaks, race conditions, seed script gaps*
2. *[The QA Cycle](02-qa-cycle-bugs.md) — 12 bugs found by AI Playwright in 90 minutes*
3. *[5 Architectural Debts](03-architectural-debt.md) — which to fix now, which to defer*

*More series: [One Dev, 843 PRs](/blog/series-1-one-dev-843-prs/), [Multi-Tenant from Scratch](/blog/series-2-multi-tenant-from-scratch/), [From Generic to Vertical](/blog/series-3-generic-to-vertical/), [Modern Java for SaaS](/blog/series-4-modern-java-for-saas/)*
