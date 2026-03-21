import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { api } from "@/lib/api";
import { listIntegrations, listProviders } from "@/lib/api/integrations";
import { IntegrationCard } from "@/components/integrations/IntegrationCard";
import { EmailIntegrationCard } from "@/components/integrations/EmailIntegrationCard";
import { PaymentIntegrationCard } from "@/components/integrations/PaymentIntegrationCard";
import type { IntegrationDomain, OrgIntegration } from "@/lib/types";
import type { BillingResponse } from "@/lib/internal-api";

const DOMAIN_CONFIG: {
  domain: IntegrationDomain;
  label: string;
  description: string;
}[] = [
  {
    domain: "ACCOUNTING",
    label: "Accounting",
    description: "Connect your accounting software for invoice sync",
  },
  {
    domain: "AI",
    label: "AI Assistant",
    description: "Enable AI-powered document drafting and analysis",
  },
  {
    domain: "DOCUMENT_SIGNING",
    label: "Document Signing",
    description: "Enable electronic signatures on documents",
  },
  {
    domain: "EMAIL",
    label: "Email Delivery",
    description: "Platform email delivery with optional BYOAK configuration",
  },
  {
    domain: "PAYMENT",
    label: "Payment Gateway",
    description: "Accept online payments from customers",
  },
];

export default async function IntegrationsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let integrations: OrgIntegration[] = [];
  let providers: Partial<Record<IntegrationDomain, string[]>> = {};
  let tier = "STARTER";

  try {
    const [integrationsResult, providersResult, billing] = await Promise.all([
      listIntegrations(),
      listProviders(),
      api.get<BillingResponse>("/api/billing/subscription"),
    ]);
    integrations = integrationsResult;
    providers = providersResult;
    tier = billing.tier;
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Integrations
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Connect third-party tools and services to your organization.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {DOMAIN_CONFIG.map((config) => {
          const integration =
            integrations.find((i) => i.domain === config.domain) ?? null;
          const domainProviders = providers[config.domain] ?? [];

          if (config.domain === "EMAIL") {
            return <EmailIntegrationCard key={config.domain} />;
          }

          if (config.domain === "PAYMENT") {
            return (
              <PaymentIntegrationCard
                key={config.domain}
                integration={integration}
                providers={domainProviders}
                slug={slug}
              />
            );
          }

          return (
            <IntegrationCard
              key={config.domain}
              domain={config.domain}
              label={config.label}
              description={config.description}
              integration={integration}
              providers={domainProviders}
              slug={slug}
              {...(config.domain === "AI" ? { tier } : {})}
            />
          );
        })}
      </div>
    </div>
  );
}
