"use client";

import { useEffect, useMemo, useRef, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { ModuleGate } from "@/components/module-gate";
import {
  DisbursementListView,
} from "@/components/legal/disbursement-list-view";
import { CreateDisbursementDialog } from "@/components/legal/create-disbursement-dialog";
import { Input } from "@/components/ui/input";
import { fetchDisbursements } from "./actions";
import type {
  DisbursementApprovalStatus,
  DisbursementBillingStatus,
  DisbursementCategory,
  DisbursementResponse,
} from "@/lib/api/legal-disbursements";
import {
  APPROVAL_STATUS_OPTIONS,
  BILLING_STATUS_OPTIONS,
  DISBURSEMENT_CATEGORY_OPTIONS,
} from "@/lib/legal/disbursement-defaults";

interface DisbursementsListClientProps {
  slug: string;
  initialDisbursements: DisbursementResponse[];
  initialTotal: number;
  initialProjectNames: Record<string, string>;
}

export function DisbursementsListClient({
  slug,
  initialDisbursements,
  initialTotal,
  initialProjectNames,
}: DisbursementsListClientProps) {
  const router = useRouter();
  const [disbursements, setDisbursements] = useState(initialDisbursements);
  const [total, setTotal] = useState(initialTotal);
  const [isPending, startTransition] = useTransition();
  const projectNames = initialProjectNames;

  const [projectFilter, setProjectFilter] = useState<string>("");
  const [categoryFilter, setCategoryFilter] = useState<DisbursementCategory | "">("");
  const [approvalFilter, setApprovalFilter] = useState<DisbursementApprovalStatus | "">("");
  const [billingFilter, setBillingFilter] = useState<DisbursementBillingStatus | "">("");
  const [dateFrom, setDateFrom] = useState<string>("");
  const [dateTo, setDateTo] = useState<string>("");

  const selectClass =
    "flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800";

  const refetch = () => {
    startTransition(async () => {
      try {
        const result = await fetchDisbursements({
          projectId: projectFilter || undefined,
          category: (categoryFilter || undefined) as DisbursementCategory | undefined,
          approvalStatus: (approvalFilter || undefined) as
            | DisbursementApprovalStatus
            | undefined,
          billingStatus: (billingFilter || undefined) as DisbursementBillingStatus | undefined,
        });
        setDisbursements(result.content);
        setTotal(result.page.totalElements);
      } catch (err) {
        console.error("Failed to refetch disbursements:", err);
      }
    });
  };

  const isInitialMount = useRef(true);
  const refetchRef = useRef(refetch);
  useEffect(() => {
    refetchRef.current = refetch;
  }, [refetch]);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    refetchRef.current();
  }, [projectFilter, categoryFilter, approvalFilter, billingFilter]);

  // Client-side date range filter (backend does not support date range).
  const visibleDisbursements = useMemo(() => {
    return disbursements.filter((d) => {
      if (dateFrom && d.incurredDate < dateFrom) return false;
      if (dateTo && d.incurredDate > dateTo) return false;
      return true;
    });
  }, [disbursements, dateFrom, dateTo]);

  const projectOptions = useMemo(
    () => Object.entries(projectNames).map(([id, name]) => ({ id, name })),
    [projectNames]
  );

  return (
    <ModuleGate module="disbursements">
      <div data-testid="disbursements-list-page" className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <select
            value={projectFilter}
            onChange={(e) => setProjectFilter(e.target.value)}
            className={selectClass}
            aria-label="Matter filter"
          >
            <option value="">All Matters</option>
            {projectOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value as DisbursementCategory | "")}
            className={selectClass}
            aria-label="Category filter"
          >
            <option value="">All Categories</option>
            {DISBURSEMENT_CATEGORY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <select
            value={approvalFilter}
            onChange={(e) =>
              setApprovalFilter(e.target.value as DisbursementApprovalStatus | "")
            }
            className={selectClass}
            aria-label="Approval status filter"
          >
            <option value="">All Approval Statuses</option>
            {APPROVAL_STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <select
            value={billingFilter}
            onChange={(e) =>
              setBillingFilter(e.target.value as DisbursementBillingStatus | "")
            }
            className={selectClass}
            aria-label="Billing status filter"
          >
            <option value="">All Billing Statuses</option>
            {BILLING_STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <Input
            type="date"
            value={dateFrom}
            onChange={(e) => setDateFrom(e.target.value)}
            className="h-9 sm:max-w-[10rem]"
            aria-label="From date"
          />
          <Input
            type="date"
            value={dateTo}
            onChange={(e) => setDateTo(e.target.value)}
            className="h-9 sm:max-w-[10rem]"
            aria-label="To date"
          />
          <div className="ml-auto">
            <CreateDisbursementDialog slug={slug} onSuccess={refetch} />
          </div>
        </div>

        <div className="text-sm text-slate-500 dark:text-slate-400">
          {total} disbursement{total !== 1 ? "s" : ""}
          {isPending && <span className="ml-2 italic">updating...</span>}
        </div>

        <DisbursementListView
          disbursements={visibleDisbursements}
          projectNames={projectNames}
          onSelect={(d) => router.push(`/org/${slug}/legal/disbursements/${d.id}`)}
        />
      </div>
    </ModuleGate>
  );
}
