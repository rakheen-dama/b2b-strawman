import { TableCell, TableHead } from "@/components/ui/table";
import { TAKE_ON_CLIENT_VALUE_LABEL } from "@/lib/types";

/**
 * The clients dataset's own columns in the take-on row table (Phase 93 655A): who each row will
 * become and the FICA facts an attester reads. Every other mapped value stays in "Other values".
 */
export const CLIENT_ROW_COLUMNS: ReadonlyArray<{ key: string; label: string }> = [
  { key: "name", label: "Client name" },
  { key: "email", label: "Email" },
  { key: "ficaClientType", label: "Client type" },
  { key: "riskRating", label: "Risk rating" },
  { key: "cddRecordsLocation", label: "CDD records location" },
];

export const CLIENT_COLUMN_KEYS: ReadonlySet<string> = new Set(
  CLIENT_ROW_COLUMNS.map((c) => c.key)
);

/** A coded FICA value's label ("UNRATED" → "Not risk-rated"); any other value as text. */
export function clientValueText(key: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "";
  const text = String(value);
  return TAKE_ON_CLIENT_VALUE_LABEL[key]?.[text] ?? text;
}

/** Values deleted on cancel (`purged`) or anonymisation (`redacted`) show no cells. */
function hidden(values: Record<string, unknown> | null) {
  return !values || values.purged === true || values.redacted === true;
}

export function ClientColumnHeads() {
  return (
    <>
      {CLIENT_ROW_COLUMNS.map((c) => (
        <TableHead key={c.key}>{c.label}</TableHead>
      ))}
    </>
  );
}

export function ClientColumnCells({
  rowNumber,
  values,
}: {
  rowNumber: number;
  values: Record<string, unknown> | null;
}) {
  const blank = hidden(values);
  return (
    <>
      {CLIENT_ROW_COLUMNS.map((c) => (
        <TableCell
          key={c.key}
          className="max-w-48 text-xs whitespace-normal text-slate-700 dark:text-slate-300"
          data-testid={`take-on-row-${rowNumber}-${c.key}`}
        >
          {blank ? "—" : clientValueText(c.key, values?.[c.key]) || "—"}
        </TableCell>
      ))}
    </>
  );
}
