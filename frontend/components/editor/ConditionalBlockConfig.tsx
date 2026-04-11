"use client";

import { useState } from "react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const OPERATORS = [
  { value: "eq", label: "equals" },
  { value: "neq", label: "does not equal" },
  { value: "isEmpty", label: "is empty" },
  { value: "isNotEmpty", label: "has a value" },
  { value: "contains", label: "contains" },
  { value: "in", label: "is one of" },
] as const;

interface ConditionalBlockConfigProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  fieldKey: string;
  operator: string;
  value: string;
  onUpdate: (attrs: { fieldKey: string; operator: string; value: string }) => void;
}

function ConditionalBlockConfigForm({
  initialFieldKey,
  initialOperator,
  initialValue,
  onUpdate,
}: {
  initialFieldKey: string;
  initialOperator: string;
  initialValue: string;
  onUpdate: ConditionalBlockConfigProps["onUpdate"];
}) {
  const [fieldKey, setFieldKey] = useState(initialFieldKey);
  const [operator, setOperator] = useState(initialOperator || "isNotEmpty");
  const [value, setValue] = useState(initialValue);

  const showValue = operator !== "isEmpty" && operator !== "isNotEmpty";
  const operatorLabel = OPERATORS.find((op) => op.value === operator)?.label ?? operator;

  const handleApply = () => {
    onUpdate({
      fieldKey: fieldKey.trim(),
      operator,
      value: showValue ? value.trim() : "",
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="cond-field-key" className="mb-1 block text-sm">
          Field
        </Label>
        <Input
          id="cond-field-key"
          value={fieldKey}
          onChange={(e) => setFieldKey(e.target.value)}
          placeholder="e.g. customer.taxNumber"
          className="font-mono text-sm"
        />
      </div>

      <div>
        <Label htmlFor="cond-operator" className="mb-1 block text-sm">
          Operator
        </Label>
        <Select value={operator} onValueChange={setOperator}>
          <SelectTrigger id="cond-operator">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {OPERATORS.map((op) => (
              <SelectItem key={op.value} value={op.value}>
                {op.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {showValue && (
        <div>
          <Label htmlFor="cond-value" className="mb-1 block text-sm">
            Value
          </Label>
          <Input
            id="cond-value"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={operator === "in" ? "e.g. company, trust, cc" : "e.g. company"}
            className="text-sm"
          />
        </div>
      )}

      {/* Preview */}
      <div className="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
        {fieldKey.trim() ? (
          <>
            Show this content if <span className="font-mono font-medium">{fieldKey.trim()}</span>{" "}
            {operatorLabel}
            {showValue && value.trim() && (
              <>
                {" "}
                <span className="font-medium">&ldquo;{value.trim()}&rdquo;</span>
              </>
            )}
          </>
        ) : (
          "Enter a field name to configure the condition"
        )}
      </div>

      <Button variant="default" size="sm" type="button" onClick={handleApply} className="w-full">
        Apply
      </Button>
    </div>
  );
}

export function ConditionalBlockConfig({
  open,
  onOpenChange,
  fieldKey,
  operator,
  value,
  onUpdate,
}: ConditionalBlockConfigProps) {
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <span />
      </PopoverTrigger>
      <PopoverContent className="w-80" align="start">
        {open && (
          <ConditionalBlockConfigForm
            initialFieldKey={fieldKey}
            initialOperator={operator}
            initialValue={value}
            onUpdate={onUpdate}
          />
        )}
      </PopoverContent>
    </Popover>
  );
}
