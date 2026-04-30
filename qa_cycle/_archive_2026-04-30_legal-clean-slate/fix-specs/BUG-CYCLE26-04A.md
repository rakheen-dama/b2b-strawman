# Fix Spec: BUG-CYCLE26-04A — KYC integration card has no Enable toggle

## Problem

Settings → Integrations → KYC Verification → Configure dialog persists `org_integrations` row (`provider_slug`, `key_suffix`) but lands `enabled=false` and provides **no UI mechanism to enable it**. After Save, the card status stays "Disabled" forever; Test Connection returns "No KYC provider configured". Backend adapters (`CheckIdKycAdapter`, `VerifyNowKycAdapter`) and the `PATCH /api/integrations/{domain}/toggle` endpoint exist and work — the firm UI just never calls them.

This is the **Day 2 wow-moment-2 (KYC Verified) blocker**. With the integration disabled, the existing onboarding-tab "Verify Now" button is gated off (`kycConfigured && item.verificationProvider`) and Sipho cannot be KYC-verified end-to-end.

Evidence: `qa_cycle/checkpoint-results/day-02.md` §2.8 (cycle-6) and the cycle-6 verification at line 107: "entered API key … → Save → DB org_integrations row updated key_suffix='-12345', enabled STILL false. KYC card still 'Disabled' badge. No Enable toggle exposed."

## Root Cause (verified)

`frontend/components/integrations/KycIntegrationCard.tsx` only wires `Configure` and `Remove` — no toggle. Compare to `PaymentIntegrationCard.tsx:476–492` which has a working pattern:

```tsx
{hasProvider && (
  <div className="flex items-center justify-between">
    <label htmlFor="enabled-PAYMENT" ...>Enabled</label>
    <Switch
      id="enabled-PAYMENT"
      checked={isEnabled}
      onCheckedChange={handleToggle}
      disabled={!hasProvider || isTogglingEnabled}
    />
  </div>
)}
```

with `handleToggle` calling `toggleIntegrationAction(slug, "PAYMENT", { enabled: checked })` (lines 133–148). The KYC card omits both the `<Switch>` block and the `handleToggle` handler.

Server action already exists: `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts:87–105` — `toggleIntegrationAction(slug, domain, { enabled })`. It revalidates the settings page on success.

Backend endpoint already exists and is exercised: `backend/.../integration/IntegrationController.java:72–77` — `PATCH /api/integrations/{domain}/toggle` with `ToggleRequest(boolean enabled)`. Service method `IntegrationService.toggleIntegration(domain, enabled)`.

## Fix

Single-file frontend change to `frontend/components/integrations/KycIntegrationCard.tsx`:

1. Add imports:
   ```tsx
   import { Switch } from "@/components/ui/switch";
   import { toggleIntegrationAction } from "@/app/(app)/org/[slug]/settings/integrations/actions";
   ```

2. Add toggle state + handler near the existing `useState` block (~line 26):
   ```tsx
   const [isToggling, setIsToggling] = useState(false);

   async function handleToggle(checked: boolean) {
     setIsToggling(true);
     setError(null);
     try {
       const result = await toggleIntegrationAction(slug, "KYC_VERIFICATION", {
         enabled: checked,
       });
       if (!result.success) {
         setError(result.error ?? "Failed to toggle integration.");
       }
     } catch {
       setError("An unexpected error occurred.");
     } finally {
       setIsToggling(false);
     }
   }
   ```

3. Inside `<CardContent>` (between the existing Provider block and the Configure/Remove button row, so before line 98 `<div className="flex gap-2">`), insert:
   ```tsx
   {hasProvider && (
     <div className="flex items-center justify-between">
       <label
         htmlFor="enabled-KYC_VERIFICATION"
         className="text-sm font-medium text-slate-700 dark:text-slate-300"
       >
         Enabled
       </label>
       <Switch
         id="enabled-KYC_VERIFICATION"
         checked={!!integration?.enabled}
         onCheckedChange={handleToggle}
         disabled={!hasProvider || isToggling}
       />
     </div>
   )}
   ```

4. Update `KycIntegrationCard.test.tsx` (`frontend/__tests__/components/integrations/KycIntegrationCard.test.tsx`):
   - Add a test that, when `integration.providerSlug` is set and `enabled=false`, the Switch renders unchecked and toggling it calls `toggleIntegrationAction` with `{ enabled: true }`. Mock the action via `vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", ...)`.
   - Add a test that the Switch is hidden when `integration` is `null` or has no `providerSlug` (i.e., no provider configured yet → don't tease an Enable toggle).

No backend, schema, or seed changes. The toggle endpoint, service, adapters, and `enabled` column already exist.

## Scope

- **Frontend only**.
- Files to modify:
  - `frontend/components/integrations/KycIntegrationCard.tsx`
  - `frontend/__tests__/components/integrations/KycIntegrationCard.test.tsx`
- Files to create: none.
- Migration needed: no.
- Backend: no changes.

## Verification

Re-run Day 2 checkpoint 2.8 (KYC integration enable):

1. Settings → Integrations → KYC Verification → Configure → select VerifyNow → enter API key `sk-verifynow-demo-test-key-12345` → Save.
2. Card status should still show "Disabled" (matching today) — but now an **Enabled** switch is visible inside the card body.
3. Toggle the switch on. Card status should flip to **Configured** (green) immediately after the server action returns and `revalidatePath` re-renders.
4. DB check: `SELECT enabled FROM tenant_5039f2d497cf.org_integrations WHERE domain='KYC_VERIFICATION'` → `t`.
5. Settings → Integrations → KYC card → Test Connection → success (or provider-specific stub-success message), no longer "No KYC provider configured".
6. Re-run Day 2 §2.9 + §2.10 — Sipho's onboarding checklist (Day 3 FICA flow) should now show the **Verify Now** button on KYC checklist rows because `kycConfigured=true` flows through `customer-detail page → ChecklistInstancePanel → ChecklistInstanceItemRow:332`.

Frontend unit tests: `pnpm --dir frontend test KycIntegrationCard` — all green including the two new toggle tests.

## Estimated Effort

S (< 30 min) — single component edit + 2 unit tests, mirrors the PaymentIntegrationCard pattern exactly.
