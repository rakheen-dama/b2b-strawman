import { HelpTip } from "@/components/help-tip";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  listIntegrations,
  listProviders,
  getXeroConnection,
  getXeroTaxMappings,
  getXeroSettings,
} from "@/lib/api/integrations";
import { IntegrationCard } from "@/components/integrations/IntegrationCard";
import { EmailIntegrationCard } from "@/components/integrations/EmailIntegrationCard";
import { PaymentIntegrationCard } from "@/components/integrations/PaymentIntegrationCard";
import { KycIntegrationCard } from "@/components/integrations/KycIntegrationCard";
import { AccountingIntegrationCard } from "@/components/integrations/AccountingIntegrationCard";
import { McpIntegrationCard } from "@/components/integrations/McpIntegrationCard";
import { XeroConnectionCard } from "@/components/integrations/XeroConnectionCard";
import { XeroTaxMappingEditor } from "@/components/integrations/XeroTaxMappingEditor";
import { XeroCustomerImport } from "@/components/integrations/XeroCustomerImport";
import { XeroSettingsForm } from "@/components/integrations/XeroSettingsForm";
import { XeroSyncSummary } from "@/components/integrations/XeroSyncSummary";
import { getMcpStatusAction } from "./mcp-actions";
import type {
  IntegrationDomain,
  OrgIntegration,
  XeroConnectionResponse,
  XeroTaxMapping,
  XeroSettingsResponse,
} from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

const DOMAIN_CONFIG: {
  domain: IntegrationDomain;
  label: string;
  description: string;
}[] = [
  {
    domain: "ACCOUNTING",
    label: "Accounting",
    description: "Connect your accounting software for fee note sync",
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
    description: "Accept online payments from clients",
  },
  {
    domain: "KYC_VERIFICATION",
    label: "KYC Verification",
    description: "Automated identity verification for FICA compliance",
  },
];

/**
 * Connections › Xero tab (Phase 87 S9, ADR-341 §12.7.1: `xero` ← integrations + integrations/xero) —
 * the former integrations hub's loader followed by the former Xero page's loader. It is the family's
 * first tab, so it stays ungated (first-tab invariant, D20); the Xero section keeps the former Xero
 * page's in-page INTEGRATION_MANAGE denial.
 */
export async function renderXeroTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();
  const canManageIntegrations =
    caps.isAdmin || caps.isOwner || caps.capabilities.includes("INTEGRATION_MANAGE");

  let integrations: OrgIntegration[] = [];
  let providers: Partial<Record<IntegrationDomain, string[]>> = {};
  let xeroConnection: XeroConnectionResponse | null = null;

  try {
    const [integrationsResult, providersResult] = await Promise.all([
      listIntegrations(),
      listProviders(),
    ]);
    integrations = integrationsResult;
    providers = providersResult;
  } catch {
    // Non-fatal: show empty state
  }

  // Fetched once: the Accounting card badge and the Xero section below both read it.
  try {
    xeroConnection = await getXeroConnection();
  } catch {
    // Not connected or error — leave as null
  }

  // getMcpStatusAction swallows errors and returns null — no try/catch needed.
  const mcpStatus = await getMcpStatusAction();

  // Former Xero page: tax mappings + settings only when connected, and only for managers (the
  // section below renders the denial otherwise, so members don't fetch them).
  const isXeroConnected = xeroConnection?.status === "CONNECTED";
  let taxMappings: XeroTaxMapping[] = [];
  let xeroSettings: XeroSettingsResponse | null = null;
  if (canManageIntegrations && isXeroConnected) {
    const [mappingsResult, settingsResult] = await Promise.allSettled([
      getXeroTaxMappings(),
      getXeroSettings(),
    ]);
    if (mappingsResult.status === "fulfilled") {
      taxMappings = mappingsResult.value;
    }
    if (settingsResult.status === "fulfilled") {
      xeroSettings = settingsResult.value;
    }
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Integrations
          <HelpTip code="integrations.overview" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Connect third-party tools and services to your organization.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {DOMAIN_CONFIG.map((config) => {
          const integration = integrations.find((i) => i.domain === config.domain) ?? null;
          const domainProviders = providers[config.domain] ?? [];

          if (config.domain === "ACCOUNTING") {
            return (
              <AccountingIntegrationCard
                key={config.domain}
                slug={slug}
                xeroStatus={xeroConnection?.status ?? null}
                xeroOrgName={xeroConnection?.xeroOrgName ?? null}
              />
            );
          }

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

          if (config.domain === "KYC_VERIFICATION") {
            return <KycIntegrationCard key={config.domain} integration={integration} slug={slug} />;
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
            />
          );
        })}

        <McpIntegrationCard slug={slug} status={mcpStatus} />
      </div>

      <section id="xero" aria-labelledby="xero-heading" className="scroll-mt-6 space-y-6">
        <div>
          <h2 id="xero-heading" className="font-display text-2xl text-slate-950 dark:text-slate-50">
            Xero Integration
          </h2>
          {canManageIntegrations ? (
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Manage your Xero connection, tax mappings, and sync settings.
            </p>
          ) : (
            <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
              You do not have permission to manage integrations. Contact your administrator.
            </p>
          )}
        </div>

        {canManageIntegrations && (
          <div className="space-y-6">
            <XeroConnectionCard connection={xeroConnection} slug={slug} />

            {isXeroConnected && (
              <>
                <XeroTaxMappingEditor mappings={taxMappings} slug={slug} />

                <XeroCustomerImport slug={slug} />

                <XeroSyncSummary slug={slug} />

                {xeroSettings && <XeroSettingsForm settings={xeroSettings} slug={slug} />}
              </>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
