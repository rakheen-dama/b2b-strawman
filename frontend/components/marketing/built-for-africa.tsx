import { Shield, Landmark, Banknote, Scale } from "lucide-react";

const pillars = [
  {
    icon: Shield,
    label: "FICA compliance",
    detail: "Built-in KYC checklists with per-step audit trails",
  },
  {
    icon: Scale,
    label: "POPIA ready",
    detail: "Data protection, retention policies, and DSAR tracking",
  },
  {
    icon: Banknote,
    label: "ZAR billing",
    detail: "Rate cards, invoicing, and profitability in Rands",
  },
  {
    icon: Landmark,
    label: "SA workflows",
    detail: "SARS deadlines, BEE documentation, and local templates",
  },
];

export function BuiltForAfrica() {
  return (
    <section className="relative overflow-hidden border-y border-teal-500/10 bg-gradient-to-b from-slate-950 to-slate-900 px-6 py-20">
      {/* Subtle teal gradient wash */}
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-teal-600/5 via-transparent to-teal-600/5" />

      <div className="relative mx-auto max-w-5xl">
        <p className="text-center text-sm font-semibold uppercase tracking-[0.2em] text-teal-400">
          Built for South Africa
        </p>
        <p className="mx-auto mt-4 max-w-2xl text-center text-lg text-slate-400">
          Kazi understands the regulations, workflows, and realities of running a
          South African practice — because that&apos;s exactly where it was built.
        </p>

        <div className="mt-14 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {pillars.map((pillar) => (
            <div
              key={pillar.label}
              className="group rounded-lg border border-white/5 bg-white/[0.02] p-5 transition-colors hover:border-teal-500/20 hover:bg-teal-500/[0.04]"
            >
              <pillar.icon className="size-5 text-teal-500" />
              <p className="mt-3 font-display text-sm font-semibold text-white">
                {pillar.label}
              </p>
              <p className="mt-1.5 text-sm leading-relaxed text-slate-500">
                {pillar.detail}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
