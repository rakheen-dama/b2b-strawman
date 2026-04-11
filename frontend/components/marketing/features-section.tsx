import { Clock, Receipt, ShieldCheck, BarChart3 } from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface Story {
  icon: LucideIcon;
  heading: string;
  description: string;
  highlights: string[];
}

const stories: Story[] = [
  {
    icon: Clock,
    heading: "Know where your time goes",
    description:
      "Track billable and non-billable hours across projects, see who's working on what, and spot utilisation gaps before they become profitability problems.",
    highlights: [
      "One-click time logging",
      "My Work cross-project view",
      "Billable vs non-billable breakdown",
    ],
  },
  {
    icon: Receipt,
    heading: "Get paid faster",
    description:
      "Turn tracked time into invoices with a few clicks. Rate cards, bulk billing runs, and a client portal where customers can view and pay — no more chasing.",
    highlights: [
      "Rate cards with project overrides",
      "Batch invoicing for month-end",
      "Client portal with payment links",
    ],
  },
  {
    icon: ShieldCheck,
    heading: "Stay compliant, effortlessly",
    description:
      "FICA checklists with audit trails, POPIA-ready data protection, document templates for engagement letters and compliance forms. Built in, not bolted on.",
    highlights: [
      "Per-step verification audit trail",
      "Configurable retention policies",
      "Template-driven document generation",
    ],
  },
  {
    icon: BarChart3,
    heading: "See your practice clearly",
    description:
      "Profitability by project, customer, and team member. Budget tracking with overspend alerts. Resource planning so you never over- or under-commit.",
    highlights: [
      "Real-time profitability dashboards",
      "Budget vs actual tracking",
      "Team capacity & allocation",
    ],
  },
];

export function FeaturesSection() {
  return (
    <section id="features" className="px-6 py-24 lg:py-32">
      <div className="mx-auto max-w-7xl">
        <p className="text-center text-sm font-semibold tracking-[0.2em] text-teal-600 uppercase">
          Everything your practice needs
        </p>
        <h2 className="font-display mx-auto mt-4 max-w-lg text-center text-3xl text-slate-950 sm:text-4xl dark:text-slate-50">
          One platform, no patchwork
        </h2>

        <div className="mt-20 flex flex-col gap-24 lg:gap-32">
          {stories.map((story, index) => (
            <div
              key={story.heading}
              className={`flex flex-col items-center gap-12 lg:flex-row lg:gap-16 ${
                index % 2 === 1 ? "lg:flex-row-reverse" : ""
              }`}
            >
              {/* Text */}
              <div className="flex-1">
                <div className="inline-flex items-center justify-center rounded-lg border border-slate-200 bg-slate-50 p-2.5 dark:border-slate-800 dark:bg-slate-900">
                  <story.icon className="size-5 text-teal-600 dark:text-teal-500" />
                </div>
                <h3 className="font-display mt-5 text-2xl text-slate-950 dark:text-slate-50">
                  {story.heading}
                </h3>
                <p className="mt-3 max-w-md text-base leading-7 text-slate-600 dark:text-slate-400">
                  {story.description}
                </p>

                <ul className="mt-6 space-y-2.5">
                  {story.highlights.map((highlight) => (
                    <li
                      key={highlight}
                      className="flex items-center gap-2.5 text-sm text-slate-700 dark:text-slate-300"
                    >
                      <span className="size-1.5 shrink-0 rounded-full bg-teal-500" />
                      {highlight}
                    </li>
                  ))}
                </ul>
              </div>

              {/* Screenshot placeholder */}
              <div className="flex-1">
                <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50 shadow-lg dark:border-slate-800 dark:bg-slate-900">
                  <div className="flex aspect-[16/10] items-center justify-center text-sm text-slate-400 dark:text-slate-600">
                    {story.heading} screenshot
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
