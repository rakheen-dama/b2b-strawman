"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/components/billing/utils";
import { deleteDemoTenant } from "@/app/(app)/platform-admin/demo/actions";

interface DeleteTenantDialogProps {
  tenant: {
    organizationId: string;
    organizationName: string;
    verticalProfile: string;
    memberCount: number;
    createdAt: string;
  };
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function DeleteTenantDialog({
  tenant,
  open,
  onOpenChange,
  onSuccess,
}: DeleteTenantDialogProps) {
  const [confirmInput, setConfirmInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const isNameMatch = confirmInput === tenant.organizationName;

  async function handleDelete() {
    if (!isNameMatch) return;
    setIsLoading(true);

    try {
      const result = await deleteDemoTenant(
        tenant.organizationId,
        confirmInput,
      );

      if (result.success) {
        toast.success("Demo tenant deleted successfully.");
        setConfirmInput("");
        onOpenChange(false);
        onSuccess();
      } else {
        toast.error(result.error ?? "Failed to delete demo tenant.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isLoading) return;
    if (!newOpen) {
      setConfirmInput("");
    }
    onOpenChange(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Demo Tenant</AlertDialogTitle>
          <AlertDialogDescription>
            This will permanently delete the organization, all its data, the
            database schema, and Keycloak resources. This action cannot be
            undone.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-3 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium text-slate-700 dark:text-slate-300">
              {tenant.organizationName}
            </span>
            <Badge variant="outline">{tenant.verticalProfile}</Badge>
          </div>
          <div className="text-slate-600 dark:text-slate-400">
            <span>{tenant.memberCount} member{tenant.memberCount !== 1 ? "s" : ""}</span>
            <span className="mx-2">&middot;</span>
            <span>Created {formatDate(tenant.createdAt)}</span>
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="confirm-name">
            Type the organization name to confirm
          </Label>
          <Input
            id="confirm-name"
            value={confirmInput}
            onChange={(e) => setConfirmInput(e.target.value)}
            placeholder={tenant.organizationName}
            disabled={isLoading}
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isLoading}>Cancel</AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={!isNameMatch || isLoading}
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Deleting...
              </>
            ) : (
              "Delete Tenant"
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
