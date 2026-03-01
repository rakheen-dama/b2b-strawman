"use client";

import { useState } from "react";
import { DollarSign, Clock, RefreshCw } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import type { FeeModel } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { MilestoneEditor } from "./milestone-editor";
import type { MilestoneData } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

const CURRENCIES = [
  { value: "ZAR", label: "ZAR (South African Rand)" },
  { value: "USD", label: "USD (US Dollar)" },
  { value: "EUR", label: "EUR (Euro)" },
  { value: "GBP", label: "GBP (British Pound)" },
];

const FEE_MODELS = [
  {
    value: "FIXED" as const,
    label: "Fixed Fee",
    description: "Set a fixed project price",
    icon: DollarSign,
  },
  {
    value: "HOURLY" as const,
    label: "Hourly",
    description: "Bill based on time tracked",
    icon: Clock,
  },
  {
    value: "RETAINER" as const,
    label: "Retainer",
    description: "Monthly recurring fee",
    icon: RefreshCw,
  },
];

export interface FeeData {
  feeModel: FeeModel;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  hourlyRateNote?: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
  milestones: MilestoneData[];
  showMilestones: boolean;
}

interface FeeConfigSectionProps {
  feeModel: FeeModel;
  onChange: (feeData: FeeData) => void;
  initialData?: Partial<FeeData>;
}

export function FeeConfigSection({
  feeModel,
  onChange,
  initialData,
}: FeeConfigSectionProps) {
  const [fixedFeeAmount, setFixedFeeAmount] = useState<number | undefined>(
    initialData?.fixedFeeAmount ?? undefined,
  );
  const [fixedFeeCurrency, setFixedFeeCurrency] = useState(
    initialData?.fixedFeeCurrency ?? "ZAR",
  );
  const [hourlyRateNote, setHourlyRateNote] = useState(
    initialData?.hourlyRateNote ?? "",
  );
  const [retainerAmount, setRetainerAmount] = useState<number | undefined>(
    initialData?.retainerAmount ?? undefined,
  );
  const [retainerCurrency, setRetainerCurrency] = useState(
    initialData?.retainerCurrency ?? "ZAR",
  );
  const [retainerHoursIncluded, setRetainerHoursIncluded] = useState<
    number | undefined
  >(initialData?.retainerHoursIncluded ?? undefined);
  const [milestones, setMilestones] = useState<MilestoneData[]>(
    initialData?.milestones ?? [],
  );
  const [showMilestones, setShowMilestones] = useState(
    initialData?.showMilestones ?? false,
  );

  function buildFeeData(overrides: Partial<FeeData>): FeeData {
    const base: FeeData = {
      feeModel,
      fixedFeeAmount,
      fixedFeeCurrency,
      hourlyRateNote,
      retainerAmount,
      retainerCurrency,
      retainerHoursIncluded,
      milestones,
      showMilestones,
      ...overrides,
    };
    // Sync local state with overrides
    if (overrides.fixedFeeAmount !== undefined) setFixedFeeAmount(overrides.fixedFeeAmount);
    if (overrides.fixedFeeCurrency !== undefined) setFixedFeeCurrency(overrides.fixedFeeCurrency);
    if (overrides.hourlyRateNote !== undefined) setHourlyRateNote(overrides.hourlyRateNote);
    if (overrides.retainerAmount !== undefined) setRetainerAmount(overrides.retainerAmount);
    if (overrides.retainerCurrency !== undefined) setRetainerCurrency(overrides.retainerCurrency);
    if (overrides.retainerHoursIncluded !== undefined) setRetainerHoursIncluded(overrides.retainerHoursIncluded);
    if (overrides.milestones !== undefined) setMilestones(overrides.milestones);
    if (overrides.showMilestones !== undefined) setShowMilestones(overrides.showMilestones);
    return base;
  }

  function handleModelChange(model: FeeModel) {
    // Reset local state
    setFixedFeeAmount(undefined);
    setFixedFeeCurrency("ZAR");
    setHourlyRateNote("");
    setRetainerAmount(undefined);
    setRetainerCurrency("ZAR");
    setRetainerHoursIncluded(undefined);
    setMilestones([]);
    setShowMilestones(false);

    onChange({
      feeModel: model,
      fixedFeeAmount: undefined,
      fixedFeeCurrency: "ZAR",
      hourlyRateNote: "",
      retainerAmount: undefined,
      retainerCurrency: "ZAR",
      retainerHoursIncluded: undefined,
      milestones: [],
      showMilestones: false,
    });
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        {FEE_MODELS.map((model) => {
          const Icon = model.icon;
          return (
            <button
              key={model.value}
              type="button"
              onClick={() => handleModelChange(model.value)}
              className={cn(
                "flex flex-col items-start gap-1 rounded-lg border p-4 text-left transition-colors",
                feeModel === model.value
                  ? "border-teal-600 bg-teal-50"
                  : "border-slate-200 hover:border-slate-300",
              )}
            >
              <Icon className="h-5 w-5 text-slate-600" />
              <span className="text-sm font-medium text-slate-900">
                {model.label}
              </span>
              <span className="text-xs text-slate-500">
                {model.description}
              </span>
            </button>
          );
        })}
      </div>

      {feeModel === "FIXED" && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="fixedFeeAmount">Amount</Label>
              <Input
                id="fixedFeeAmount"
                type="number"
                min={0}
                step={0.01}
                placeholder="0.00"
                value={fixedFeeAmount ?? ""}
                onChange={(e) =>
                  onChange(
                    buildFeeData({
                      fixedFeeAmount: e.target.value
                        ? Number(e.target.value)
                        : undefined,
                    }),
                  )
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="fixedFeeCurrency">Currency</Label>
              <Select
                value={fixedFeeCurrency}
                onValueChange={(value) =>
                  onChange(buildFeeData({ fixedFeeCurrency: value }))
                }
              >
                <SelectTrigger id="fixedFeeCurrency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CURRENCIES.map((c) => (
                    <SelectItem key={c.value} value={c.value}>
                      {c.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="showMilestones"
              checked={showMilestones}
              onChange={(e) =>
                onChange(
                  buildFeeData({
                    showMilestones: e.target.checked,
                    milestones: e.target.checked ? milestones : [],
                  }),
                )
              }
              className="h-4 w-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
            />
            <Label htmlFor="showMilestones" className="cursor-pointer">
              Add milestones
            </Label>
          </div>

          {showMilestones && (
            <MilestoneEditor
              milestones={milestones}
              onChange={(updated) =>
                onChange(buildFeeData({ milestones: updated }))
              }
            />
          )}
        </div>
      )}

      {feeModel === "HOURLY" && (
        <div className="space-y-2">
          <Label htmlFor="hourlyRateNote">Rate Note</Label>
          <Textarea
            id="hourlyRateNote"
            placeholder="Describe your hourly rate terms..."
            value={hourlyRateNote}
            onChange={(e) =>
              onChange(buildFeeData({ hourlyRateNote: e.target.value }))
            }
            rows={3}
          />
          <p className="text-xs text-slate-500">
            Hourly rates are configured in your rate cards. This note will appear
            on the proposal.
          </p>
        </div>
      )}

      {feeModel === "RETAINER" && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="retainerAmount">Monthly Amount</Label>
              <Input
                id="retainerAmount"
                type="number"
                min={0}
                step={0.01}
                placeholder="0.00"
                value={retainerAmount ?? ""}
                onChange={(e) =>
                  onChange(
                    buildFeeData({
                      retainerAmount: e.target.value
                        ? Number(e.target.value)
                        : undefined,
                    }),
                  )
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="retainerCurrency">Currency</Label>
              <Select
                value={retainerCurrency}
                onValueChange={(value) =>
                  onChange(buildFeeData({ retainerCurrency: value }))
                }
              >
                <SelectTrigger id="retainerCurrency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CURRENCIES.map((c) => (
                    <SelectItem key={c.value} value={c.value}>
                      {c.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="retainerHours">Included Hours</Label>
            <Input
              id="retainerHours"
              type="number"
              min={0}
              step={1}
              placeholder="0"
              value={retainerHoursIncluded ?? ""}
              onChange={(e) =>
                onChange(
                  buildFeeData({
                    retainerHoursIncluded: e.target.value
                      ? Number(e.target.value)
                      : undefined,
                  }),
                )
              }
            />
          </div>
        </div>
      )}
    </div>
  );
}
