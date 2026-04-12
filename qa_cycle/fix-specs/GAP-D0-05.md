# Fix Spec: GAP-D0-05 — Industry dropdown should say "Legal Services" not "Legal"

## Problem
The access-request form's Industry dropdown lists "Legal" as one of the options. The scenario (`qa/testplan/demos/legal-za-90day-keycloak.md`) instructs QA to select "Legal Services". This is minor copy drift between the scripted demo narrative and the actual dropdown.

## Root Cause (confirmed)
File: `frontend/lib/access-request-data.ts`, lines 13–22:

```ts
export const INDUSTRIES = [
  "Accounting",
  "Legal",            // ← line 15
  "Consulting",
  "Engineering",
  "Architecture",
  "IT Services",
  "Marketing",
  "Other",
];
```

The constant is consumed by `frontend/components/access-request/request-access-form.tsx` at line 378 via `{INDUSTRIES.map(...)}`. No other place in the codebase hardcodes "Legal" as an industry option for this dropdown.

## Fix
In `frontend/lib/access-request-data.ts`, line 15:
- Change `"Legal",` to `"Legal Services",`.

No other changes. The `industry` value is stored as free-form string on the `access_requests` row (see `AccessRequest` entity, column `industry`) — no enum, no FK, so changing the display label is safe. Backend does not branch on the exact value ("Legal" vs "Legal Services") for any vertical-profile resolution — the vertical profile (`legal-za`) is assigned by a separate pack-detection path, confirmed by QA in CP 0.17 where `vertical_profile = legal-za` was assigned independently of the industry string.

## Scope
- Frontend
- Files to modify:
  - `frontend/lib/access-request-data.ts`
- Files to create: none
- Migration needed: no

## Verification
1. Re-run CP 0.3 (form fields present) — dropdown should list "Legal Services" not "Legal".
2. Re-run CP 0.4 (fill form) — select "Legal Services", submit, OTP flow continues.
3. Verify `public.access_requests` row for the submission has `industry = 'Legal Services'`.
4. Re-run CP 0.17 — `tenant_<hash>.org_settings.vertical_profile` is still `legal-za` (industry string does not gate this).

## Estimated Effort
S (< 5 min)

## Priority Reason
Trivial change, aligns the UI with the scripted demo narrative, removes a QA friction point.
