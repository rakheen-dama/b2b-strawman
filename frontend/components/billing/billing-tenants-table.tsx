"use client";

import { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { CreditCard } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import { BillingDetailSheet } from "@/components/billing/billing-detail-sheet";
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
  { value: "PAYFAST", label: "PayFast" },
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

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function BillingTenantsTable({ tenants }: BillingTenantsTableProps) {
  const [statusFilter, setStatusFilter] = useState(ALL_VALUE);
  const [methodFilter, setMethodFilter] = useState(ALL_VALUE);
  const [profileFilter, setProfileFilter] = useState(ALL_VALUE);
  const [search, setSearch] = useState("");
  const [selectedTenant, setSelectedTenant] =
    useState<AdminTenantBilling | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const router = useRouter();

  const filtered = useMemo(() => {
    return tenants.filter((t) => {
      if (statusFilter !== ALL_VALUE && t.subscriptionStatus !== statusFilter)
        return false;
      if (methodFilter !== ALL_VALUE && t.billingMethod !== methodFilter)
        return false;
      if (profileFilter !== ALL_VALUE && t.verticalProfile !== profileFilter)
        return false;
      if (
        search &&
        !t.organizationName.toLowerCase().includes(search.toLowerCase())
      )
        return false;
      return true;
    });
  }, [tenants, statusFilter, methodFilter, profileFilter, search]);

  const sorted = useMemo(
    () =>
      [...filtered].sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      ),
    [filtered],
  );

  function handleOpenDetail(tenant: AdminTenantBilling) {
    setSelectedTenant(tenant);
    setSheetOpen(true);
  }

  function handleSuccess() {
    router.refresh();
  }

  return (
    <div data-testid="billing-tenants-page">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
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
      {sorted.length === 0 ? (
        <EmptyState
          icon={CreditCard}
          title="No tenants found"
          description="No tenants match the current filters."
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Org Name</TableHead>
              <TableHead>Profile</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Billing Method</TableHead>
              <TableHead>Trial / Period End</TableHead>
              <TableHead className="text-right">Members</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((tenant) => (
              <TableRow key={tenant.organizationId}>
                <TableCell className="font-medium">
                  {tenant.organizationName}
                </TableCell>
                <TableCell className="text-sm text-slate-500">
                  {tenant.verticalProfile}
                </TableCell>
                <TableCell>
                  <StatusBadge status={tenant.subscriptionStatus} />
                </TableCell>
                <TableCell>
                  <MethodBadge method={tenant.billingMethod} />
                </TableCell>
                <TableCell className="text-sm">
                  {tenant.trialEndsAt
                    ? formatDate(tenant.trialEndsAt)
                    : formatDate(tenant.currentPeriodEnd)}
                </TableCell>
                <TableCell className="text-right">
                  {tenant.memberCount}
                </TableCell>
                <TableCell className="text-sm">
                  {formatDate(tenant.createdAt)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleOpenDetail(tenant)}
                  >
                    Open
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <BillingDetailSheet
        tenant={selectedTenant}
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        onSuccess={handleSuccess}
      />
    </div>
  );
}
