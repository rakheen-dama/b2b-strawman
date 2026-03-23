import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";

const included = [
  "Time tracking & My Work",
  "Invoicing & billing runs",
  "FICA & POPIA compliance tools",
  "Profitability dashboards",
  "Document templates & generation",
  "Client portal",
  "AI assistant (BYOAK)",
  "Dedicated database per organisation",
];

export function PricingPreview() {
  return (
    <section id="pricing" className="px-6 py-24 lg:py-32">
      <div className="mx-auto max-w-3xl">
        <p className="text-center text-sm font-semibold uppercase tracking-[0.2em] text-teal-600">
          Pricing
        </p>
        <h2 className="mt-4 text-center font-display text-3xl text-slate-950 dark:text-slate-50">
          Transparent pricing for your practice size
        </h2>
        <p className="mx-auto mt-4 max-w-lg text-center text-base text-slate-600 dark:text-slate-400">
          Every practice is different. We&apos;ll put together a plan that fits
          your team size, feature needs, and growth trajectory.
        </p>

        <div className="mt-14 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm sm:p-10 dark:border-slate-800 dark:bg-slate-900">
          <p className="font-display text-lg text-slate-950 dark:text-slate-50">
            What&apos;s included
          </p>

          <ul className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
            {included.map((feature) => (
              <li
                key={feature}
                className="flex items-center gap-2.5 text-sm text-slate-700 dark:text-slate-300"
              >
                <Check className="size-4 shrink-0 text-teal-500" />
                {feature}
              </li>
            ))}
          </ul>

          <div className="mt-10 flex flex-col items-start gap-4 border-t border-slate-100 pt-8 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Pricing tailored to your practice. No hidden fees, no lock-in.
            </p>
            <Button variant="accent" asChild>
              <a href="mailto:hello@kazi.africa">
                Get in touch
                <ArrowRight className="size-4" />
              </a>
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}
