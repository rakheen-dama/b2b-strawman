\set t tenant_7d218705360b
-- read-only cross-check for 657D OBS (select only)
select a.account_name, a.account_type, a.is_primary, a.opened_date, a.take_on_cutover_date, a.take_on_batch_id from :t.trust_accounts a order by a.created_at;
select r.id report_id, a.account_name, r.cutover_date, r.generated_at from :t.take_on_reports r join :t.take_on_batches b on b.id = r.batch_id join :t.trust_accounts a on a.id = b.trust_account_id order by r.generated_at;
select f.report_id, f.format, f.sha256, f.file_size, f.s3_key, f.rendered_at from :t.take_on_report_files f order by f.rendered_at, f.format;
select report_id, attempts, last_error_code, last_attempt_at, failed_at from :t.take_on_report_renders order by last_attempt_at;
select event_type, entity_type, entity_id, details, occurred_at from :t.audit_events where event_type = 'takeon.report.downloaded' order by occurred_at;
select a.account_name, s.file_name, s.period_start, s.period_end, s.opening_balance, s.closing_balance, s.line_count, s.matched_count, s.status from :t.bank_statements s join :t.trust_accounts a on a.id = s.trust_account_id order by s.created_at;
select a.account_name, l.line_number, l.transaction_date, l.description, l.reference, l.amount, l.running_balance, l.match_status, l.classification, l.excluded_reason, t.transaction_type matched_type from :t.bank_statement_lines l join :t.bank_statements s on s.id = l.bank_statement_id join :t.trust_accounts a on a.id = s.trust_account_id left join :t.trust_transactions t on t.id = l.trust_transaction_id order by a.account_name, l.line_number;
select entity_id statement_id, details from :t.audit_events where event_type = 'bank_statement.imported' order by occurred_at;
select count(*) as line_excluded_audit_rows from :t.audit_events where event_type = 'bank_statement_line.excluded';
select a.account_name, r.period_end, r.status, r.is_balanced, r.bank_balance, r.outstanding_deposits, r.outstanding_payments, r.adjusted_bank_balance, r.cashbook_balance, r.client_ledger_total, r.fund_ledger_balance, r.unidentified_deposit_total, r.inter_account_balance, r.creditor_total, r.difference, r.notes, r.completed_at, r.integrity_flags from :t.trust_reconciliations r join :t.trust_accounts a on a.id = r.trust_account_id order by r.created_at;
select a.account_name, m.period_end, m.adjusted_bank_balance, m.cashbook_balance, m.creditor_total, m.difference, m.locked_at from :t.trust_monthly_lists m join :t.trust_accounts a on a.id = m.trust_account_id;
select a.account_name, t.transaction_type, t.amount, t.transaction_date, t.reference, t.status, t.cleared_at_cutover, t.take_on_batch_id is not null as take_on from :t.trust_transactions t join :t.trust_accounts a on a.id = t.trust_account_id where a.account_name like 'OBS 657D Trust banking G' order by t.transaction_date, t.transaction_type;
select a.account_name, sum(t.cashbook_effect) cashbook_now from :t.trust_transactions t join :t.trust_accounts a on a.id = t.trust_account_id where t.posted_at is not null group by 1 order by 1;
select event_type, count(*) from :t.audit_events where occurred_at >= :'from' group by 1 order by 1;
