# Phase 80 Follow-up — Deal Activity Tab: Audit `entityType` Casing Fix

**Status:** ✅ Done (PR #1503) — Option B (code-only, no data migration; no historical `"DEAL"` rows existed)
**Origin:** Surfaced during Epic 580 (PR #1502) review. Frontend-only epic could not fix it (backend scope).
**Scope:** Backend only. No frontend change required.
**Size:** S (single bug-class cluster — 2 service write sites + 1 test + optional data-remediation migration).

---

## 1. Problem

The deal **Activity tab** (`frontend/components/pipeline/DealActivityTab.tsx:18`) fetches audit
events with a **lowercase** `entityType = "deal"`. The audit query is an exact, case-sensitive
string equality. But deal audit rows are written with **inconsistent casing**:

| Write site | `entityType` written | Casing | Shows on Activity tab? |
|---|---|---|---|
| `DealService` create/update/delete (`DealService.java:202,223,347`) | `"deal"` | lowercase ✅ | **Yes** |
| `DealTransitionService` move/win/lose/re-open (`DealTransitionService.java:131`) | `"DEAL"` | UPPERCASE ❌ | **No** |
| `DealProposalService` win-via-proposal (`DealProposalService.java:199`) | `"DEAL"` | UPPERCASE ❌ | **No** |

**User-visible symptom:** A deal's creation and field edits appear in the Activity tab, but its
**stage transitions (moved / won / lost / re-opened)** — the most important history — never appear.

### Root cause (evidenced)

- `AuditEventBuilder.entityType(String)` (`AuditEventBuilder.java:213`) stores the value verbatim — no normalization.
- `audit_events.entity_type` is `VARCHAR(50)` with default Postgres collation — case-sensitive (`V12__create_audit_events.sql`).
- The read query uses exact equality, not case-insensitive matching:
  - JPQL: `AuditEventRepository.java:35` → `WHERE (:entityType IS NULL OR e.entityType = :entityType)`
  - Native: `AuditEventRepository.java:314` → `e.entity_type = CAST(:entityType AS TEXT)`
- Frontend sends `"deal"` (`DealActivityTab.tsx:18` → `audit-events.ts:155` → `AuditEventController.java:99`).
- Therefore rows written as `"DEAL"` never match a `"deal"` query.

### Dominant convention (evidenced)

Every other entity writes **lowercase / snake_case** audit `entityType`:
`"project"` (`ProjectService.java:440…`), `"customer"` (`CustomerService.java:255…`),
`"task_item"` (`TaskItemService.java:66…`), `"org_settings"` (`OrgSettingsService.java`),
plus `"invoice"`, `"proposal"`, `"data_subject_request"`, `"generated_document"`, `"billing_run"`.
The `fielddefinition.EntityType` enum (`PROJECT, TASK, CUSTOMER, INVOICE, DEAL`) is **only** for
custom-field definitions; audit code does **not** derive its strings from it.

**Conclusion:** `DealTransitionService` / `DealProposalService` are the outliers. The fix is to
unify deal audit writes on lowercase **`"deal"`** — matching `DealService`, the cross-entity
convention, and the frontend query. Do **not** invent a new convention (e.g. case-insensitive
query) — that would paper over the bug and carry index/perf implications across all entities.

### Stage history is audit-only (intentional — no new endpoint needed)

There is no `deal_stage_changes` table or stage-history entity; `DealStageChangedEvent` is a
transient domain event. Deal stage history is **derivable only from audit events**. Once the
casing is unified, the existing Activity tab + `/api/audit-events/deal/{id}` endpoint fully
surfaces transitions. **No new stage-history endpoint is in scope.**

---

## 2. Reproduce-before-fix (mandatory)

Write a failing integration test FIRST (red), then fix (green). Suggested in
`DealTransitionServiceTest` (it already has the harness):

```
@Test
void transitionAuditRows_areQueryableByLowercaseDealEntityType() {
  var customerId = createCustomer("Audit Co", "audit@test.com");
  var dealId = createDeal(customerId, "Audit Deal");
  UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

  inTenant(() ->
      dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

  // Query exactly as the Activity tab does: lowercase "deal".
  long rows = inTenant(() ->
      auditService.findEvents(
          new AuditEventFilter("deal", dealId, null, null, null, null, null),
          Pageable.ofSize(50)).getTotalElements());

  // FAILS before the fix (transition rows were written as "DEAL"); PASSES after.
  assertThat(rows).isGreaterThanOrEqualTo(1);
}
```

This proves the symptom from the consumer's perspective (lowercase query, matching the frontend),
not just that *some* row was written.

---

## 3. The fix

### 3a. Code (the actual fix)

1. `crm/DealTransitionService.java:131` — change `.entityType("DEAL")` → `.entityType("deal")`.
   (One `audit(...)` helper feeds all four transition event types — moved/won/lost/re-opened —
   so this single line fixes all of them.)
2. `crm/DealProposalService.java:199` — change `.entityType("DEAL")` → `.entityType("deal")`.

### 3b. Test alignment

3. `crm/DealTransitionServiceTest.java:~321-340` — the existing test
   `auditRowsWritten_withUppercaseDealEntityType` **asserts the bug** (queries `"DEAL"`). It must
   be updated, not left to enshrine the wrong casing:
   - Rename to reflect the corrected behaviour (e.g. `auditRowsWritten_withLowercaseDealEntityType`).
   - Change its filter from `"DEAL"` → `"deal"`.
   - Keep / fold in the new red-green test from §2 (they may merge into one).
   Search the suite for any other `AuditEventFilter("DEAL"` or `entityType("DEAL")` assertions and
   update them too.

### 3c. Historical-data remediation (decision required — see §5)

Pre-fix, transition rows already in `audit_events` carry `"DEAL"` and will stay hidden on the
Activity tab even after the code fix (audit table is append-only). To surface historical
transitions, add an idempotent **tenant** Flyway migration (next free `V###` in
`db/migration/tenant/`):

```sql
-- V###__normalize_deal_audit_entity_type.sql
-- One-time casing normalization: unify deal audit entityType on the project-wide
-- lowercase convention so the deal Activity tab surfaces historical transitions.
UPDATE audit_events SET entity_type = 'deal' WHERE entity_type = 'DEAL';
```

It must be a **tenant-scoped** migration (audit_events lives in each tenant schema). Confirm the
migration runner applies tenant migrations across all existing tenants (it does for prior tenant
migrations — verify against the most recent `V1xx` tenant migration's behaviour).

---

## 4. Acceptance criteria

- [ ] New integration test queries audit events with lowercase `"deal"` after a transition and
      finds the row — **fails on `main`, passes after the fix** (reproduce-before-fix proven).
- [ ] `DealTransitionService` and `DealProposalService` write `entityType = "deal"` for all deal
      audit rows.
- [ ] No remaining `entityType("DEAL")` write or `AuditEventFilter("DEAL"` assertion in the codebase
      (grep clean).
- [ ] Manual/observed verification: in a running stack, move a deal across stages → open the deal's
      Activity tab → the transition events render (PASS = observed in browser, per CLAUDE.md §3).
- [ ] (If §3c chosen) historical `"DEAL"` rows are normalized to `"deal"` and now render.
- [ ] Full `./mvnw verify` runs clean (production behaviour changed → full suite required, not targeted).

---

## 5. Decisions for the orchestrator / user (not agent calls)

1. **Audit-row mutation (§3c).** Audit events are conceptually append-only/immutable. The proposed
   migration only normalizes a casing **typo** on the `entity_type` label (entityId, eventType,
   actor, details, timestamps untouched), so it does not alter audit semantics — but rewriting any
   audit row is a governance call. **Choose:**
   - **A (recommended):** code fix **+** the data-remediation migration — historical transitions
     become visible; cleanest end state.
   - **B:** code fix only — new transitions show; pre-existing `"DEAL"` rows stay hidden. Acceptable
     if historical fidelity isn't required (e.g. demo/fresh environments) or if audit immutability
     is treated as inviolable.
   - **C (not recommended):** make the audit query case-insensitive instead of fixing the writers —
     rejected: hides the convention violation, affects all entity types, and has index/perf cost.

2. **PR bundling.** This is a single bug-class cluster (two services with the identical casing
   defect + their test). Per CLAUDE.md §7 this may ship as **one PR** — confirm that authorization.

---

## 6. Files

### Modify
- `backend/.../crm/DealTransitionService.java` (line 131: `"DEAL"` → `"deal"`)
- `backend/.../crm/DealProposalService.java` (line 199: `"DEAL"` → `"deal"`)
- `backend/.../crm/DealTransitionServiceTest.java` (update/rename the casing assertion; add §2 test)

### Create (only if Decision A in §5)
- `backend/src/main/resources/db/migration/tenant/V###__normalize_deal_audit_entity_type.sql`

### No change
- Frontend — already sends `"deal"` correctly (`DealActivityTab.tsx:18`).
- `AuditEventRepository` / `AuditEventController` — query is correct; the writers were wrong.

---

## 7. Verification commands

```bash
# Backend (full verify — production behaviour change)
cd backend && ./mvnw clean verify -q

# Grep clean check (should return nothing after the fix)
grep -rn 'entityType("DEAL")\|AuditEventFilter("DEAL"' backend/src
```

Manual (observed): start the stack, create a deal, move it across stages (and win via a proposal),
open the deal's Activity tab, confirm the transition events render.
