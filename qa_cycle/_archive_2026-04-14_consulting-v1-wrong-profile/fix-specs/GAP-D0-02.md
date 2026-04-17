# Fix Spec: GAP-D0-02 — Rate cards not pre-seeded from accounting-za profile

## Problem
Settings > Rates shows all three members (Thandi, Bob, Carol) with "Not set" billing rates. The accounting-za vertical profile has a rate pack (`rate-packs/accounting-za.json`) but it seeds org-level role-based rate tiers (Partner R2500, Manager R1800, Senior Accountant R1200, Clerk/Trainee R650), not individual member billing rates. Reported at Day 0 checkpoint 0.30.

## Root Cause (hypothesis)
The `RatePackSeeder` creates `BillingRate` records with `memberId = null` — these are org-level rate tiers, not member-specific rates. The Settings > Rates page shows per-member rates, which are separate records that need to be created by assigning a rate tier to each member during onboarding. This is by design — the seeder provides the rate schedule, but member assignment is a manual step.

The rate pack JSON (`rate-packs/accounting-za.json`) uses role descriptions ("Partner", "Manager", etc.) that don't directly map to Keycloak roles ("Owner", "Admin", "Member"). Compare with the vertical profile JSON (`vertical-profiles/accounting-za.json`) which has `rateCardDefaults` with role-based rates (Owner R1500, Admin R850, Member R450).

## Fix
**WONT_FIX for this cycle.** The rate pack seeder works as designed — it seeds org-level rate tiers. Member rates are intentionally assigned manually. The test plan acknowledges this: "create manually to match — non-blocking."

The vertical profile's `rateCardDefaults` object IS meant to auto-assign rates to members, but this feature may not be implemented in the provisioning flow. If desired in a future cycle:
1. During provisioning, after seeding rate packs AND creating the initial member (owner), apply the `rateCardDefaults` from the vertical profile to assign member-level billing/cost rates based on role.
2. This would require the provisioning service to resolve `rateCardDefaults.billingRates[].roleName` to actual member roles and create per-member BillingRate records.

## Scope
N/A (WONT_FIX)

## Verification
N/A

## Estimated Effort
L (> 2 hr) — requires new provisioning logic
