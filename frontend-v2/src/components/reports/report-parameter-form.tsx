"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
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
import { Loader2 } from "lucide-react";
import type { ParameterSchema } from "@/lib/api/reports";
import { EntityPicker } from "@/components/reports/entity-picker";

interface ReportParameterFormProps {
  schema: ParameterSchema;
  onSubmit: (parameters: Record<string, unknown>) => void;
  isLoading: boolean;
}

export function ReportParameterForm({
  schema,
  onSubmit,
  isLoading,
}: ReportParameterFormProps) {
  const [values, setValues] = useState<Record<string, unknown>>(() => {
    const initial: Record<string, unknown> = {};
    for (const param of schema.parameters) {
      if (param.default) {
        initial[param.name] = param.default;
      } else {
        initial[param.name] = "";
      }
    }
    return initial;
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const newErrors: Record<string, string> = {};
    for (const param of schema.parameters) {
      if (
        param.required &&
        (!values[param.name] || values[param.name] === "")
      ) {
        newErrors[param.name] = `${param.label} is required`;
      }
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});
    onSubmit(values);
  }

  function updateValue(name: string, value: unknown) {
    setValues((prev) => ({ ...prev, [name]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[name];
      return next;
    });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {schema.parameters.map((param) => (
          <div key={param.name} className="space-y-1.5">
            <Label htmlFor={`param-${param.name}`}>
              {param.label}
              {param.required && (
                <span className="ml-0.5 text-red-500">*</span>
              )}
            </Label>

            {param.type === "date" && (
              <Input
                id={`param-${param.name}`}
                type="date"
                value={String(values[param.name] ?? "")}
                onChange={(e) => updateValue(param.name, e.target.value)}
                disabled={isLoading}
                className={cn(errors[param.name] && "border-red-500")}
              />
            )}

            {param.type === "enum" && (
              <Select
                value={String(values[param.name] ?? "")}
                onValueChange={(v) => updateValue(param.name, v)}
                disabled={isLoading}
              >
                <SelectTrigger
                  id={`param-${param.name}`}
                  className={cn(errors[param.name] && "border-red-500")}
                >
                  <SelectValue
                    placeholder={`Select ${param.label.toLowerCase()}`}
                  />
                </SelectTrigger>
                <SelectContent>
                  {param.options?.map((opt) => (
                    <SelectItem key={opt} value={opt}>
                      {opt}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {param.type === "uuid" && param.entityType && (
              <EntityPicker
                id={`param-${param.name}`}
                entityType={param.entityType}
                value={String(values[param.name] ?? "")}
                onChange={(v) => updateValue(param.name, v)}
                disabled={isLoading}
                hasError={!!errors[param.name]}
              />
            )}

            {param.type === "uuid" && !param.entityType && (
              <Input
                id={`param-${param.name}`}
                type="text"
                placeholder="Entity ID (UUID)"
                value={String(values[param.name] ?? "")}
                onChange={(e) => updateValue(param.name, e.target.value)}
                disabled={isLoading}
                className={cn(errors[param.name] && "border-red-500")}
              />
            )}

            {errors[param.name] && (
              <p className="text-xs text-red-600">{errors[param.name]}</p>
            )}
          </div>
        ))}
      </div>

      <Button type="submit" disabled={isLoading}>
        {isLoading && <Loader2 className="mr-2 size-4 animate-spin" />}
        Run Report
      </Button>
    </form>
  );
}
