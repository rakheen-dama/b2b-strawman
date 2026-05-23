import {
  ArrowLeftRight,
  CreditCard,
  Mail,
  UserCheck,
  Brain,
  Receipt,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface Integration {
  icon: LucideIcon;
  name: string;
  providers: string;
  description: string;
}

const integrations: Integration[] = [
  {
    icon: Receipt,
    name: "Accounting",
    providers: "Xero",
    description:
      "Sync invoices, import customers, and map tax codes. Two-way connection keeps your books and your practice in lockstep.",
  },
  {
    icon: CreditCard,
    name: "Payments",
    providers: "Stripe, PayFast",
    description:
      "Accept online payments on invoices. PayFast for local ZAR transactions, Stripe for international clients.",
  },
  {
    icon: UserCheck,
    name: "KYC Verification",
    providers: "VerifyNow, Check ID SA",
    description:
      "Automated identity verification for FICA compliance. Verify clients against SA databases during onboarding.",
  },
  {
    icon: Brain,
    name: "AI Assistant",
    providers: "Anthropic (Claude)",
    description:
      "Bring your own API key. Claude powers FICA verification, matter intake, and document analysis — configured to your practice.",
  },
  {
    icon: Mail,
    name: "Email Delivery",
    providers: "Platform email",
    description:
      "Transactional email for invoices, information requests, and portal invitations. Delivery tracking and bounce monitoring.",
  },
  {
    icon: ArrowLeftRight,
    name: "Data Export",
    providers: "CSV, PDF",
    description:
      "Export any report, client list, or transaction history. Generate PDF statements of account and fee notes for offline sharing.",
  },
];

export function IntegrationsSection() {
  return (
    <section className="border-t border-slate-200 px-6 py-24 lg:py-32 dark:border-slate-800">
      <div className="mx-auto max-w-6xl">
        <p className="text-center text-sm font-semibold tracking-[0.2em] text-teal-600 uppercase dark:text-teal-400">
          Integrations
        </p>
        <h2 className="font-display mx-auto mt-4 max-w-xl text-center text-3xl text-slate-950 sm:text-4xl dark:text-slate-50">
          Connects to the tools you already use
        </h2>
        <p className="mx-auto mt-5 max-w-2xl text-center text-base leading-relaxed text-slate-600 dark:text-slate-400">
          Kazi integrates with South African payment gateways, accounting platforms, and identity
          verification providers — so your practice runs on one connected system.
        </p>

        <div className="mt-16 grid grid-cols-1 gap-px overflow-hidden rounded-2xl border border-slate-200 bg-slate-200 sm:grid-cols-2 lg:grid-cols-3 dark:border-slate-800 dark:bg-slate-800">
          {integrations.map((integration) => (
            <div
              key={integration.name}
              className="bg-white p-6 dark:bg-slate-950"
            >
              <div className="flex items-center gap-3">
                <div className="flex size-9 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                  <integration.icon className="size-4.5 text-teal-600 dark:text-teal-500" />
                </div>
                <div>
                  <p className="font-display text-sm font-semibold text-slate-950 dark:text-slate-50">
                    {integration.name}
                  </p>
                  <p className="text-xs text-slate-500">{integration.providers}</p>
                </div>
              </div>
              <p className="mt-4 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                {integration.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
