import Link from "next/link";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";

const plans = [
  {
    name: "Starter",
    price: "Free",
    features: [
      "2 team members",
      "Shared infrastructure",
      "Row-level data isolation",
      "Community support",
    ],
    button: { label: "Get Started", variant: "outline" as const },
    highlighted: false,
  },
  {
    name: "Pro",
    price: "$29/month",
    features: [
      "10 team members",
      "Dedicated infrastructure",
      "Schema-level data isolation",
      "Priority support",
    ],
    button: { label: "Get Started", variant: "accent" as const },
    highlighted: true,
  },
];

export function PricingPreview() {
  return (
    <section className="px-6 py-24">
      <div className="mx-auto max-w-4xl">
        <h2 className="text-center font-display text-3xl text-slate-950 dark:text-slate-50">
          Simple, transparent pricing
        </h2>

        <div className="mt-12 grid grid-cols-1 gap-8 md:grid-cols-2">
          {plans.map((plan) => (
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
                    <span className="text-sm text-slate-700 dark:text-slate-300">{feature}</span>
                  </li>
                ))}
              </ul>

              <div className="mt-8">
                <Button
                  variant={plan.button.variant}
                  className="w-full"
                  asChild
                >
                  <Link href="/sign-up">{plan.button.label}</Link>
                </Button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
