#!/bin/bash
# Read-only evidence snapshot (SELECTs only). Usage: psql-snap.sh <label> <since-iso>
S=tenant_7d218705360b; L=$1; SINCE=${2:-2026-11-24T00:00:00Z}; OUT=$(dirname "$0")/psql-$L.txt
q(){ echo "-- $1"; docker exec e2e-postgres psql -U postgres -d app -c "$1"; }
{
echo "# snapshot $L at $(date -u +%FT%TZ) (since $SINCE)"
q "select next_number from $S.invoice_counters"
q "select i.invoice_number, i.status, i.issue_date, i.due_date, i.subtotal, i.tax_amount, i.total, i.amount_paid, i.currency, (i.take_on_batch_id is not null) as brought_forward, i.take_on_batch_id, i.payment_url, i.payment_session_id, c.name as client, (i.approved_by is not null) as approved_by_set, i.created_at from $S.invoices i join $S.customers c on c.id=i.customer_id order by i.created_at, i.invoice_number"
q "select i.invoice_number, l.line_type, l.description, l.quantity, l.unit_price, l.amount, l.tax_rate_id, l.tax_amount, (l.project_id is not null) as has_matter, p.reference_number as matter_ref from $S.invoices i join $S.invoice_lines l on l.invoice_id=i.id left join $S.projects p on p.id=l.project_id where i.take_on_batch_id is not null order by i.invoice_number"
q "select count(*) as notifications_since, string_agg(distinct type, ',') as types from $S.notifications where created_at >= '$SINCE'"
q "select n.type, n.title, m.name as recipient, n.created_at from $S.notifications n join $S.members m on m.id=n.recipient_member_id where n.created_at >= '$SINCE' order by n.created_at"
q "select count(*) as accounting_sync_entries_total from $S.accounting_sync_entry"
q "select e.entity_type, e.trigger, e.state, i.invoice_number, e.created_at from $S.accounting_sync_entry e left join $S.invoices i on i.id=e.entity_id order by e.created_at"
q "select count(*) as payment_events_total from $S.payment_events"
q "select count(*) as automation_executions_since from $S.automation_executions where created_at >= '$SINCE'"
q "select count(*) as email_delivery_log_since from $S.email_delivery_log where created_at >= '$SINCE'"
q "select domain, provider_slug, enabled from $S.org_integrations"
q "select pi.invoice_number, pi.status, pi.issue_date, pi.due_date, pi.total, pi.tax_amount, pi.payment_url, pi.payment_session_id, pi.synced_at from portal.portal_invoices pi where pi.org_id='e2e-test-org' order by pi.invoice_number"
q "select event_type, entity_type, details, occurred_at from $S.audit_events where occurred_at >= '$SINCE' and (event_type like 'invoice.%' or event_type like 'takeon.batch.%' or event_type like 'payment%') order by occurred_at"
} > "$OUT" 2>&1
echo "wrote $OUT"
