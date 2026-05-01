# Hypothesis-Driven Audits — 2026-05-01

Mechanical greps run after the cycle 22/23 verdict identified four bug-classes with one observed instance each. Goal: find the rest before they ship.

| Audit | Hypothesis | Concrete bugs found | Suspect items | Status |
|---|---|---|---|---|
| [01-notification-listeners.md](./01-notification-listeners.md) | Templates exist but no listener wires them to a portal contact. | **1 (OBS-AUDIT-N1)** — `portal-proposal-expired.html` orphaned. | None | Triaged |
| [02-flyway-default-drift.md](./02-flyway-default-drift.md) | `ADD COLUMN ... DEFAULT X` only applies to new INSERTs; pre-existing rows have NULL/empty. | 0 confirmed; **5 suspect** rows on `org_settings` and `field_definitions`. | 5 | Needs follow-up grep to compare stored values per tenant. |
| [03-radix-aschild.md](./03-radix-aschild.md) | Adjacent `<Trigger asChild>` siblings collide under React 19 Slot reconciliation. | 0 confirmed, **0 known-adjacent pairs** outside the already-fixed customer detail surface. | 196 declaration sites, ~10 dialog-pair sites worth eyeballing. | Defer to slop-hunt Pass B. |
| [04-sql-cartesian.md](./04-sql-cartesian.md) | `LEFT JOIN ... GROUP BY` over multiple sources inflates SUMs by the Cartesian product. | 0 confirmed (`UnbilledTimeService` uses `LATERAL` correctly; `BillingRunSelectionService` already fixed in OBS-2104b). | 2 (`ProjectRepository`, `AuditEventRepository`) need spot-check. | Triaged. |

**Net concrete bug filed: OBS-AUDIT-N1** (portal contact not notified when proposal expires; same class as OBS-703 / OBS-2106).

**Total suspect items requiring deeper investigation**: ~7. None are blockers; all are prophylaxis against the next QA cycle's regressions.
