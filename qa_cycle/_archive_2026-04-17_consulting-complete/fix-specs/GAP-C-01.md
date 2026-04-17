# Fix Spec: GAP-C-01 — Industry → Profile mapping missing Marketing & Consulting

## Problem

At Day 0 checkpoint 0.10, the scenario asserts that selecting industry "Marketing" or "Consulting" during the access-request flow must auto-assign `vertical_profile = consulting-za` to the newly provisioned org. Currently the backend's `INDUSTRY_TO_PROFILE` map only contains entries for `Accounting` → `accounting-za` and `Legal Services` → `legal-za`. Any other industry — including both choices the scenario exercises — silently resolves to `null`, so no vertical profile is assigned and none of the `consulting-za` packs (rate, template, automation, clause, request, custom-field) get installed for the tenant. In the 2026-04-14 QA run this cascaded into 6 downstream false-positive gaps (missing rate defaults, missing templates, missing automations, etc.).

## Root Cause (confirmed)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`

Lines 29–32:

```java
private static final Map<String, String> INDUSTRY_TO_PROFILE =
    Map.of(
        "Accounting", "accounting-za",
        "Legal Services", "legal-za");
```

Line 105 looks it up: `INDUSTRY_TO_PROFILE.get(request.getIndustry())` — returns `null` for anything else, and the caller treats null as "no profile".

The frontend industry dropdown (`frontend/lib/access-request-data.ts`) already offers both `Marketing` and `Consulting` as options, so no frontend change is needed.

## Fix

1. Edit `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`:
   - Replace `Map.of(...)` (which is capped at 10 pairs but currently holds 2) with an explicit `Map.of` that includes:
     - `"Accounting"` → `"accounting-za"` (existing)
     - `"Legal Services"` → `"legal-za"` (existing)
     - `"Marketing"` → `"consulting-za"` (NEW)
     - `"Consulting"` → `"consulting-za"` (NEW)
2. Write (or extend) a unit test in `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/` that asserts:
   - Input industry "Marketing" resolves to vertical profile "consulting-za"
   - Input industry "Consulting" resolves to vertical profile "consulting-za"
   - Input industry "Accounting" still resolves to "accounting-za" (regression guard)
   - Input industry "Legal Services" still resolves to "legal-za" (regression guard)
   - Input industry "Other" still resolves to null (unmapped path preserved)

   If no test class for `AccessRequestApprovalService` exists yet (search first), prefer extending an existing `AccessRequest*Test`. Otherwise add a small focused test — do NOT boot the full Spring context just for this.

3. Keep the map definition small and readable. Do not introduce a config file or builder pattern — YAGNI.

## Scope

Backend only.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java` (4-line change: add 2 entries to the map)

Files to create (or extend):
- A test under `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/` — check for existing `AccessRequest*Test.java` first and extend it rather than creating a new class if possible.

Migration needed: no.

## Verification

After merge + backend restart:

- Checkpoint **0.10** — QA agent submits an access request with industry "Marketing", platform admin approves, and after approval the org's `vertical_profile` is `consulting-za`.
- Checkpoints **0.28–0.52** — the `consulting-za` packs (rate, project template, clause, request, automation) appear pre-seeded for the new tenant.

## Estimated Effort

S — under 30 minutes (map edit + 1 test + verify).
