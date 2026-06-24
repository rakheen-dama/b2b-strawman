import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatCurrency, formatDate } from "@/lib/format";
import { ArrowRight } from "lucide-react";
import type { DealResponse, DealStatus } from "@/lib/api/crm";

const STATUS_BADGE: Record<
  DealStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" }
> = {
  OPEN: { label: "Open", variant: "lead" },
  WON: { label: "Won", variant: "success" },
  LOST: { label: "Lost", variant: "destructive" },
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-slate-900 dark:text-slate-100">{children}</p>
    </div>
  );
}

interface DealOverviewProps {
  deal: DealResponse;
  slug: string;
  customerName: string;
  ownerName: string | null;
}

export function DealOverview({ deal, slug, customerName, ownerName }: DealOverviewProps) {
  const currency = deal.valueCurrency || "ZAR";
  const statusBadge = STATUS_BADGE[deal.status] ?? { label: deal.status, variant: "neutral" };
  const customFieldEntries = Object.entries(deal.customFields ?? {}).filter(
    ([, v]) => v != null && v !== ""
  );

  return (
    <div className="space-y-6">
      <Card data-testid="deal-overview">
        <CardHeader>
          <CardTitle className="text-sm font-medium">Overview</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <Field label="Value">
              {deal.valueAmount != null ? formatCurrency(deal.valueAmount, currency) : "—"}
            </Field>
            <Field label="Stage">{deal.stageName ?? "—"}</Field>
            <Field label="Status">
              <Badge variant={statusBadge.variant} data-testid="deal-status-badge">
                {statusBadge.label}
              </Badge>
            </Field>
            <Field label="Probability">{deal.effectiveProbabilityPct}%</Field>
            <Field label="Weighted value">
              {deal.weightedValue != null ? formatCurrency(deal.weightedValue, currency) : "—"}
            </Field>
            <Field label="Owner">{ownerName ?? "Unassigned"}</Field>
            <Field label="Source">{deal.source ?? "—"}</Field>
            <Field label="Expected close">
              {deal.expectedCloseDate ? formatDate(deal.expectedCloseDate) : "—"}
            </Field>
            {deal.status === "WON" && deal.wonAt && (
              <Field label="Won">{formatDate(deal.wonAt)}</Field>
            )}
            {deal.status === "LOST" && deal.lostAt && (
              <Field label="Lost">{formatDate(deal.lostAt)}</Field>
            )}
            {deal.status === "LOST" && deal.lostReason && (
              <Field label="Lost reason">{deal.lostReason}</Field>
            )}
          </div>

          {customFieldEntries.length > 0 && (
            <div className="mt-6 border-t border-slate-200 pt-4 dark:border-slate-800">
              <p className="mb-3 text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Custom fields
              </p>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                {customFieldEntries.map(([key, value]) => (
                  <Field key={key} label={key}>
                    {String(value)}
                  </Field>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card data-testid="deal-customer-link">
        <CardHeader>
          <CardTitle className="text-sm font-medium">Customer</CardTitle>
        </CardHeader>
        <CardContent>
          <Link
            href={`/org/${slug}/customers/${deal.customerId}`}
            className="inline-flex items-center gap-1.5 font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
          >
            {customerName}
            <ArrowRight className="size-4" />
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
