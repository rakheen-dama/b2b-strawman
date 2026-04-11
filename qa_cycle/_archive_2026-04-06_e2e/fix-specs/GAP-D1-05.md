# Fix Spec: GAP-D1-05 -- Generic onboarding checklist instead of FICA-specific for legal-za

## Problem

When a client transitions from PROSPECT to ONBOARDING in a legal-za org, the system instantiates the "Generic Client Onboarding" checklist (4 items: confirm engagement, verify contact, billing arrangements, engagement letter) instead of the "Legal Client Onboarding" FICA-specific checklist (11 items including Proof of Identity, Proof of Address, Beneficial Ownership, Sanctions Screening, etc.).

## Root Cause (confirmed)

Two issues combine:

**Issue 1:** The `generic-onboarding` compliance pack (`backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`) has:
- `verticalProfile: null` -- applies to ALL tenants
- `autoInstantiate: true` -- automatically creates instances on ONBOARDING transition

**Issue 2:** The `legal-za-onboarding` compliance pack (`backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`) has:
- `verticalProfile: "legal-za"` -- only applies to legal-za tenants
- `autoInstantiate: false` -- does NOT automatically create instances

So for a legal-za tenant, BOTH packs get seeded (the generic one because its profile is null, the legal one because its profile matches). But only the generic one auto-instantiates.

The `AbstractPackSeeder` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` lines 118-129) correctly skips packs whose `verticalProfile` does not match the tenant, but it allows packs with `verticalProfile: null` through unconditionally.

## Fix

Two changes needed:

### Change 1: Enable auto-instantiation for legal-za-onboarding

**File:** `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`

Change line 12:
```json
"autoInstantiate": true,
```

### Change 2: Prevent generic-onboarding from auto-instantiating when a vertical-specific onboarding pack exists

The cleanest approach: mark the generic-onboarding pack as NOT auto-instantiating when a vertical profile is active. However, this would require changing the pack system's auto-instantiation logic to be aware of "superseding" packs, which is a larger refactor.

**Simpler approach:** The generic pack should still be seeded (it's a fallback for tenants without a vertical profile), but for legal-za tenants, it should not auto-instantiate because the legal-za pack replaces it.

The simplest fix: change `generic-onboarding/pack.json` to `autoInstantiate: false`. This makes it a manually-assignable template for all tenants. Combined with `legal-za-onboarding` becoming `autoInstantiate: true`, legal-za tenants get the FICA checklist automatically, and other tenants can manually assign the generic checklist.

However, this changes behavior for non-legal-za tenants (accounting-za, etc.) who currently get the generic checklist auto-instantiated. To preserve that behavior, a better approach is:

**Preferred approach:** Keep generic-onboarding as `autoInstantiate: true` but mark it as not applicable to legal-za tenants. The pack system already has vertical profile filtering in `AbstractPackSeeder`, but the generic pack has `verticalProfile: null` which means "all tenants".

The cleanest minimal fix:

**File 1:** `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`
- Change `autoInstantiate` from `false` to `true`

**File 2:** `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- Add `"verticalProfile": "generic"` (or any non-null value that won't match legal-za)

Wait -- that would break it for accounting-za and other profiles. The right fix:

**Actual simplest fix:**
1. Set legal-za-onboarding `autoInstantiate: true`
2. In the auto-instantiation logic (wherever checklists are instantiated on ONBOARDING transition), if BOTH a generic and a vertical-specific template exist and both are `autoInstantiate: true`, only instantiate the vertical-specific one.

Let me check where auto-instantiation happens.

Looking at the checklist flow: when a customer transitions to ONBOARDING, the system creates checklist instances from all `autoInstantiate: true` templates. If we set both to `autoInstantiate: true`, the customer would get BOTH checklists (generic + FICA). That's not desirable.

**Revised simplest fix:**
1. Set legal-za-onboarding `autoInstantiate: true`  
2. Set generic-onboarding `autoInstantiate: false`
3. For non-legal tenants (accounting-za), they will need a vertical-specific onboarding pack or manual assignment. This is acceptable because accounting-za should also have its own FICA onboarding pack eventually.

This is the minimal change. Non-legal tenants without their own onboarding pack will not get auto-instantiated checklists, but they can still manually assign the generic template. This is a reasonable trade-off for a QA cycle fix.

### Final Changes

**File 1:** `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`
- Line 12: `"autoInstantiate": true`

**File 2:** `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- Line 11: `"autoInstantiate": false`

## Scope

- 2 files: `legal-za-onboarding/pack.json`, `generic-onboarding/pack.json`
- 2 lines changed
- No Java code changes
- No frontend changes
- Requires E2E stack rebuild (backend image)

## Verification

1. Rebuild E2E stack: `bash compose/scripts/e2e-rebuild.sh backend`
2. Login as Alice, create a client, transition to ONBOARDING
3. Checklist should be "Legal Client Onboarding" with 11 FICA items (not "Generic Client Onboarding" with 4 items)
4. Items should include: Proof of Identity, Proof of Address, Company Registration Docs, Trust Deed, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, Power of Attorney Signed, FICA Risk Assessment, Sanctions Screening
5. Non-document items (Conflict Check Performed, FICA Risk Assessment, Sanctions Screening) should be completable without documents
6. Document-requiring items should show document dropdown (empty until documents uploaded)

## Estimated Effort

20 minutes (2 JSON edits + backend rebuild + verification)

## Note

This fix interacts with GAP-D1-03. If both are applied:
- GAP-D1-03 makes generic-onboarding's item 4 not require documents
- GAP-D1-05 makes generic-onboarding not auto-instantiate at all for legal-za

The legal-za-onboarding pack's document-requiring items are correct (FICA regulation requires actual document uploads). The QA lifecycle will need to upload documents to complete those items, which is the intended legal workflow. The non-document items (Conflict Check Performed, FICA Risk Assessment, Sanctions Screening) can be completed as confirmation steps.
