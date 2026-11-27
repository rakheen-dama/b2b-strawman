"use client";

import { createContext, use, useMemo, useRef, useState, type RefObject } from "react";
import { ArrowDown, ArrowUp, ChevronsUpDown, MoreHorizontal, Receipt } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { CellContext, HeaderContext } from "@tanstack/react-table";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { cn } from "@/lib/utils";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type {
  DisbursementResponse,
  DisbursementApprovalStatus,
  DisbursementBillingStatus,
  DisbursementCategory,
} from "@/lib/api/legal-disbursements";

interface DisbursementListViewProps {
  disbursements: DisbursementResponse[];
  projectNames?: Record<string, string>;
  /** Row click opens this href (kit `rowHref`: clicks on the row's buttons and menus are ignored). */
  rowHref?: (disbursement: DisbursementResponse) => string;
  onEdit?: (disbursement: DisbursementResponse) => void;
  onUploadReceipt?: (disbursement: DisbursementResponse) => void;
  /** Offered on UNBILLED rows (any approval state — A14). Pass only with WRITE_OFF_DISBURSEMENTS. */
  onWriteOff?: (disbursement: DisbursementResponse) => void;
  /** "Undo write-off" on WRITTEN_OFF rows. Pass only with WRITE_OFF_DISBURSEMENTS. */
  onRestore?: (disbursement: DisbursementResponse) => void;
}

type SortField = "incurredDate" | "supplierName" | "amount" | "approvalStatus";
type SortDir = "asc" | "desc";

export function approvalStatusBadge(status: DisbursementApprovalStatus) {
  switch (status) {
    case "DRAFT":
      return <Badge variant="neutral">Draft</Badge>;
    case "PENDING_APPROVAL":
      return <Badge variant="warning">Pending</Badge>;
    case "APPROVED":
      return <Badge variant="success">Approved</Badge>;
    case "REJECTED":
      return <Badge variant="destructive">Rejected</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}

export function billingStatusBadge(status: DisbursementBillingStatus) {
  switch (status) {
    case "UNBILLED":
      return <Badge variant="neutral">Unbilled</Badge>;
    case "BILLED":
      return <Badge variant="success">Billed</Badge>;
    case "WRITTEN_OFF":
      return <Badge variant="neutral">Written off</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}

export function categoryLabel(category: DisbursementCategory): string {
  return category
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

/**
 * Column priority by the table's own container width (a container query, so the sidebar, the page's
 * max width and the matter tab's extra padding are all accounted for). As the container narrows,
 * Supplier hides first, then Category, then Matter; a hidden column's value moves to a muted second
 * line under the Description, hidden again at the same breakpoint its column comes back, so nothing
 * is lost and nothing shows twice. While the Supplier column is hidden, its sort control sits in the
 * Description header (next to the second line that carries the supplier), so Supplier sorting is
 * reachable at every width. Billing and Actions are never hidden: see `COMPACT`.
 *
 * Measured containers (`[data-testid="disbursement-list"]` clientWidth on today's shell): firm list
 * 704 / 960 / 1120 px at 1024 / 1280 / 1440 viewports (≈1200 max); matter Fees › Disbursements tab
 * 656 / 912 / 1072 px (≈1152 max). Measured table min-content widths (Description at 28ch, `px-4`):
 * Date + Description + Amount + Approval + Billing + Actions 907 px (→ 58rem); + Matter 1033
 * (→ 66rem); + Category, at its widest label "Deeds Office Fees", 1193 (→ 75rem); + Supplier 1328
 * (→ 84rem). The compact layout measured 586 px. So at 1440 both mounts show Matter, with Category
 * and Supplier on the second line (and the Supplier sort in the Description header), and on today's
 * shell Supplier never gets its column. Re-measure these thresholds if a column, a cap or the shell
 * width changes.
 *
 * Each entry pairs the column's `<th>`/`<td>` class (`col`) with its second-line fragment's class
 * (`sub`); the two must name the same breakpoint. Tailwind only sees literal class strings, so keep
 * them literal.
 */
const PRIORITY = {
  matter: { col: "hidden @min-[66rem]:table-cell", sub: "@min-[66rem]:hidden" },
  category: { col: "hidden @min-[75rem]:table-cell", sub: "@min-[75rem]:hidden" },
  supplier: { col: "hidden @min-[84rem]:table-cell", sub: "@min-[84rem]:hidden" },
} as const;

/**
 * The compact layout, below 58rem (every entry must name that one breakpoint): the Billing badge
 * stacks under the Approval badge and only the separate Billing column hides, the cells tighten to
 * `px-2`, the Description caps at 20ch and the Amount header may wrap.
 */
const COMPACT = {
  billingCol: "hidden @min-[58rem]:table-cell",
  billingStacked: "@min-[58rem]:hidden",
  cell: "px-2 py-3 @min-[58rem]:px-4",
  description: "max-w-[20ch] @min-[58rem]:max-w-[28ch]",
  amountHeader: "whitespace-normal @min-[58rem]:whitespace-nowrap",
} as const;

const CELL = COMPACT.cell;
/**
 * A sortable header: the kit reserves `pr-5` for the sort icon (`SORTABLE_HEAD_CLASS`), so only
 * the left padding follows the compact breakpoint — a `px-*` here would drop that slot. The kit
 * sort `<button>` does not inherit the `<th>`'s `uppercase` (the browser stylesheet sets
 * `text-transform: none` on buttons; preflight already makes font and letter-spacing inherit), so
 * `[&_button]:uppercase` keeps these labels in the same case as the plain headers.
 */
const SORT_HEAD = "pl-2 py-3 @min-[58rem]:pl-4 [&_button]:uppercase";

/** The kit header button's look (`DataTableColumnHeader`), for the Description header's Supplier sort. */
const SORT_CONTROL =
  "hover:text-foreground focus-visible:ring-ring/50 inline-flex items-center rounded-sm tracking-wider uppercase outline-none focus-visible:ring-[3px]";

function canEditStatus(status: DisbursementApprovalStatus): boolean {
  return status === "DRAFT" || status === "PENDING_APPROVAL";
}

/**
 * The per-render inputs of the cells. The columns below are module constants and read these from
 * context: a column array rebuilt on every render (the consumers' handlers are new functions each
 * time) would give `flexRender` new cell component types and remount every cell — dropping focus
 * from the row "…" trigger that a write-off dialog returns to.
 */
interface DisbursementRowContextValue {
  projectNames?: Record<string, string>;
  onEdit?: (disbursement: DisbursementResponse) => void;
  onUploadReceipt?: (disbursement: DisbursementResponse) => void;
  onWriteOff?: (disbursement: DisbursementResponse) => void;
  onRestore?: (disbursement: DisbursementResponse) => void;
  /**
   * A row-menu action that opens a dialog runs only once the menu has fully closed (its
   * onCloseAutoFocus): opening the dialog while the menu is still exiting leaves Radix's
   * body `pointer-events: none` behind on a quick Escape, and the dialog would record the
   * vanishing menu item — not the "…" trigger — as its focus-return target.
   */
  afterMenuCloseRef: RefObject<(() => void) | null>;
}

const DisbursementRowContext = createContext<DisbursementRowContextValue | null>(null);

function useRowContext(): DisbursementRowContextValue {
  const value = use(DisbursementRowContext);
  if (!value) throw new Error("DisbursementListView cells need DisbursementRowContext");
  return value;
}

type Cell = CellContext<DisbursementResponse, unknown>;

function MatterCell({ row }: Cell) {
  const { projectNames } = useRowContext();
  return <>{projectNames?.[row.original.matterId] ?? "\u2014"}</>;
}

/** Description, plus the hidden columns' values on a muted second line. */
function DescriptionCell({ row }: Cell) {
  const { projectNames } = useRowContext();
  const d = row.original;
  const matterName = projectNames?.[d.matterId];
  return (
    <>
      <div className="truncate" title={d.description}>
        {d.description}
      </div>
      {/* The hidden columns' values: Matter, then Category, then Supplier. Each separator
          is inside the fragment before it, so it stays attached to that item (a wrap
          leaves it ending the line, e.g. "Sheriff Fees ·") and hides with it; Supplier
          is the last column to come back, so whenever a fragment shows, Supplier
          follows it. */}
      <div
        className={cn(
          "text-muted-foreground mt-0.5 flex flex-wrap gap-x-1.5 text-xs",
          PRIORITY.supplier.sub
        )}
      >
        {matterName && (
          <span className={cn("flex max-w-full min-w-0 gap-x-1.5", PRIORITY.matter.sub)}>
            <span className="truncate" title={matterName}>
              {matterName}
            </span>
            <span aria-hidden="true">&middot;</span>
          </span>
        )}
        <span className={cn("flex max-w-full min-w-0 gap-x-1.5", PRIORITY.category.sub)}>
          <span className="truncate">{categoryLabel(d.category)}</span>
          <span aria-hidden="true">&middot;</span>
        </span>
        <span className={cn("max-w-full truncate", PRIORITY.supplier.sub)} title={d.supplierName}>
          {d.supplierName}
        </span>
      </div>
    </>
  );
}

/**
 * The Description header: its label, plus the Supplier sort while the Supplier column is hidden —
 * the supplier is then on the Description's second line, so its sort lives in this header.
 */
function DescriptionHeader({ table }: HeaderContext<DisbursementResponse, unknown>) {
  const sort = table.options.meta?.dataTableSort;
  const active = sort?.key === "supplierName";
  const SortIcon = !active ? ChevronsUpDown : sort?.dir === "asc" ? ArrowUp : ArrowDown;
  return (
    <div className="flex flex-wrap items-center gap-x-1.5">
      <span>Description</span>
      <span
        className={cn("flex items-center gap-x-1.5", PRIORITY.supplier.sub)}
        data-testid="disbursement-description-supplier-sort"
      >
        <span aria-hidden="true">&middot;</span>
        <button
          type="button"
          onClick={() => sort?.onSort?.("supplierName")}
          className={cn(SORT_CONTROL, active && "text-foreground")}
          aria-label="Sort by supplier"
        >
          Supplier
          <SortIcon aria-hidden="true" className={cn("ml-0.5 size-3.5", !active && "opacity-40")} />
        </button>
      </span>
    </div>
  );
}

function ActionsCell({ row }: Cell) {
  const { onEdit, onUploadReceipt, onWriteOff, onRestore, afterMenuCloseRef } = useRowContext();
  const d = row.original;
  const editable = canEditStatus(d.approvalStatus);
  const edit = editable ? onEdit : undefined;
  const uploadReceipt = editable ? onUploadReceipt : undefined;
  const writeOff = d.billingStatus === "UNBILLED" ? onWriteOff : undefined;
  const restore = d.billingStatus === "WRITTEN_OFF" ? onRestore : undefined;
  const hasActions = !!edit || !!uploadReceipt || !!writeOff || !!restore;
  if (!hasActions) return null;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="size-8 p-0"
          onClick={(e) => e.stopPropagation()}
          data-testid={`disbursement-actions-${d.id}`}
        >
          <MoreHorizontal className="size-4" />
          <span className="sr-only">Actions</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        onCloseAutoFocus={() => {
          const run = afterMenuCloseRef.current;
          afterMenuCloseRef.current = null;
          run?.();
        }}
      >
        {edit && (
          <DropdownMenuItem
            onClick={(e) => {
              e.stopPropagation();
              edit(d);
            }}
          >
            Edit
          </DropdownMenuItem>
        )}
        {uploadReceipt && (
          <DropdownMenuItem
            onClick={(e) => {
              e.stopPropagation();
              uploadReceipt(d);
            }}
          >
            Upload receipt
          </DropdownMenuItem>
        )}
        {writeOff && (
          <DropdownMenuItem
            data-testid={`disbursement-write-off-item-${d.id}`}
            onClick={(e) => e.stopPropagation()}
            onSelect={() => {
              afterMenuCloseRef.current = () => writeOff(d);
            }}
          >
            Write off
          </DropdownMenuItem>
        )}
        {restore && (
          <DropdownMenuItem
            data-testid={`disbursement-restore-item-${d.id}`}
            onClick={(e) => {
              e.stopPropagation();
              restore(d);
            }}
          >
            Undo write-off
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Today's columns in today's order; the PRIORITY / COMPACT classes moved onto `meta`. */
const COLUMNS = defineColumns<DisbursementResponse>([
  {
    id: "incurredDate",
    meta: {
      label: "Date",
      sortKey: "incurredDate",
      headerClassName: SORT_HEAD,
      cellClassName: cn(CELL, "text-muted-foreground whitespace-nowrap"),
    },
    cell: ({ row }) => formatLocalDate(row.original.incurredDate),
  },
  {
    id: "matter",
    meta: {
      label: "Matter",
      headerClassName: cn(CELL, PRIORITY.matter.col),
      cellClassName: cn(
        CELL,
        "text-sm whitespace-normal text-slate-700 dark:text-slate-300",
        PRIORITY.matter.col
      ),
    },
    cell: MatterCell,
  },
  {
    id: "category",
    meta: {
      label: "Category",
      headerClassName: cn(CELL, PRIORITY.category.col),
      cellClassName: cn(CELL, PRIORITY.category.col),
    },
    cell: ({ row }) => <Badge variant="outline">{categoryLabel(row.original.category)}</Badge>,
  },
  {
    id: "description",
    enableHiding: false,
    meta: {
      label: "Description",
      headerClassName: CELL,
      cellClassName: cn(
        CELL,
        COMPACT.description,
        "text-sm whitespace-normal text-slate-700 dark:text-slate-300"
      ),
    },
    header: DescriptionHeader,
    cell: DescriptionCell,
  },
  {
    id: "supplierName",
    meta: {
      label: "Supplier",
      sortKey: "supplierName",
      headerClassName: cn(SORT_HEAD, PRIORITY.supplier.col),
      cellClassName: cn(
        CELL,
        "text-sm whitespace-normal text-slate-700 dark:text-slate-300",
        PRIORITY.supplier.col
      ),
    },
    cell: ({ row }) => row.original.supplierName,
  },
  {
    id: "amount",
    meta: {
      label: "Amount (incl VAT)",
      sortKey: "amount",
      align: "end",
      headerClassName: cn(SORT_HEAD, COMPACT.amountHeader),
      cellClassName: cn(CELL, "text-foreground font-mono text-sm whitespace-nowrap tabular-nums"),
    },
    cell: ({ row }) => formatCurrency(row.original.amount + row.original.vatAmount, "ZAR"),
  },
  {
    id: "approvalStatus",
    meta: {
      label: "Approval",
      sortKey: "approvalStatus",
      headerClassName: SORT_HEAD,
      cellClassName: CELL,
    },
    cell: ({ row }) => (
      <>
        {approvalStatusBadge(row.original.approvalStatus)}
        <div className={cn("mt-1", COMPACT.billingStacked)}>
          {billingStatusBadge(row.original.billingStatus)}
        </div>
      </>
    ),
  },
  {
    id: "billing",
    meta: {
      label: "Billing",
      headerClassName: cn(CELL, COMPACT.billingCol),
      cellClassName: cn(CELL, COMPACT.billingCol),
    },
    cell: ({ row }) => billingStatusBadge(row.original.billingStatus),
  },
  {
    id: "actions",
    enableHiding: false,
    meta: { label: "Actions", align: "end", headerClassName: CELL, cellClassName: CELL },
    cell: ActionsCell,
  },
]);

export function DisbursementListView({
  disbursements,
  projectNames,
  rowHref,
  onEdit,
  onUploadReceipt,
  onWriteOff,
  onRestore,
}: DisbursementListViewProps) {
  const [sortField, setSortField] = useState<SortField>("incurredDate");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const afterMenuCloseRef = useRef<(() => void) | null>(null);

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  }

  // A new array only when the rows or the sort change: `data` identity is navigation to the kit.
  const sorted = useMemo(
    () =>
      [...disbursements].sort((a, b) => {
        let cmp = 0;
        if (sortField === "incurredDate") {
          cmp = a.incurredDate.localeCompare(b.incurredDate);
        } else if (sortField === "supplierName") {
          cmp = a.supplierName.localeCompare(b.supplierName);
        } else if (sortField === "amount") {
          cmp = a.amount + a.vatAmount - (b.amount + b.vatAmount);
        } else if (sortField === "approvalStatus") {
          cmp = a.approvalStatus.localeCompare(b.approvalStatus);
        }
        return sortDir === "asc" ? cmp : -cmp;
      }),
    [disbursements, sortField, sortDir]
  );

  if (disbursements.length === 0) {
    // `inline`: the title is a <p>, never a heading (`day-05:428` matches any heading containing
    // "Disbursements" in the matter tab).
    return (
      <EmptyState
        variant="inline"
        icon={Receipt}
        title="No disbursements found."
        description={null}
      />
    );
  }

  return (
    // relative: the row Actions trigger's sr-only label is position:absolute — without a positioned
    // ancestor inside this scroll box it escapes the clip and widens the document (G88-M4).
    // `@container`: the column-priority queries measure this box (the kit frame inside is its own
    // `@container` too), and inline-size containment keeps the table's width from feeding back into
    // the page layout. overflow-x-auto stays as the safety net for content wider than the measured
    // thresholds allow for.
    <div className="@container relative overflow-x-auto" data-testid="disbursement-list">
      <DisbursementRowContext
        value={{ projectNames, onEdit, onUploadReceipt, onWriteOff, onRestore, afterMenuCloseRef }}
      >
        <DataTable
          columns={COLUMNS}
          data={sorted}
          getRowId={(d) => d.id}
          sort={{ key: sortField, dir: sortDir, onSort: toggleSort }}
          rowHref={rowHref}
          rowTestId={(d) => `disbursement-row-${d.id}`}
          columnVisibility={false}
          stickyHeader={false}
          empty={null}
        />
      </DisbursementRowContext>
    </div>
  );
}
