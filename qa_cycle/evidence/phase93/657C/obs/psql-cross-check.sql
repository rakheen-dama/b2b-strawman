\set t tenant_7d218705360b
select a.account_name, a.account_type, a.is_primary, a.take_on_cutover_date, a.take_on_batch_id from :t.trust_accounts a order by a.created_at;
select a.account_name, sum(t.cashbook_effect) cashbook_at_cutover, count(*) rows from :t.trust_transactions t join :t.trust_accounts a on a.id = t.trust_account_id where t.transaction_type like 'TAKEON_%' and t.take_on_batch_id = :'batch' group by a.account_name order by 1;
select c.name client, p.name matter, sum(t.ledger_effect) filter (where t.ledger = 'CLIENT') client_ledger, sum(t.ledger_effect) filter (where t.ledger = 'CLIENT_INVESTMENT') invested from :t.trust_transactions t left join :t.customers c on c.id = t.customer_id left join :t.projects p on p.id = t.project_id where t.take_on_batch_id = :'batch' and t.customer_id is not null group by 1,2 order by 1;
select ca.account_type creditor, sum(t.ledger_effect) balance from :t.trust_transactions t join :t.trust_creditor_accounts ca on ca.id = t.creditor_account_id where t.take_on_batch_id = :'batch' group by 1 order by 1;
select t.transaction_type, t.amount, t.reference, t.transaction_date, t.status, t.cleared_at_cutover, c.name client from :t.trust_transactions t left join :t.customers c on c.id = t.customer_id where t.take_on_batch_id = :'batch' order by t.transaction_type, t.reference;
select status, count(*) from :t.trust_transactions where take_on_batch_id = :'batch' group by 1;
select id, status, block_count, cutover_date from :t.take_on_batches where id = :'batch';
select kind, member_name, role_name, statement_version, signed_at, revoked_at from :t.take_on_sign_offs where batch_id = :'batch';
select id, cutover_date, generated_at, snapshot->'differences' differences, snapshot->'creditorList'->>'creditorTotal' creditor_total from :t.take_on_reports where batch_id = :'batch';
select pt.transaction_type, pt.amount, pt.running_balance, pt.description, pt.transaction_date from portal.portal_trust_transaction pt join :t.customers c on c.id = pt.customer_id where c.name = 'Amahle Zulu' order by pt.transaction_date;
select pb.* from portal.portal_trust_balance pb join :t.customers c on c.id = pb.customer_id where c.name = 'Amahle Zulu';
select count(*) as trust_transactions_total, count(*) filter (where transaction_type like 'TAKEON_%') takeon_rows, count(*) filter (where reference like 'OBS657C-BACKDATED%') backdated_obs_rows from :t.trust_transactions;
