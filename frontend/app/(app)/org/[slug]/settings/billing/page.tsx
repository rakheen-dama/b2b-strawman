import Link from "next/link";
import { Check, ChevronLeft, Sparkles, Users } from "lucide-react";
import { api } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { UpgradeButton } from "@/components/billing/upgrade-button";
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

export default async function BillingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const billing = await api.get<BillingResponse>("/api/billing/subscription");

  const isPro = billing.tier === "PRO";
  const { maxMembers, currentMembers } = billing.limits;
  const usagePercent =
    maxMembers > 0 ? Math.round((currentMembers / maxMembers) * 100) : 0;

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      {/* Page header */}
      <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Billing</h1>

      {/* Current Plan card */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <div className="flex items-center gap-3">
          <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
            {isPro ? "Pro" : "Starter"} Plan
          </h2>
          <Badge variant={isPro ? "pro" : "starter"}>
            {isPro ? "Pro" : "Starter"}
          </Badge>
        </div>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          {isPro
            ? "You\u2019re on the Pro plan with dedicated infrastructure and higher limits."
            : "You\u2019re on the Starter plan with shared infrastructure."}
        </p>

        <div className="mt-6 space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-1.5 text-slate-700 dark:text-slate-300">
              <Users className="size-4 text-slate-500" />
              Members
            </span>
            <span className="text-slate-600 dark:text-slate-400">
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

      {/* Plan comparison + Upgrade CTA (Starter only) */}
      {!isPro && (
        <>
          {/* Pricing cards */}
          <div>
            <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
              Compare Plans
            </h2>
            <div className="mt-6 grid grid-cols-1 gap-8 md:grid-cols-2">
              {PLANS.map((plan) => (
                <div
                  key={plan.name}
                  className={`relative rounded-lg bg-slate-950/[0.025] p-8 dark:bg-slate-50/[0.05] ${
                    plan.highlighted ? "border-2 border-teal-200" : ""
                  }`}
                >
                  {plan.highlighted && (
                    <span className="absolute -top-3 right-6 rounded-full bg-teal-100 px-3 py-1 text-xs font-semibold text-teal-700">
                      Most popular
                    </span>
                  )}

                  <p className="font-semibold text-slate-950 dark:text-slate-50">{plan.name}</p>
                  <p className="mt-2 font-display text-3xl text-slate-950 dark:text-slate-50">
                    {plan.price}
                  </p>
                  <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
                    {plan.subtitle}
                  </p>

                  <ul className="mt-6 space-y-3">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2">
                        <Check
                          className={`mt-0.5 size-4 shrink-0 ${
                            plan.highlighted
                              ? "text-teal-500"
                              : "text-slate-500"
                          }`}
                        />
                        <span className="text-sm text-slate-700 dark:text-slate-300">
                          {feature}
                        </span>
                      </li>
                    ))}
                  </ul>

                  <div className="mt-8">
                    {plan.highlighted ? (
                      <UpgradeButton slug={slug} className="w-full" />
                    ) : (
                      <Badge variant="starter">Current plan</Badge>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Upgrade CTA section */}
          <div className="rounded-lg bg-slate-100 p-8 text-center dark:bg-slate-900">
            <Sparkles className="mx-auto size-8 text-teal-500" />
            <h2 className="mt-4 font-display text-xl text-slate-950 dark:text-slate-50">
              Ready for dedicated infrastructure?
            </h2>
            <p className="mx-auto mt-2 max-w-md text-sm text-slate-600 dark:text-slate-400">
              Upgrade to Pro for schema isolation, more members, and priority
              support.
            </p>
            <div className="mt-6">
              <UpgradeButton slug={slug} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
