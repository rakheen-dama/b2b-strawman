import { StatCard } from "@b2mash/ui/stat-card";
import { formatCurrency } from "@/lib/format";
import type { BillingRun } from "@/lib/api/billing-runs";

interface BillingRunSummaryCardsProps {
  billingRun: BillingRun;
}

/**
 * The fee run's five kit StatCards (Phase 91 666A). Container-query columns sized so each card's
 * content box (`p-5`) holds an 11-character `text-2xl` mono value (~158px): the page column is
 * ~712px at 1024 (2-up) and ~1128px at 1440 (5-up, ~170px content); one column under 28rem.
 */
export function BillingRunSummaryCards({ billingRun }: BillingRunSummaryCardsProps) {
  const totalCustomers = billingRun.totalClients ?? 0;
  const totalInvoices = billingRun.totalInvoices ?? 0;
  const totalSent = billingRun.totalSent ?? 0;
  const totalFailed = billingRun.totalFailed ?? 0;
  const totalAmount = billingRun.totalAmount ?? 0;

  return (
    <div className="@container">
      <div className="grid grid-cols-1 gap-4 @md:grid-cols-2 @3xl:grid-cols-3 @5xl:grid-cols-5">
        <StatCard label="Clients" value={String(totalCustomers)} />
        <StatCard label="Fee notes generated" value={String(totalInvoices)} />
        <StatCard label="Sent" value={String(totalSent)} />
        <StatCard label="Failed" value={String(totalFailed)} />
        <StatCard label="Total Amount" value={formatCurrency(totalAmount, billingRun.currency)} />
      </div>
    </div>
  );
}
