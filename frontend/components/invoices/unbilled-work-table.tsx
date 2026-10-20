import Link from "next/link";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrency } from "@/lib/format";
import type { CustomerUnbilledSummary } from "@/lib/api/billing-runs";

interface UnbilledWorkTableProps {
  rows: CustomerUnbilledSummary[];
  currency: string;
  slug: string;
}

/**
 * Per-client unbilled time + expenses (GET /api/invoices/unbilled-summary), Phase 87 612B.
 * Not `unbilled-summary.tsx` — that renders one client's entry-level detail (UnbilledTimeResponse).
 */
export function UnbilledWorkTable({ rows, currency, slug }: UnbilledWorkTableProps) {
  const total = rows.reduce((sum, r) => sum + r.totalUnbilledAmount, 0);
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
      <Table data-testid="unbilled-work-table">
        <TableCaption className="sr-only">Unbilled time and expenses per client</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Client</TableHead>
            <TableHead scope="col" className="text-right">
              Time
            </TableHead>
            <TableHead scope="col" className="text-right">
              Expenses
            </TableHead>
            <TableHead scope="col" className="text-right">
              Total
            </TableHead>
            <TableHead scope="col">Ready to bill</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.clientId}>
              <TableCell>
                <Link
                  href={`/org/${slug}/clients/${r.clientId}`}
                  className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                >
                  {r.clientName}
                </Link>
              </TableCell>
              <TableCell className="text-right font-mono tabular-nums">
                {formatCurrency(r.unbilledTimeAmount, currency)}
                <span className="ml-1 text-xs text-slate-500">({r.unbilledTimeEntryCount})</span>
              </TableCell>
              <TableCell className="text-right font-mono tabular-nums">
                {formatCurrency(r.unbilledExpenseAmount, currency)}
                <span className="ml-1 text-xs text-slate-500">({r.unbilledExpenseCount})</span>
              </TableCell>
              <TableCell className="text-right font-mono font-medium tabular-nums">
                {formatCurrency(r.totalUnbilledAmount, currency)}
              </TableCell>
              <TableCell className="text-sm text-slate-600 dark:text-slate-400">
                {r.hasPrerequisiteIssues ? (r.prerequisiteIssueReason ?? "Missing details") : "Yes"}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell className="font-semibold">Total</TableCell>
            <TableCell />
            <TableCell />
            <TableCell
              className="text-right font-mono font-semibold tabular-nums"
              data-testid="unbilled-grand-total"
            >
              {formatCurrency(total, currency)}
            </TableCell>
            <TableCell />
          </TableRow>
        </TableFooter>
      </Table>
    </div>
  );
}
