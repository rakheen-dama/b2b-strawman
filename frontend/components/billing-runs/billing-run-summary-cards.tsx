import { Card } from "@/components/ui/card";
import { formatCurrency } from "@/lib/format";
import type { BillingRun } from "@/lib/api/billing-runs";

interface BillingRunSummaryCardsProps {
  billingRun: BillingRun;
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <div className="flex flex-col gap-1 px-4 py-3">
        <span className="text-muted-foreground text-xs tracking-wider uppercase">{label}</span>
        <span className="font-mono text-2xl font-bold tracking-tight tabular-nums">{value}</span>
      </div>
    </Card>
  );
}

export function BillingRunSummaryCards({ billingRun }: BillingRunSummaryCardsProps) {
  const totalCustomers = billingRun.totalCustomers ?? 0;
  const totalInvoices = billingRun.totalInvoices ?? 0;
  const totalSent = billingRun.totalSent ?? 0;
  const totalFailed = billingRun.totalFailed ?? 0;
  const totalAmount = billingRun.totalAmount ?? 0;

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
      <StatCard label="Customers" value={String(totalCustomers)} />
      <StatCard label="Invoices Generated" value={String(totalInvoices)} />
      <StatCard label="Sent" value={String(totalSent)} />
      <StatCard label="Failed" value={String(totalFailed)} />
      <StatCard label="Total Amount" value={formatCurrency(totalAmount, billingRun.currency)} />
    </div>
  );
}
