import { Bot, ShieldCheck, FileSearch, Clock, Gauge, Key } from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface AiFeature {
  icon: LucideIcon;
  title: string;
  description: string;
}

const features: AiFeature[] = [
  {
    icon: FileSearch,
    title: "FICA Verification",
    description:
      "AI analyses uploaded identity documents against FICA checklist requirements, flags missing items, and assigns a risk level — in seconds, not hours.",
  },
  {
    icon: Bot,
    title: "Matter Intake",
    description:
      "Describe a new matter in plain language. AI recommends the right template, estimates fees, surfaces potential conflicts, and pre-fills custom fields.",
  },
  {
    icon: Clock,
    title: "Time Entry Polish",
    description:
      "AI rewrites time entry descriptions into professional, invoice-ready language while preserving the substance of the work recorded.",
  },
  {
    icon: ShieldCheck,
    title: "Human-in-the-Loop",
    description:
      "Every AI action flows through a governance gate. Review, approve, or reject proposals before they touch your data. Full audit trail included.",
  },
  {
    icon: Gauge,
    title: "Cost Control",
    description:
      "Set monthly budget caps in Rands. Track cost per invocation, token usage, and cache efficiency. Choose between fast (Sonnet) and capable (Opus) models.",
  },
  {
    icon: Key,
    title: "Bring Your Own Key",
    description:
      "Connect your own Anthropic API key for direct billing. No markup, no middleman — your key, your usage, your costs.",
  },
];

export function AiSection() {
  return (
    <section className="relative overflow-hidden bg-slate-950 px-6 py-24 lg:py-32">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.02]"
        style={{
          backgroundImage: "radial-gradient(circle at 1px 1px, white 1px, transparent 0)",
          backgroundSize: "40px 40px",
        }}
      />

      <div className="relative mx-auto max-w-6xl">
        <p className="text-center text-sm font-semibold tracking-[0.2em] text-teal-400 uppercase">
          AI-Powered
        </p>
        <h2 className="font-display mx-auto mt-4 max-w-2xl text-center text-3xl text-white sm:text-4xl">
          Intelligence that understands <span className="text-teal-400">your practice</span>
        </h2>
        <p className="mx-auto mt-5 max-w-2xl text-center text-base leading-relaxed text-slate-400">
          Kazi&apos;s AI is configured per firm — your practice areas, jurisdiction, risk
          calibration, and house style. It doesn&apos;t guess. It works within your rules, and every
          action requires your approval.
        </p>

        <div className="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => (
            <div
              key={feature.title}
              className="rounded-xl border border-white/5 bg-white/[0.02] p-6 transition-colors hover:border-teal-500/20 hover:bg-teal-500/[0.03]"
            >
              <div className="flex size-9 items-center justify-center rounded-lg bg-teal-500/10">
                <feature.icon className="size-4.5 text-teal-400" />
              </div>
              <h3 className="font-display mt-4 text-sm font-semibold text-white">
                {feature.title}
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{feature.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
