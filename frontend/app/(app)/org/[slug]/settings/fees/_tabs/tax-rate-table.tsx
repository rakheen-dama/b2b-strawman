"use client";

import { useMemo, useState } from "react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { AddTaxRateDialog } from "@/components/settings/add-tax-rate-dialog";
import { EditTaxRateDialog } from "@/components/settings/edit-tax-rate-dialog";
import { Pencil, Plus, Ban, Percent } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { deactivateTaxRate } from "./tax-actions";
import type { TaxRateResponse } from "@/lib/types";

const getTaxRateId = (taxRate: TaxRateResponse) => taxRate.id;

// The destructive ghost icon needs the dark-variant overrides too: the kit ghost's dark text and
// hover classes would otherwise win (twMerge keeps one class per variant group; D-55 / F-665C-10).
const DESTRUCTIVE_GHOST =
  "text-destructive hover:bg-destructive/10 hover:text-destructive dark:text-destructive dark:hover:bg-destructive/10 dark:hover:text-destructive";

interface TaxRateTableProps {
  slug: string;
  taxRates: TaxRateResponse[];
}

function DeactivateDialog({
  slug,
  taxRate,
  children,
}: {
  slug: string;
  taxRate: TaxRateResponse;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [isDeactivating, setIsDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDeactivate(e: React.MouseEvent) {
    e.preventDefault();
    setIsDeactivating(true);
    setError(null);

    try {
      const result = await deactivateTaxRate(slug, taxRate.id);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to deactivate tax rate.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeactivating(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Deactivate Tax Rate</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to deactivate &quot;{taxRate.name}&quot;? This rate will no longer
            be available for new fee note lines.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeactivating}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={handleDeactivate}
            disabled={isDeactivating}
          >
            {isDeactivating ? "Deactivating..." : "Deactivate"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/** Fees › Tax rates (Phase 91 665D): DataTable client mode in the "Tax Rates" section. */
export function TaxRateTable({ slug, taxRates }: TaxRateTableProps) {
  // Stable columns: EditTaxRateDialog / DeactivateDialog own their open state, and a rebuilt `cell`
  // would remount them (closing an open dialog). The component has no state of its own.
  const columns = useMemo(
    () =>
      defineColumns<TaxRateResponse>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <div className="flex items-center gap-2">
              <span className="text-foreground text-sm font-medium" data-testid="tax-rate-name">
                {row.original.name}
              </span>
              {row.original.isExempt && <Badge variant="neutral">Exempt</Badge>}
            </div>
          ),
        },
        {
          id: "rate",
          meta: { label: "Rate", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => (
            <span data-testid="tax-rate-value">{row.original.rate.toFixed(2)}%</span>
          ),
        },
        {
          id: "default",
          meta: {
            label: "Default",
            headerClassName: "hidden sm:table-cell",
            cellClassName: "hidden sm:table-cell",
          },
          cell: ({ row }) => (
            <span data-testid="tax-rate-default">
              {row.original.isDefault && <Badge variant="success">Default</Badge>}
            </span>
          ),
        },
        {
          id: "status",
          meta: {
            label: "Status",
            headerClassName: "hidden md:table-cell",
            cellClassName: "hidden text-sm md:table-cell",
          },
          cell: ({ row }) =>
            row.original.active ? (
              <span className="text-success-foreground">Active</span>
            ) : (
              <span className="text-muted-foreground">Inactive</span>
            ),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => {
            const taxRate = row.original;
            return (
              <div className="flex items-center justify-end gap-1">
                <EditTaxRateDialog slug={slug} taxRate={taxRate}>
                  <Button variant="ghost" size="sm">
                    <Pencil className="size-4" />
                    <span className="sr-only">Edit {taxRate.name}</span>
                  </Button>
                </EditTaxRateDialog>
                {taxRate.active && !taxRate.isDefault && (
                  <DeactivateDialog slug={slug} taxRate={taxRate}>
                    <Button variant="ghost" size="sm" className={DESTRUCTIVE_GHOST}>
                      <Ban className="size-4" />
                      <span className="sr-only">Deactivate {taxRate.name}</span>
                    </Button>
                  </DeactivateDialog>
                )}
              </div>
            );
          },
        },
      ]),
    [slug]
  );

  return (
    <SettingsSection
      title="Tax Rates"
      description="Manage the tax rates applied to fee note line items."
      actions={
        <AddTaxRateDialog slug={slug}>
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            Add Tax Rate
          </Button>
        </AddTaxRateDialog>
      }
    >
      <DataTable
        columns={columns}
        data={taxRates}
        getRowId={getTaxRateId}
        rowTestId="tax-rate-row"
        columnVisibility={false}
        pageSize={null}
        stickyHeader={false}
        frame={false}
        empty={
          <EmptyState
            variant="compact"
            icon={Percent}
            title="No tax rates yet"
            description="Create your first tax rate to apply taxes to fee notes."
            action={
              <AddTaxRateDialog slug={slug}>
                <Button size="sm">
                  <Plus className="mr-1.5 size-4" />
                  Add Tax Rate
                </Button>
              </AddTaxRateDialog>
            }
          />
        }
      />
    </SettingsSection>
  );
}
