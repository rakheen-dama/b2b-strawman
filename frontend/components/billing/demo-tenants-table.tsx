"use client";

import { createContext, use, useState } from "react";
import type { CellContext } from "@tanstack/react-table";
import { useRouter } from "next/navigation";
import { Loader2, Users } from "lucide-react";
import { toast } from "sonner";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import { formatDate } from "@/components/billing/utils";
import { DeleteTenantDialog } from "@/components/billing/delete-tenant-dialog";
import { reseedDemoTenant } from "@/app/(app)/platform-admin/demo/actions";
import type { AdminTenantBilling } from "@/app/(app)/platform-admin/billing/actions";

interface DemoTenantsTableProps {
  tenants: AdminTenantBilling[];
}

/**
 * The actions cell's per-render inputs. `COLUMNS` is a module constant and its actions cell reads
 * these from context (669A adverse-parties precedent): a column array rebuilt when `reseedingId`
 * changes would remount every cell.
 */
interface DemoRowContextValue {
  reseedingId: string | null;
  onReseed: (orgId: string) => void;
  onDelete: (tenant: AdminTenantBilling) => void;
}

const DemoRowContext = createContext<DemoRowContextValue | null>(null);

function DemoActionsCell({ row }: CellContext<AdminTenantBilling, unknown>) {
  const context = use(DemoRowContext);
  if (!context) throw new Error("DemoActionsCell needs DemoRowContext");
  const { reseedingId, onReseed, onDelete } = context;
  const tenant = row.original;
  return (
    <div className="flex justify-end gap-2">
      <Button
        variant="outline"
        size="sm"
        disabled={reseedingId === tenant.organizationId}
        onClick={() => onReseed(tenant.organizationId)}
      >
        {reseedingId === tenant.organizationId ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Reseeding...
          </>
        ) : (
          "Reseed Data"
        )}
      </Button>
      <Button variant="destructive" size="sm" onClick={() => onDelete(tenant)}>
        Delete Tenant
      </Button>
    </div>
  );
}

const COLUMNS = defineColumns<AdminTenantBilling>([
  {
    id: "organizationName",
    enableHiding: false,
    meta: { label: "Org Name", cellClassName: "font-medium" },
    cell: ({ row }) => row.original.organizationName,
  },
  {
    id: "verticalProfile",
    meta: { label: "Profile" },
    cell: ({ row }) => <Badge variant="outline">{row.original.verticalProfile}</Badge>,
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
    id: "createdAt",
    meta: { label: "Created" },
    // Kept verbatim ("01 May 2026", as BillingDetailSheet): D-95.
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
  {
    id: "memberCount",
    meta: { label: "Members", align: "end" },
    cell: ({ row }) => row.original.memberCount,
  },
  {
    id: "actions",
    enableHiding: false,
    meta: { label: "Actions", align: "end" },
    cell: DemoActionsCell,
  },
]);

export function DemoTenantsTable({ tenants }: DemoTenantsTableProps) {
  const router = useRouter();
  const [reseedingId, setReseedingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminTenantBilling | null>(null);

  // Tenants are already filtered to PILOT/COMPLIMENTARY by the server action
  const demoTenants = tenants;

  async function handleReseed(orgId: string) {
    setReseedingId(orgId);
    try {
      const result = await reseedDemoTenant(orgId);
      if (result.success) {
        toast.success("Demo data reseeded successfully.");
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to reseed demo data.");
      }
    } catch {
      toast.error("Failed to reseed demo data.");
    } finally {
      setReseedingId(null);
    }
  }

  function handleDeleteSuccess() {
    setDeleteTarget(null);
    router.refresh();
  }

  return (
    <>
      <DemoRowContext value={{ reseedingId, onReseed: handleReseed, onDelete: setDeleteTarget }}>
        <DataTable
          columns={COLUMNS}
          data={demoTenants}
          getRowId={(tenant) => tenant.organizationId}
          rowTestId={(tenant) => `demo-tenant-row-${tenant.organizationName}`}
          tableTestId="demo-tenants-table"
          columnVisibility={false}
          stickyHeader={false}
          empty={
            <EmptyState
              variant="compact"
              icon={Users}
              title="No demo tenants"
              description="No demo tenants have been provisioned yet."
            />
          }
        />
      </DemoRowContext>

      {deleteTarget && (
        <DeleteTenantDialog
          tenant={{
            organizationId: deleteTarget.organizationId,
            organizationName: deleteTarget.organizationName,
            verticalProfile: deleteTarget.verticalProfile,
            memberCount: deleteTarget.memberCount,
            createdAt: deleteTarget.createdAt,
          }}
          open={!!deleteTarget}
          onOpenChange={(open) => {
            if (!open) setDeleteTarget(null);
          }}
          onSuccess={handleDeleteSuccess}
        />
      )}
    </>
  );
}
