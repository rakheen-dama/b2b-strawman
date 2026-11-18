-- 648F OBS read-only evidence (SELECT only). obs-sql.out is the output of this file's text as committed in 4e0f23781, run against the stack built from 6bff38cd0 ([f1]-[f6], [g1]-[g5], [d1], generated documents);
-- [f4b] and [g4r] were added afterwards (CodeRabbit round 2) and have no recorded output — see README "obs-sql provenance".
-- Tenant schema tenant_7d218705360b (the only tenant on the fresh e2e stack).
-- QuickCollect = 2db7efe4-899b-4589-a1bf-dc483496e762 ; Sipho (depth-ii run) = c2b4ec99-9879-438f-a82e-7112c3b7e519 ; general account 27564008-6ca0-4b6d-8147-7e253b574b31

-- [f1] legal-za template pack install version
select pack_id, pack_type, pack_version, item_count, installed_at from tenant_7d218705360b.pack_install where pack_id = 'legal-za';
-- [f2] org_settings template pack status (applied versions)
select template_pack_status from tenant_7d218705360b.org_settings;
-- [f3] the two statement templates: install row, install-time content_hash (covers CSS), content markers
select pack_template_key, name, source, pack_id, source_pack_install_id, content_hash, created_at, updated_at,
       position('Section 86(4) investment' in content::text) > 0 as has_invested_block,
       position('Invested on your instruction (section 86(4))' in content::text) > 0 as has_invested_line,
       position('Opening balance' in content::text) > 0 as has_opening_balance
  from tenant_7d218705360b.document_templates where pack_template_key in ('client-trust-statement','statement-of-account') order by 1;
-- [f4] diagnostic only: md5 + length of the jsonb text (NOT the [f5] hash input)
select pack_template_key, md5(content::text) as md5_jsonb_text, length(content::text) from tenant_7d218705360b.document_templates where pack_template_key in ('client-trust-statement','statement-of-account') order by 1;
-- [f4b] (added in CodeRabbit round 2, NOT part of the 6bff38cd0 run — see README "obs-sql provenance")
-- machine-readable export of the [f5] hash input: one line per template, "<key>\t<content as JSON>".
-- Run unaligned/tuples-only and hash each body as canonical JSON (ADR-356 / ContentHashUtil.hashOf:
-- sorted keys, compact separators, raw UTF-8), e.g.
--   docker exec e2e-postgres psql -U postgres -d app -At -F $'\t' -c "<this select>" > f4b-content.tsv
--   python3 -I -c 'import sys,json,hashlib
--   for l in open(sys.argv[1], encoding="utf-8"):
--       k, body = l.rstrip("\n").split("\t", 1)
--       c = json.dumps(json.loads(body), sort_keys=True, separators=(",", ":"), ensure_ascii=False)
--       print(k, "sha256:" + hashlib.sha256(c.encode("utf-8")).hexdigest())' f4b-content.tsv
-- The same python canonicalisation applied to backend/src/main/resources/template-packs/legal-za/{client-trust-statement,statement-of-account}.json
-- reproduces the [f5] "file=" digests (re-checked offline in round 2).
select pack_template_key, content::text from tenant_7d218705360b.document_templates where pack_template_key in ('client-trust-statement','statement-of-account') and pack_id = 'legal-za' and source = 'PLATFORM' and source_template_id is null order by 1;

-- [g1] QuickCollect trust rows (all accounts, all ledgers)
select t.transaction_date, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, a.account_type, a.account_name, t.status,
       t.reference, (t.posted_at is not null) as posted, (t.reversal_of is not null) as is_reversal, (t.reversed_by_id is not null) as reversed
  from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id = t.trust_account_id
 where t.customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762' order by t.created_at;
-- [g2] QuickCollect investments
select id, investment_basis, institution, account_number, principal, deposit_date, status, withdrawal_amount, deposit_transaction_id, withdrawal_transaction_id
  from tenant_7d218705360b.trust_investments where customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762';
-- [g3] QuickCollect interest entries
select ii.entry_date, ii.gross_amount, ii.fund_share_amount, ii.net_amount, ii.presentation, ii.status
  from tenant_7d218705360b.trust_investment_interest ii join tenant_7d218705360b.trust_investments i on i.id = ii.investment_id where i.customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762';
-- [g4] ALL-ACCOUNTS, AS-AT scope (the CTS "Current Balance" / firm client-ledger scope): QuickCollect independent recomputation (posted rows only, every account, no date bound)
select
  coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT' and a.account_type = 'GENERAL'), 0) as general_balance,
  coalesce(sum(t.amount) filter (where t.transaction_type = 'INV_PLACE'), 0)                           as capital_placed,
  coalesce(sum(t.amount) filter (where t.transaction_type = 'INV_RETURN'), 0)                          as capital_returned,
  coalesce(sum(t.amount) filter (where t.transaction_type = 'INV_INTEREST'), 0)                        as interest_net_rows,
  coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT_INVESTMENT'), 0)                      as invested_balance,
  coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT' and a.account_type = 'GENERAL'), 0)
    + coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT_INVESTMENT'), 0)                  as held_in_trust_total
  from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id = t.trust_account_id
 where t.customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762' and t.posted_at is not null;
-- [g4] ALL-ACCOUNTS, AS-AT scope: interest entries of every QuickCollect investment, no date bound
select sum(gross_amount) as interest_gross, sum(fund_share_amount) as fund_share, sum(net_amount) as interest_net,
       round(sum(fund_share_amount) / nullif(sum(gross_amount), 0) * 100, 2) as fund_pct
  from tenant_7d218705360b.trust_investment_interest ii join tenant_7d218705360b.trust_investments i on i.id = ii.investment_id where i.customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762' and ii.status = 'POSTED';
-- [g4r] REPORT scope (added in CodeRabbit round 2, NOT part of the 6bff38cd0 run — see README "obs-sql provenance"):
-- the client-ledger-statement report request in obs/api-report-quickcollect.json — funding account trust_account_id
-- 27564008-6ca0-4b6d-8147-7e253b574b31, dateFrom 2026-08-20, dateTo 2026-11-18. General: CLIENT rows on that account only.
-- Invested: CLIENT_INVESTMENT rows / interest entries of QuickCollect investments funded from that account (the 648E
-- funding-account rule). Opening = before dateFrom; period = dateFrom..dateTo inclusive; closing = up to dateTo.
with p as (select '2db7efe4-899b-4589-a1bf-dc483496e762'::uuid as customer_id, '27564008-6ca0-4b6d-8147-7e253b574b31'::uuid as account_id,
                  date '2026-08-20' as date_from, date '2026-11-18' as date_to),
     gen as (select t.* from tenant_7d218705360b.trust_transactions t, p
              where t.customer_id = p.customer_id and t.trust_account_id = p.account_id and t.ledger = 'CLIENT' and t.posted_at is not null),
     inv as (select i.id from tenant_7d218705360b.trust_investments i, p where i.customer_id = p.customer_id and i.trust_account_id = p.account_id),
     invrows as (select t.* from tenant_7d218705360b.trust_transactions t
                  where t.investment_id in (select id from inv) and t.ledger = 'CLIENT_INVESTMENT' and t.posted_at is not null)
select
  (select coalesce(sum(ledger_effect), 0) from gen, p where transaction_date < p.date_from)                                           as general_opening,
  (select coalesce(sum(-ledger_effect), 0) from gen, p where ledger_effect < 0 and transaction_date between p.date_from and p.date_to) as general_debits,
  (select coalesce(sum(ledger_effect), 0) from gen, p where ledger_effect > 0 and transaction_date between p.date_from and p.date_to)  as general_credits,
  (select coalesce(sum(ledger_effect), 0) from gen, p where transaction_date <= p.date_to)                                            as general_closing,
  (select count(*) from gen, p where transaction_date between p.date_from and p.date_to)                                              as general_count,
  (select coalesce(sum(ledger_effect), 0) from invrows, p where transaction_date < p.date_from)                                        as invested_opening,
  (select coalesce(sum(amount), 0) from gen, p where transaction_type = 'INV_PLACE' and transaction_date between p.date_from and p.date_to)  as capital_placed,
  (select coalesce(sum(amount), 0) from gen, p where transaction_type = 'INV_RETURN' and transaction_date between p.date_from and p.date_to) as returned_to_trust,
  (select coalesce(sum(ledger_effect), 0) from invrows, p where transaction_date <= p.date_to)                                        as invested_closing;
-- [g4r] REPORT scope: interest entries of investments funded from the report's account, dated inside the report period
with p as (select '2db7efe4-899b-4589-a1bf-dc483496e762'::uuid as customer_id, '27564008-6ca0-4b6d-8147-7e253b574b31'::uuid as account_id,
                  date '2026-08-20' as date_from, date '2026-11-18' as date_to)
select sum(ii.gross_amount) as interest_gross, sum(ii.fund_share_amount) as fund_share, sum(ii.net_amount) as interest_net
  from tenant_7d218705360b.trust_investment_interest ii join tenant_7d218705360b.trust_investments i on i.id = ii.investment_id, p
 where i.customer_id = p.customer_id and i.trust_account_id = p.account_id and ii.status = 'POSTED'
   and ii.entry_date between p.date_from and p.date_to;

-- [g5] Fund-ledger / non-client rows tied to QuickCollect's investment (must not reach the client documents)
select t.transaction_type, t.ledger, t.amount, t.customer_id from tenant_7d218705360b.trust_transactions t
 where t.investment_id in (select id from tenant_7d218705360b.trust_investments where customer_id = '2db7efe4-899b-4589-a1bf-dc483496e762') and (t.customer_id is distinct from '2db7efe4-899b-4589-a1bf-dc483496e762' or t.ledger not in ('CLIENT','CLIENT_INVESTMENT'));

-- [d1] Sipho: no investment, no investment-ledger rows; general balance
select (select count(*) from tenant_7d218705360b.trust_investments where customer_id = 'c2b4ec99-9879-438f-a82e-7112c3b7e519') as investments,
       (select count(*) from tenant_7d218705360b.trust_transactions where customer_id = 'c2b4ec99-9879-438f-a82e-7112c3b7e519' and ledger <> 'CLIENT') as non_general_rows,
       (select coalesce(sum(ledger_effect), 0) from tenant_7d218705360b.trust_transactions where customer_id = 'c2b4ec99-9879-438f-a82e-7112c3b7e519' and ledger = 'CLIENT' and posted_at is not null) as general_balance;

-- [a/c] documents the firm UI generated in this OBS window (CTS Download PDF + SoA Preview & Save)
select g.id, t.pack_template_key, g.primary_entity_type, g.primary_entity_id, g.file_name, g.file_size, g.contains_trust_data, g.generated_at
  from tenant_7d218705360b.generated_documents g join tenant_7d218705360b.document_templates t on t.id = g.template_id
 where g.generated_at >= '2026-11-18T14:35:42Z' order by g.generated_at;
