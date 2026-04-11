# Fix Spec: GAP-D1-07 — Template name placeholder overrides user input

## Problem
When creating a matter from a legal template (e.g. Litigation), the project name uses the template's `namePattern` like `{client} - {type}` instead of the user-entered name. The tokens `{client}`, `{type}`, `{deceased}`, `{debtor}`, `{transaction}` are never resolved.

Related: GAP-D30-05 (invoice project column shows the same placeholder).

## Root Cause (confirmed)
**Two-part problem:**

1. **Legal templates use unsupported tokens.** The legal-za template pack (`backend/src/main/resources/project-template-packs/legal-za.json`) defines patterns like:
   - `{client} - {type}` (Litigation)
   - `{client} - Estate of {deceased}` (Estates)
   - `{client} - Collections v {debtor}` (Collections)
   - `{client} - {transaction}` (Commercial)

2. **Neither the frontend nor backend `NameTokenResolver` handles these tokens.** Both only support: `{customer}`, `{month}`, `{month_short}`, `{year}`, `{period_start}`, `{period_end}`.

3. **Frontend pre-fills with unresolved pattern.** `NewFromTemplateDialog` (line 94-99) calls `resolveNameTokens()` which leaves `{client}`, `{type}` unreplaced. The pre-filled name input shows `{client} - {type}`. If the user types a different name, the `handleCustomerChange` callback (line 162-169) OVERWRITES it with the pattern every time the customer dropdown changes.

## Fix

### Part 1: Map `{client}` to `{customer}` in templates (backend)
Since `{customer}` IS already supported by `NameTokenResolver`, change the legal template pack to use `{customer}`:

**`backend/src/main/resources/project-template-packs/legal-za.json`:**
- Litigation: `"{customer} - Litigation"` (replace `{client} - {type}` with concrete type)
- Estates: `"{customer} - Estate of {deceased}"` (keep `{deceased}` as manual-fill)
- Collections: `"{customer} - Collections v {debtor}"` (keep `{debtor}` as manual-fill)
- Commercial: `"{customer} - {transaction}"` (keep `{transaction}` as manual-fill)

### Part 2: Add `{client}` as an alias for `{customer}` in both resolvers

**`backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/NameTokenResolver.java`:**
Add after `{customer}` replacement:
```java
result = result.replace("{client}", customer.getName());
```

**`frontend/lib/name-token-resolver.ts`:**
Add after `{customer}` replacement:
```typescript
result = result.replace(/\{client\}/g, customerName);
```

### Part 3: Stop overwriting user-typed names in the dialog

**`frontend/components/templates/NewFromTemplateDialog.tsx`:**
In `handleCustomerChange` (line 162-169), only update `projectName` if it still matches the previously resolved pattern (i.e., the user hasn't modified it):

```typescript
function handleCustomerChange(newCustomerId: string) {
  setCustomerId(newCustomerId);
  if (selectedTemplate) {
    const oldPreview = resolveNameTokens(selectedTemplate.namePattern, selectedCustomerName, new Date());
    const newCustomerName = customers.find((c) => c.id === newCustomerId)?.name;
    const newPreview = resolveNameTokens(selectedTemplate.namePattern, newCustomerName, new Date());
    // Only auto-update if user hasn't manually changed the name
    if (projectName === oldPreview || projectName === selectedTemplate.namePattern) {
      setProjectName(newPreview);
    }
  }
}
```

### Part 4: Leave unreplaced tokens as empty or descriptive placeholders
Any token like `{deceased}`, `{debtor}`, `{transaction}` that doesn't have a resolver should be stripped from the pre-filled name. Add a cleanup step after resolution:
```typescript
result = result.replace(/\{[a-z_]+\}/g, '').replace(/\s{2,}/g, ' ').trim();
```

## Scope
- `backend/src/main/resources/project-template-packs/legal-za.json` (4 namePattern changes)
- `backend/src/main/java/.../projecttemplate/NameTokenResolver.java` (add `{client}` alias)
- `frontend/lib/name-token-resolver.ts` (add `{client}` alias + strip unresolved tokens)
- `frontend/components/templates/NewFromTemplateDialog.tsx` (stop overwriting user input)
- Existing projects with `{client} - {type}` names need no migration (cosmetic only in past data)

## Verification
1. Open Projects page > "New from Template"
2. Select Litigation template
3. Select a customer — name pre-fills as "Sipho Ndlovu - Litigation"
4. Change the name to "RAF Claim - Personal Injury"
5. Change the customer dropdown — verify name is NOT overwritten
6. Create the project — verify the entered name is used
7. Check invoice line items — project column shows proper name
8. Run `pnpm test` and `./mvnw test`

## Estimated Effort
1 hour
