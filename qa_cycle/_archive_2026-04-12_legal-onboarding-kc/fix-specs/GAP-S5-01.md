# Fix Spec: GAP-S5-01 — Engagement Letter Fee Model missing Contingency option

## Priority
HIGH — blocks the single most important legal-vertical workflow for personal-injury/RAF firms
in South Africa. Contingency fees are the norm for plaintiff work (LPC Rule 59, capped 25%).

## Problem
The New Engagement Letter dialog's Fee Model select offers only FIXED / HOURLY / RETAINER. There
is no CONTINGENCY option, even though the `legal-za-fees-contingency` clause pack has already
been seeded (Session 2 verified). Blocks Session 5 steps 5.20–5.24.

## Root Cause (confirmed via grep)
Files:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/FeeModel.java:4-8` — Enum has only
  three values: `FIXED`, `HOURLY`, `RETAINER`.
- `frontend/lib/schemas/proposal.ts:3` —
  `const feeModelEnum = z.enum(["FIXED", "HOURLY", "RETAINER"]);`
- `frontend/components/proposals/create-proposal-dialog.tsx:54-56` — fee model label map.
- `frontend/components/proposals/create-proposal-dialog.tsx:114-124` — per-model conditional
  fields block (`feeModel === "FIXED" && { ... }` etc.). Need to add a matching CONTINGENCY
  branch.
- `backend/src/main/resources/clause-packs/legal-za-clauses/pack.json:90-104` — the
  `legal-za-fees-contingency` clause already exists with the full 25% cap text referencing the
  Contingency Fees Act 66 of 1997.

## Fix Steps
1. **Backend enum extension**:
   - Add `CONTINGENCY("Contingency")` to `FeeModel.java`.
   - Grep all service/validation code for switch statements on FeeModel — update to handle
     CONTINGENCY. Key locations to check: `ProposalService`, `ProposalOrchestrationService`,
     `ProposalVariableResolver`.
2. **Schema migration** (tenant): add three nullable columns to `proposals`:
   ```sql
   ALTER TABLE proposals ADD COLUMN contingency_percent NUMERIC(5,2);
   ALTER TABLE proposals ADD COLUMN contingency_cap_percent NUMERIC(5,2);
   ALTER TABLE proposals ADD COLUMN contingency_description TEXT;
   ```
   File: `backend/src/main/resources/db/migration/tenant/V{next}__add_contingency_fee_fields.sql`.
3. **Backend entity + request DTO**:
   - Add three fields on `Proposal.java` (with getters/setters).
   - Extend `CreateProposalRequest` / `UpdateProposalRequest` DTOs.
4. **Frontend schema**:
   - In `frontend/lib/schemas/proposal.ts`, extend the enum and add the three new optional
     fields:
     ```ts
     const feeModelEnum = z.enum(["FIXED", "HOURLY", "RETAINER", "CONTINGENCY"]);
     // ... existing fields ...
     contingencyPercent: z.number().min(0).max(100).optional(),
     contingencyCapPercent: z.number().min(0).max(100).optional(),
     contingencyDescription: z.string().max(500).optional().or(z.literal("")),
     ```
5. **Frontend dialog**:
   - In `create-proposal-dialog.tsx`:
     - Add `CONTINGENCY: "Contingency"` to the label map (line 54-56).
     - Set the default form value for CONTINGENCY scenarios. Keep existing default as-is.
     - Add a new conditional block (`feeModel === "CONTINGENCY" && (...)`) rendering:
       - Contingency Percent input (number, default 25, max 25 — display helper text "LPC Rule
         59 caps contingency fees at 25%")
       - Contingency Cap Percent input (number, default 25)
       - Contingency Description textarea
     - Add CONTINGENCY to the serialized payload in the submit handler (line 113-124):
       ```ts
       ...(values.feeModel === "CONTINGENCY" && {
         contingencyPercent: values.contingencyPercent,
         contingencyCapPercent: values.contingencyCapPercent,
         contingencyDescription: values.contingencyDescription,
       }),
       ```
6. **Clause auto-insert** (optional but recommended): when a proposal is created with
   `feeModel = CONTINGENCY`, the `ProposalOrchestrationService` should auto-prepend the
   `legal-za-fees-contingency` clause to the rendered letter body. This is a separate small
   change in the orchestration service — locate the existing clause-pack resolution logic and
   add the CONTINGENCY branch.
7. **Tests**:
   - Backend: add a `ProposalServiceTest` case creating a CONTINGENCY proposal and asserting
     the three new fields persist.
   - Frontend: extend `proposals-dashboard.test.tsx` or `create-proposal-dialog.test.tsx` to
     exercise the new path.

## Scope
- Backend + Frontend + Migration
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/FeeModel.java`
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java`
  - `backend/.../proposal/ProposalService.java` (request DTOs, create/update)
  - `backend/.../proposal/ProposalOrchestrationService.java` (clause auto-insert, optional)
  - `frontend/lib/schemas/proposal.ts`
  - `frontend/lib/types/proposal.ts`
  - `frontend/components/proposals/create-proposal-dialog.tsx`
- Files to create:
  - `backend/src/main/resources/db/migration/tenant/V{next}__add_contingency_fee_fields.sql`
- Migration needed: yes (tenant schema)

## Verification
1. Re-run Session 5 step 5.20: Open New Engagement Letter dialog. Fee Model select now shows
   four options including Contingency.
2. Select Contingency → three new fields appear. Fill: percent 25, cap 25, description "RAF
   plaintiff claim — 25% contingency per LPC Rule 59".
3. Save as draft. DB check: `SELECT fee_model, contingency_percent, contingency_cap_percent
   FROM proposals;` returns the row with CONTINGENCY.
4. Open the proposal detail page — verify the contingency clause text appears in the rendered
   preview.

## Estimated Effort
M (~2 hr — backend enum + DTO + migration + frontend schema + dialog. Clause auto-insert is
optional; drop if tight on time.)
