# Day 14 — Firm onboards Moroka Family Trust (isolation setup) `[FIRM]` — 2026-07-06

**Actor**: Thandi Mathebula (Owner) on :3000.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 14.1 | PASS | Clients → New Client dialog (step 1 of 2) |
| 14.2 | PASS (with product-shape notes) | Name "Moroka Family Trust", Type **Trust**, email moroka.portal@example.com, Registration Number "IT 001234/2024", Entity Type Trust, Country South Africa. NOTES: (a) no beneficial-owners fields exist in the create dialog (step 2 shows the SA Legal — Client Details promoted group: ID/Passport, Postal Address, Preferred Correspondence, Referred By) — beneficial owners are not part of client intake; (b) scenario's "FICA trust template" beneficial-owner capture is not a create-dialog surface |
| 14.3 | PASS | Client created → `/customers/559a9e8f-7904-418a-8ae7-358ca9a93871`, lifecycle Prospect |
| 14.4 | PASS | More actions → Run Conflict Check → `/conflict-check` pre-filled "Moroka Family Trust" + reg no → Run → **No Conflict** rendered; History (1) tab increments |
| 14.5 | PASS | Client Work > Matters → New Matter → template picker → **Deceased Estate Administration** (9 tasks) |
| 14.6 | PASS (note) | Name "Estate Late Peter Moroka", Reference **EST-2026-002**, lead Thandi, client Moroka pre-selected; template description "Administration of deceased estate… Matter type: ESTATES". NOTE: no "Master's Office" input in the configure step (promoted field on matter Fields tab if needed) — left unset, not required for isolation purpose |
| 14.7 | PASS | Matter created → `/projects/54baf135-7cd3-4b77-b6ef-5de5742291a3`, header EST-2026-002, Active, client Moroka Family Trust |
| 14.8 | PASS | Client > Requests → New Request → template **Liquidation and Distribution Account Pack (5 items)** → contact Moroka Family Trust (moroka.portal@example.com), due 2026-07-30 → Send Now → row **REQ-0002 / Sent / 0/5 accepted / 6 Jul 2026** |
| 14.9 | PASS | Work > Documents → dropzone upload `death-certificate-moroka.pdf` (626 B) → row status **Uploaded**, 6 Jul 2026 |
| 14.10 | PASS | Trust deposit R 25 000, ref DEP/2026/002, client Moroka, matter Estate Late Peter Moroka, desc "Estate deposit — EST-2026-002" → posts RECORDED |
| 14.11 | PASS | IDs captured to `qa_cycle/checkpoint-results/isolation-probe-ids.txt` (client/matter/info-request via UI URLs; document + trust-tx via read-only SELECT on tenant_5039f2d497cf, evidence only) |

## Day 14 day-level checkpoints

- Two clients + their matters exist on tenant (Sipho/RAF + Moroka/Estate; plus the Day-10-noted auto-created engagement matter for Sipho): **PASS**
- Moroka has 1 info request (REQ-0002), 1 document, 1 trust deposit (R 25 000): **PASS**
- Moroka entity IDs captured for Day 15 probes: **PASS**

## Gaps

None new. Observations: beneficial-owner capture absent from client create dialog (14.2); Master's Office not a create-step field (14.6).
