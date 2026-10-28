# 627A after-fix3 — observer re-walk (PR #1693)

Stack: E2E mock-auth :3001, built from a16fec0f8 (product-equivalent to head 8e8c118d7), seeded by legal-lifecycle + legal-depth-ii. Login Alice (owner). Tenant schema `tenant_7d218705360b`. Observed 2026-10-28 ~05:47–05:48 UTC via Playwright MCP.

Client: Sipho Ndlovu mvrovhe0b72d, id `3c1891ee-7557-4acb-b985-2bea77607f8b`.

## Q1 — R-19 client header ID strip — PASS
URL: http://localhost:3001/org/e2e-test-org/clients/3c1891ee-7557-4acb-b985-2bea77607f8b

| Step | `client-header-fields` innerText | DB `id_number` | DB `custom_fields->>'id_passport_number'` |
|---|---|---|---|
| Before | `ID 8501015800087` (q1-1) | 8501015800087 | 8501015800087 |
| Edit dialog (More actions → Edit Client) ID → `9001015800085`, Save (q1-2) | `ID 9001015800085` (live, and after full reload q1-3) | 9001015800085 | 8501015800087 (stale — the case under test) |
| Restore via Edit dialog → `8501015800087` | `ID 8501015800087` (q1-4) | 8501015800087 | 8501015800087 |

Value used: 9001015800085. The Edit form does no SA-ID checksum validation (no Luhn check found in frontend; note the seeded 8501015800087 is itself not Luhn-valid), so the suggested value was accepted as-is.

## Q2 — R-18 conflict-check link opens new tab — PASS
Run while structural id_number = 9001015800085 differed from custom field 8501015800087 (stronger prefill test).
1. Matters → New Matter; Name typed `Q2 Observer Matter 627A`; Client = Sipho Ndlovu mvrovhe0b72d (q2-1).
2. Link attributes (browser_evaluate): href=`/org/e2e-test-org/conflict-check?checkType=NEW_MATTER&customerId=3c1891ee-7557-4acb-b985-2bea77607f8b`, target=`_blank`, rel=`noopener noreferrer`, text "Run a conflict check first → (opens in a new tab)".
3. Click → browser_tabs shows 2 tabs; tab 1 URL `http://localhost:3001/org/e2e-test-org/conflict-check?checkType=NEW_MATTER&customerId=3c1891ee-7557-4acb-b985-2bea77607f8b` (no name / ID in URL).
4. Tab 1 prefill (q2-2): Name to Check = `Sipho Ndlovu mvrovhe0b72d`; ID Number = `9001015800085` (structural, not the stale custom field); Check Type = New Matter (`NEW_MATTER`); Client = Sipho Ndlovu mvrovhe0b72d.
5. Tab 0 (q2-3): "Create Matter" dialog still open, Name = `Q2 Observer Matter 627A`, Client still selected.
6. Cleanup: dialog cancelled (DB: 0 projects named `Q2 Observer%`), tab 1 closed.

## Q3 — console errors — PASS
browser_console_messages level=error: 0 errors / 0 warnings on client detail, matters list + dialog, conflict-check tab.

## Defects
None.
