-- Add FIXED_FEE to invoice_lines line_type check constraint (Epic 233B)
ALTER TABLE invoice_lines DROP CONSTRAINT IF EXISTS invoice_lines_line_type_check;
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check
    CHECK (line_type IN ('TIME', 'RETAINER', 'EXPENSE', 'MANUAL', 'FIXED_FEE'));
