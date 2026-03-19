# ADR-194: Retention Policy Granularity

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 50 (Data Protection Compliance)

## Context

DocTeams already has a `RetentionPolicy` entity (created in V29, Phase 14) with `recordType`, `retentionDays`, `triggerEvent`, and `action` fields. The entity supports per-type retention rules — one policy per `(recordType, triggerEvent)` pair, enforced by a unique constraint. `RetentionService.runCheck()` evaluates active policies for CUSTOMER, AUDIT_EVENT, and DOCUMENT record types.

Phase 50 introduces jurisdiction-aware defaults (POPIA requires 5 years for financial records, 7 years best practice for audit trails) and adds TIME_ENTRY as a record type. The question is whether the existing per-entity-type granularity is sufficient, or whether we need finer-grained control (per-record retention) or coarser control (a single global policy).

The existing `RetentionPolicy` table has a unique constraint on `(record_type, trigger_event)`, meaning there can be at most one policy per combination. This constraint would need to change for per-record policies.

## Options Considered

### Option 1: Global Retention Policy (Single Configuration)

Replace per-type policies with a single retention period applied to all entity types uniformly. Configuration would be a single `default_retention_months` on `OrgSettings`.

- **Pros:**
  - Simplest possible configuration — one number to set
  - No per-type complexity for small firms
  - Easy to explain to non-technical users

- **Cons:**
  - Cannot differentiate between financial records (5-year legal minimum) and comments (no legal requirement)
  - Would force firms to set 5 years for everything — even data types where 1-2 years is appropriate
  - Comments and audit events have very different retention needs
  - Cannot satisfy POPIA Section 14(1) which requires retention only "for the period for which the purpose of processing reasonably requires" — different purposes require different periods

### Option 2: Per-Entity-Type Policies (Current Model — Selected)

Keep the existing model: one policy per `(recordType, triggerEvent)` pair. Extend with descriptions, financial minimum enforcement, and new record types. Seed jurisdiction-specific defaults when a tenant configures their jurisdiction.

- **Pros:**
  - Already implemented and working — minimal code change
  - Granularity matches the regulatory reality — financial records, audit trails, and communications have genuinely different retention requirements
  - Settings UI is manageable (5-6 rows in a table, not hundreds)
  - Jurisdiction defaults can be seeded as a set of policies
  - Unique constraint `(record_type, trigger_event)` prevents duplicate/conflicting policies
  - Financial minimum enforcement applies cleanly per record type

- **Cons:**
  - Cannot set different retention for different customers' data (e.g., high-risk clients vs. standard)
  - Cannot set different retention for different projects within a customer
  - Adding a new entity type requires code changes (new case in `RetentionService.runCheck()`)

### Option 3: Per-Record Retention Policies

Allow individual records (e.g., a specific customer, a specific document) to have their own retention override. This could be implemented as a `retention_override_days` column on each entity or as a join table `record_retention_overrides(entity_type, entity_id, retention_days)`.

- **Pros:**
  - Maximum flexibility — each record can have its own retention
  - Could handle per-customer retention requirements (e.g., a client's contract specifies 7-year retention)
  - Fine-grained compliance for regulated industries with per-engagement requirements

- **Cons:**
  - Massive configuration burden — tens of thousands of records, each potentially needing review
  - Evaluation job becomes expensive — must check each record individually rather than batch queries by type
  - Settings UI becomes unmanageable — cannot display per-record overrides in a simple table
  - Overkill for the current user base (small-to-medium professional services firms with 50-500 customers)
  - The `retention_policies` unique constraint on `(record_type, trigger_event)` would need to be dropped, losing duplicate prevention
  - No existing B2B SaaS in the professional services space offers this granularity

## Decision

**Option 2 — Per-entity-type policies (existing model, extended).**

## Rationale

The existing per-entity-type model hits the right balance for DocTeams' target market. Small-to-medium professional services firms (3-50 staff) have straightforward retention needs: financial records for 5 years (legal mandate), audit trails for 7 years (best practice), communications for 2-3 years (operational), and everything else following a sensible default.

Per-record policies (Option 3) solve a problem that doesn't exist for our users. A 10-person accounting firm is not going to configure individual retention periods for each of their 200 customers. They want to set "financial records: 5 years" once and move on. The per-type model lets them do exactly that.

The existing unique constraint on `(record_type, trigger_event)` is a feature, not a limitation. It prevents conflicting policies (e.g., two policies saying "delete customers after 2 years" and "anonymize customers after 5 years") and makes the evaluation logic deterministic.

Phase 50 extends this model with:
1. **Financial minimum enforcement** — retention for financial record types cannot be set below `financialRetentionMonths` from OrgSettings
2. **Jurisdiction-aware seeding** — when a tenant sets their jurisdiction, sensible defaults are created
3. **TIME_ENTRY support** — extending the set of supported record types
4. **Description field** — human-readable explanation in the settings UI

If per-record retention becomes necessary (e.g., for enterprise clients with per-engagement SLAs), it can be added as an override layer on top of the per-type base without breaking the existing model.

## Consequences

- **Positive:**
  - Minimal code change — extends existing model rather than replacing it
  - Configuration remains simple (5-6 policies per tenant)
  - Financial minimum enforcement prevents accidental non-compliance
  - Jurisdiction seeding gives tenants a working baseline immediately
  - Evaluation job remains efficient — batch queries by type, not per-record

- **Negative:**
  - Cannot differentiate retention within a record type (e.g., high-risk customer vs. standard customer)
  - Adding new entity types to retention evaluation requires code changes in `RetentionService`
  - If a tenant has unusual per-customer retention requirements, they must manage those manually

- **Neutral:**
  - The `triggerEvent` field provides limited per-context granularity within a type (e.g., "delete documents when CUSTOMER_OFFBOARDED" vs. "delete documents when RECORD_CREATED")
  - The existing V29 migration and table structure are unchanged — only new columns are added
