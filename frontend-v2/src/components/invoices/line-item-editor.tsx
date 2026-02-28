"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Plus, Trash2, Pencil, Loader2 } from "lucide-react";

import type { InvoiceLineResponse } from "@/lib/types";
import { formatCurrency } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableFooter,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

const lineItemSchema = z.object({
  description: z.string().min(1, "Description is required"),
  quantity: z.number().min(0.01, "Quantity must be positive"),
  unitPrice: z.number().min(0, "Unit price must be non-negative"),
});

type LineItemFormValues = z.infer<typeof lineItemSchema>;

interface LineItemEditorProps {
  lines: InvoiceLineResponse[];
  currency: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  isEditable: boolean;
  taxBreakdown?: { taxRateName: string; taxAmount: number }[];
  onAddLine?: (data: LineItemFormValues) => Promise<void>;
  onUpdateLine?: (lineId: string, data: LineItemFormValues) => Promise<void>;
  onDeleteLine?: (lineId: string) => Promise<void>;
}

export function LineItemEditor({
  lines,
  currency,
  subtotal,
  taxAmount,
  total,
  isEditable,
  taxBreakdown,
  onAddLine,
  onUpdateLine,
  onDeleteLine,
}: LineItemEditorProps) {
  const [editingLine, setEditingLine] = useState<InvoiceLineResponse | null>(
    null,
  );
  const [isAddOpen, setIsAddOpen] = useState(false);

  return (
    <div className="space-y-4">
      {isEditable && onAddLine && (
        <div className="flex justify-end">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsAddOpen(true)}
          >
            <Plus className="mr-1.5 size-4" />
            Add Line
          </Button>
        </div>
      )}

      <div className="rounded-lg border border-slate-200 overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[40%]">Description</TableHead>
              <TableHead className="text-right">Qty</TableHead>
              <TableHead className="text-right">Unit Price</TableHead>
              <TableHead className="text-right">Amount</TableHead>
              {isEditable && <TableHead className="w-[80px]" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={isEditable ? 5 : 4}
                  className="h-20 text-center text-sm text-slate-500"
                >
                  No line items yet. Add one to get started.
                </TableCell>
              </TableRow>
            ) : (
              lines
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map((line) => (
                  <TableRow key={line.id}>
                    <TableCell>
                      <div>
                        <p className="text-sm font-medium text-slate-900">
                          {line.description}
                        </p>
                        {line.projectName && (
                          <p className="text-xs text-slate-500">
                            {line.projectName}
                          </p>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-mono text-sm tabular-nums">
                      {line.quantity}
                    </TableCell>
                    <TableCell className="text-right font-mono text-sm tabular-nums">
                      {formatCurrency(line.unitPrice, currency)}
                    </TableCell>
                    <TableCell className="text-right font-mono text-sm tabular-nums">
                      {formatCurrency(line.amount, currency)}
                    </TableCell>
                    {isEditable && (
                      <TableCell>
                        <div className="flex items-center gap-1">
                          {onUpdateLine && (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => setEditingLine(line)}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                          )}
                          {onDeleteLine && (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => onDeleteLine(line.id)}
                            >
                              <Trash2 className="size-3.5 text-red-500" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                ))
            )}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell
                colSpan={isEditable ? 3 : 2}
                className="text-right text-sm font-medium text-slate-600"
              >
                Subtotal
              </TableCell>
              <TableCell className="text-right font-mono text-sm font-medium tabular-nums">
                {formatCurrency(subtotal, currency)}
              </TableCell>
              {isEditable && <TableCell />}
            </TableRow>
            {taxBreakdown && taxBreakdown.length > 0 ? (
              taxBreakdown.map((tb, idx) => (
                <TableRow key={idx}>
                  <TableCell
                    colSpan={isEditable ? 3 : 2}
                    className="text-right text-sm text-slate-500"
                  >
                    {tb.taxRateName}
                  </TableCell>
                  <TableCell className="text-right font-mono text-sm tabular-nums text-slate-600">
                    {formatCurrency(tb.taxAmount, currency)}
                  </TableCell>
                  {isEditable && <TableCell />}
                </TableRow>
              ))
            ) : (
              taxAmount > 0 && (
                <TableRow>
                  <TableCell
                    colSpan={isEditable ? 3 : 2}
                    className="text-right text-sm text-slate-500"
                  >
                    Tax
                  </TableCell>
                  <TableCell className="text-right font-mono text-sm tabular-nums text-slate-600">
                    {formatCurrency(taxAmount, currency)}
                  </TableCell>
                  {isEditable && <TableCell />}
                </TableRow>
              )
            )}
            <TableRow>
              <TableCell
                colSpan={isEditable ? 3 : 2}
                className="text-right text-base font-semibold text-slate-900"
              >
                Total
              </TableCell>
              <TableCell className="text-right font-mono text-base font-semibold tabular-nums text-slate-900">
                {formatCurrency(total, currency)}
              </TableCell>
              {isEditable && <TableCell />}
            </TableRow>
          </TableFooter>
        </Table>
      </div>

      {/* Add line dialog */}
      {onAddLine && (
        <LineItemDialog
          open={isAddOpen}
          onOpenChange={setIsAddOpen}
          title="Add Line Item"
          onSubmit={async (data) => {
            await onAddLine(data);
            setIsAddOpen(false);
          }}
        />
      )}

      {/* Edit line dialog */}
      {onUpdateLine && editingLine && (
        <LineItemDialog
          open={!!editingLine}
          onOpenChange={(open) => {
            if (!open) setEditingLine(null);
          }}
          title="Edit Line Item"
          defaultValues={{
            description: editingLine.description,
            quantity: editingLine.quantity,
            unitPrice: editingLine.unitPrice,
          }}
          onSubmit={async (data) => {
            await onUpdateLine(editingLine.id, data);
            setEditingLine(null);
          }}
        />
      )}
    </div>
  );
}

// ---- Internal dialog for add/edit ----

interface LineItemDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  defaultValues?: Partial<LineItemFormValues>;
  onSubmit: (data: LineItemFormValues) => Promise<void>;
}

function LineItemDialog({
  open,
  onOpenChange,
  title,
  defaultValues,
  onSubmit,
}: LineItemDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<LineItemFormValues>({
    resolver: zodResolver(lineItemSchema),
    defaultValues: {
      description: defaultValues?.description ?? "",
      quantity: defaultValues?.quantity ?? 1,
      unitPrice: defaultValues?.unitPrice ?? 0,
    },
  });

  const handleFormSubmit = async (data: LineItemFormValues) => {
    setIsSubmitting(true);
    try {
      await onSubmit(data);
      reset();
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(handleFormSubmit)} className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="li-desc">Description</Label>
            <Input id="li-desc" {...register("description")} />
            {errors.description && (
              <p className="text-xs text-red-600">{errors.description.message}</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="li-qty">Quantity</Label>
              <Input
                id="li-qty"
                type="number"
                step="0.01"
                {...register("quantity", { valueAsNumber: true })}
              />
              {errors.quantity && (
                <p className="text-xs text-red-600">{errors.quantity.message}</p>
              )}
            </div>
            <div className="grid gap-2">
              <Label htmlFor="li-price">Unit Price</Label>
              <Input
                id="li-price"
                type="number"
                step="0.01"
                {...register("unitPrice", { valueAsNumber: true })}
              />
              {errors.unitPrice && (
                <p className="text-xs text-red-600">
                  {errors.unitPrice.message}
                </p>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              )}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
