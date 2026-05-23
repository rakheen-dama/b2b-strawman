import {
  Shield,
  Scale,
  Landmark,
  Banknote,
  FileSearch,
  GavelIcon,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface Regulation {
  icon: LucideIcon;
  acronym: string;
  name: string;
  detail: string;
  features: string[];
}

const regulations: Regulation[] = [
  {
    icon: Shield,
    acronym: "FICA",
    name: "Financial Intelligence Centre Act",
    detail:
      "Client identity verification and anti-money laundering compliance built into every onboarding flow.",
    features: [
      "KYC checklists for individuals and entities",
      "Document verification with audit trail",
      "Risk assessment per client",
      "Automated verification status tracking",
    ],
  },
  {
    icon: Scale,
    acronym: "POPIA",
    name: "Protection of Personal Information Act",
    detail:
      "Data protection by design — retention policies, consent tracking, and data subject access requests.",
    features: [
      "Configurable data retention windows",
      "DSAR request management",
      "Consent and processing records",
      "Automatic data lifecycle enforcement",
    ],
  },
  {
    icon: FileSearch,
    acronym: "PAIA",
    name: "Promotion of Access to Information Act",
    detail:
      "Information request workflows for PAIA compliance, with tracking and audit trails.",
    features: [
      "Structured request intake forms",
      "Response deadline tracking",
      "Decision recording with reasons",
      "Complete request audit history",
    ],
  },
  {
    icon: GavelIcon,
    acronym: "LSSA",
    name: "Law Society of South Africa",
    detail:
      "Trust accounting, tariff billing, and practice management aligned with Law Society rules.",
    features: [
      "Section 86 trust account management",
      "LSSA tariff schedule billing",
      "Three-way bank reconciliation",
      "Matter closure compliance gates",
    ],
  },
  {
    icon: Landmark,
    acronym: "SARS",
    name: "SA Revenue Service",
    detail:
      "Tax deadline tracking, VAT calculations, and financial year-end management.",
    features: [
      "Automated deadline calculation",
      "VAT-inclusive and zero-rated billing",
      "Financial year-end tracking per client",
      "ZAR-native invoicing and reporting",
    ],
  },
  {
    icon: Banknote,
    acronym: "Trust Accounting",
    name: "Attorneys Act Section 78",
    detail:
      "Ring-fenced client funds with deposit tracking, payment approvals, and interest distribution.",
    features: [
      "Separate trust and business accounts",
      "Multi-level payment approval workflow",
      "Interest calculation with LPFF rates",
      "Client ledger reconciliation and reports",
    ],
  },
];

export function BuiltForAfrica() {
  return (
    <section className="relative overflow-hidden border-y border-teal-500/10 bg-gradient-to-b from-slate-950 to-slate-900 px-6 py-24 lg:py-32">
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-teal-600/5 via-transparent to-teal-600/5" />

      <div className="relative mx-auto max-w-6xl">
        <p className="text-center text-sm font-semibold tracking-[0.2em] text-teal-400 uppercase">
          Built for South Africa
        </p>
        <h2 className="font-display mx-auto mt-4 max-w-2xl text-center text-3xl text-white sm:text-4xl">
          Compliance isn&apos;t an add-on.{" "}
          <span className="text-teal-400">It&apos;s the foundation.</span>
        </h2>
        <p className="mx-auto mt-5 max-w-2xl text-center text-base leading-relaxed text-slate-400">
          Every South African professional practice operates under layers of regulation — FICA,
          POPIA, PAIA, Law Society rules, SARS deadlines. Kazi doesn&apos;t bolt compliance on
          after the fact. It&apos;s woven into every workflow, every client record, every
          transaction.
        </p>

        <div className="mt-16 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {regulations.map((reg) => (
            <div
              key={reg.acronym}
              className="group rounded-xl border border-white/5 bg-white/[0.02] p-6 transition-colors hover:border-teal-500/20 hover:bg-teal-500/[0.03]"
            >
              <div className="flex items-center gap-3">
                <div className="flex size-9 items-center justify-center rounded-lg bg-teal-500/10">
                  <reg.icon className="size-4.5 text-teal-400" />
                </div>
                <div>
                  <p className="font-display text-sm font-bold text-white">{reg.acronym}</p>
                  <p className="text-xs text-slate-500">{reg.name}</p>
                </div>
              </div>

              <p className="mt-4 text-sm leading-relaxed text-slate-400">{reg.detail}</p>

              <ul className="mt-4 space-y-1.5">
                {reg.features.map((f) => (
                  <li key={f} className="flex items-start gap-2 text-xs text-slate-500">
                    <span className="mt-1.5 size-1 shrink-0 rounded-full bg-teal-500/60" />
                    {f}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
