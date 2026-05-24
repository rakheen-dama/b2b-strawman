# Day 85 — Audit log sweep (Accounting ZA)

**Cycle**: Accounting ZA 90-Day Lifecycle (Keycloak)
**Branch**: `main`
**Actor**: Thandi Thornton (Owner, `:3000`, Keycloak auth)
**Result**: **3 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**

## Checkpoints

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 85.1 | Navigate to Audit Log | **PASS** | Settings > Audit Log page loaded at `/org/thornton-associates/settings/audit-log`. Heading: "Audit log". Description: "Read-only feed of audit events. Filter by date range, severity, actor, event type, or entity." **270 total events** across 6 pages. Filters available: Preset dropdown, From/To date pickers, Actor ID (UUID), Event type (text), Entity type (text), Entity ID (UUID), Severity toggle (INFO/NOTICE/WARNING/CRITICAL). Export button present. Table columns: Time, Severity, Event, Actor, Entity. Clickable entity links to drill into referenced objects. |
| 85.2 | Filter by actor = Thandi, entity = Engagement — verify expected events | **PASS** | Filtered via `?entityType=project`. Page 1 of 1 returned **6 engagement-level events**: (1) `project.completed` — Thandi Thornton — project `583ee45e` (Sipho Tax Return, Day 72) — 15 May 19:20; (2) `project.created_from_template` — Thandi — project `302efdce` (Mathole VAT Return, Day 32) — 15 May 16:00; (3) `project.created_from_template` — Thandi — project `0a39ccb1` (Moroka Trust AFS, Day 16) — 15 May 14:38; (4) `project.created_from_template` — Thandi — project `388d5104` (Kgosi Year-End Pack, Day 6) — 14 May 23:51; (5) `project.created_from_template` — Thandi — project `a32c67d5` (Kgosi Monthly Bookkeeping, Day 5) — 14 May 23:38; (6) `project.created_from_template` — Bob Ndlovu — project `583ee45e` (Sipho Tax Return, Day 3) — 14 May 23:07. All 5 engagement IDs match status.md records. 5 of 6 by Thandi (expected — Bob created Sipho's engagement on Day 3). |
| 85.3 | Filter by action = CREATE_INVOICE — verify all invoices generated over 90 days | **PASS** | Filtered via `?eventType=invoice.created`. **5 invoice creation events** returned, matching all 5 invoices in the lifecycle: (1) invoice `c889d1a8` (INV-0005, Kgosi Bookkeeping May) — 15 May 18:33; (2) invoice `771c5e27` (INV-0004, Kgosi Bookkeeping April) — 15 May 18:07; (3) invoice `079e7cb4` (INV-0003, Kgosi Year-End Pack) — 15 May 17:57; (4) invoice `9dca277d` (INV-0002, Sipho Tax Return fixed fee) — 15 May 17:41; (5) invoice `b6ba784c` (INV-0001, Kgosi Bookkeeping first) — 15 May 17:30. All by Thandi Thornton. All invoice IDs match status.md. Clickable entity links present for each. |

## Summary

- Audit log contains **270 events** spanning the full accounting lifecycle (Days 0-80+)
- Entity type filter correctly narrows to engagement-level events (6 project events)
- Event type filter correctly isolates `invoice.created` events (5 invoices, matching all invoices generated during the cycle)
- All actors correctly attributed (Thandi Thornton for most, Bob Ndlovu for Sipho engagement creation)
- All entity IDs match known UUIDs from previous checkpoints
- Filters work via URL query parameters (`entityType`, `eventType`)
- No new gaps filed
