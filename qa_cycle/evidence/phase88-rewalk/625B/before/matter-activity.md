# Matter Activity feed — BEFORE (625B reproducer)

Matter: `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` "Estate Late Ndlovu 625A repro" (client Sipho Ndlovu mvmzw78f31c9)
URL: http://localhost:3001/org/e2e-test-org/matters/9a27cd08-76fc-44b1-8c41-a8dd1aa1af22?tab=activity (Alice Owner, mock auth), captured 2026-10-27 ~11:17 UTC
Rendered rows: 28 (20 on first load + 8 after one "Load more"; no further Load more). Rendered text == `message` field of `GET /api/matters/{id}/activity` (see api-matter-activity.json) — verified identical row-for-row.
Screenshots: matter-activity-page1.png (first 20), matter-activity-all.png (all 28).

| # | occurred_at (UTC) | event_type | Rendered sentence (verbatim) | Flag | Why |
|---|---|---|---|---|---|
| 1 | 2026-10-25T00:45:38 | `trust_payment.approved` | Bob Admin performed trust_payment.approved on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_payment.approved`; raw entity type `trust_transaction`; no amount/reference |
| 2 | 2026-10-25T00:45:21 | `trust_payment.recorded` | Alice Owner performed trust_payment.recorded on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_payment.recorded`; raw entity type `trust_transaction`; no amount/reference |
| 3 | 2026-10-25T00:44:53 | `trust_deposit.recorded` | Alice Owner performed trust_deposit.recorded on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_deposit.recorded`; raw entity type `trust_transaction`; no amount/reference |
| 4 | 2026-10-25T00:44:05 | `customer.linked` | Alice Owner performed customer.linked on customer | FALLBACK | generic "performed" fallback; raw key `customer.linked`; raw entity type `on customer` |
| 5 | 2026-10-25T00:28:58 | `invoice.partially_paid` | Alice Owner performed invoice.partially_paid on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.partially_paid`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 6 | 2026-10-25T00:28:39 | `invoice.sent` | Alice Owner performed invoice.sent on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.sent`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 7 | 2026-10-25T00:28:28 | `invoice.approved` | Alice Owner performed invoice.approved on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.approved`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 8 | 2026-10-25T00:28:11 | `invoice.created` | Alice Owner performed invoice.created on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.created`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 9 | 2026-10-25T00:26:59 | `time_entry.created` | Alice Owner logged 2h on task "Open estate bank account" | OK | - |
| 10 | 2026-10-25T00:26:34 | `acceptance.declined` | Sipho Ndlovu mvmzw78f31c9 performed acceptance.declined on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.declined`; raw entity type `acceptance_request` |
| 11 | 2026-10-25T00:26:27 | `acceptance.viewed` | Sipho Ndlovu mvmzw78f31c9 performed acceptance.viewed on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.viewed`; raw entity type `acceptance_request` |
| 12 | 2026-10-25T00:26:15 | `acceptance.sent` | Alice Owner performed acceptance.sent on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.sent`; raw entity type `acceptance_request` |
| 13 | 2026-10-25T00:26:15 | `acceptance.created` | Alice Owner performed acceptance.created on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.created`; raw entity type `acceptance_request` |
| 14 | 2026-10-25T00:25:50 | `document.generated` | Alice Owner generated document "standard-engagement-letter-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Standard Engagement Letter" | OK | - |
| 15 | 2026-10-25T00:25:21 | `acceptance.certificate_generated` | System performed acceptance.certificate_generated on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.certificate_generated`; raw entity type `acceptance_request` |
| 16 | 2026-10-25T00:25:21 | `acceptance.accepted` | Sipho Ndlovu mvmzw78f31c9 performed acceptance.accepted on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.accepted`; raw entity type `acceptance_request` |
| 17 | 2026-10-25T00:25:15 | `acceptance.viewed` | Sipho Ndlovu mvmzw78f31c9 performed acceptance.viewed on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.viewed`; raw entity type `acceptance_request` |
| 18 | 2026-10-25T00:24:58 | `acceptance.sent` | Alice Owner performed acceptance.sent on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.sent`; raw entity type `acceptance_request` |
| 19 | 2026-10-25T00:24:58 | `acceptance.created` | Alice Owner performed acceptance.created on acceptance_request | FALLBACK | generic "performed" fallback; raw key `acceptance.created`; raw entity type `acceptance_request` |
| 20 | 2026-10-25T00:24:25 | `document.generated` | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Engagement Letter — General" | OK | - |
| 21 | 2026-10-25T00:23:37 | `trust_payment.approved` | Bob Admin performed trust_payment.approved on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_payment.approved`; raw entity type `trust_transaction`; no amount/reference |
| 22 | 2026-10-25T00:23:17 | `trust_payment.recorded` | Alice Owner performed trust_payment.recorded on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_payment.recorded`; raw entity type `trust_transaction`; no amount/reference |
| 23 | 2026-10-25T00:22:37 | `trust_deposit.recorded` | Alice Owner performed trust_deposit.recorded on trust_transaction | FALLBACK | generic "performed" fallback; raw key `trust_deposit.recorded`; raw entity type `trust_transaction`; no amount/reference |
| 24 | 2026-10-24T23:39:33 | `invoice.partially_paid` | Alice Owner performed invoice.partially_paid on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.partially_paid`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 25 | 2026-10-24T23:39:17 | `invoice.sent` | Alice Owner performed invoice.sent on invoice | FALLBACK | generic "performed" fallback; raw key `invoice.sent`; raw entity type `on invoice`; says "invoice" not "fee note"; no INV number/amount |
| 26 | 2026-10-24T23:38:24 | `time_entry.created` | Alice Owner logged 4h on task "Obtain death certificate & will" | OK | - |
| 27 | 2026-10-24T23:36:40 | `document.generated` | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-24.pdf" from template "Engagement Letter — General" | OK | - |
| 28 | 2026-10-24T23:33:26 | `project.created_from_template` | Alice Owner performed project.created_from_template on project | FALLBACK | generic "performed" fallback; raw key `project.created_from_template`; raw entity type `on project` |

**Totals: 28 rows — 23 FALLBACK (generic `<actor> performed <event_type> on <entity_type>`), 5 OK.**

OK rows are only `time_entry.created` (2) and `document.generated` (3). Every trust, fee-note (invoice), acceptance, customer.linked and project.created_from_template row falls through to `ActivityMessageFormatter.formatMessage` default branch (backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:153).
`entityName` is `unknown` for all 23 fallback rows (resolveEntityName has no trust_transaction / invoice / acceptance_request / customer / project branch).
Data needed for good sentences IS present in `details` (see db-audit.txt §2): trust rows carry amount + reference; invoice.approved/sent/partially_paid carry invoice_number (+ total, amount, balance_due, payment_method); acceptance.* carry document_file_name / contact_name / decline_reason / acceptor_name; project.created_from_template carries template_name. `invoice.created` has no invoice_number (draft) — only subtotal/currency/line_count.
Filter chips are All / Tasks / Documents / Comments / Members / Time — no Fees/Trust/Acceptance chip (observation only).
