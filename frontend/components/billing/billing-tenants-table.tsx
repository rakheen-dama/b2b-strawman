"use client";

import { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { CreditCard } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@b2mash/ui/input";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import { BillingDetailSheet } from "@/components/billing/billing-detail-sheet";
import { formatDate } from "@/components/billing/utils";
import type { AdminTenantBilling } from "@/app/(app)/platform-admin/billing/actions";

const ALL_VALUE = "__ALL__";

const STATUS_FILTER_OPTIONS = [
  { value: ALL_VALUE, label: "All Statuses" },
  { value: "TRIALING", label: "Trialing" },
  { value: "ACTIVE", label: "Active" },
  { value: "GRACE_PERIOD", label: "Grace Period" },
  { value: "LOCKED", label: "Locked" },
  { value: "EXPIRED", label: "Expired" },
  { value: "PAST_DUE", label: "Past Due" },
  { value: "SUSPENDED", label: "Suspended" },
] as const;

const METHOD_FILTER_OPTIONS = [
  { value: ALL_VALUE, label: "All Methods" },
  { value: "DEBIT_ORDER", label: "Debit Order" },
  { value: "PILOT", label: "Pilot" },
  { value: "COMPLIMENTARY", label: "Complimentary" },
  { value: "MANUAL", label: "Manual" },
] as const;

const PROFILE_FILTER_OPTIONS = [
  { value: ALL_VALUE, label: "All Profiles" },
  { value: "GENERIC", label: "Generic" },
  { value: "ACCOUNTING", label: "Accounting" },
  { value: "LEGAL", label: "Legal" },
] as const;

interface BillingTenantsTableProps {
  tenants: AdminTenantBilling[];
}

export function BillingTenantsTable({ tenants }: BillingTenantsTableProps) {
  const [statusFilter, setStatusFilter] = useState(ALL_VALUE);
  const [methodFilter, setMethodFilter] = useState(ALL_VALUE);
  const [profileFilter, setProfileFilter] = useState(ALL_VALUE);
  const [search, setSearch] = useState("");
  const [selectedTenant, setSelectedTenant] = useState<AdminTenantBilling | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const router = useRouter();

  const filtered = useMemo(() => {
    return tenants.filter((t) => {
      if (statusFilter !== ALL_VALUE && t.subscriptionStatus !== statusFilter) return false;
      if (methodFilter !== ALL_VALUE && t.billingMethod !== methodFilter) return false;
      if (profileFilter !== ALL_VALUE && t.verticalProfile !== profileFilter) return false;
      if (search && !t.organizationName.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [tenants, statusFilter, methodFilter, profileFilter, search]);

  const sorted = useMemo(
    () =>
      [...filtered].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      ),
    [filtered]
  );

  // Dates keep `components/billing/utils` formatDate ("01 May 2026", as BillingDetailSheet): D-95.
  const columns = useMemo(
    () =>
      defineColumns<AdminTenantBilling>([
        {
          id: "organizationName",
          enableHiding: false,
          meta: { label: "Org Name", cellClassName: "font-medium" },
          cell: ({ row }) => row.original.organizationName,
        },
        {
          id: "verticalProfile",
          meta: { label: "Profile", cellClassName: "text-sm text-slate-500" },
          cell: ({ row }) => row.original.verticalProfile,
        },
        {
          id: "subscriptionStatus",
          meta: { label: "Status" },
          cell: ({ row }) => <StatusBadge status={row.original.subscriptionStatus} />,
        },
        {
          id: "billingMethod",
          meta: { label: "Billing Method" },
          cell: ({ row }) => <MethodBadge method={row.original.billingMethod} />,
        },
        {
          id: "periodEnd",
          meta: { label: "Trial / Period End", cellClassName: "text-sm" },
          cell: ({ row }) =>
            row.original.trialEndsAt
              ? formatDate(row.original.trialEndsAt, "\u2014")
              : formatDate(row.original.currentPeriodEnd, "\u2014"),
        },
        {
          id: "memberCount",
          meta: { label: "Members", align: "end" },
          cell: ({ row }) => row.original.memberCount,
        },
        {
          id: "createdAt",
          meta: { label: "Created", cellClassName: "text-sm" },
          cell: ({ row }) => formatDate(row.original.createdAt, "\u2014"),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setSelectedTenant(row.original);
                setSheetOpen(true);
              }}
            >
              Open
            </Button>
          ),
        },
      ]),
    []
  );

  function handleSuccess() {
    router.refresh();
  }

  return (
    <div data-testid="billing-tenants-page">
      {/* Filters */}
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <Input
          placeholder="Search by org name..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-64"
        />
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_FILTER_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={methodFilter} onValueChange={setMethodFilter}>
          <SelectTrigger className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {METHOD_FILTER_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={profileFilter} onValueChange={setProfileFilter}>
          <SelectTrigger className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PROFILE_FILTER_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <DataTable
        columns={columns}
        data={sorted}
        getRowId={(tenant) => tenant.organizationId}
        columnVisibility={false}
        stickyHeader={false}
        empty={
          <EmptyState
            variant="inline"
            icon={CreditCard}
            title="No tenants found"
            description="No tenants match the current filters."
          />
        }
      />

      <BillingDetailSheet
        key={selectedTenant?.organizationId ?? "none"}
        tenant={selectedTenant}
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        onSuccess={handleSuccess}
      />
    </div>
  );
}
