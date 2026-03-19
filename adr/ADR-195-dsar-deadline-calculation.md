# ADR-195: DSAR Deadline Calculation

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 50 (Data Protection Compliance)

## Context

Data subject access requests (DSARs) have statutory deadlines. POPIA (South Africa) requires a response within 30 days (Section 23). GDPR (EU) requires 30 days (Article 12(3)). LGPD (Brazil) requires 15 days (Article 18). DocTeams already has a `dataRequestDeadlineDays` field on `OrgSettings` (defaulting to 30) and the `DataSubjectRequestService` uses it to calculate deadlines when creating new DSARs.

Phase 50 introduces jurisdiction awareness — tenants set their `dataProtectionJurisdiction` (e.g., "ZA" for POPIA), and the system should use the correct statutory deadline. The question is how to calculate the deadline: should it always use the jurisdiction's statutory default, or should tenants be able to override it?

The existing `DataSubjectRequest.deadline` field is a `LocalDate` (not `Instant`) — it tracks the calendar date, not the exact time. This is appropriate because statutory deadlines are expressed in calendar days, not hours.

## Options Considered

### Option 1: Hardcoded Jurisdiction Defaults (No Override)

The deadline is always the statutory maximum for the configured jurisdiction. `dataRequestDeadlineDays` is ignored if a jurisdiction is set.

- **Pros:**
  - Impossible to accidentally set a deadline longer than the law allows
  - No configuration needed — just set the jurisdiction and deadlines are correct
  - Simplest implementation — a single `switch` on jurisdiction

- **Cons:**
  - Firms that want to respond faster than required (e.g., 14-day internal SLA) cannot configure that
  - If the statutory deadline changes (e.g., POPIA amendment), requires a code change
  - Ignores the existing `dataRequestDeadlineDays` field — creates confusion about what it does

### Option 2: Jurisdiction Default with Tenant Override, Capped at Statutory Maximum (Selected)

Use the jurisdiction's statutory deadline as the default. If the tenant sets a `dataRequestDeadlineDays` value, use it — but never allow it to exceed the jurisdiction maximum. Tenants can respond faster than required but never slower.

- **Pros:**
  - Firms can set an internal SLA shorter than the statutory requirement (common practice for client service)
  - Cannot accidentally exceed the statutory deadline — the cap prevents non-compliance
  - Existing `dataRequestDeadlineDays` field continues to work as expected
  - Jurisdiction default provides a sensible out-of-the-box experience
  - If no jurisdiction is set, falls back to the existing 30-day default (backward compatible)

- **Cons:**
  - Slightly more complex logic than hardcoded defaults
  - Two sources of truth (jurisdiction default + tenant override) could confuse administrators
  - The "cap" behavior needs to be communicated in the UI ("Your custom deadline of 45 days exceeds the POPIA limit of 30 days — deadline will be set to 30 days")

### Option 3: Fully Configurable Per-Tenant (No Jurisdiction Constraint)

Tenant sets whatever deadline they want. The system provides jurisdiction-specific warnings but does not enforce limits.

- **Pros:**
  - Maximum flexibility
  - No code changes needed when statutory deadlines change
  - Tenants in non-standard jurisdictions (or with no jurisdiction set) can use any deadline

- **Cons:**
  - A tenant could set 90-day deadlines, violating POPIA's 30-day requirement
  - The platform would be complicit in non-compliance — displaying a "compliant" status while the firm is operating outside the law
  - Warning-only enforcement is easy to ignore — especially by firms who don't understand the regulatory implications
  - Undermines the purpose of jurisdiction awareness — if the system doesn't enforce statutory deadlines, why track the jurisdiction at all?

## Decision

**Option 2 — Jurisdiction default with tenant override, capped at statutory maximum.**

## Rationale

The cap-at-maximum approach provides the safety of hardcoded defaults with the flexibility of tenant configuration. Most firms will never change the default — they'll set their jurisdiction, get a 30-day deadline, and move on. The few firms that want a tighter internal SLA (e.g., "we respond to all DSARs within 14 days") can configure that without risk of accidentally exceeding the statutory limit.

The implementation is clean:

```java
int deadlineDays;
if (tenantOverride != null && tenantOverride > 0) {
    deadlineDays = Math.min(tenantOverride, jurisdictionMax);
} else {
    deadlineDays = jurisdictionDefault;
}
```

Backward compatibility is preserved: tenants without a jurisdiction configured continue to use `dataRequestDeadlineDays` (or 30 if not set). Adding a jurisdiction later starts capping the deadline — this is a compliance improvement, not a breaking change.

The UI should display the effective deadline clearly: "DSAR deadline: 14 days (POPIA allows up to 30 days)" or "DSAR deadline: 30 days (POPIA statutory maximum)". This transparency helps administrators understand why their configured value might differ from the effective deadline.

## Consequences

- **Positive:**
  - Non-compliance via configuration is impossible — statutory deadline is always the upper bound
  - Existing `dataRequestDeadlineDays` continues to work — no breaking change for tenants who set it before configuring a jurisdiction
  - Firms with proactive compliance cultures can set tighter deadlines
  - The `JurisdictionDefaults` utility class centralizes all statutory values — adding a new jurisdiction is a single code change

- **Negative:**
  - Administrators may be confused when their 45-day setting is silently capped to 30 days — the UI must communicate this clearly
  - If a jurisdiction's statutory deadline changes (e.g., POPIA extends to 45 days), the `JurisdictionDefaults` class requires a code update and deployment
  - Two-source logic is slightly more complex to test than a simple lookup

- **Neutral:**
  - The `deadline` field on `DataSubjectRequest` remains a `LocalDate` — the calculation happens at creation time and is stored as a fixed date
  - The `dataRequestDeadlineDays` field on `OrgSettings` takes on dual semantics: "tenant-preferred deadline" when a jurisdiction is set (capped), "absolute deadline" when no jurisdiction is set (uncapped)
