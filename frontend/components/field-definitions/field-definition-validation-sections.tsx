"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

// --- Text Validation ---

interface TextValidationProps {
  minLength: string;
  onMinLengthChange: (value: string) => void;
  maxLength: string;
  onMaxLengthChange: (value: string) => void;
  pattern: string;
  onPatternChange: (value: string) => void;
}

export function TextValidationSection({
  minLength,
  onMinLengthChange,
  maxLength,
  onMaxLengthChange,
  pattern,
  onPatternChange,
}: TextValidationProps) {
  return (
    <div className="space-y-3 rounded-md border border-slate-200 p-3 dark:border-slate-700">
      <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Text Validation</p>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          <Label htmlFor="fd-min-length">Min Length</Label>
          <Input
            id="fd-min-length"
            type="number"
            min={0}
            value={minLength}
            onChange={(e) => onMinLengthChange(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="fd-max-length">Max Length</Label>
          <Input
            id="fd-max-length"
            type="number"
            min={0}
            value={maxLength}
            onChange={(e) => onMaxLengthChange(e.target.value)}
          />
        </div>
      </div>
      <div className="space-y-1">
        <Label htmlFor="fd-pattern">Pattern (regex)</Label>
        <Input
          id="fd-pattern"
          value={pattern}
          onChange={(e) => onPatternChange(e.target.value)}
          placeholder="e.g. ^[A-Z].*"
        />
      </div>
    </div>
  );
}

// --- Number Validation ---

interface NumberValidationProps {
  min: string;
  onMinChange: (value: string) => void;
  max: string;
  onMaxChange: (value: string) => void;
}

export function NumberValidationSection({
  min,
  onMinChange,
  max,
  onMaxChange,
}: NumberValidationProps) {
  return (
    <div className="space-y-3 rounded-md border border-slate-200 p-3 dark:border-slate-700">
      <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Number Validation</p>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          <Label htmlFor="fd-min-number">Min</Label>
          <Input
            id="fd-min-number"
            type="number"
            value={min}
            onChange={(e) => onMinChange(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="fd-max-number">Max</Label>
          <Input
            id="fd-max-number"
            type="number"
            value={max}
            onChange={(e) => onMaxChange(e.target.value)}
          />
        </div>
      </div>
    </div>
  );
}

// --- Date Validation ---

interface DateValidationProps {
  minDate: string;
  onMinDateChange: (value: string) => void;
  maxDate: string;
  onMaxDateChange: (value: string) => void;
}

export function DateValidationSection({
  minDate,
  onMinDateChange,
  maxDate,
  onMaxDateChange,
}: DateValidationProps) {
  return (
    <div className="space-y-3 rounded-md border border-slate-200 p-3 dark:border-slate-700">
      <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Date Validation</p>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          <Label htmlFor="fd-min-date">Min Date</Label>
          <Input
            id="fd-min-date"
            type="date"
            value={minDate}
            onChange={(e) => onMinDateChange(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="fd-max-date">Max Date</Label>
          <Input
            id="fd-max-date"
            type="date"
            value={maxDate}
            onChange={(e) => onMaxDateChange(e.target.value)}
          />
        </div>
      </div>
    </div>
  );
}
