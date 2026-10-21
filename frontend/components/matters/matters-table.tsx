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
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { DiaryKindIcon, DIARY_KIND_SINGULAR } from "@/components/diary/diary-event-row";
import { DIARY_KINDS, type DiaryKind } from "@/app/(app)/org/[slug]/diary/diary-types";
import { formatCurrency, formatDate, formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  CUSTOM_FIELD_COLUMN_PREFIX,
  buildSortHref,
  formatCustomFieldCell,
  formatUnbilled,
  humaniseWorkType,
  isMatterColumnId,
  matterColumnLabel,
  type MatterColumnId,
  type MatterSort,
} from "@/lib/matters-list";
import type { MatterListRow, MatterStatus } from "@/lib/types";
import { MatterTableRow } from "./matter-table-row";
import { MatterRowActions } from "./matter-row-actions";

const STATUS_BADGE: Record<MatterStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  COMPLETED: { label: "Completed", variant: "neutral" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
  CLOSED: { label: "Closed", variant: "neutral" },
};

const NUMERIC_COLUMNS = new Set<string>(["unbilled", "trustBalance"]);

const MAX_TAG_CHIPS = 3;

function isDiaryKind(value: string | null): value is DiaryKind {
  return value !== null && (DIARY_KINDS as readonly string[]).includes(value);
}

const EMPTY = <span className="text-slate-400 dark:text-slate-600">—</span>;

interface MattersTableProps {
  slug: string;
  rows: MatterListRow[];
  /** Resolved column ids (`resolveMatterColumns`): matter column ids and `cf:<slug>`. */
  columns: string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Record<string, string>;
  sort: MatterSort;
  /** Current URL params (strings only), kept by the sort links. */
  query: Record<string, string>;
  /** Visually hidden table caption. */
  caption: string;
}

/**
 * Dense Matters table (arch §12.6.4). Directive-free so the server page renders it; the row click
 * handler and the row menu are client children.
 */
export function MattersTable({
  slug,
  rows,
  columns,
  customFieldNames,
  sort,
  query,
  caption,
}: MattersTableProps) {
  const matterHref = (id: string) => `/org/${slug}/matters/${id}`;

  function renderCell(row: MatterListRow, column: MatterColumnId) {
    switch (column) {
      case "referenceNumber":
        return row.referenceNumber ? (
          <Link
            href={matterHref(row.id)}
            className="font-mono text-xs text-slate-600 hover:text-slate-950 hover:underline dark:text-slate-400 dark:hover:text-slate-50"
          >
            {row.referenceNumber}
          </Link>
        ) : (
          EMPTY
        );
      case "name":
        return (
          <div className="flex min-w-0 items-center gap-2">
            <Link
              href={matterHref(row.id)}
              data-testid="matter-link"
              className="truncate font-medium text-slate-950 hover:underline dark:text-slate-50"
            >
              {row.name}
            </Link>
            {row.tags.length > 0 && (
              <span className="flex shrink-0 items-center gap-1">
                {row.tags.slice(0, MAX_TAG_CHIPS).map((tag) => (
                  <Badge
                    key={tag.id}
                    variant="outline"
                    className="text-xs"
                    style={tag.color ? { borderColor: tag.color, color: tag.color } : undefined}
                  >
                    {tag.name}
                  </Badge>
                ))}
                {row.tags.length > MAX_TAG_CHIPS && (
                  <Badge variant="neutral" className="text-xs">
                    +{row.tags.length - MAX_TAG_CHIPS}
                  </Badge>
                )}
              </span>
            )}
          </div>
        );
      case "clientName":
        return row.clientId && row.clientName ? (
          <Link
            href={`/org/${slug}/clients/${row.clientId}`}
            className="text-slate-700 hover:text-slate-950 hover:underline dark:text-slate-300 dark:hover:text-slate-50"
          >
            {row.clientName}
          </Link>
        ) : (
          EMPTY
        );
      case "workType":
        return row.workType ? humaniseWorkType(row.workType) : EMPTY;
      case "feeEarnerName":
        return row.feeEarnerName ? (
          <span className="inline-flex items-center gap-2">
            <AvatarCircle name={row.feeEarnerName} size={24} />
            <span>{row.feeEarnerName}</span>
          </span>
        ) : (
          EMPTY
        );
      case "status": {
        const badge = STATUS_BADGE[row.status];
        return badge ? (
          <Badge variant={badge.variant} data-testid="matter-status-badge">
            {badge.label}
          </Badge>
        ) : (
          row.status
        );
      }
      case "createdAt":
        return formatDate(row.createdAt) || EMPTY;
      case "unbilled": {
        const text = formatUnbilled(row.unbilledMinutes, row.unbilledValue, row.currency);
        return text === "—" ? EMPTY : text;
      }
      case "trustBalance":
        return row.trustBalance == null ? EMPTY : formatCurrency(row.trustBalance, row.currency);
      case "nextDiaryDate":
        if (!row.nextDiaryDate) return EMPTY;
        return (
          <span className="inline-flex items-center gap-1.5">
            {isDiaryKind(row.nextDiaryKind) && (
              <>
                <DiaryKindIcon kind={row.nextDiaryKind} />
                <span className="sr-only">{DIARY_KIND_SINGULAR[row.nextDiaryKind]}: </span>
              </>
            )}
            {formatLocalDate(row.nextDiaryDate)}
          </span>
        );
    }
  }

  return (
    <Table data-testid="matters-table">
      <TableCaption className="sr-only">{caption}</TableCaption>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          {columns.map((column) => {
            if (!isMatterColumnId(column)) {
              const fieldSlug = column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length);
              return (
                <TableHead key={column} scope="col">
                  {customFieldNames[fieldSlug] ?? fieldSlug}
                </TableHead>
              );
            }
            const active = sort.key === column;
            return (
              <TableHead
                key={column}
                scope="col"
                aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
                className={cn(NUMERIC_COLUMNS.has(column) && "text-right")}
              >
                <Link
                  href={buildSortHref(slug, query, column, sort)}
                  className={cn(
                    "inline-flex items-center gap-1 hover:text-slate-950 dark:hover:text-slate-50",
                    active && "text-slate-950 dark:text-slate-50"
                  )}
                >
                  {matterColumnLabel(column)}
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
          <MatterTableRow key={row.id} href={matterHref(row.id)}>
            {columns.map((column) => (
              <TableCell
                key={column}
                className={cn(
                  NUMERIC_COLUMNS.has(column) && "text-right font-mono tabular-nums",
                  column === "name" && "max-w-xs"
                )}
              >
                {isMatterColumnId(column)
                  ? renderCell(row, column)
                  : formatCustomFieldCell(
                      row.customFields?.[column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length)]
                    )}
              </TableCell>
            ))}
            <TableCell className="w-10 text-right">
              <MatterRowActions slug={slug} matterId={row.id} clientId={row.clientId} />
            </TableCell>
          </MatterTableRow>
        ))}
      </TableBody>
    </Table>
  );
}
