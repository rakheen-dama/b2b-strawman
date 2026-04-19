"use client";

import { useState } from "react";
import { ArrowUpDown, MoreHorizontal } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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

function canEditStatus(status: DisbursementApprovalStatus): boolean {
  return status === "DRAFT" || status === "PENDING_APPROVAL";
}

export function DisbursementListView({
  disbursements,
  projectNames,
  onSelect,
  onEdit,
  onUploadReceipt,
}: DisbursementListViewProps) {
  const [sortField, setSortField] = useState<SortField>("incurredDate");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

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
      cmp = (a.amount + a.vatAmount) - (b.amount + b.vatAmount);
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
    <div className="overflow-x-auto" data-testid="disbursement-list">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("incurredDate")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Date <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Matter
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Category
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Description
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("supplierName")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Supplier <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-right">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("amount")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Amount (incl VAT) <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("approvalStatus")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Approval <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Billing
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((d) => {
            const inclVat = d.amount + d.vatAmount;
            const editable = canEditStatus(d.approvalStatus);
            const hasActions = !!onEdit || !!onUploadReceipt;

            return (
              <tr
                key={d.id}
                className={cn(
                  "group border-b border-slate-100 transition-colors last:border-0 dark:border-slate-800/50",
                  onSelect &&
                    "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                )}
                onClick={() => onSelect?.(d)}
                data-testid={`disbursement-row-${d.id}`}
              >
                <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                  {d.incurredDate}
                </td>
                <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {projectNames?.[d.projectId] ?? "\u2014"}
                </td>
                <td className="px-4 py-3">
                  <Badge variant="outline">{categoryLabel(d.category)}</Badge>
                </td>
                <td className="max-w-sm truncate px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {d.description}
                </td>
                <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {d.supplierName}
                </td>
                <td className="px-4 py-3 text-right font-mono text-sm tabular-nums text-slate-950 dark:text-slate-50">
                  {formatCurrency(inclVat, "ZAR")}
                </td>
                <td className="px-4 py-3">{approvalStatusBadge(d.approvalStatus)}</td>
                <td className="px-4 py-3">{billingStatusBadge(d.billingStatus)}</td>
                <td className="px-4 py-3 text-right">
                  {hasActions && editable && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="size-8 p-0"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <MoreHorizontal className="size-4" />
                          <span className="sr-only">Actions</span>
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {onEdit && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onEdit(d);
                            }}
                          >
                            Edit
                          </DropdownMenuItem>
                        )}
                        {onUploadReceipt && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onUploadReceipt(d);
                            }}
                          >
                            Upload receipt
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
