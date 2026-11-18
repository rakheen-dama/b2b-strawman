import { formatCurrency } from "@/lib/format";
import { interestSplit } from "@/lib/trust-interest";

interface InterestSharesProps {
  gross: number | undefined;
  /** The Fund's share as the bank shows it; omitted: 5% of the gross. */
  typedFundShare?: number;
  currency: string;
}

/**
 * 648B: what a section 86(4) interest entry posts — the Legal Practitioners' Fidelity Fund's 5% and
 * the client's 95% of the gross — shown live as the firm types (the server computes the same).
 */
export function InterestShares({ gross, typedFundShare, currency }: InterestSharesProps) {
  const split = interestSplit(gross, typedFundShare);
  return (
    <div className="space-y-1 rounded-lg border border-slate-200 p-3 text-sm dark:border-slate-700">
      <div className="flex justify-between">
        <span className="text-slate-600 dark:text-slate-300">Fidelity Fund&apos;s 5%</span>
        <span className="font-mono tabular-nums" data-testid="interest-fund-share">
          {split ? formatCurrency(split.fundShare, currency) : "—"}
        </span>
      </div>
      <div className="flex justify-between font-semibold">
        <span className="text-slate-950 dark:text-slate-50">Client&apos;s share</span>
        <span className="font-mono tabular-nums" data-testid="interest-client-share">
          {split ? formatCurrency(split.clientShare, currency) : "—"}
        </span>
      </div>
    </div>
  );
}
