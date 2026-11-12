/**
 * Matters list column definitions for `DataTable` (Phase 90 641A, ADR-369 §6, arch §15.3.3).
 *
 * Imported ONLY by the "use client" `MattersDataTable` (`matters-table.tsx`): the column defs hold
 * functions (cells, `meta.csv`), which cannot cross the RSC boundary (OQ #19). Never import this
 * from a Server Component. No directive: no hooks here; the importing client module puts it in the
 * client bundle.
 *
 * Column ids = sort keys = saved-view `columns` values (`MATTER_COLUMNS`, §12.6.4 order), plus
 * `cf:<slug>` custom-field columns (plain headers, not sortable) and a trailing row menu.
 */
import type { ReactNode } from "react";
import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { DateCell, EmptyCell, MoneyCell, StatusBadgeCell } from "@/components/data-table/cells";
import {
  defineColumns,
  type DataTableColumn,
  type DataTableColumnPriority,
} from "@/components/data-table/define-columns";
import { DiaryKindIcon, DIARY_KIND_SINGULAR } from "@/components/diary/diary-event-row";
import { DIARY_KINDS, type DiaryKind } from "@/app/(app)/org/[slug]/diary/diary-types";
import { formatDate, formatLocalDate } from "@/lib/format";
import {
  CUSTOM_FIELD_COLUMN_PREFIX,
  formatCustomFieldCell,
  formatUnbilled,
  humaniseWorkType,
  isMatterColumnId,
  matterColumnLabel,
  type MatterColumnId,
} from "@/lib/matters-list";
import type { MatterListRow, MatterStatus } from "@/lib/types";
import { MatterRowActions } from "./matter-row-actions";

const STATUS_BADGE: Record<MatterStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  COMPLETED: { label: "Completed", variant: "neutral" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
  CLOSED: { label: "Closed", variant: "neutral" },
};

const NUMERIC_COLUMNS = new Set<MatterColumnId>(["unbilled", "trustBalance"]);

const MAX_TAG_CHIPS = 3;

/**
 * Lower-priority columns, shown only when the table's own container is wide enough for them (a
 * container query, so the sidebar and the page's max width are accounted for). Thresholds follow
 * the measured column widths with the Matter/Client caps below: the other default columns plus
 * the row menu need ~66.5rem, Fee earner included; Work type and Opened add ~13rem. At 1440px the
 * container is ~70rem, so Fee earner shows. The shell's `max-w-7xl` caps the container at ~75rem
 * (measured at 1920px), so on today's shell Work type and Opened never reach their threshold: on
 * the default column set they render only as the active sort (Dormant sorts by Opened) or when a
 * saved view picks them. Re-measure these thresholds if a column, a cap or the shell width changes.
 * `low` = `hidden @min-[67rem]:table-cell`, `lowest` = `hidden @7xl:table-cell` (DataTable).
 */
const LOW_PRIORITY: Partial<Record<MatterColumnId, DataTableColumnPriority>> = {
  feeEarnerName: "low",
  workType: "lowest",
  createdAt: "lowest",
};

function isDiaryKind(value: string | null): value is DiaryKind {
  return value !== null && (DIARY_KINDS as readonly string[]).includes(value);
}

/** The "—" placeholder (or an empty string) is an empty CSV field. */
function nullIfDash(text: string): string | null {
  return text === "—" || text === "" ? null : text;
}

function matterCell(slug: string, column: MatterColumnId, row: MatterListRow): ReactNode {
  const matterHref = `/org/${slug}/matters/${row.id}`;
  switch (column) {
    case "referenceNumber":
      return row.referenceNumber ? (
        <Link
          href={matterHref}
          className="text-muted-foreground hover:text-foreground font-mono text-xs hover:underline"
        >
          {row.referenceNumber}
        </Link>
      ) : (
        <EmptyCell />
      );
    case "name":
      return (
        <div className="flex max-w-[14rem] min-w-0 items-center gap-2">
          <Link
            href={matterHref}
            data-testid="matter-link"
            title={row.name}
            className="text-foreground truncate font-medium hover:underline"
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
          title={row.clientName}
          className="text-foreground/80 hover:text-foreground inline-block max-w-[10rem] truncate align-middle hover:underline"
        >
          {row.clientName}
        </Link>
      ) : (
        <EmptyCell />
      );
    case "workType":
      return row.workType ? humaniseWorkType(row.workType) : <EmptyCell />;
    case "feeEarnerName":
      return row.feeEarnerName ? (
        <span className="inline-flex items-center gap-2">
          <AvatarCircle name={row.feeEarnerName} size={24} />
          <span className="max-w-[8rem] truncate" title={row.feeEarnerName}>
            {row.feeEarnerName}
          </span>
        </span>
      ) : (
        <EmptyCell />
      );
    case "status": {
      const badge = STATUS_BADGE[row.status];
      return badge ? (
        <StatusBadgeCell
          label={badge.label}
          variant={badge.variant}
          data-testid="matter-status-badge"
        />
      ) : (
        row.status
      );
    }
    case "createdAt":
      return <DateCell value={row.createdAt} />;
    case "unbilled": {
      const text = formatUnbilled(row.unbilledMinutes, row.unbilledValue, row.currency);
      return text === "—" ? <EmptyCell /> : text;
    }
    case "trustBalance":
      return <MoneyCell amount={row.trustBalance} currency={row.currency} />;
    case "nextDiaryDate":
      if (!row.nextDiaryDate) return <EmptyCell />;
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

/** CSV value: the text the cell shows ("—" → empty); Trust as the raw amount (D6). */
function matterCsv(column: MatterColumnId, row: MatterListRow): string | number | null {
  switch (column) {
    case "referenceNumber":
      return row.referenceNumber;
    case "name":
      return row.name;
    case "clientName":
      return row.clientName;
    case "workType":
      return row.workType ? humaniseWorkType(row.workType) : null;
    case "feeEarnerName":
      return row.feeEarnerName;
    case "status":
      return STATUS_BADGE[row.status]?.label ?? row.status;
    case "createdAt":
      return formatDate(row.createdAt) || null;
    case "unbilled":
      return nullIfDash(formatUnbilled(row.unbilledMinutes, row.unbilledValue, row.currency));
    case "trustBalance":
      return row.trustBalance;
    case "nextDiaryDate":
      return row.nextDiaryDate ? formatLocalDate(row.nextDiaryDate) || null : null;
  }
}

export interface MatterColumnOptions {
  slug: string;
  /** Resolved column ids (`resolveMatterColumns`): matter column ids and `cf:<slug>`. */
  columns: readonly string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Readonly<Record<string, string>>;
  /** `false` for a saved view's own column choice: no column is hidden by priority. */
  hideLowPriorityColumns: boolean;
}

/** The DataTable columns for the resolved column ids, in order, plus the trailing row menu. */
export function buildMatterColumns({
  slug,
  columns,
  customFieldNames,
  hideLowPriorityColumns,
}: MatterColumnOptions): DataTableColumn<MatterListRow>[] {
  const defs = columns.map((column): DataTableColumn<MatterListRow> => {
    if (!isMatterColumnId(column)) {
      const fieldSlug = column.slice(CUSTOM_FIELD_COLUMN_PREFIX.length);
      return {
        id: column,
        meta: {
          label: customFieldNames[fieldSlug] ?? fieldSlug,
          csv: (row) => nullIfDash(formatCustomFieldCell(row.customFields?.[fieldSlug])),
        },
        cell: ({ row }) => {
          const text = formatCustomFieldCell(row.original.customFields?.[fieldSlug]);
          return text === "—" ? <EmptyCell /> : text;
        },
      };
    }
    const numeric = NUMERIC_COLUMNS.has(column);
    return {
      id: column,
      // The Matter link is the keyboard path and the e2e row filter: never hideable.
      enableHiding: column !== "name",
      meta: {
        label: matterColumnLabel(column),
        sortKey: column,
        priority: hideLowPriorityColumns ? LOW_PRIORITY[column] : undefined,
        align: numeric ? "end" : undefined,
        cellClassName: numeric ? "font-mono tabular-nums" : undefined,
        csv: (row) => matterCsv(column, row),
      },
      cell: ({ row }) => matterCell(slug, column, row.original),
    };
  });

  return defineColumns<MatterListRow>([
    ...defs,
    {
      id: "actions",
      enableHiding: false,
      meta: { label: "Actions", align: "end", headerClassName: "w-10", cellClassName: "w-10" },
      header: () => <span className="sr-only">Actions</span>,
      cell: ({ row }) => (
        <MatterRowActions slug={slug} matterId={row.original.id} clientId={row.original.clientId} />
      ),
    },
  ]);
}
