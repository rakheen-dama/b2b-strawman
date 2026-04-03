# Track 6 — Invoice Tariff Integration (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (owner, legal tenant)
**Method**: API attempted

## Summary

**BLOCKED** — Invoice creation fails with 422 because the seeded customers lack required address fields (`address_line1`, `city`, `country`, `tax_number`). These fields are pack-defined (from field group system), and the customer update endpoint does not accept them via the `customFields` map in the PUT body. Without a working invoice, tariff line item integration cannot be tested.

Additionally, the Tariffs page UI is crashed (GAP-P55-012), so the "Add Tariff Item" dialog on invoices cannot be verified through the UI either.

---

## T6.1 — Prepare Invoice

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T6.1.1 Log time entries | **BLOCKED** | Time entries require a task ID (`POST /api/tasks/{taskId}/time-entries`); no tasks exist on the test matters |
| T6.1.2 Create invoice | **BLOCKED** | `POST /api/invoices` → 422: "4 required field(s) missing for Invoice Generation" (address_line1, city, country, tax_number) |
| T6.1.3 Verify time-based lines | **BLOCKED** | No invoice created |

## T6.2–T6.5 — All Blocked

All remaining checkpoints (T6.2 Add Tariff Line, T6.3 Amount Override, T6.4 Tax Calculation, T6.5 Module Gate on Invoice) are **BLOCKED** by the inability to create an invoice.

---

## Notes

- The customer address fields are managed through the field group/pack system, not plain `customFields` on the customer entity
- Populating these fields requires either the UI customer profile page or knowledge of the specific field definition IDs
- This is a test data setup gap, not necessarily a product bug
- T6.5 (Thornton "Add Tariff Item" button hidden) was partially verified: Thornton gets 403 on all tariff APIs, so the button would correctly not appear if the UI conditionally checks module availability
