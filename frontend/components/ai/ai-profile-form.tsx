"use client";

import { useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  FormDescription,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@b2mash/ui/label";
import { Loader2, X } from "lucide-react";
import { aiProfileSchema, type AiProfileFormData } from "@/lib/schemas/ai-profile";
import { updateAiProfileAction } from "@/app/(app)/org/[slug]/settings/ai/actions";

const ZA_PROVINCES = [
  { value: "ZA-GP", label: "Gauteng" },
  { value: "ZA-WC", label: "Western Cape" },
  { value: "ZA-KZN", label: "KwaZulu-Natal" },
  { value: "ZA-EC", label: "Eastern Cape" },
  { value: "ZA-FS", label: "Free State" },
  { value: "ZA-LP", label: "Limpopo" },
  { value: "ZA-MP", label: "Mpumalanga" },
  { value: "ZA-NW", label: "North West" },
  { value: "ZA-NC", label: "Northern Cape" },
] as const;

const RISK_CALIBRATIONS = [
  {
    value: "CONSERVATIVE",
    label: "Conservative",
    description: "Strict interpretation, minimal risk tolerance. Best for high-stakes matters.",
  },
  {
    value: "MODERATE",
    label: "Moderate",
    description: "Balanced approach with reasonable risk tolerance. Suitable for most practices.",
  },
  {
    value: "AGGRESSIVE",
    label: "Aggressive",
    description: "Pragmatic interpretation, higher risk tolerance. Best for commercial practices.",
  },
] as const;

const MODEL_OPTIONS = [
  {
    value: "claude-sonnet-4-6",
    label: "Claude Sonnet 4.6",
    description: "Fast, cost-effective. Best for routine tasks.",
    costIndicator: "~R0.50/invocation",
  },
  {
    value: "claude-opus-4-6",
    label: "Claude Opus 4.6",
    description: "Most capable. Best for complex analysis.",
    costIndicator: "~R2.50/invocation",
  },
] as const;

const COMMON_PRACTICE_AREAS = [
  "Commercial Law",
  "Labour Law",
  "Property Law",
  "Family Law",
  "Criminal Law",
  "Tax Law",
  "Intellectual Property",
  "Litigation",
  "Immigration",
  "Estate Planning",
];

interface AiProfileFormProps {
  slug: string;
  initialData: {
    practiceAreas: string[];
    jurisdiction: string;
    riskCalibration: string;
    houseStyleNotes: string | null;
    ficaRequirements: Record<string, unknown> | null;
    feeEstimationNotes: string | null;
    preferredModel: string;
    monthlyBudgetCents: number | null;
    coldStartCompleted: boolean;
  };
}

export function AiProfileForm({ slug, initialData }: AiProfileFormProps) {
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);
  const [practiceAreaInput, setPracticeAreaInput] = useState("");

  const form = useForm<AiProfileFormData>({
    resolver: zodResolver(aiProfileSchema) as Resolver<AiProfileFormData>,
    defaultValues: {
      practiceAreas: initialData.practiceAreas,
      jurisdiction: initialData.jurisdiction,
      riskCalibration: initialData.riskCalibration as AiProfileFormData["riskCalibration"],
      houseStyleNotes: initialData.houseStyleNotes ?? "",
      ficaRequirements: (initialData.ficaRequirements as AiProfileFormData["ficaRequirements"]) ?? {
        enhancedDueDiligence: false,
        pepScreening: false,
        sourceOfFundsRequired: false,
      },
      feeEstimationNotes: initialData.feeEstimationNotes ?? "",
      preferredModel: initialData.preferredModel as AiProfileFormData["preferredModel"],
      monthlyBudgetCents: initialData.monthlyBudgetCents ?? undefined,
      coldStartCompleted: initialData.coldStartCompleted,
    },
  });

  async function onSubmit(data: AiProfileFormData) {
    setSaving(true);
    setMessage(null);

    try {
      const submitData = {
        ...data,
        houseStyleNotes: data.houseStyleNotes || null,
        feeEstimationNotes: data.feeEstimationNotes || null,
        coldStartCompleted: true,
      };

      const result = await updateAiProfileAction(slug, submitData);
      if (result.success) {
        setMessage("Configuration saved successfully.");
        setIsError(false);
        setTimeout(() => setMessage(null), 3000);
      } else {
        setMessage(result.error ?? "Failed to save configuration.");
        setIsError(true);
      }
    } catch {
      setMessage("An unexpected error occurred. Please try again.");
      setIsError(true);
    } finally {
      setSaving(false);
    }
  }

  function addPracticeArea(area: string) {
    const trimmed = area.trim();
    if (!trimmed) return;
    const current = form.getValues("practiceAreas");
    if (!current.includes(trimmed)) {
      form.setValue("practiceAreas", [...current, trimmed], { shouldValidate: true });
    }
    setPracticeAreaInput("");
  }

  function removePracticeArea(area: string) {
    const current = form.getValues("practiceAreas");
    form.setValue(
      "practiceAreas",
      current.filter((a) => a !== area),
      { shouldValidate: true }
    );
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {/* Practice Areas */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Practice Areas
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Define your firm&apos;s primary practice areas for tailored AI responses.
          </p>

          <FormField
            control={form.control}
            name="practiceAreas"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormControl>
                  <div className="space-y-3">
                    <div className="flex flex-wrap gap-2">
                      {field.value.map((area) => (
                        <span
                          key={area}
                          className="inline-flex items-center gap-1 rounded-md bg-teal-50 px-2.5 py-1 text-sm font-medium text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                        >
                          {area}
                          <button
                            type="button"
                            onClick={() => removePracticeArea(area)}
                            aria-label={`Remove ${area}`}
                            className="ml-0.5 rounded-sm hover:bg-teal-100 dark:hover:bg-teal-900"
                          >
                            <X className="size-3.5" />
                          </button>
                        </span>
                      ))}
                    </div>
                    <div className="flex gap-2">
                      <Input
                        value={practiceAreaInput}
                        onChange={(e) => setPracticeAreaInput(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            e.preventDefault();
                            addPracticeArea(practiceAreaInput);
                          }
                        }}
                        placeholder="Type a practice area and press Enter"
                        className="flex-1"
                      />
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => addPracticeArea(practiceAreaInput)}
                        disabled={!practiceAreaInput.trim()}
                      >
                        Add
                      </Button>
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {COMMON_PRACTICE_AREAS.filter((area) => !field.value.includes(area)).map(
                        (area) => (
                          <button
                            key={area}
                            type="button"
                            onClick={() => addPracticeArea(area)}
                            className="rounded-md border border-slate-200 px-2 py-0.5 text-xs text-slate-600 hover:border-teal-300 hover:text-teal-700 dark:border-slate-700 dark:text-slate-400 dark:hover:border-teal-700 dark:hover:text-teal-300"
                          >
                            + {area}
                          </button>
                        )
                      )}
                    </div>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Jurisdiction */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Jurisdiction</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Select the primary jurisdiction for regulatory and compliance context.
          </p>

          <FormField
            control={form.control}
            name="jurisdiction"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormLabel>Province</FormLabel>
                <Select onValueChange={field.onChange} defaultValue={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a province" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ZA_PROVINCES.map((province) => (
                      <SelectItem key={province.value} value={province.value}>
                        {province.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Risk Calibration */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Risk Calibration
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Set how conservatively the AI interprets legal requirements and risk.
          </p>

          <FormField
            control={form.control}
            name="riskCalibration"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormControl>
                  <RadioGroup
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                    className="space-y-3"
                  >
                    {RISK_CALIBRATIONS.map((option) => (
                      <div key={option.value} className="flex items-start gap-3">
                        <RadioGroupItem value={option.value} id={`risk-${option.value}`} />
                        <div className="grid gap-0.5">
                          <Label htmlFor={`risk-${option.value}`} className="font-medium">
                            {option.label}
                          </Label>
                          <p className="text-sm text-slate-600 dark:text-slate-400">
                            {option.description}
                          </p>
                        </div>
                      </div>
                    ))}
                  </RadioGroup>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* House Style Notes */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            House Style Notes
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Firm-specific writing and drafting guidelines for AI-generated content.
          </p>

          <FormField
            control={form.control}
            name="houseStyleNotes"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormControl>
                  <Textarea
                    {...field}
                    placeholder="e.g., Use formal tone, avoid contractions, refer to clients as 'the Applicant' in immigration matters..."
                    className="min-h-[100px]"
                  />
                </FormControl>
                <FormDescription>
                  Optional. Max 2000 characters. These notes guide the AI&apos;s writing style.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* FICA Requirements */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            FICA Requirements
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Configure Know Your Client (KYC) and anti-money laundering preferences.
          </p>

          <div className="mt-4 space-y-3">
            <FormField
              control={form.control}
              name="ficaRequirements.enhancedDueDiligence"
              render={({ field }) => (
                <FormItem className="flex items-center gap-3">
                  <FormControl>
                    <Checkbox checked={field.value ?? false} onCheckedChange={field.onChange} />
                  </FormControl>
                  <div className="space-y-0.5">
                    <FormLabel className="font-normal">Enhanced Due Diligence</FormLabel>
                    <FormDescription>
                      Require enhanced due diligence for high-risk clients.
                    </FormDescription>
                  </div>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="ficaRequirements.pepScreening"
              render={({ field }) => (
                <FormItem className="flex items-center gap-3">
                  <FormControl>
                    <Checkbox checked={field.value ?? false} onCheckedChange={field.onChange} />
                  </FormControl>
                  <div className="space-y-0.5">
                    <FormLabel className="font-normal">PEP Screening</FormLabel>
                    <FormDescription>
                      Screen for Politically Exposed Persons as part of client intake.
                    </FormDescription>
                  </div>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="ficaRequirements.sourceOfFundsRequired"
              render={({ field }) => (
                <FormItem className="flex items-center gap-3">
                  <FormControl>
                    <Checkbox checked={field.value ?? false} onCheckedChange={field.onChange} />
                  </FormControl>
                  <div className="space-y-0.5">
                    <FormLabel className="font-normal">Source of Funds</FormLabel>
                    <FormDescription>
                      Require source of funds declaration for all transactions.
                    </FormDescription>
                  </div>
                </FormItem>
              )}
            />
          </div>
        </div>

        {/* Fee Estimation Notes */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Fee Estimation Notes
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Guidance for AI when generating fee estimates and cost projections.
          </p>

          <FormField
            control={form.control}
            name="feeEstimationNotes"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormControl>
                  <Textarea
                    {...field}
                    placeholder="e.g., Standard consultation rate is R2,500/hour. Property transfers include conveyancing fees per tariff..."
                    className="min-h-[100px]"
                  />
                </FormControl>
                <FormDescription>
                  Optional. Max 2000 characters. Helps the AI produce accurate fee estimates.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Model Preference */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Model Preference
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Choose the default AI model for your firm&apos;s invocations.
          </p>

          <FormField
            control={form.control}
            name="preferredModel"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormControl>
                  <RadioGroup
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                    className="space-y-3"
                  >
                    {MODEL_OPTIONS.map((option) => (
                      <div key={option.value} className="flex items-start gap-3">
                        <RadioGroupItem value={option.value} id={`model-${option.value}`} />
                        <div className="grid gap-0.5">
                          <Label htmlFor={`model-${option.value}`} className="font-medium">
                            {option.label}
                            <span className="ml-2 text-xs font-normal text-slate-500 dark:text-slate-400">
                              {option.costIndicator}
                            </span>
                          </Label>
                          <p className="text-sm text-slate-600 dark:text-slate-400">
                            {option.description}
                          </p>
                        </div>
                      </div>
                    ))}
                  </RadioGroup>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Monthly Budget */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Monthly Budget
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Set an optional monthly spending limit for AI invocations. Leave empty for unlimited.
          </p>

          <FormField
            control={form.control}
            name="monthlyBudgetCents"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormLabel>Budget (ZAR)</FormLabel>
                <FormControl>
                  <div className="relative">
                    <span className="absolute top-1/2 left-3 -translate-y-1/2 text-sm text-slate-500">
                      R
                    </span>
                    <Input
                      type="number"
                      min={0}
                      step={100}
                      placeholder="e.g., 5000"
                      className="pl-7"
                      value={field.value ? Math.round(field.value / 100) : ""}
                      onChange={(e) => {
                        const rands = e.target.value ? parseInt(e.target.value, 10) : undefined;
                        field.onChange(rands !== undefined ? rands * 100 : undefined);
                      }}
                    />
                  </div>
                </FormControl>
                <FormDescription>
                  Enter amount in Rands. Alerts trigger at 80% and 100% of budget.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Submit */}
        <div className="flex items-center gap-3">
          <Button type="submit" disabled={saving}>
            {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            {initialData.coldStartCompleted ? "Save Configuration" : "Complete Setup"}
          </Button>
          {message && (
            <p
              className={`text-sm ${isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}
            >
              {message}
            </p>
          )}
        </div>
      </form>
    </Form>
  );
}
