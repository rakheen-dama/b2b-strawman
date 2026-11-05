"use client";

import { useRef, useState } from "react";
import { ArrowUpDown, MoreHorizontal } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { formatCurrency } from "@/lib/format";
import type {
  DisbursementResponse,
  DisbursementApprovalStatus,
  DisbursementBillingStatus,
  DisbursementCategory,
} from "@/lib/api/legal-disbursements";

interface DisbursementListViewProps {
  disbursements: DisbursementResponse[];
  projectNames?: Record<string, string>;
  onSelect?: (disbursement: DisbursementResponse) => void;
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

const HEADER_TEXT =
  "text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400";
const SORT_BUTTON =
  "h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400";

function canEditStatus(status: DisbursementApprovalStatus): boolean {
  return status === "DRAFT" || status === "PENDING_APPROVAL";
}

export function DisbursementListView({
  disbursements,
  projectNames,
  onSelect,
  onEdit,
  onUploadReceipt,
  onWriteOff,
  onRestore,
}: DisbursementListViewProps) {
  const [sortField, setSortField] = useState<SortField>("incurredDate");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  // A row-menu action that opens a dialog runs only once the menu has fully closed (its
  // onCloseAutoFocus): opening the dialog while the menu is still exiting leaves Radix's
  // body `pointer-events: none` behind on a quick Escape, and the dialog would record the
  // vanishing menu item — not the "…" trigger — as its focus-return target.
  const afterMenuClose = useRef<(() => void) | null>(null);

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  }

  const sorted = [...disbursements].sort((a, b) => {
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
  });

  if (disbursements.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
        <p className="text-sm text-slate-500 dark:text-slate-400">No disbursements found.</p>
      </div>
    );
  }

  return (
    // relative: the row Actions trigger's sr-only label is position:absolute — without a positioned
    // ancestor inside this scroll box it escapes the clip and widens the document (G88-M4).
    // `@container`: the column-priority queries measure this box, and inline-size containment keeps
    // the table's width from feeding back into the page layout. overflow-x-auto stays as the safety
    // net for content wider than the measured thresholds allow for.
    <div className="@container relative overflow-x-auto" data-testid="disbursement-list">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className={cn(CELL, "text-left")}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("incurredDate")}
                className={SORT_BUTTON}
              >
                Date <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className={cn(CELL, "text-left", HEADER_TEXT, PRIORITY.matter.col)}>Matter</th>
            <th className={cn(CELL, "text-left", HEADER_TEXT, PRIORITY.category.col)}>Category</th>
            <th className={cn(CELL, "text-left")}>
              <div className="flex flex-wrap items-center gap-x-1.5">
                <span className={HEADER_TEXT}>Description</span>
                {/* Supplier sort while the Supplier column is hidden: the supplier is then on the
                    Description's second line, so its sort lives in this header. */}
                <span
                  className={cn("flex items-center gap-x-1.5", PRIORITY.supplier.sub)}
                  data-testid="disbursement-description-supplier-sort"
                >
                  <span aria-hidden="true" className={HEADER_TEXT}>
                    &middot;
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => toggleSort("supplierName")}
                    className={SORT_BUTTON}
                    aria-label="Sort by supplier"
                  >
                    Supplier <ArrowUpDown className="ml-1 size-3" />
                  </Button>
                </span>
              </div>
            </th>
            <th className={cn(CELL, "text-left", PRIORITY.supplier.col)}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("supplierName")}
                className={SORT_BUTTON}
              >
                Supplier <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className={cn(CELL, "text-right")}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("amount")}
                className={cn(SORT_BUTTON, COMPACT.amountHeader)}
              >
                Amount (incl VAT) <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className={cn(CELL, "text-left")}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("approvalStatus")}
                className={SORT_BUTTON}
              >
                Approval <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className={cn(CELL, "text-left", HEADER_TEXT, COMPACT.billingCol)}>Billing</th>
            <th className={cn(CELL, "text-right", HEADER_TEXT)}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((d) => {
            const inclVat = d.amount + d.vatAmount;
            const matterName = projectNames?.[d.matterId];
            const editable = canEditStatus(d.approvalStatus);
            const edit = editable ? onEdit : undefined;
            const uploadReceipt = editable ? onUploadReceipt : undefined;
            const writeOff = d.billingStatus === "UNBILLED" ? onWriteOff : undefined;
            const restore = d.billingStatus === "WRITTEN_OFF" ? onRestore : undefined;
            const hasActions = !!edit || !!uploadReceipt || !!writeOff || !!restore;

            return (
              <tr
                key={d.id}
                className={cn(
                  "group border-b border-slate-100 transition-colors last:border-0 dark:border-slate-800/50",
                  onSelect && "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                )}
                onClick={() => onSelect?.(d)}
                data-testid={`disbursement-row-${d.id}`}
              >
                <td
                  className={cn(
                    CELL,
                    "font-mono text-sm whitespace-nowrap text-slate-600 dark:text-slate-400"
                  )}
                >
                  {d.incurredDate}
                </td>
                <td
                  className={cn(
                    CELL,
                    "text-sm text-slate-700 dark:text-slate-300",
                    PRIORITY.matter.col
                  )}
                >
                  {matterName ?? "\u2014"}
                </td>
                <td className={cn(CELL, PRIORITY.category.col)}>
                  <Badge variant="outline">{categoryLabel(d.category)}</Badge>
                </td>
                <td
                  className={cn(
                    CELL,
                    COMPACT.description,
                    "text-sm text-slate-700 dark:text-slate-300"
                  )}
                >
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
                      "mt-0.5 flex flex-wrap gap-x-1.5 text-xs text-slate-500 dark:text-slate-400",
                      PRIORITY.supplier.sub
                    )}
                  >
                    {matterName && (
                      <span
                        className={cn("flex max-w-full min-w-0 gap-x-1.5", PRIORITY.matter.sub)}
                      >
                        <span className="truncate" title={matterName}>
                          {matterName}
                        </span>
                        <span aria-hidden="true">&middot;</span>
                      </span>
                    )}
                    <span
                      className={cn("flex max-w-full min-w-0 gap-x-1.5", PRIORITY.category.sub)}
                    >
                      <span className="truncate">{categoryLabel(d.category)}</span>
                      <span aria-hidden="true">&middot;</span>
                    </span>
                    <span
                      className={cn("max-w-full truncate", PRIORITY.supplier.sub)}
                      title={d.supplierName}
                    >
                      {d.supplierName}
                    </span>
                  </div>
                </td>
                <td
                  className={cn(
                    CELL,
                    "text-sm text-slate-700 dark:text-slate-300",
                    PRIORITY.supplier.col
                  )}
                >
                  {d.supplierName}
                </td>
                <td
                  className={cn(
                    CELL,
                    "text-right font-mono text-sm whitespace-nowrap text-slate-950 tabular-nums dark:text-slate-50"
                  )}
                >
                  {formatCurrency(inclVat, "ZAR")}
                </td>
                <td className={CELL}>
                  {approvalStatusBadge(d.approvalStatus)}
                  <div className={cn("mt-1", COMPACT.billingStacked)}>
                    {billingStatusBadge(d.billingStatus)}
                  </div>
                </td>
                <td className={cn(CELL, COMPACT.billingCol)}>
                  {billingStatusBadge(d.billingStatus)}
                </td>
                <td className={cn(CELL, "text-right")}>
                  {hasActions && (
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
                          const run = afterMenuClose.current;
                          afterMenuClose.current = null;
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
                              afterMenuClose.current = () => writeOff(d);
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
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
