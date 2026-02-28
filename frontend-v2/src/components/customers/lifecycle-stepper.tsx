"use client";

import { Check } from "lucide-react";

import type { LifecycleStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

const LIFECYCLE_STEPS: {
  status: LifecycleStatus;
  label: string;
  description: string;
}[] = [
  {
    status: "PROSPECT",
    label: "Prospect",
    description: "Initial lead or inquiry",
  },
  {
    status: "ONBOARDING",
    label: "Onboarding",
    description: "Completing setup checklist",
  },
  {
    status: "ACTIVE",
    label: "Active",
    description: "Engaged, billable client",
  },
  {
    status: "DORMANT",
    label: "Dormant",
    description: "No recent activity",
  },
  {
    status: "OFFBOARDING",
    label: "Offboarding",
    description: "Winding down engagement",
  },
  {
    status: "OFFBOARDED",
    label: "Offboarded",
    description: "Relationship concluded",
  },
];

/**
 * Returns the ordered index of the given lifecycle status, treating
 * the sequence as a progression from PROSPECT (0) to OFFBOARDED (5).
 */
function statusIndex(status: LifecycleStatus): number {
  return LIFECYCLE_STEPS.findIndex((s) => s.status === status);
}

interface LifecycleStepperProps {
  currentStatus: LifecycleStatus;
  className?: string;
}

export function LifecycleStepper({
  currentStatus,
  className,
}: LifecycleStepperProps) {
  const currentIdx = statusIndex(currentStatus);

  return (
    <div className={cn("space-y-1", className)}>
      <div className="relative flex items-start gap-0">
        {LIFECYCLE_STEPS.map((step, idx) => {
          const isCurrent = idx === currentIdx;
          const isPast = idx < currentIdx;
          const isFuture = idx > currentIdx;

          return (
            <div key={step.status} className="flex flex-1 flex-col items-center">
              {/* Connector + Circle */}
              <div className="relative flex w-full items-center justify-center">
                {/* Left connector line */}
                {idx > 0 && (
                  <div
                    className={cn(
                      "absolute left-0 right-1/2 top-1/2 h-0.5 -translate-y-1/2",
                      isPast || isCurrent ? "bg-teal-500" : "bg-slate-200"
                    )}
                  />
                )}
                {/* Right connector line */}
                {idx < LIFECYCLE_STEPS.length - 1 && (
                  <div
                    className={cn(
                      "absolute left-1/2 right-0 top-1/2 h-0.5 -translate-y-1/2",
                      isPast ? "bg-teal-500" : "bg-slate-200"
                    )}
                  />
                )}

                {/* Circle */}
                <div
                  className={cn(
                    "relative z-10 flex size-8 items-center justify-center rounded-full border-2 text-xs font-semibold transition-colors",
                    isCurrent &&
                      "border-teal-500 bg-teal-500 text-white shadow-md shadow-teal-500/30",
                    isPast && "border-teal-500 bg-teal-50 text-teal-600",
                    isFuture && "border-slate-200 bg-white text-slate-400"
                  )}
                >
                  {isPast ? (
                    <Check className="size-4" />
                  ) : (
                    <span>{idx + 1}</span>
                  )}
                </div>
              </div>

              {/* Label */}
              <p
                className={cn(
                  "mt-2 text-center text-xs font-medium",
                  isCurrent ? "text-teal-700" : "text-slate-500"
                )}
              >
                {step.label}
              </p>
              <p className="mt-0.5 text-center text-[10px] text-slate-400 hidden sm:block">
                {step.description}
              </p>
            </div>
          );
        })}
      </div>
    </div>
  );
}
