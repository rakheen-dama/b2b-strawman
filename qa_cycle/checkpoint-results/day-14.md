# Day 14 — Firm onboards Moroka Family Trust (isolation setup) `[FIRM]` — Cycle 2026-07-12

**Actor**: Thandi Mathebula (Owner) on :3000 (KC session from Day 7).

| # | Result | Evidence |
|---|--------|----------|
| 14.1 | PASS | Clients → New Client → "Create Client" dialog (Step 1 of 2) |
| 14.2 | PASS (same product-shape notes as prior cycle) | Name "Moroka Family Trust", Type **Trust**, email moroka.portal@example.com, Registration Number "IT 001234/2024", Country South Africa (ZA). No beneficial-owner fields in intake (step 2 = SA Legal — Client Details promoted group) — known product shape, not re-filed |
| 14.3 | PASS | Client created → `/customers/51247065-644b-4387-b11b-303960087727`, Active/Prospect |
| 14.4 | PASS | More actions → Run Conflict Check → pre-filled "Moroka Family Trust" → Run → **No Conflict**; History incremented to (2) |
| 14.5 | PASS | Client Work > Matters → New Matter → template picker → **Deceased Estate Administration** (9 tasks) |
| 14.6 | PASS (same note) | Configure: name "Estate Late Peter Moroka", ref **EST-2026-002**, client Moroka pre-bound, lead Thandi; description "…Matter type: ESTATES". No Master's Office input in configure step (promoted-field surface) — left unset, same as prior cycle |
| 14.7 | PASS | Matter created → `/projects/690b8246-a999-4cad-9fcc-cf76544f268f`, header "Estate Late Peter Moroka / Active / EST-2026-002 / Moroka Family Trust" |
| 14.8 | PASS | Matter Client > Requests → New Request → template **Liquidation and Distribution Account Pack (5 items)** → contact Moroka Family Trust (moroka.portal@example.com), due 2026-07-30 → Send Now → row **REQ-0002 / Sent / 0/5 accepted / 12 Jul 2026**; id `a2452183-1736-4ec7-9d44-b1fe943d9b53` |
| 14.9 | PASS | Work > Documents → dropzone upload `death-certificate-moroka.pdf` (626 B) → row **Uploaded**, 12 Jul 2026 |
| 14.10 | PASS | Trust deposit R 25 000, ref DEP/2026/002, client Moroka, matter Estate Late Peter Moroka, desc "Estate deposit — EST-2026-002" → posts **RECORDED** (transactions list now DEP/2026/001 R 50 000 + DEP/2026/002 R 25 000) |
| 14.11 | PASS | IDs captured to `qa_cycle/checkpoint-results/isolation-probe-ids.txt` (client/matter/info-request from UI URLs; document + trust-tx via read-only SELECT on tenant_5039f2d497cf — evidence only) |

## Day 14 day-level checkpoints

- Two clients + matters on tenant (Sipho/RAF + Moroka/Estate; plus known auto-created engagement matter for Sipho): PASS
- Moroka has 1 info request (REQ-0002), 1 document, 1 trust deposit (R 25 000): PASS
- Moroka entity IDs captured for Day 15 probes: PASS

## Gaps

- None new. Observations unchanged from prior cycle: beneficial-owner capture absent from intake dialog; Master's Office not a configure-step field.
