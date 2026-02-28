"use client";

import {
  Calculator,
  Brain,
  FileSignature,
  Mail,
  CreditCard,
  CheckCircle2,
  Circle,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { IntegrationDomain, OrgIntegration } from "@/lib/types";
import type { LucideIcon } from "lucide-react";

const DOMAIN_CONFIG: {
  domain: IntegrationDomain;
  label: string;
  description: string;
  icon: LucideIcon;
}[] = [
  {
    domain: "ACCOUNTING",
    label: "Accounting",
    description: "Connect your accounting software for invoice sync",
    icon: Calculator,
  },
  {
    domain: "AI",
    label: "AI Assistant",
    description: "Enable AI-powered document drafting and analysis",
    icon: Brain,
  },
  {
    domain: "DOCUMENT_SIGNING",
    label: "Document Signing",
    description: "Enable electronic signatures on documents",
    icon: FileSignature,
  },
  {
    domain: "EMAIL",
    label: "Email Delivery",
    description: "Platform email delivery with optional BYOAK configuration",
    icon: Mail,
  },
  {
    domain: "PAYMENT",
    label: "Payment Gateway",
    description: "Accept online payments from customers",
    icon: CreditCard,
  },
];

interface IntegrationGridProps {
  slug: string;
  integrations: OrgIntegration[];
  providers: Partial<Record<IntegrationDomain, string[]>>;
}

export function IntegrationGrid({
  slug,
  integrations,
  providers,
}: IntegrationGridProps) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {DOMAIN_CONFIG.map((config) => {
        const integration =
          integrations.find((i) => i.domain === config.domain) ?? null;
        const domainProviders = providers[config.domain] ?? [];
        const Icon = config.icon;
        const isConnected = integration?.enabled === true;

        return (
          <div
            key={config.domain}
            className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100">
                  <Icon className="size-5 text-slate-600" />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">
                    {config.label}
                  </h3>
                  <p className="text-sm text-slate-500">
                    {config.description}
                  </p>
                </div>
              </div>
              {isConnected ? (
                <Badge variant="success">
                  <CheckCircle2 className="mr-1 size-3" />
                  Connected
                </Badge>
              ) : (
                <Badge variant="neutral">
                  <Circle className="mr-1 size-3" />
                  Not connected
                </Badge>
              )}
            </div>

            {integration?.providerSlug && (
              <p className="text-sm text-slate-500">
                Provider:{" "}
                <span className="font-medium text-slate-700">
                  {integration.providerSlug}
                </span>
              </p>
            )}

            <div className="flex gap-2">
              {!isConnected && domainProviders.length > 0 && (
                <Button size="sm" variant="outline">
                  Connect
                </Button>
              )}
              {isConnected && (
                <>
                  <Button size="sm" variant="outline">
                    Configure
                  </Button>
                  <Button size="sm" variant="ghost" className="text-red-600">
                    Disconnect
                  </Button>
                </>
              )}
              {!isConnected && domainProviders.length === 0 && (
                <p className="text-sm italic text-slate-400">
                  No providers available
                </p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
