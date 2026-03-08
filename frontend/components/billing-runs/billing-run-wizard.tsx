"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfigureStep } from "./configure-step";
import { CustomerSelectionStep } from "./customer-selection-step";
import { CherryPickStep } from "./cherry-pick-step";
import type { BillingRunItem } from "@/lib/api/billing-runs";

const STEP_LABELS = [
  "Configure",
  "Select Customers",
  "Review & Cherry-Pick",
  "Review Drafts",
  "Send",
];

interface BillingRunWizardProps {
  slug: string;
  billingRunId?: string | null;
}

export function BillingRunWizard({
  slug,
  billingRunId: initialBillingRunId,
}: BillingRunWizardProps) {
  const [currentStep, setCurrentStep] = useState(
    initialBillingRunId ? 2 : 1,
  );
  const [billingRunId, setBillingRunId] = useState<string | null>(
    initialBillingRunId ?? null,
  );
  // TODO: Read org's default currency from org settings when available
  const [currency, setCurrency] = useState("ZAR");
  const [includeRetainers, setIncludeRetainers] = useState(false);
  const [cherryPickItems, setCherryPickItems] = useState<BillingRunItem[]>([]);

  function handleConfigureNext(
    newBillingRunId: string,
    runCurrency: string,
    retainers?: boolean,
  ) {
    setBillingRunId(newBillingRunId);
    setCurrency(runCurrency);
    if (retainers !== undefined) setIncludeRetainers(retainers);
    setCurrentStep(2);
  }

  function handleCustomerSelectionNext(items: BillingRunItem[]) {
    setCherryPickItems(items);
    setCurrentStep(3);
  }

  function handleBack() {
    setCurrentStep((prev) => Math.max(1, prev - 1));
  }

  function handleNext() {
    setCurrentStep((prev) => Math.min(STEP_LABELS.length, prev + 1));
  }

  return (
    <div className="space-y-8">
      {/* Step Indicator */}
      <nav aria-label="Wizard steps">
        <ol className="flex items-center gap-2">
          {STEP_LABELS.map((label, index) => {
            const stepNum = index + 1;
            const isActive = stepNum === currentStep;
            const isCompleted = stepNum < currentStep;

            return (
              <li key={label} className="flex items-center gap-2">
                <div
                  className={`flex size-8 items-center justify-center rounded-full text-sm font-medium ${
                    isActive
                      ? "bg-teal-600 text-white"
                      : isCompleted
                        ? "bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                        : "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400"
                  }`}
                >
                  {stepNum}
                </div>
                <span
                  className={`text-sm ${
                    isActive
                      ? "font-medium text-slate-950 dark:text-slate-50"
                      : "text-slate-500 dark:text-slate-400"
                  }`}
                >
                  {label}
                </span>
                {index < STEP_LABELS.length - 1 && (
                  <div className="mx-2 h-px w-8 bg-slate-200 dark:bg-slate-700" />
                )}
              </li>
            );
          })}
        </ol>
      </nav>

      {/* Step Content */}
      {currentStep === 1 && (
        <ConfigureStep slug={slug} onNext={handleConfigureNext} />
      )}
      {currentStep === 2 && billingRunId && (
        <CustomerSelectionStep
          slug={slug}
          billingRunId={billingRunId}
          currency={currency}
          onBack={handleBack}
          onNext={handleCustomerSelectionNext}
        />
      )}
      {currentStep === 3 && billingRunId && (
        <CherryPickStep
          slug={slug}
          billingRunId={billingRunId}
          currency={currency}
          includeRetainers={includeRetainers}
          items={cherryPickItems}
          onBack={handleBack}
          onNext={handleNext}
        />
      )}
      {currentStep === 4 && (
        <PlaceholderStep
          title="Review Drafts"
          onBack={handleBack}
          onNext={handleNext}
        />
      )}
      {currentStep === 5 && (
        <PlaceholderStep title="Send" onBack={handleBack} />
      )}
    </div>
  );
}

function PlaceholderStep({
  title,
  onBack,
  onNext,
}: {
  title: string;
  onBack: () => void;
  onNext?: () => void;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-800 dark:bg-slate-950">
      <p className="text-slate-500 dark:text-slate-400">
        {title} — coming soon
      </p>
      <div className="mt-6 flex justify-center gap-3">
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
        {onNext && (
          <Button variant="default" onClick={onNext}>
            Next
          </Button>
        )}
      </div>
    </div>
  );
}
