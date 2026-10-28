# Slice 626G — Phase 1 BEFORE (cluster templates), observer run 2026-10-28 ~02:45 UTC

Stack: e2e (frontend :3001, backend :8081). Backend container at that point was built 2026-10-28T00:48:56Z from
`/Users/rakheendama/Projects/2026/worktree-epic-626E/compose` (pre-626G backend code; 626F was frontend-only).
Actor: Alice (owner) via Playwright MCP. Matter `02b5a264-61b9-4932-9b74-f844cd85afaa`.

| Template | Clause step? | Preview `clause not found` (iframe srcdoc) | Screenshot |
|---|---|---|---|
| Deed of Transfer | NO (dialog "Generate: Deed of Transfer / Download PDF / Save to Documents / Close") | `transfer-duty-liability`, `rates-clearance`, `body-corporate-clearance` | 01-deed-preview-before.png, deed-preview.html |
| Bond Cancellation Instruction | NO | `cost-of-cancellation` | 02-bond-preview-before.png, bond-preview.html |
| Power of Attorney to Pass Transfer | NO | `fica-compliance` | 03-poa-preview-before.png, poa-preview.html |

DB (db-before.txt): `conveyancing-za-clauses` pack_install v1; template_clauses: bond 0, deed 0, OTP 0, POA 0,
EL-conveyancing 5, EL-general 5, EL-litigation 6, standard-engagement-letter 12, clone "Engagement Letter — Litigation (Custom)" 0. Total 28 rows.
Embedded clauseBlock slugs (document order): OTP voetstoots,occupation-date,suspensive-bond,transfer-duty-liability,fica-compliance,rates-clearance,jurisdiction-za;
deed transfer-duty-liability,rates-clearance,body-corporate-clearance; bond cost-of-cancellation; POA fica-compliance.
