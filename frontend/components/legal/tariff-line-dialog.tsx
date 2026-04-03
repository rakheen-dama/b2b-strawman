"use client";

import { useState, useEffect, useTransition, useCallback } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Trash2 } from "lucide-react";
import { toast } from "sonner";
import { TariffItemBrowser } from "@/components/legal/tariff-item-browser";
import {
  fetchTariffSchedules,
  fetchActiveSchedule,
} from "@/app/(app)/org/[slug]/legal/tariffs/actions";
import { addLineItem } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import type { TariffSchedule, TariffItem } from "@/lib/types";

interface SelectedItem {
  item: TariffItem;
  quantity: number;
}

function formatZAR(rateInCents: number): string {
  return `R ${(rateInCents / 100).toFixed(2)}`;
}

interface TariffLineDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  invoiceId: string;
  slug: string;
  customerId: string;
  onSuccess: () => void;
}

export function TariffLineDialog({
  open,
  onOpenChange,
  invoiceId,
  slug,
  customerId,
  onSuccess,
}: TariffLineDialogProps) {
  const [schedules, setSchedules] = useState<TariffSchedule[]>([]);
  const [selectedScheduleId, setSelectedScheduleId] = useState<string | null>(null);
  const [selectedItems, setSelectedItems] = useState<Map<string, SelectedItem>>(new Map());
  const [isLoading, startTransition] = useTransition();
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Load schedules when dialog opens
  useEffect(() => {
    if (!open) return;
    startTransition(async () => {
      try {
        // Try to get active schedule first
        const active = await fetchActiveSchedule();
        if (active) {
          setSelectedScheduleId(active.id);
        }
        // Also load all schedules for the picker
        const result = await fetchTariffSchedules();
        setSchedules(result ?? []);
        // If no active, auto-select first
        if (!active && result.length > 0) {
          setSelectedScheduleId(result[0].id);
        }
      } catch (err) {
        console.error("Failed to load tariff schedules:", err);
      }
    });
  }, [open]);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedItems(new Map());
    }
  }, [open]);

  const handleSelectItem = useCallback((item: TariffItem) => {
    setSelectedItems((prev) => {
      const next = new Map(prev);
      if (next.has(item.id)) {
        next.delete(item.id);
      } else {
        next.set(item.id, { item, quantity: 1 });
      }
      return next;
    });
  }, []);

  function handleQuantityChange(itemId: string, quantity: number) {
    setSelectedItems((prev) => {
      const next = new Map(prev);
      const entry = next.get(itemId);
      if (entry) {
        next.set(itemId, { ...entry, quantity: Math.max(1, quantity) });
      }
      return next;
    });
  }

  function handleRemoveItem(itemId: string) {
    setSelectedItems((prev) => {
      const next = new Map(prev);
      next.delete(itemId);
      return next;
    });
  }

  const totalAmount = Array.from(selectedItems.values()).reduce(
    (sum, { item, quantity }) => sum + item.rateInCents * quantity,
    0,
  );

  async function handleSubmit() {
    if (selectedItems.size === 0) return;
    setIsSubmitting(true);

    try {
      const entries = Array.from(selectedItems.values());
      for (const { item, quantity } of entries) {
        const result = await addLineItem(slug, invoiceId, customerId, {
          description: `${item.itemNumber} - ${item.description}`,
          quantity,
          unitPrice: item.rateInCents / 100,
          lineType: "TARIFF",
          tariffItemId: item.id,
        });
        if (!result.success) {
          toast.error(result.error ?? `Failed to add line for ${item.itemNumber}`);
          setIsSubmitting(false);
          return;
        }
      }
      toast.success(`Added ${entries.length} tariff line${entries.length !== 1 ? "s" : ""} to invoice`);
      onSuccess();
    } catch {
      toast.error("An unexpected error occurred while adding tariff lines.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const selectedItemIds = new Set(selectedItems.keys());
  const selectedEntries = Array.from(selectedItems.values());

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="flex max-h-[85vh] flex-col sm:max-w-2xl"
        data-testid="tariff-line-dialog"
      >
        <DialogHeader>
          <DialogTitle>Add Tariff Items</DialogTitle>
        </DialogHeader>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-hidden">
          {/* Schedule picker */}
          {schedules.length > 1 && (
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Schedule
              </label>
              <select
                value={selectedScheduleId ?? ""}
                onChange={(e) => setSelectedScheduleId(e.target.value)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
              >
                {schedules.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.code})
                    {s.active ? " - Active" : ""}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Item browser */}
          {isLoading && (
            <p className="py-4 text-center text-sm text-slate-500">Loading schedules...</p>
          )}
          {selectedScheduleId && !isLoading && (
            <div className="min-h-0 flex-1 overflow-y-auto">
              <TariffItemBrowser
                scheduleId={selectedScheduleId}
                onSelectItem={handleSelectItem}
                selectedItemIds={selectedItemIds}
              />
            </div>
          )}

          {/* Selected items summary */}
          {selectedEntries.length > 0 && (
            <div className="shrink-0 space-y-2 border-t border-slate-200 pt-3 dark:border-slate-800">
              <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">
                Selected Items ({selectedEntries.length})
              </h4>
              <div className="max-h-32 space-y-1 overflow-y-auto">
                {selectedEntries.map(({ item, quantity }) => (
                  <div
                    key={item.id}
                    className="flex items-center gap-2 text-sm"
                    data-testid={`selected-item-${item.id}`}
                  >
                    <span className="min-w-0 flex-1 truncate text-slate-700 dark:text-slate-300">
                      {item.itemNumber} - {item.description}
                    </span>
                    <Badge variant="neutral" className="shrink-0 text-xs">
                      {formatZAR(item.rateInCents)}
                    </Badge>
                    <span className="shrink-0 text-slate-500">&times;</span>
                    <Input
                      type="number"
                      min={1}
                      value={quantity}
                      onChange={(e) =>
                        handleQuantityChange(item.id, parseInt(e.target.value, 10) || 1)
                      }
                      className="h-7 w-16 text-center text-sm"
                      data-testid={`qty-input-${item.id}`}
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveItem(item.id)}
                      className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950 dark:hover:text-red-400"
                      aria-label={`Remove ${item.itemNumber}`}
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Bottom bar */}
        <div className="flex items-center justify-between border-t border-slate-200 pt-3 dark:border-slate-800">
          <div className="text-sm text-slate-600 dark:text-slate-400">
            {selectedEntries.length > 0 ? (
              <>
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {selectedEntries.length}
                </span>{" "}
                item{selectedEntries.length !== 1 ? "s" : ""} &middot;{" "}
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {formatZAR(totalAmount)}
                </span>
              </>
            ) : (
              "Select items to add"
            )}
          </div>
          <Button
            onClick={handleSubmit}
            disabled={selectedEntries.length === 0 || isSubmitting}
            data-testid="add-to-invoice-btn"
          >
            {isSubmitting ? "Adding..." : "Add to Invoice"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
