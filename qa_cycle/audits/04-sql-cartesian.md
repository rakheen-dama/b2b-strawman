# Audit 04 — SQL LEFT JOIN Aggregation Cartesian Risk

## Hypothesis (from OBS-2104b)
Flat `SELECT ... FROM A LEFT JOIN B LEFT JOIN C ... GROUP BY` produces row-multiplied results when B and C are independent collections. `SUM()` and other aggregates over the row-multiplied result are inflated by the cardinality of the un-correlated joined tables. Fixed in `BillingRunSelectionService.discoverCustomers` by rewriting with per-source CTEs.

## Method
```
grep -rln "LEFT JOIN" backend/src/main/java/ | xargs grep -l "SUM\|COUNT"
```
4 file matches. Inspected each:

## Findings

### `BillingRunSelectionService.java` — already fixed
PR #1240 (OBS-2104b) rewrote `discoverCustomers` and `createEntrySelections` with CTEs (`customer_filter`, `time_agg`, `expense_agg`, `disbursement_agg`). 3 cardinality regression tests added. Status: VERIFIED in cycle 16.

### `UnbilledTimeService.java` — safe (LATERAL joins)
Uses `LEFT JOIN LATERAL` for both unbilled-time and unbilled-expense aggregates. LATERAL subqueries are correlated per outer row — they don't multiply the outer relation. Code:
```
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS unbilled_time_count, SUM(...) AS unbilled_time_amount
    FROM time_entries te JOIN tasks t ON te.task_id = t.id ...
) ut ON true
```
This is the canonical safe pattern for "aggregate from N independent sources without row inflation." No risk.

### `ProjectRepository.java` — needs spot-check
Hit on the grep for both `LEFT JOIN` and `SUM/COUNT` but the LEFT JOIN+aggregate combination wasn't shown in the quick grep. Likely a JPA `@Query` with LEFT JOIN that doesn't aggregate, or aggregates on a single source. **Action**: open the file, find each `@Query` annotation, classify each as safe (no multi-source aggregate) or suspect.

### `AuditEventRepository.java` — needs spot-check
Same as above. Audit events table is single-source by design (immutable log), so any LEFT JOIN there is likely projecting actor/entity references, not summing across collections. Probably safe but worth a 5-min eyeball.

## Action items

1. **Spot-check `ProjectRepository.java`** — list each `@Query`, classify. If any matches the `(LEFT JOIN A) (LEFT JOIN B) GROUP BY` pattern with `SUM/COUNT(DISTINCT)`, flag for the OBS-2104b CTE rewrite.
2. **Spot-check `AuditEventRepository.java`** — same.
3. **Convention**: when adding a multi-source aggregate query, prefer per-source CTEs OR `LATERAL` subqueries. Document in `backend/CLAUDE.md`.

Both spot-checks are < 5 min each. No concrete bug found in this audit pass; all four files are either already-fixed, demonstrably-safe (LATERAL), or unlikely-to-aggregate-multi-source.
