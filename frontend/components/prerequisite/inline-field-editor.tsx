"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { FieldType } from "@/lib/types";

/** Minimal field definition shape accepted by InlineFieldEditor */
export interface InlineFieldEditorField {
  id: string;
  name: string;
  slug: string;
  fieldType: FieldType;
  description: string | null;
  required: boolean;
  options: Array<{ value: string; label: string }> | null;
}

interface InlineFieldEditorProps {
  fieldDefinition: InlineFieldEditorField;
  value: unknown;
  onChange: (value: unknown) => void;
  disabled?: boolean;
  error?: string;
}

export function InlineFieldEditor({
  fieldDefinition,
  value,
  onChange,
  disabled = false,
  error,
}: InlineFieldEditorProps) {
  const id = `inline-${fieldDefinition.slug}`;

  switch (fieldDefinition.fieldType) {
    case "TEXT":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder={fieldDefinition.description ?? undefined}
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "NUMBER":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="number"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "DATE":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="date"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "DROPDOWN":
      return (
        <div className="space-y-1">
          <select
            id={id}
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-700"
          >
            <option value="">Select...</option>
            {fieldDefinition.options?.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "BOOLEAN":
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <input
              id={id}
              type="checkbox"
              checked={(value as boolean) ?? false}
              onChange={(e) => onChange(e.target.checked)}
              disabled={disabled}
              className="size-4 rounded border-slate-300 text-slate-600 focus:ring-slate-500"
            />
            <Label htmlFor={id} className="text-sm font-normal">
              {fieldDefinition.description ?? "Enabled"}
            </Label>
          </div>
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "PHONE":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="tel"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="+1 (555) 000-0000"
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "EMAIL":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="email"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="name@example.com"
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "URL":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="url"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="https://..."
          />
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );

    case "CURRENCY": {
      const currencyObj =
        (value as { amount?: number | string; currency?: string }) ?? {};
      return (
        <div className="space-y-1">
          <div className="flex gap-2">
            <Input
              id={id}
              type="number"
              step="0.01"
              placeholder="Amount"
              value={currencyObj.amount ?? ""}
              onChange={(e) =>
                onChange({
                  ...currencyObj,
                  amount: e.target.value
                    ? parseFloat(e.target.value)
                    : "",
                })
              }
              disabled={disabled}
              aria-invalid={!!error}
              className="flex-1"
            />
            <select
              value={currencyObj.currency ?? ""}
              onChange={(e) =>
                onChange({ ...currencyObj, currency: e.target.value })
              }
              disabled={disabled}
              className="flex h-9 w-28 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-700"
              aria-label="Currency"
            >
              <option value="">Currency</option>
              {[
                "USD",
                "EUR",
                "GBP",
                "ZAR",
                "AUD",
                "CAD",
                "CHF",
                "JPY",
                "CNY",
                "INR",
              ].map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          {error && (
            <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>
      );
    }

    default:
      return null;
  }
}
