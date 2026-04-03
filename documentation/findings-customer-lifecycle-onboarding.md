# Findings: Customer Lifecycle, Onboarding & Compliance Packs

**Date:** 2026-02-20
**Status:** Research complete, implementation deferred to dedicated phase

---

## Executive Summary

Phase 14 built a complete customer lifecycle state machine and compliance pack system, but the onboarding flow is non-functional because customers default to ACTIVE instead of PROSPECT. Fixing this is not a simple bug — the ACTIVE default is deeply embedded across 30+ test files and every feature built after Phase 14 (invoicing, retainers, billing rates, portal, profitability, data anonymization). This requires a dedicated planning effort.

---

## What Was Built (Phase 14)

### Lifecycle State Machine
```
PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING → OFFBOARDED
```

### Compliance Packs (3 shipped, classpath-bundled)
| Pack | Auto-instantiate | Items | Customer Type |
|------|-----------------|-------|---------------|
| `generic-onboarding` | Yes (ACTIVE) | 4 (confirm engagement, verify contacts, billing, signed letter) | ANY |
| `sa-fica-individual` | No (admin activates) | 5 (ID doc, risk assessment, FICA sign-off) | INDIVIDUAL |
| `sa-fica-company` | No (admin activates) | 6 (company reg, risk assessment, FICA sign-off) | COMPANY |

### Designed Flow
1. Create customer → starts as **PROSPECT** (can't create projects/invoices)
2. Admin clicks "Start Onboarding" → **ONBOARDING** (auto-instantiates checklist from active packs)
3. Staff completes checklist items (FICA docs, engagement letters, etc.)
4. All checklists complete → auto-transitions to **ACTIVE** (full access)

### What Actually Happens
1. Create customer → starts as **ACTIVE** (full access immediately)
2. Onboarding tab never appears (only shows for ONBOARDING status)
3. Compliance packs are seeded but checklists are never instantiated
4. The entire lifecycle UI exists but is invisible in normal workflow

---

## Root Cause

`Customer.java` constructor hardcodes:
```java
this.lifecycleStatus = LifecycleStatus.ACTIVE;  // Line 94
```

The V29 migration correctly defaults to `PROSPECT` at the database level, but the Java constructor overrides it.

---

## Additional Design Gaps Found

| # | Issue | Severity |
|---|-------|----------|
| 1 | **Default ACTIVE instead of PROSPECT** | Critical — bypasses entire onboarding |
| 2 | **PROSPECT → ACTIVE shortcut allowed** | Medium — lets admins skip onboarding |
| 3 | **OFFBOARDED is terminal (no reactivation)** | Medium — spec allows OFFBOARDED → ACTIVE |
| 4 | **Archive doesn't update lifecycle** | Medium — `status=ARCHIVED, lifecycleStatus=ACTIVE` inconsistency |
| 5 | **Frontend missing TRUST customer type** | Low — backend has INDIVIDUAL/COMPANY/TRUST, frontend only INDIVIDUAL/COMPANY |
| 6 | **Dormancy is report-only** | Low — `runDormancyCheck()` returns candidates but doesn't auto-transition |

---

## Why This Is Not a Simple Fix

Changing the default to PROSPECT breaks **54 tests across 18+ files** in domains far beyond customer management:

### Affected Domains (by failure count)

| Domain | Files | Failures | Root Cause |
|--------|-------|----------|------------|
| Customer-Project linking | 2 | 11 | `CustomerLifecycleGuard` blocks `CREATE_PROJECT` for PROSPECT |
| Portal event projection | 3 | 6 | Creates customer → links project (blocked) |
| Invoicing (controller) | 0 | 0 | Already fixed in first pass |
| Retainer controllers | 3 | 9 | Creates customer via API → needs ACTIVE for retainer creation |
| Billing rate | 2 | 2 | Creates customer → links project (blocked) |
| Profitability reports | 2 | 2 | Creates customer → links project (blocked) |
| Data anonymization | 1 | 11 | Creates customer → transitions PROSPECT → OFFBOARDING (invalid) |
| Customer audit | 1 | 2 | Creates customer via API → links project (blocked) |
| Customer lifecycle | 2 | 2 | Assertion changes (expected ACTIVE, now PROSPECT; OFFBOARDED transitions) |
| Customer readiness | 1 | 1 | Asserts default is ACTIVE |

### The Complication: Checklist Guard on ONBOARDING → ACTIVE

The transition `ONBOARDING → ACTIVE` is guarded by `CustomerLifecycleService.checkOnboardingGuard()` — it requires ALL checklist instances to be COMPLETED. Since the `generic-onboarding` pack auto-instantiates with `autoInstantiate: true`, any tenant with compliance packs seeded will have checklist instances created when a customer enters ONBOARDING. Those must be completed before ACTIVE.

This means test setup can't just do `PROSPECT → ONBOARDING → ACTIVE` via the service — it would need to complete all checklist items first. Tests can bypass this by calling `customer.transitionLifecycleStatus()` directly on the entity (which only checks the transition graph, not the service-level guard), but this pattern needs to be applied consistently across 18+ files.

---

## Recommended Approach

This should be a **dedicated mini-phase** (or a slice within a future phase) rather than a hotfix:

### Option A: Full Lifecycle Activation (Recommended)
1. Change Customer default to PROSPECT
2. Remove PROSPECT → ACTIVE shortcut (force onboarding)
3. Add OFFBOARDED → ACTIVE reactivation
4. Align archive with lifecycle
5. Add frontend TRUST type
6. Fix all 54 test failures with a consistent helper pattern
7. Add a test helper: `TestCustomerHelper.createActiveCustomer()` that creates + transitions via entity (bypassing service guard)
8. Frontend: update transition map to match

**Effort estimate:** ~2-3 hours of focused implementation

### Option B: Minimal API-Level Fix
1. Only change the API layer (`CustomerService.createCustomer()`) to accept an optional `lifecycleStatus` parameter defaulting to PROSPECT
2. Keep the constructor default as ACTIVE for backward compat in tests
3. Frontend: pass no lifecycleStatus (gets PROSPECT from API)

**Effort estimate:** ~30 minutes, but leaves internal inconsistency

### Option C: Feature Flag
1. Add an org-level setting `onboarding_required: boolean` (default false)
2. When true: new customers default to PROSPECT, onboarding flow enforced
3. When false: new customers default to ACTIVE (current behavior)
4. Lets orgs opt-in without breaking existing workflows

**Effort estimate:** ~1-2 hours, most flexible but adds complexity

---

## Key Files Reference

| Category | Path |
|----------|------|
| Customer entity | `backend/.../customer/Customer.java` (line 94) |
| Lifecycle enum | `backend/.../customer/LifecycleStatus.java` |
| Lifecycle service | `backend/.../compliance/CustomerLifecycleService.java` |
| Lifecycle guard | `backend/.../compliance/CustomerLifecycleGuard.java` |
| Checklist instantiation | `backend/.../compliance/ChecklistInstantiationService.java` |
| Pack seeder | `backend/.../compliance/CompliancePackSeeder.java` |
| Pack JSONs | `backend/.../resources/compliance-packs/*/pack.json` |
| V29 migration | `backend/.../resources/db/migration/tenant/V29__customer_compliance_lifecycle.sql` |
| Frontend transitions | `frontend/components/compliance/LifecycleTransitionDropdown.tsx` |
| Frontend types | `frontend/lib/types.ts` (line 121) |
| Requirements | `requirements/claude-code-prompt-phase14.md` |
| Architecture | `architecture/phase14-customer-compliance-lifecycle.md` |
| ADRs | `adr/ADR-060` through `ADR-063` |
