# OPT-003 — VariableFormatter locale/currency from context

**Severity**: Medium (correctness — affects multi-currency/multi-locale)
**Effort**: M (1-2 hours)
**File**: `backend/src/main/java/.../template/VariableFormatter.java:43`

## Problem

`VariableFormatter` hardcodes `Locale.of("en", "ZA")` and always formats as ZAR. The `loopTable` `format: "currency"` attribute (added in GAP-P49-018) routes through this same formatter — so all table cells render as "R 1 234,56" regardless of the invoice's actual currency.

This is correct for the `accounting-za` vertical but blocks multi-currency support (ADR-041).

## Analysis

The existing data model already supports multi-currency:
- `OrgSettings.defaultCurrency` — org-level default (e.g., "ZAR", "USD")
- `Invoice.currency` — per-invoice override (not yet implemented but designed in ADR-041)
- ADR-041 (multi-currency) is the governing decision

The TODO at line 42 already references this: "Support multi-currency via OrgSettings.defaultCurrency / invoice.currency".

## Fix

### Step 1: Make VariableFormatter accept locale

Change `format()` from static utility to accept a `Locale` parameter:

```java
public static String format(Object value, String typeHint, Locale locale) {
    // ...
    case "currency" -> formatCurrency(value, locale);
    case "number" -> formatNumber(value, locale);
    // ...
}

// Overload for backward compatibility:
public static String format(Object value, String typeHint) {
    return format(value, typeHint, ZA_LOCALE); // default fallback
}
```

### Step 2: Pass locale from rendering context

In `TiptapRenderer.renderLoopTable()` and `renderNode()`, pass the locale from the template context:

```java
// Context should contain "locale" or derive from org settings
Locale renderLocale = (Locale) context.getOrDefault("_locale", ZA_LOCALE);
String formatted = VariableFormatter.format(value, format, renderLocale);
```

### Step 3: Context builders populate locale

In each context builder (`ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder`), add locale to context:

```java
// From OrgSettings:
orgSettingsRepository.findForCurrentTenant().ifPresent(settings -> {
    String currencyCode = settings.getDefaultCurrency(); // "ZAR", "USD", etc.
    context.put("_locale", resolveLocale(currencyCode));
});
```

### Locale resolution

```java
private static Locale resolveLocale(String currencyCode) {
    if (currencyCode == null) return Locale.of("en", "ZA");
    return switch (currencyCode) {
        case "ZAR" -> Locale.of("en", "ZA");
        case "USD" -> Locale.US;
        case "GBP" -> Locale.UK;
        case "EUR" -> Locale.GERMANY; // or Locale.FRANCE depending on preference
        default -> Locale.of("en", "ZA"); // safe fallback
    };
}
```

### Impact

- Correct currency formatting for non-ZAR organizations
- Backward compatible — ZAR remains default
- Foundation for ADR-041 multi-currency

### Tests

1. Test formatting with ZAR locale (existing behavior)
2. Test formatting with USD locale
3. Test formatting with GBP locale
4. Test context builder populates `_locale` from OrgSettings
5. Test default fallback when no OrgSettings exist
