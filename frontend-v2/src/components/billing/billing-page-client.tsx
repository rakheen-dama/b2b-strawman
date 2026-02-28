"use client";

import { useState } from "react";
import { Check, Sparkles, Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { upgradeToPro } from "@/app/(app)/org/[slug]/settings/billing/actions";
import type { BillingResponse } from "@/lib/internal-api";

const PLANS = [
  {
    name: "Starter",
    price: "Free",
    subtitle: "For small teams getting started",
    features: [
      "2 team members",
      "Shared infrastructure",
      "Row-level data isolation",
      "Community support",
    ],
    highlighted: false,
  },
  {
    name: "Pro",
    price: "$29/month",
    subtitle: "For growing teams that need more",
    features: [
      "10 team members",
      "Dedicated infrastructure",
      "Schema-level data isolation",
      "Priority support",
    ],
    highlighted: true,
  },
];

interface BillingPageClientProps {
  slug: string;
  billing: BillingResponse | null;
}

export function BillingPageClient({ slug, billing }: BillingPageClientProps) {
  const [isUpgrading, setIsUpgrading] = useState(false);
  const [upgradeError, setUpgradeError] = useState<string | null>(null);

  if (!billing) {
    return (
      <p className="text-sm text-slate-500">
        Unable to load billing information.
      </p>
    );
  }

  const isPro = billing.tier === "PRO";
  const { maxMembers, currentMembers } = billing.limits;
  const usagePercent =
    maxMembers > 0 ? Math.round((currentMembers / maxMembers) * 100) : 0;

  async function handleUpgrade() {
    setIsUpgrading(true);
    setUpgradeError(null);
    try {
      const result = await upgradeToPro();
      if (!result.success) {
        setUpgradeError(result.error ?? "Failed to upgrade.");
      } else {
        window.location.reload();
      }
    } catch {
      setUpgradeError("An unexpected error occurred.");
    } finally {
      setIsUpgrading(false);
    }
  }

  return (
    <div className="space-y-8">
      {/* Current Plan */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold text-slate-900">
            {isPro ? "Pro" : "Starter"} Plan
          </h2>
          <Badge variant={isPro ? "pro" : "starter"}>
            {isPro ? "Pro" : "Starter"}
          </Badge>
        </div>
        <p className="mt-1 text-sm text-slate-500">
          {isPro
            ? "You're on the Pro plan with dedicated infrastructure and higher limits."
            : "You're on the Starter plan with shared infrastructure."}
        </p>

        <div className="mt-6 space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-1.5 text-slate-700">
              <Users className="size-4 text-slate-400" />
              Members
            </span>
            <span className="text-slate-500">
              {currentMembers} of {maxMembers}
            </span>
          </div>
          <div
            className={
              isPro
                ? "[&_[data-slot=progress-indicator]]:bg-teal-500"
                : "[&_[data-slot=progress-indicator]]:bg-slate-500"
            }
          >
            <Progress value={usagePercent} />
          </div>
        </div>
      </div>

      {/* Compare Plans (Starter only) */}
      {!isPro && (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Compare Plans
            </h2>
            <div className="mt-6 grid grid-cols-1 gap-8 md:grid-cols-2">
              {PLANS.map((plan) => (
                <div
                  key={plan.name}
                  className={`relative rounded-lg bg-slate-50 p-8 ${
                    plan.highlighted ? "border-2 border-teal-200" : ""
                  }`}
                >
                  {plan.highlighted && (
                    <span className="absolute -top-3 right-6 rounded-full bg-teal-100 px-3 py-1 text-xs font-semibold text-teal-700">
                      Most popular
                    </span>
                  )}

                  <p className="font-semibold text-slate-900">{plan.name}</p>
                  <p className="mt-2 font-display text-3xl text-slate-900">
                    {plan.price}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    {plan.subtitle}
                  </p>

                  <ul className="mt-6 space-y-3">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2">
                        <Check
                          className={`mt-0.5 size-4 shrink-0 ${
                            plan.highlighted
                              ? "text-teal-500"
                              : "text-slate-400"
                          }`}
                        />
                        <span className="text-sm text-slate-700">
                          {feature}
                        </span>
                      </li>
                    ))}
                  </ul>

                  <div className="mt-8">
                    {plan.highlighted ? (
                      <Button
                        onClick={handleUpgrade}
                        disabled={isUpgrading}
                        className="w-full"
                      >
                        {isUpgrading ? "Upgrading..." : "Upgrade to Pro"}
                      </Button>
                    ) : (
                      <Badge variant="starter">Current plan</Badge>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {upgradeError && (
            <p className="text-sm text-red-600">{upgradeError}</p>
          )}

          {/* CTA */}
          <div className="rounded-lg bg-slate-100 p-8 text-center">
            <Sparkles className="mx-auto size-8 text-teal-500" />
            <h2 className="mt-4 font-display text-xl text-slate-900">
              Ready for dedicated infrastructure?
            </h2>
            <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">
              Upgrade to Pro for schema isolation, more members, and priority
              support.
            </p>
            <div className="mt-6">
              <Button onClick={handleUpgrade} disabled={isUpgrading}>
                {isUpgrading ? "Upgrading..." : "Upgrade to Pro"}
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
