"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { deactivateTaxRate } from "@/app/(app)/org/[slug]/settings/tax/actions";
import type { TaxRateResponse } from "@/lib/types";

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
            Are you sure you want to deactivate &quot;{taxRate.name}&quot;? This
            rate will no longer be available for new invoice lines.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeactivating}>
            Cancel
          </AlertDialogCancel>
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

export function TaxRateTable({ slug, taxRates }: TaxRateTableProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Tax Rates
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Manage the tax rates applied to invoice line items.
          </p>
        </div>
        <AddTaxRateDialog slug={slug}>
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            Add Tax Rate
          </Button>
        </AddTaxRateDialog>
      </div>

      {taxRates.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Percent className="size-12 text-slate-300 dark:text-slate-700" />
          <h3 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No tax rates yet
          </h3>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Create your first tax rate to apply taxes to invoices.
          </p>
          <div className="mt-4">
            <AddTaxRateDialog slug={slug}>
              <Button size="sm">
                <Plus className="mr-1.5 size-4" />
                Add Tax Rate
              </Button>
            </AddTaxRateDialog>
          </div>
        </div>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Rate
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 sm:table-cell">
                  Default
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 md:table-cell">
                  Status
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {taxRates.map((taxRate) => (
                <tr
                  key={taxRate.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                        {taxRate.name}
                      </span>
                      {taxRate.isExempt && (
                        <Badge variant="neutral">Exempt</Badge>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {taxRate.rate.toFixed(2)}%
                  </td>
                  <td className="hidden px-4 py-3 sm:table-cell">
                    {taxRate.isDefault && (
                      <Badge variant="success">Default</Badge>
                    )}
                  </td>
                  <td className="hidden px-4 py-3 text-sm md:table-cell">
                    {taxRate.active ? (
                      <span className="text-green-600 dark:text-green-400">
                        Active
                      </span>
                    ) : (
                      <span className="text-slate-400 dark:text-slate-600">
                        Inactive
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <EditTaxRateDialog slug={slug} taxRate={taxRate}>
                        <Button variant="ghost" size="sm">
                          <Pencil className="size-4" />
                          <span className="sr-only">
                            Edit {taxRate.name}
                          </span>
                        </Button>
                      </EditTaxRateDialog>
                      {taxRate.active && !taxRate.isDefault && (
                        <DeactivateDialog slug={slug} taxRate={taxRate}>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                          >
                            <Ban className="size-4" />
                            <span className="sr-only">
                              Deactivate {taxRate.name}
                            </span>
                          </Button>
                        </DeactivateDialog>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
