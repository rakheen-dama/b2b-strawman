/**
 * Clients list column definitions for `DataTable` (Phase 90 642A, ADR-369, arch §15.3.3).
 *
 * Imported ONLY by the "use client" `ClientsDataTable` (`clients-table.tsx`): the column defs hold
 * functions (cells, headers, `meta.csv`), which cannot cross the RSC boundary (OQ #19). Never
 * import this from a Server Component. No directive: no hooks here; the importing client module
 * puts it in the client bundle.
 *
 * Column ids = sort keys = saved-view `columns` values (`CLIENT_COLUMNS`, §12.6.4 order), plus
 * `cf:<slug>` custom-field columns (plain headers, not sortable) and a trailing row menu.
 */
import type { ReactNode } from "react";
import Link from "next/link";
import type { HeaderContext } from "@tanstack/react-table";
import { Badge } from "@b2mash/ui/badge";
import {
  LifecycleStatusBadge,
  lifecycleStatusLabel,
} from "@/components/compliance/LifecycleStatusBadge";
import { CompletenessBadge } from "@/components/customers/completeness-badge";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { formatDateInSA } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  CLIENT_TYPE_LABEL,
  CUSTOM_FIELD_COLUMN_PREFIX,
  UNSORTABLE_CLIENT_COLUMNS,
  clientColumnLabel,
  formatCustomFieldCell,
  formatOutstanding,
  isClientColumnId,
  type ClientColumnId,
} from "@/lib/clients-list";
import type { ClientListRow, FicaOnboardingStatus } from "@/lib/types";
import { ClientRowActions } from "./client-row-actions";

const NUMERIC_COLUMNS = new Set<ClientColumnId>(["openMatterCount", "outstanding", "trustBalance"]);

const MAX_TAG_CHIPS = 3;

/**
 * Lower-priority columns, shown only when the table's own container is wide enough for them (a
 * container query, so the sidebar and the page's max width are accounted for). Hide order, first
 * to go: Contact → Created → Open matters → Completeness. The Client column takes the width the
 * others leave, so the table never overflows; the thresholds keep it at least ~14.5rem wide.
 * Measured (E2E stack, 615D fix): the always-shown columns (Lifecycle, FICA, Outstanding fee notes,
 * Trust balance, row menu) need ~28rem; Completeness adds ~7.5rem, Open matters ~5rem, Created
 * ~6rem, Contact ~13rem. The container is ~44 / 70 / 75rem at 1024 / 1440 / 1920px (the shell's
 * `max-w-7xl` caps it), so 1024 shows the always-shown set, 1440 adds all but Contact, and 1920
 * shows every column. Re-measure if a column, a cap or the shell width changes.
 * A column is never hidden when it is the active sort or when a saved view chose the columns.
 *
 * Applied through `meta.headerClassName` / `meta.cellClassName` rather than DataTable `priority`:
 * the kit has two thresholds (67rem / 7xl), this table needs four. The active sort column and a
 * saved view's own column choice are never hidden (same rule as the kit's `priority`).
 */
const LOW_PRIORITY_COLUMN_CLASS: Partial<Record<ClientColumnId, string>> = {
  completeness: "hidden @min-[50rem]:table-cell",
  openMatterCount: "hidden @min-[55rem]:table-cell",
  createdAt: "hidden @min-[61rem]:table-cell",
  contact: "hidden @min-[74rem]:table-cell",
};

/**
 * The Client column takes whatever width the other columns leave (`w-full`) and, with
 * `max-w-0`, adds nothing to the table's minimum width — so a long name truncates instead of
 * pushing the row menu out of view.
 */
const CLIENT_CELL_CLASS = "w-full max-w-0";

const FICA_LABEL: Record<FicaOnboardingStatus, string> = {
  DONE: "Done",
  IN_PROGRESS: "In progress",
  NOT_STARTED: "Not started",
};

/**
 * A multi-word header breaks after its first word ("Outstanding / fee notes"): two short lines
 * instead of one wide one. The space between the lines keeps the text content and accessible name
 * intact.
 */
function HeaderLabel({ label }: { label: string }) {
  const space = label.indexOf(" ");
  if (space === -1) return <>{label}</>;
  return (
    <span className="inline-flex flex-col leading-tight">
      <span>{label.slice(0, space)}</span> <span>{label.slice(space + 1)}</span>
    </span>
  );
}

/** DataTable's sort-link header with the label broken after its first word. */
function renderClientHeader({ column, table }: HeaderContext<ClientListRow, unknown>) {
  return (
    <DataTableColumnHeader column={column} sort={table.options.meta?.dataTableSort}>
      <HeaderLabel label={column.columnDef.meta?.label ?? column.id} />
    </DataTableColumnHeader>
  );
}

/** FICA verified date, cell and CSV alike, on the South African calendar (see `DateCell`). */
function ficaVerified(row: ClientListRow): string {
  return row.ficaStatus === "DONE" && row.ficaLastVerifiedAt
    ? formatDateInSA(row.ficaLastVerifiedAt)
    : "";
}

function FicaCell({ row }: { row: ClientListRow }) {
  switch (row.ficaStatus) {
    case "DONE": {
      const verified = ficaVerified(row);
      return (
        // The verified date stacks under the badge, keeping the column narrow.
        <span className="inline-flex flex-col items-start gap-0.5 whitespace-nowrap">
          <Badge variant="default" data-testid="client-fica-status">
            Done
          </Badge>
          {verified && <span className="text-muted-foreground text-xs">{verified}</span>}
        </span>
      );
    }
    case "IN_PROGRESS":
      return (
        <Badge variant="outline" className="whitespace-nowrap" data-testid="client-fica-status">
          In progress
        </Badge>
      );
    default:
      return (
        <span
          className="text-destructive text-xs font-medium whitespace-nowrap"
          data-testid="client-fica-status"
        >
          Not started
        </span>
      );
  }
}

/** The "—" placeholder (or an empty string) is an empty CSV field. */
function nullIfDash(text: string): string | null {
  return text === "—" || text === "" ? null : text;
}

/** CSV value of a custom field: NUMBER values stay numbers (no formula guard); the rest as shown. */
function customFieldCsv(value: unknown): string | number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return nullIfDash(formatCustomFieldCell(value));
}

function contactParts(row: ClientListRow): string[] {
  return [row.email, row.phone].filter((v): v is string => v != null && v.trim() !== "");
}

function outstandingText(row: ClientListRow): string {
  return formatOutstanding(row.outstandingFeeNoteTotal, row.outstandingFeeNoteCount, row.currency);
}

function clientCell(slug: string, column: ClientColumnId, row: ClientListRow): ReactNode {
  switch (column) {
    case "name": {
      const typeLabel = row.clientType ? CLIENT_TYPE_LABEL[row.clientType] : undefined;
      return (
        // The cell takes the table's spare width and truncates (see CLIENT_CELL_CLASS); the
        // client type and tags stack under the name instead of widening the row.
        <div className="flex min-w-0 flex-col items-start gap-0.5">
          {/* D-9: the link text is exactly the name; the client type is a sibling, so
              getByRole("link", { name, exact: true }) keeps matching. */}
          <Link
            href={`/org/${slug}/clients/${row.id}`}
            data-testid="client-link"
            title={row.name}
            className="text-foreground max-w-full truncate font-medium hover:underline"
          >
            {row.name}
          </Link>
          {(typeLabel || row.tags.length > 0) && (
            <div className="flex max-w-full min-w-0 items-center gap-1 overflow-hidden">
              {typeLabel && (
                <span className="text-muted-foreground shrink-0 text-xs" data-testid="client-type">
                  {typeLabel}
                </span>
              )}
              {row.tags.slice(0, MAX_TAG_CHIPS).map((tag) => (
                <Badge
                  key={tag.id}
                  variant="outline"
                  className="shrink-0 text-xs"
                  style={tag.color ? { borderColor: tag.color, color: tag.color } : undefined}
                >
                  {tag.name}
                </Badge>
              ))}
              {row.tags.length > MAX_TAG_CHIPS && (
                <Badge variant="neutral" className="shrink-0 text-xs">
                  +{row.tags.length - MAX_TAG_CHIPS}
                </Badge>
              )}
            </div>
          )}
        </div>
      );
    }
    case "lifecycleStatus":
      return row.lifecycleStatus ? (
        <LifecycleStatusBadge status={row.lifecycleStatus} />
      ) : (
        <EmptyCell />
      );
    case "ficaStatus":
      return <FicaCell row={row} />;
    case "openMatterCount":
      return row.openMatterCount;
    case "outstanding": {
      const text = outstandingText(row);
      return text === "—" ? <EmptyCell /> : <span className="whitespace-nowrap">{text}</span>;
    }
    case "trustBalance":
      // null = redacted or trust accounting off → "—", never 0.
      return (
        <MoneyCell
          amount={row.trustBalance}
          currency={row.currency}
          className="whitespace-nowrap"
        />
      );
    case "completeness":
      // The row carries the percentage only; null = no required field applies (N/A).
      return <CompletenessBadge percentage={row.completenessPercent} />;
    case "createdAt":
      return <DateCell value={row.createdAt} />;
    case "contact": {
      const parts = contactParts(row);
      if (parts.length === 0) return <EmptyCell />;
      // Email and phone stack, each capped and truncated (full text in the title).
      return (
        <div className="text-muted-foreground flex flex-col">
          {parts.map((part) => (
            <span key={part} className="block max-w-[12rem] truncate" title={part}>
              {part}
            </span>
          ))}
        </div>
      );
    }
  }
}

/** CSV value: the text the cell shows ("—" → empty); Trust as the raw amount (641A D6). */
function clientCsv(column: ClientColumnId, row: ClientListRow): string | number | null {
  switch (column) {
    case "name":
      return row.name;
    case "lifecycleStatus":
      return row.lifecycleStatus ? lifecycleStatusLabel(row.lifecycleStatus) : null;
    case "ficaStatus": {
      const label = FICA_LABEL[row.ficaStatus] ?? FICA_LABEL.NOT_STARTED;
      const verified = ficaVerified(row);
      return verified ? `${label} · ${verified}` : label;
    }
    case "openMatterCount":
      return row.openMatterCount;
    case "outstanding":
      return nullIfDash(outstandingText(row));
    case "trustBalance":
      return row.trustBalance;
    case "completeness":
      return row.completenessPercent == null ? "N/A" : `${row.completenessPercent}%`;
    case "createdAt":
      return formatDateInSA(row.createdAt) || null;
    case "contact":
      return contactParts(row).join(" · ") || null;
  }
}

export interface ClientColumnOptions {
  slug: string;
  /** Resolved column ids (`resolveClientColumns`): client column ids and `cf:<slug>`. */
  columns: readonly string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Readonly<Record<string, string>>;
  /** `false` for a saved view's own column choice: no column is hidden by priority. */
  hideLowPriorityColumns: boolean;
  /** The active sort column: never hidden by priority. */
  sortKey: ClientColumnId;
}

/** The DataTable columns for the resolved column ids, in order, plus the trailing row menu. */
export function buildClientColumns({
  slug,
  columns,
  customFieldNames,
  hideLowPriorityColumns,
  sortKey,
}: ClientColumnOptions): DataTableColumn<ClientListRow>[] {
  function widthClass(column: ClientColumnId): string | undefined {
    if (column === "name") return CLIENT_CELL_CLASS;
    if (!hideLowPriorityColumns || column === sortKey) return undefined;
    return LOW_PRIORITY_COLUMN_CLASS[column];
  }

  const defs = columns.map((column): DataTableColumn<ClientListRow> => {
    if (!isClientColumnId(column)) {
      const fieldSlug = column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length);
      return {
        id: column,
        meta: {
          label: customFieldNames[fieldSlug] ?? fieldSlug,
          csv: (row) => customFieldCsv(row.customFields?.[fieldSlug]),
        },
        cell: ({ row }) => {
          const text = formatCustomFieldCell(row.original.customFields?.[fieldSlug]);
          return text === "—" ? <EmptyCell /> : text;
        },
      };
    }
    const numeric = NUMERIC_COLUMNS.has(column);
    const width = widthClass(column);
    return {
      id: column,
      // The Client link is the keyboard path and the e2e row filter: never hideable.
      enableHiding: column !== "name",
      header: renderClientHeader,
      meta: {
        label: clientColumnLabel(column),
        sortKey: UNSORTABLE_CLIENT_COLUMNS.has(column) ? undefined : column,
        align: numeric ? "end" : undefined,
        headerClassName: width,
        cellClassName: cn(numeric && "font-mono tabular-nums", width) || undefined,
        csv: (row) => clientCsv(column, row),
      },
      cell: ({ row }) => clientCell(slug, column, row.original),
    };
  });

  return defineColumns<ClientListRow>([
    ...defs,
    {
      id: "actions",
      enableHiding: false,
      meta: { label: "Actions", align: "end", headerClassName: "w-10", cellClassName: "w-10" },
      header: () => <span className="sr-only">Actions</span>,
      cell: ({ row }) => <ClientRowActions slug={slug} clientId={row.original.id} />,
    },
  ]);
}
