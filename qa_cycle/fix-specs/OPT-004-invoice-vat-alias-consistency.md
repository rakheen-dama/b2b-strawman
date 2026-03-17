# OPT-004 — InvoiceContextBuilder customerVatNumber alias consistency

**Severity**: Low (latent bug — doesn't bite today because vat_number is TEXT, not DROPDOWN)
**Effort**: XS (5 min)
**File**: `backend/src/main/java/.../template/InvoiceContextBuilder.java:114-118`

## Problem

The top-level `customerVatNumber` alias reads directly from `customer.getCustomFields()` (raw storage value), while `customer.customFields` in the context map has already been through `resolveDropdownLabels()`.

```java
// Line 107-109: Resolved (dropdown labels applied)
customerMap.put("customFields",
    contextHelper.resolveDropdownLabels(customer.getCustomFields(), EntityType.CUSTOMER));

// Lines 114-118: Raw (no dropdown resolution)
var customFields = customer.getCustomFields();
if (customFields != null && customFields.containsKey("vat_number")) {
    context.put("customerVatNumber", customFields.get("vat_number"));
}
```

Since `vat_number` is a TEXT field in the accounting-za field pack, both paths return the same value today. But if anyone changes it to a DROPDOWN (e.g., for VAT registration types), the alias would show the raw value while `customer.customFields.vat_number` would show the display label.

## Fix

Read the alias from the already-resolved customer map:

```java
// After customerMap is built (line 111):
var resolvedCustomFields = (Map<String, Object>) customerMap.get("customFields");
if (resolvedCustomFields != null && resolvedCustomFields.containsKey("vat_number")) {
    context.put("customerVatNumber", resolvedCustomFields.get("vat_number"));
} else {
    context.put("customerVatNumber", null);
}
```

### Impact

- Eliminates inconsistency between alias and nested value
- No behavioral change for current TEXT fields
- Prevents future bugs if field type changes

### Tests

Existing InvoiceContextBuilder tests cover the `customerVatNumber` alias. No new tests needed.
