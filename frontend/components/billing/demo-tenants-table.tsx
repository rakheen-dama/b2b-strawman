"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Users } from "lucide-react";
import { toast } from "sonner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
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

export function DemoTenantsTable({ tenants }: DemoTenantsTableProps) {
  const router = useRouter();
  const [reseedingId, setReseedingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminTenantBilling | null>(
    null,
  );

  const demoTenants = tenants.filter(
    (t) => t.billingMethod === "PILOT" || t.billingMethod === "COMPLIMENTARY",
  );

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

  if (demoTenants.length === 0) {
    return (
      <EmptyState
        icon={Users}
        title="No demo tenants"
        description="No demo tenants have been provisioned yet."
      />
    );
  }

  return (
    <div data-testid="demo-tenants-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Org Name</TableHead>
            <TableHead>Profile</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Billing Method</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Members</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {demoTenants.map((tenant) => (
            <TableRow
              key={tenant.organizationId}
              data-testid={`demo-tenant-row-${tenant.organizationName}`}
            >
              <TableCell className="font-medium">
                {tenant.organizationName}
              </TableCell>
              <TableCell>
                <MethodBadge method={tenant.verticalProfile} />
              </TableCell>
              <TableCell>
                <StatusBadge status={tenant.subscriptionStatus} />
              </TableCell>
              <TableCell>
                <MethodBadge method={tenant.billingMethod} />
              </TableCell>
              <TableCell>{formatDate(tenant.createdAt)}</TableCell>
              <TableCell className="text-right">
                {tenant.memberCount}
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={reseedingId === tenant.organizationId}
                    onClick={() => handleReseed(tenant.organizationId)}
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
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => setDeleteTarget(tenant)}
                  >
                    Delete Tenant
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

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
    </div>
  );
}
