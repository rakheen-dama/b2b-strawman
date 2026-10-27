# Matter Activity feed — AFTER (625B observer, task 625.6)

Matter: `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` "Estate Late Ndlovu 625A repro". Signed in as Alice Owner (owner) on http://localhost:3001 (mock auth). Backend: e2e-backend rebuilt from worktree-epic-625B @ `132f60ae2` (see README).
URL: http://localhost:3001/org/e2e-test-org/matters/9a27cd08-76fc-44b1-8c41-a8dd1aa1af22?tab=activity, captured 2026-10-27 ~12:22 UTC. The first load showed 20 rows; one "Load more" click loaded the other 8 (28 total), after which the button was gone.
The rendered DOM text (`dom-matter-activity.json`) is identical, row for row, to `content[].message` in `api-matter-activity-alice.json` (checked by script: True).
Screenshot: `matter-activity-all.png`.

| # | event_type | Actor | BEFORE (verbatim) | AFTER (verbatim, rendered) | After flag |
|---|---|---|---|---|---|
| 1 | `trust_payment.approved` | Bob Admin | Bob Admin performed trust_payment.approved on trust_transaction | Bob Admin approved the trust payment of R2 500,00 | OK |
| 2 | `trust_payment.recorded` | Alice Owner | Alice Owner performed trust_payment.recorded on trust_transaction | Alice Owner recorded a trust payment of R2 500,00 (PAY/625A-FIX/KHU) | OK |
| 3 | `trust_deposit.recorded` | Alice Owner | Alice Owner performed trust_deposit.recorded on trust_transaction | Alice Owner recorded a trust deposit of R10 000,00 (DEP/625A-FIX/KHU) | OK |
| 4 | `customer.linked` | Alice Owner | Alice Owner performed customer.linked on customer | Alice Owner linked a client to the matter | OK |
| 5 | `invoice.partially_paid` | Alice Owner | Alice Owner performed invoice.partially_paid on invoice | Alice Owner recorded a part payment of R2 000,00 on fee note INV-0003 | OK |
| 6 | `invoice.sent` | Alice Owner | Alice Owner performed invoice.sent on invoice | Alice Owner sent fee note INV-0003 | OK |
| 7 | `invoice.approved` | Alice Owner | Alice Owner performed invoice.approved on invoice | Alice Owner approved fee note INV-0003 | OK |
| 8 | `invoice.created` | Alice Owner | Alice Owner performed invoice.created on invoice | Alice Owner created a draft fee note | OK |
| 9 | `time_entry.created` | Alice Owner | Alice Owner logged 2h on task "Open estate bank account" | Alice Owner logged 2h on task "Open estate bank account" | OK |
| 10 | `acceptance.declined` | Sipho Ndlovu mvmzw78f31c9 | Sipho Ndlovu mvmzw78f31c9 performed acceptance.declined on acceptance_request | Sipho Ndlovu mvmzw78f31c9 declined a document sent for acceptance | OK |
| 11 | `acceptance.viewed` | Sipho Ndlovu mvmzw78f31c9 | Sipho Ndlovu mvmzw78f31c9 performed acceptance.viewed on acceptance_request | Sipho Ndlovu mvmzw78f31c9 viewed a document sent for acceptance | OK |
| 12 | `acceptance.sent` | Alice Owner | Alice Owner performed acceptance.sent on acceptance_request | Alice Owner sent "standard-engagement-letter-estate-late-ndlovu-625a-repro-2026-10-25.pdf" to Sipho Ndlovu mvmzw78f31c9 for acceptance | OK |
| 13 | `acceptance.created` | Alice Owner | Alice Owner performed acceptance.created on acceptance_request | Alice Owner created an acceptance request for "standard-engagement-letter-estate-late-ndlovu-625a-repro-2026-10-25.pdf" | OK |
| 14 | `document.generated` | Alice Owner | Alice Owner generated document "standard-engagement-letter-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Standard Engagement Letter" | Alice Owner generated document "standard-engagement-letter-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Standard Engagement Letter" | OK |
| 15 | `acceptance.certificate_generated` | System | System performed acceptance.certificate_generated on acceptance_request | Certificate of acceptance generated | OK |
| 16 | `acceptance.accepted` | Sipho Ndlovu mvmzw78f31c9 | Sipho Ndlovu mvmzw78f31c9 performed acceptance.accepted on acceptance_request | Sipho Ndlovu mvmzw78f31c9 accepted a document sent for acceptance | OK |
| 17 | `acceptance.viewed` | Sipho Ndlovu mvmzw78f31c9 | Sipho Ndlovu mvmzw78f31c9 performed acceptance.viewed on acceptance_request | Sipho Ndlovu mvmzw78f31c9 viewed a document sent for acceptance | OK |
| 18 | `acceptance.sent` | Alice Owner | Alice Owner performed acceptance.sent on acceptance_request | Alice Owner sent "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-25.pdf" to Sipho Ndlovu mvmzw78f31c9 for acceptance | OK |
| 19 | `acceptance.created` | Alice Owner | Alice Owner performed acceptance.created on acceptance_request | Alice Owner created an acceptance request for "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-25.pdf" | OK |
| 20 | `document.generated` | Alice Owner | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Engagement Letter — General" | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-25.pdf" from template "Engagement Letter — General" | OK |
| 21 | `trust_payment.approved` | Bob Admin | Bob Admin performed trust_payment.approved on trust_transaction | Bob Admin approved the trust payment of R1 500,00 | OK |
| 22 | `trust_payment.recorded` | Alice Owner | Alice Owner performed trust_payment.recorded on trust_transaction | Alice Owner recorded a trust payment of R1 500,00 (PAY/625A-OBS/001) | OK |
| 23 | `trust_deposit.recorded` | Alice Owner | Alice Owner performed trust_deposit.recorded on trust_transaction | Alice Owner recorded a trust deposit of R30 000,00 (DEP/625A-OBS/001) | OK |
| 24 | `invoice.partially_paid` | Alice Owner | Alice Owner performed invoice.partially_paid on invoice | Alice Owner recorded a part payment of R5 000,00 on fee note INV-0002 | OK |
| 25 | `invoice.sent` | Alice Owner | Alice Owner performed invoice.sent on invoice | Alice Owner sent fee note INV-0002 | OK |
| 26 | `time_entry.created` | Alice Owner | Alice Owner logged 4h on task "Obtain death certificate & will" | Alice Owner logged 4h on task "Obtain death certificate & will" | OK |
| 27 | `document.generated` | Alice Owner | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-24.pdf" from template "Engagement Letter — General" | Alice Owner generated document "engagement-letter-general-estate-late-ndlovu-625a-repro-2026-10-24.pdf" from template "Engagement Letter — General" | OK |
| 28 | `project.created_from_template` | Alice Owner | Alice Owner performed project.created_from_template on project | Alice Owner opened the matter from template "Deceased Estate Administration" | OK |

**Totals: 28 rows. 0 contain "performed", 0 contain raw keys (`x.y` / `x_y` tokens outside quoted file names), 0 contain raw entity types or "invoice"/"project" wording. 28 of 28 OK** (before: 23 FALLBACK, 5 OK).

Every row matched its before-row by audit event id (all 28 ids are unchanged).
