"use client";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TaxRateResponse } from "@/lib/types";

// --- Add Line Form ---

interface AddLineFormProps {
  description: string;
  onDescriptionChange: (value: string) => void;
  quantity: string;
  onQuantityChange: (value: string) => void;
  unitPrice: string;
  onUnitPriceChange: (value: string) => void;
  taxRateId: string;
  onTaxRateIdChange: (value: string) => void;
  taxRates: TaxRateResponse[];
  isPending: boolean;
  onSubmit: () => void;
  onCancel: () => void;
}

export function AddLineForm({
  description,
  onDescriptionChange,
  quantity,
  onQuantityChange,
  unitPrice,
  onUnitPriceChange,
  taxRateId,
  onTaxRateIdChange,
  taxRates,
  isPending,
  onSubmit,
  onCancel,
}: AddLineFormProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
      <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
        Add Line Item
      </h3>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="sm:col-span-3">
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Description
          </label>
          <input
            type="text"
            value={description}
            onChange={(e) => onDescriptionChange(e.target.value)}
            placeholder="Line item description"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Quantity
          </label>
          <input
            type="number"
            value={quantity}
            onChange={(e) => onQuantityChange(e.target.value)}
            min="0.01"
            step="0.01"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Unit Price
          </label>
          <input
            type="number"
            value={unitPrice}
            onChange={(e) => onUnitPriceChange(e.target.value)}
            min="0"
            step="0.01"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        {taxRates.length > 0 && (
          <div>
            <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
              Tax Rate
            </label>
            <TaxRateSelect
              value={taxRateId}
              onValueChange={onTaxRateIdChange}
              taxRates={taxRates}
            />
          </div>
        )}
        <div className="flex items-end gap-2">
          <Button
            variant="accent"
            size="sm"
            onClick={onSubmit}
            disabled={isPending || !description.trim()}
          >
            Add
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onCancel}
            disabled={isPending}
          >
            Cancel
          </Button>
        </div>
      </div>
    </div>
  );
}

// --- Edit Line Form ---

interface EditLineFormProps {
  description: string;
  onDescriptionChange: (value: string) => void;
  quantity: string;
  onQuantityChange: (value: string) => void;
  unitPrice: string;
  onUnitPriceChange: (value: string) => void;
  taxRateId: string;
  onTaxRateIdChange: (value: string) => void;
  taxRates: TaxRateResponse[];
  isPending: boolean;
  onSubmit: () => void;
  onCancel: () => void;
}

export function EditLineForm({
  description,
  onDescriptionChange,
  quantity,
  onQuantityChange,
  unitPrice,
  onUnitPriceChange,
  taxRateId,
  onTaxRateIdChange,
  taxRates,
  isPending,
  onSubmit,
  onCancel,
}: EditLineFormProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
      <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
        Edit Line Item
      </h3>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="sm:col-span-3">
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Description
          </label>
          <input
            type="text"
            value={description}
            onChange={(e) => onDescriptionChange(e.target.value)}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Quantity
          </label>
          <input
            type="number"
            value={quantity}
            onChange={(e) => onQuantityChange(e.target.value)}
            min="0.01"
            step="0.01"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Unit Price
          </label>
          <input
            type="number"
            value={unitPrice}
            onChange={(e) => onUnitPriceChange(e.target.value)}
            min="0"
            step="0.01"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        {taxRates.length > 0 && (
          <div>
            <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
              Tax Rate
            </label>
            <TaxRateSelect
              value={taxRateId}
              onValueChange={onTaxRateIdChange}
              taxRates={taxRates}
            />
          </div>
        )}
        <div className="flex items-end gap-2">
          <Button
            variant="accent"
            size="sm"
            onClick={onSubmit}
            disabled={isPending || !description.trim()}
          >
            Save
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onCancel}
            disabled={isPending}
          >
            Cancel
          </Button>
        </div>
      </div>
    </div>
  );
}

// --- Shared Tax Rate Select ---

function TaxRateSelect({
  value,
  onValueChange,
  taxRates,
}: {
  value: string;
  onValueChange: (value: string) => void;
  taxRates: TaxRateResponse[];
}) {
  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger className="w-full">
        <SelectValue placeholder="Select tax rate" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="none">None</SelectItem>
        {taxRates
          .filter((r) => r.active)
          .map((rate) => (
            <SelectItem key={rate.id} value={rate.id}>
              {rate.name} ({rate.rate}%)
            </SelectItem>
          ))}
      </SelectContent>
    </Select>
  );
}
