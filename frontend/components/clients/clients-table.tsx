import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import { CompletenessBadge } from "@/components/customers/completeness-badge";
import { formatCurrency, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  CLIENT_TYPE_LABEL,
  CUSTOM_FIELD_COLUMN_PREFIX,
  UNSORTABLE_CLIENT_COLUMNS,
  buildClientSortHref,
  clientColumnLabel,
  formatCustomFieldCell,
  formatOutstanding,
  isClientColumnId,
  type ClientColumnId,
  type ClientSort,
} from "@/lib/clients-list";
import type { ClientListRow } from "@/lib/types";
import { ClientTableRow } from "./client-table-row";
import { ClientRowActions } from "./client-row-actions";

const NUMERIC_COLUMNS = new Set<string>(["openMatterCount", "outstanding", "trustBalance"]);

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
 */
export const LOW_PRIORITY_COLUMN_CLASS: Partial<Record<ClientColumnId, string>> = {
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

const EMPTY = <span className="text-slate-400 dark:text-slate-600">—</span>;

interface ClientsTableProps {
  slug: string;
  rows: ClientListRow[];
  /** Resolved column ids (`resolveClientColumns`): client column ids and `cf:<slug>`. */
  columns: string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Record<string, string>;
  sort: ClientSort;
  /** Current URL params (strings only), kept by the sort links. */
  query: Record<string, string>;
  /** Visually hidden table caption. */
  caption: string;
  /**
   * Hide lower-priority columns in narrow containers. Off for a saved view's own column choice;
   * the active sort column is never hidden.
   */
  hideLowPriorityColumns?: boolean;
}

function FicaCell({ row }: { row: ClientListRow }) {
  switch (row.ficaStatus) {
    case "DONE": {
      const verified = row.ficaLastVerifiedAt ? formatDate(row.ficaLastVerifiedAt) : "";
      return (
        // The verified date stacks under the badge, keeping the column narrow.
        <span className="inline-flex flex-col items-start gap-0.5 whitespace-nowrap">
          <Badge variant="default" data-testid="client-fica-status">
            Done
          </Badge>
          {verified && (
            <span className="text-xs text-slate-500 dark:text-slate-400">{verified}</span>
          )}
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
          className="text-xs font-medium whitespace-nowrap text-red-600 dark:text-red-400"
          data-testid="client-fica-status"
        >
          Not started
        </span>
      );
  }
}

/**
 * Dense Clients table (arch §12.6.4). Directive-free so the server page renders it; the row click
 * handler and the row menu are client children.
 */
export function ClientsTable({
  slug,
  rows,
  columns,
  customFieldNames,
  sort,
  query,
  caption,
  hideLowPriorityColumns = true,
}: ClientsTableProps) {
  const clientHref = (id: string) => `/org/${slug}/clients/${id}`;

  function priorityClass(column: string): string | undefined {
    if (column === "name") return CLIENT_CELL_CLASS;
    if (!hideLowPriorityColumns || !isClientColumnId(column) || sort.key === column)
      return undefined;
    return LOW_PRIORITY_COLUMN_CLASS[column];
  }

  function renderCell(row: ClientListRow, column: ClientColumnId) {
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
              href={clientHref(row.id)}
              data-testid="client-link"
              title={row.name}
              className="max-w-full truncate font-medium text-slate-950 hover:underline dark:text-slate-50"
            >
              {row.name}
            </Link>
            {(typeLabel || row.tags.length > 0) && (
              <div className="flex max-w-full min-w-0 items-center gap-1 overflow-hidden">
                {typeLabel && (
                  <span
                    className="shrink-0 text-xs text-slate-500 dark:text-slate-400"
                    data-testid="client-type"
                  >
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
        return row.lifecycleStatus ? <LifecycleStatusBadge status={row.lifecycleStatus} /> : EMPTY;
      case "ficaStatus":
        return <FicaCell row={row} />;
      case "openMatterCount":
        return row.openMatterCount;
      case "outstanding": {
        const text = formatOutstanding(
          row.outstandingFeeNoteTotal,
          row.outstandingFeeNoteCount,
          row.currency
        );
        return text === "—" ? EMPTY : <span className="whitespace-nowrap">{text}</span>;
      }
      case "trustBalance":
        return row.trustBalance == null ? (
          EMPTY
        ) : (
          <span className="whitespace-nowrap">
            {formatCurrency(row.trustBalance, row.currency)}
          </span>
        );
      case "completeness":
        // The row carries the percentage only; null = no required field applies (N/A).
        return <CompletenessBadge percentage={row.completenessPercent} />;
      case "createdAt":
        return formatDate(row.createdAt) || EMPTY;
      case "contact": {
        const parts = [row.email, row.phone].filter(
          (v): v is string => v != null && v.trim() !== ""
        );
        if (parts.length === 0) return EMPTY;
        // Email and phone stack, each capped and truncated (full text in the title).
        return (
          <div className="flex flex-col text-slate-500 dark:text-slate-400">
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

  return (
    // `@container`: the low-priority column queries measure this box, and inline-size containment
    // keeps the table's width from feeding back into the page layout.
    <div className="@container">
      <Table data-testid="clients-table">
        <TableCaption className="sr-only">{caption}</TableCaption>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {columns.map((column) => {
              if (!isClientColumnId(column)) {
                const fieldSlug = column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length);
                return (
                  <TableHead key={column} scope="col">
                    {customFieldNames[fieldSlug] ?? fieldSlug}
                  </TableHead>
                );
              }
              const numeric = NUMERIC_COLUMNS.has(column) && "text-right";
              if (UNSORTABLE_CLIENT_COLUMNS.has(column)) {
                return (
                  <TableHead
                    key={column}
                    scope="col"
                    className={cn(numeric, priorityClass(column))}
                  >
                    <HeaderLabel label={clientColumnLabel(column)} />
                  </TableHead>
                );
              }
              const active = sort.key === column;
              return (
                <TableHead
                  key={column}
                  scope="col"
                  aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
                  className={cn(numeric, priorityClass(column))}
                >
                  <Link
                    href={buildClientSortHref(slug, query, column, sort)}
                    className={cn(
                      "inline-flex items-center gap-1 hover:text-slate-950 dark:hover:text-slate-50",
                      active && "text-slate-950 dark:text-slate-50"
                    )}
                  >
                    <HeaderLabel label={clientColumnLabel(column)} />
                    {active && <span aria-hidden="true">{sort.dir === "asc" ? "↑" : "↓"}</span>}
                  </Link>
                </TableHead>
              );
            })}
            <TableHead scope="col" className="w-10">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <ClientTableRow key={row.id} href={clientHref(row.id)}>
              {columns.map((column) => (
                <TableCell
                  key={column}
                  className={cn(
                    NUMERIC_COLUMNS.has(column) && "text-right font-mono tabular-nums",
                    priorityClass(column)
                  )}
                >
                  {isClientColumnId(column)
                    ? renderCell(row, column)
                    : formatCustomFieldCell(
                        row.customFields?.[column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length)]
                      )}
                </TableCell>
              ))}
              <TableCell className="w-10 text-right">
                <ClientRowActions slug={slug} clientId={row.id} />
              </TableCell>
            </ClientTableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
