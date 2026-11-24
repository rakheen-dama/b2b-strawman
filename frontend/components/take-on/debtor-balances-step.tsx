import { Badge } from "@b2mash/ui/badge";
import { TableCell, TableHead } from "@/components/ui/table";
import { formatCurrency } from "@/lib/format";
import { takeOnLongDate, type TakeOnRow } from "@/lib/types";

/**
 * The debtor opening balances' own columns in the take-on row table (Phase 93 658A, §17.3.10): the
 * fee note brought forward from the previous system — its number, the client the dry run matched
 * it to (and by which reference), its dates and outstanding amount — and whether the client's
 * billing details are missing in Kazi (a warning only, FD-21). Every other mapped value stays in
 * "Other values".
 */
export const DEBTOR_ROW_COLUMNS: ReadonlyArray<{ key: string; label: string }> = [
  { key: "feeNoteNumber", label: "Fee note number" },
  { key: "client", label: "Client" },
  { key: "issueDate", label: "Issue date" },
  { key: "dueDate", label: "Due date" },
  { key: "outstandingAmount", label: "Outstanding" },
  { key: "clientDetails", label: "Client details" },
];

/** How the dry run matched the client reference (`clientResolvedBy`), as the reference's label. */
const CLIENT_RESOLVED_BY_LABEL: Readonly<Record<string, string>> = {
  email: "Client email",
  previousSystemId: "Client previous-system ID",
  idNumber: "Client ID number",
};

/** The warning codes the columns show as badges (the message stays in the Messages column). */
export const DUE_DATE_DEFAULTED = "DUE_DATE_DEFAULTED";
export const CLIENT_DETAILS_MISSING = "CLIENT_DETAILS_MISSING";

/**
 * Keys shown in the debtor columns, or derived by the dry run (`clientId`, `clientResolvedBy`,
 * `matterId` — ids and codes, never shown), so not repeated in "Other values".
 */
export const DEBTOR_COLUMN_KEYS: ReadonlySet<string> = new Set([
  "feeNoteNumber",
  "clientRef",
  "issueDate",
  "dueDate",
  "outstandingAmount",
  "clientId",
  "clientResolvedBy",
  "matterId",
]);

/**
 * A debtor value as text: the dates (date-only "yyyy-MM-dd") as the dry run's messages write them,
 * "15 September 2026" — plain calendar arithmetic, so the day is the same in every time zone (a
 * `Date` parse reads them as UTC midnight, the previous day west of UTC); anything else as given.
 */
export function debtorValueText(key: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "";
  const text = String(value);
  if (key === "issueDate" || key === "dueDate") return takeOnLongDate(text) || text;
  return text;
}

/** Values deleted on cancel (`purged`) or anonymisation (`redacted`) show no cells. */
function hidden(values: Record<string, unknown> | null) {
  return !values || values.purged === true || values.redacted === true;
}

const text = (value: unknown) =>
  value === null || value === undefined || value === "" ? null : String(value);

function amountText(amount: unknown, currency: unknown) {
  const value = text(amount);
  if (value === null) return null;
  const n = Number(value);
  return Number.isFinite(n) ? formatCurrency(n, text(currency) ?? "ZAR") : value;
}

export function DebtorColumnHeads() {
  return (
    <>
      {DEBTOR_ROW_COLUMNS.map((c) => (
        <TableHead key={c.key}>{c.label}</TableHead>
      ))}
    </>
  );
}

const CELL = "max-w-48 text-xs whitespace-normal text-slate-700 dark:text-slate-300";

export function DebtorColumnCells({ row }: { row: TakeOnRow }) {
  const n = row.rowNumber;
  const values = row.values;
  if (hidden(values)) {
    return (
      <>
        {DEBTOR_ROW_COLUMNS.map((c) => (
          <TableCell key={c.key} className={CELL} data-testid={`take-on-row-${n}-${c.key}`}>
            —
          </TableCell>
        ))}
      </>
    );
  }
  const resolvedBy = text(values?.clientResolvedBy);
  const referenceLabel = resolvedBy ? CLIENT_RESOLVED_BY_LABEL[resolvedBy] : undefined;
  const reference = text(values?.clientRef);
  const dueDefaulted = row.warnings.some((w) => w.code === DUE_DATE_DEFAULTED);
  const detailsMissing = row.warnings.some((w) => w.code === CLIENT_DETAILS_MISSING);
  const issueDate = text(values?.issueDate);
  const dueDate = text(values?.dueDate);
  return (
    <>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-feeNoteNumber`}>
        {text(values?.feeNoteNumber) ?? "—"}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-client`}>
        {referenceLabel && reference ? (
          <>
            <span className="text-slate-500 dark:text-slate-400">{referenceLabel}:</span>{" "}
            <span className="break-all">{reference}</span>
          </>
        ) : (
          (reference ?? "—")
        )}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-issueDate`}>
        {issueDate ? debtorValueText("issueDate", issueDate) : "—"}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-dueDate`}>
        {dueDate ? debtorValueText("dueDate", dueDate) : "—"}
        {dueDefaulted && (
          <Badge
            variant="warning"
            className="ml-1"
            data-testid={`take-on-row-${n}-due-date-default`}
          >
            Default
          </Badge>
        )}
      </TableCell>
      <TableCell
        className={`${CELL} font-mono tabular-nums`}
        data-testid={`take-on-row-${n}-outstandingAmount`}
      >
        {amountText(values?.outstandingAmount, values?.currency) ?? "—"}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-clientDetails`}>
        {detailsMissing ? (
          <Badge variant="warning" data-testid={`take-on-row-${n}-details-warning`}>
            Address or email missing
          </Badge>
        ) : (
          "—"
        )}
      </TableCell>
    </>
  );
}
