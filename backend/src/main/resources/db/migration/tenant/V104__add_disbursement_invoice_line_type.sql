-- Add DISBURSEMENT to invoice_lines.line_type CHECK constraint (Epic 487B).
--
-- V101 updated line_source to include DISBURSEMENT and added invoice_lines.disbursement_id,
-- but left line_type unchanged. InvoiceCreationService.createDisbursementLines sets
-- BOTH line_type=DISBURSEMENT and line_source=DISBURSEMENT (mirroring TIME/EXPENSE/
-- FIXED_FEE/TARIFF), so line_type_check must be extended here.
--
-- References: architecture/phase67-legal-depth-ii.md §67, ADR-247.
ALTER TABLE invoice_lines DROP CONSTRAINT IF EXISTS invoice_lines_line_type_check;
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check
    CHECK (line_type IN ('TIME', 'RETAINER', 'EXPENSE', 'MANUAL', 'FIXED_FEE', 'TARIFF', 'DISBURSEMENT'));
