import { notFound } from "next/navigation";
import { Hourglass } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { getUnbilledSummary, type CustomerUnbilledSummary } from "@/lib/api/billing-runs";
import { EmptyState } from "@/components/empty-state";
import { UnbilledWorkTable } from "@/components/invoices/unbilled-work-table";
import { formatLocalDate } from "@/lib/format";
import { UnbilledWorkFilters } from "./unbilled-work-filters";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const CURRENCY_CODE = /^[A-Z]{3}$/;
/** GET /api/invoices/unbilled-summary needs a closed period (Phase 87 OQ #15). */
const DEFAULT_PERIOD_MONTHS = 24;
const COMMON_CURRENCIES = ["ZAR", "USD", "EUR", "GBP"];

/** Local yyyy-MM-dd (same idiom as invoices/page.tsx). */
function toIsoDate(d: Date): string {
  return d.toLocaleDateString("en-CA");
}

export default async function UnbilledWorkPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ periodFrom?: string; periodTo?: string; currency?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;

  // Same gate as the nav item and the sibling Fee notes / Debtors pages — before any fetch.
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("INVOICING")) {
    notFound();
  }

  let orgCurrency = "ZAR";
  try {
    const settings = await getOrgSettings();
    orgCurrency = settings.defaultCurrency || "ZAR";
  } catch (error) {
    // Non-fatal — fall back to ZAR (legal-za).
    console.error("Failed to load org settings for unbilled work page", error);
  }

  const now = new Date();
  const defaultTo = toIsoDate(now);
  const defaultFrom = toIsoDate(
    new Date(now.getFullYear(), now.getMonth() - DEFAULT_PERIOD_MONTHS, now.getDate())
  );
  const periodFrom =
    search.periodFrom && ISO_DATE.test(search.periodFrom) ? search.periodFrom : defaultFrom;
  const periodTo = search.periodTo && ISO_DATE.test(search.periodTo) ? search.periodTo : defaultTo;
  const currency =
    search.currency && CURRENCY_CODE.test(search.currency) ? search.currency : orgCurrency;
  const currencyOptions = [...new Set([orgCurrency, currency, ...COMMON_CURRENCIES])];

  let rows: CustomerUnbilledSummary[] = [];
  let fetchError = false;
  try {
    rows = await getUnbilledSummary({ periodFrom, periodTo, currency });
  } catch (error) {
    console.error("Failed to fetch unbilled summary:", error);
    fetchError = true;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Unbilled work</h1>
        <p
          className="mt-1 text-sm text-slate-600 dark:text-slate-400"
          data-testid="unbilled-period-help"
        >
          Unbilled time and expenses per client from {formatLocalDate(periodFrom)} to{" "}
          {formatLocalDate(periodTo)}, in {currency}. The period defaults to the last{" "}
          {DEFAULT_PERIOD_MONTHS} months because the summary needs a start and end date — change the
          dates or currency below.
        </p>
      </div>

      <UnbilledWorkFilters
        key={`${periodFrom}|${periodTo}|${currency}`}
        periodFrom={periodFrom}
        periodTo={periodTo}
        currency={currency}
        currencyOptions={currencyOptions}
      />

      {fetchError ? (
        <EmptyState
          icon={Hourglass}
          title="Unable to load unbilled work"
          description="There was a problem loading the unbilled summary. Please try again later."
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={Hourglass}
          title="No unbilled work"
          description="No client has unbilled time or expenses in this period and currency. Widen the period or pick another currency."
        />
      ) : (
        <UnbilledWorkTable rows={rows} currency={currency} slug={slug} />
      )}
    </div>
  );
}
