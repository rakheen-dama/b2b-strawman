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
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { CREDITOR_LINE_KIND_LABELS, type CreditorLine } from "@/lib/types";

interface MonthlyListCreditorsTableProps {
  /** The locked list's creditor lines, in the server's order. */
  lines: CreditorLine[];
  creditorTotal: number;
  currency: string;
  /** The list's period end, already formatted ("Trust creditors as at …"). */
  caption: string;
}

/** A per-matter / brought-forward sub-row: indented, muted. */
const SUB_ROW_NAME = "text-muted-foreground py-2 pl-6 whitespace-normal";
const SUB_ROW_KIND = "text-muted-foreground py-2";
const SUB_ROW_MONEY = "text-muted-foreground py-2 text-right font-mono tabular-nums";

/**
 * The Rule 54.15 list's trust creditors (Phase 92 650A; Phase 91 667B on the kit table). Built on the
 * kit `ui/table` primitives, not `DataTable`: every creditor carries sub-rows (its matters, its
 * balance brought forward at take-on) and the table ends in a `<tfoot>` total, neither of which
 * `DataTable` renders (ADR-395: "a table that cannot reach parity … stays on kit table styling").
 * A server component (no hooks, no handlers); the client-mode after-lock table is in
 * `monthly-list-tables.tsx`.
 */
export function MonthlyListCreditorsTable({
  lines,
  creditorTotal,
  currency,
  caption,
}: MonthlyListCreditorsTableProps) {
  return (
    <Table data-testid="monthly-list-creditors">
      <TableCaption className="sr-only">{caption}</TableCaption>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead scope="col">Trust creditor</TableHead>
          <TableHead scope="col">Kind</TableHead>
          <TableHead scope="col" className="text-right">
            Balance
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {lines.map((line, index) => (
          <CreditorRows key={`${line.kind}-${index}`} line={line} currency={currency} />
        ))}
      </TableBody>
      <TableFooter>
        <TableRow className="hover:bg-transparent">
          <TableCell colSpan={2} className="text-foreground">
            Total trust creditors
          </TableCell>
          <TableCell className="text-foreground text-right font-mono tabular-nums">
            {formatCurrency(creditorTotal, currency)}
          </TableCell>
        </TableRow>
      </TableFooter>
    </Table>
  );
}

/**
 * One creditor, its per-matter breakdown beneath; an unidentified deposit shows when received; a
 * client taken on shows its balance brought forward at take-on (657A).
 */
function CreditorRows({ line, currency }: { line: CreditorLine; currency: string }) {
  const name =
    line.kind === "UNIDENTIFIED"
      ? `${line.bankDescription ?? "Unidentified deposit"}${
          line.receivedDate ? ` · received ${formatLocalDate(line.receivedDate)}` : ""
        }`
      : (line.name ?? CREDITOR_LINE_KIND_LABELS[line.kind]);
  return (
    <>
      <TableRow>
        <TableCell className="text-foreground py-3 font-medium whitespace-normal">{name}</TableCell>
        <TableCell className="text-muted-foreground py-3">
          {CREDITOR_LINE_KIND_LABELS[line.kind]}
        </TableCell>
        <TableCell className="py-3 text-right font-mono tabular-nums">
          {formatCurrency(line.balance, currency)}
        </TableCell>
      </TableRow>
      {/* 657A: a client taken on from the previous system — its balance brought forward */}
      {line.kind === "CLIENT" && line.broughtForward != null && line.broughtForward !== 0 && (
        <TableRow data-testid="monthly-list-brought-forward">
          <TableCell className={SUB_ROW_NAME}>B/f at take-on</TableCell>
          <TableCell className={SUB_ROW_KIND}>Take-on</TableCell>
          <TableCell className={SUB_ROW_MONEY}>
            {formatCurrency(line.broughtForward, currency)}
          </TableCell>
        </TableRow>
      )}
      {line.matters.length > 0 &&
        line.matters.map((matter, index) => (
          <TableRow key={`${matter.matterId ?? "none"}-${index}`} data-testid="monthly-list-matter">
            <TableCell className={SUB_ROW_NAME}>
              {matter.name ?? "Not linked to a matter"}
            </TableCell>
            <TableCell className={SUB_ROW_KIND}>Matter</TableCell>
            <TableCell className={SUB_ROW_MONEY}>
              {formatCurrency(matter.balance, currency)}
            </TableCell>
          </TableRow>
        ))}
    </>
  );
}
