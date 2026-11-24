import { Badge } from "@b2mash/ui/badge";
import { TableCell, TableHead } from "@/components/ui/table";
import type { TakeOnRow } from "@/lib/types";
import { humaniseWorkType } from "@/lib/matters-list";

/**
 * The matters dataset's own columns in the take-on row table (Phase 93 656A): the matter, the
 * client the dry run matched it to — and by which reference — and the responsible attorney. Every
 * other mapped value stays in "Other values".
 */
export const MATTER_ROW_COLUMNS: ReadonlyArray<{ key: string; label: string }> = [
  { key: "name", label: "Matter name" },
  { key: "referenceNumber", label: "Matter reference" },
  { key: "client", label: "Client" },
  { key: "leadMemberEmail", label: "Responsible attorney" },
];

/** The client references, in the order the dry run tries them, with their catalogue labels. */
const CLIENT_REFERENCE_LABEL: Readonly<Record<string, string>> = {
  clientEmail: "Client email",
  clientRef: "Client previous-system ID",
  clientIdNumber: "Client ID number",
};

/** Lead warnings and their badge (the backend's message stays in the Messages column). */
const LEAD_WARNING_BADGE: Readonly<Record<string, string>> = {
  LEAD_MEMBER_UNKNOWN: "Not a member",
  LEAD_NOT_APPLIED: "Not made lead",
};

/**
 * Keys shown in the matter columns, or derived by the dry run (`clientId`, `clientResolvedBy`,
 * `leadMemberId` — ids and codes, never shown), so not repeated in "Other values".
 */
export const MATTER_COLUMN_KEYS: ReadonlySet<string> = new Set([
  "name",
  "referenceNumber",
  "leadMemberEmail",
  ...Object.keys(CLIENT_REFERENCE_LABEL),
  "clientId",
  "clientResolvedBy",
  "leadMemberId",
]);

const MATTER_VALUE_LABEL: Readonly<Record<string, Record<string, string>>> = {
  priority: { LOW: "Low", MEDIUM: "Medium", HIGH: "High" },
};

/**
 * A coded matter value's label ("HIGH" → "High"; the stored work type "LITIGATION_MATTERS" →
 * "Litigation matters", as the Matters list shows it); any other value as text.
 */
export function matterValueText(key: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "";
  const text = String(value);
  if (key === "workType") return humaniseWorkType(text);
  return MATTER_VALUE_LABEL[key]?.[text] ?? text;
}

/** Values deleted on cancel (`purged`) or anonymisation (`redacted`) show no cells. */
function hidden(values: Record<string, unknown> | null) {
  return !values || values.purged === true || values.redacted === true;
}

const text = (value: unknown) =>
  value === null || value === undefined || value === "" ? null : String(value);

export function MatterColumnHeads() {
  return (
    <>
      {MATTER_ROW_COLUMNS.map((c) => (
        <TableHead key={c.key}>{c.label}</TableHead>
      ))}
    </>
  );
}

const CELL = "max-w-48 text-xs whitespace-normal text-slate-700 dark:text-slate-300";

export function MatterColumnCells({ row }: { row: TakeOnRow }) {
  const n = row.rowNumber;
  const values = row.values;
  if (hidden(values)) {
    return (
      <>
        {MATTER_ROW_COLUMNS.map((c) => (
          <TableCell key={c.key} className={CELL} data-testid={`take-on-row-${n}-${c.key}`}>
            —
          </TableCell>
        ))}
      </>
    );
  }
  const resolvedBy = text(values?.clientResolvedBy);
  const reference = resolvedBy ? text(values?.[resolvedBy]) : null;
  const referenceLabel = resolvedBy ? CLIENT_REFERENCE_LABEL[resolvedBy] : undefined;
  const leadWarning = row.warnings.find((w) => w.code in LEAD_WARNING_BADGE);
  return (
    <>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-name`}>
        {text(values?.name) ?? "—"}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-referenceNumber`}>
        {text(values?.referenceNumber) ?? "—"}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-client`}>
        {referenceLabel && reference ? (
          <>
            <span className="text-slate-500 dark:text-slate-400">{referenceLabel}:</span>{" "}
            {reference}
          </>
        ) : (
          "—"
        )}
      </TableCell>
      <TableCell className={CELL} data-testid={`take-on-row-${n}-leadMemberEmail`}>
        <span className="break-all">{text(values?.leadMemberEmail) ?? "—"}</span>
        {leadWarning && (
          <Badge variant="warning" className="ml-1" data-testid={`take-on-row-${n}-lead-warning`}>
            {LEAD_WARNING_BADGE[leadWarning.code]}
          </Badge>
        )}
      </TableCell>
    </>
  );
}
