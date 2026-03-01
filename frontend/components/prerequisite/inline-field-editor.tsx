"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { FieldType } from "@/lib/types";

/** Proper union type for field values instead of unknown */
export type FieldValue =
  | string
  | number
  | boolean
  | { amount?: number | string; currency?: string }
  | null
  | undefined;

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
  value: FieldValue;
  onChange: (value: FieldValue) => void;
  disabled?: boolean;
  error?: string;
}

/** Supported currency codes for CURRENCY field type */
const CURRENCY_CODES = [
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
] as const;

/** Mapping of simple input field types to their HTML input type and placeholder */
const SIMPLE_INPUT_TYPES: Partial<
  Record<FieldType, { type: string; placeholder?: string }>
> = {
  TEXT: { type: "text" },
  NUMBER: { type: "number" },
  PHONE: { type: "tel", placeholder: "+1 (555) 000-0000" },
  EMAIL: { type: "email", placeholder: "name@example.com" },
  URL: { type: "url", placeholder: "https://..." },
};

function ErrorMessage({ error }: { error?: string }) {
  if (!error) return null;
  return (
    <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
  );
}

export function InlineFieldEditor({
  fieldDefinition,
  value,
  onChange,
  disabled = false,
  error,
}: InlineFieldEditorProps) {
  const id = `inline-${fieldDefinition.slug}`;
  const { fieldType } = fieldDefinition;

  // Collapsed branch for TEXT, NUMBER, PHONE, EMAIL, URL
  const simpleConfig = SIMPLE_INPUT_TYPES[fieldType];
  if (simpleConfig) {
    return (
      <div className="space-y-1">
        <Input
          id={id}
          type={simpleConfig.type}
          value={(value as string) ?? ""}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          aria-invalid={!!error}
          placeholder={
            simpleConfig.placeholder ?? fieldDefinition.description ?? undefined
          }
        />
        <ErrorMessage error={error} />
      </div>
    );
  }

  switch (fieldType) {
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
          <ErrorMessage error={error} />
        </div>
      );

    case "DROPDOWN":
      return (
        <div className="space-y-1">
          <Select
            value={(value as string) ?? ""}
            onValueChange={(val) => onChange(val)}
            disabled={disabled}
          >
            <SelectTrigger
              id={id}
              className="w-full"
              aria-invalid={!!error}
            >
              <SelectValue placeholder="Select..." />
            </SelectTrigger>
            <SelectContent>
              {fieldDefinition.options?.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <ErrorMessage error={error} />
        </div>
      );

    case "BOOLEAN":
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Checkbox
              id={id}
              checked={(value as boolean) ?? false}
              onCheckedChange={(checked) => onChange(checked === true)}
              disabled={disabled}
            />
            <Label htmlFor={id} className="text-sm font-normal">
              {fieldDefinition.description ?? "Enabled"}
            </Label>
          </div>
          <ErrorMessage error={error} />
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
            <Select
              value={currencyObj.currency ?? ""}
              onValueChange={(val) =>
                onChange({ ...currencyObj, currency: val })
              }
              disabled={disabled}
            >
              <SelectTrigger className="w-28" aria-label="Currency">
                <SelectValue placeholder="Currency" />
              </SelectTrigger>
              <SelectContent>
                {CURRENCY_CODES.map((c) => (
                  <SelectItem key={c} value={c}>
                    {c}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <ErrorMessage error={error} />
        </div>
      );
    }

    default:
      return null;
  }
}
