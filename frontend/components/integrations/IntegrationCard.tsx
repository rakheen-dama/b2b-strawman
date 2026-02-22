"use client";

import { useState } from "react";
import { Calculator, Sparkles, PenTool, CreditCard, KeyRound } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { SetApiKeyDialog } from "@/components/integrations/SetApiKeyDialog";
import { ConnectionTestButton } from "@/components/integrations/ConnectionTestButton";
import {
  upsertIntegrationAction,
  toggleIntegrationAction,
  deleteApiKeyAction,
} from "@/app/(app)/org/[slug]/settings/integrations/actions";
import type { IntegrationDomain, OrgIntegration } from "@/lib/types";

const DOMAIN_ICONS: Record<
  IntegrationDomain,
  React.ComponentType<{ className?: string }>
> = {
  ACCOUNTING: Calculator,
  AI: Sparkles,
  DOCUMENT_SIGNING: PenTool,
  PAYMENT: CreditCard,
};

interface IntegrationCardProps {
  domain: IntegrationDomain;
  label: string;
  description: string;
  integration: OrgIntegration | null;
  providers: string[];
  slug: string;
}

export function IntegrationCard({
  domain,
  label,
  description,
  integration,
  providers,
  slug,
}: IntegrationCardProps) {
  const [isTogglingProvider, setIsTogglingProvider] = useState(false);
  const [isTogglingEnabled, setIsTogglingEnabled] = useState(false);
  const [isDeletingKey, setIsDeletingKey] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const Icon = DOMAIN_ICONS[domain];
  const hasProvider = !!integration?.providerSlug;
  const isEnabled = !!integration?.enabled;
  const hasKey = !!integration?.keySuffix;

  function getStatusBadge() {
    if (!integration || !integration.providerSlug) {
      return <Badge variant="neutral">Not Configured</Badge>;
    }
    if (!integration.enabled) {
      return <Badge variant="warning">Disabled</Badge>;
    }
    return <Badge variant="success">Active</Badge>;
  }

  async function handleProviderChange(providerSlug: string) {
    setIsTogglingProvider(true);
    setError(null);
    try {
      const result = await upsertIntegrationAction(slug, domain, {
        providerSlug,
      });
      if (!result.success) {
        setError(result.error ?? "Failed to update provider.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsTogglingProvider(false);
    }
  }

  async function handleToggle(checked: boolean) {
    setIsTogglingEnabled(true);
    setError(null);
    try {
      const result = await toggleIntegrationAction(slug, domain, {
        enabled: checked,
      });
      if (!result.success) {
        setError(result.error ?? "Failed to toggle integration.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsTogglingEnabled(false);
    }
  }

  async function handleDeleteKey() {
    setIsDeletingKey(true);
    setError(null);
    try {
      const result = await deleteApiKeyAction(slug, domain);
      if (!result.success) {
        setError(result.error ?? "Failed to remove API key.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeletingKey(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <Icon className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">{label}</CardTitle>
          </div>
          {getStatusBadge()}
        </div>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        {/* Provider selector */}
        <div className="space-y-2">
          <label
            htmlFor={`provider-${domain}`}
            className="text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Provider
          </label>
          <Select
            value={integration?.providerSlug ?? ""}
            onValueChange={handleProviderChange}
            disabled={isTogglingProvider}
          >
            <SelectTrigger id={`provider-${domain}`} className="w-full">
              <SelectValue placeholder="Select provider" />
            </SelectTrigger>
            <SelectContent>
              {providers.map((provider) => (
                <SelectItem key={provider} value={provider}>
                  {provider}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* API Key section */}
        {hasProvider && (
          <div className="space-y-2">
            <label
              htmlFor={`api-key-${domain}`}
              className="text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              API Key
            </label>
            <div id={`api-key-${domain}`} className="flex items-center gap-3">
              {hasKey ? (
                <>
                  <span className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                    <KeyRound className="size-4" />
                    {"••••" + integration.keySuffix}
                  </span>
                  <SetApiKeyDialog slug={slug} domain={domain}>
                    <Button variant="outline" size="sm">
                      Update Key
                    </Button>
                  </SetApiKeyDialog>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleDeleteKey}
                    disabled={isDeletingKey}
                    className="text-destructive hover:text-destructive"
                  >
                    {isDeletingKey ? "Removing..." : "Remove Key"}
                  </Button>
                </>
              ) : (
                <SetApiKeyDialog slug={slug} domain={domain}>
                  <Button variant="outline" size="sm">
                    Set API Key
                  </Button>
                </SetApiKeyDialog>
              )}
            </div>
          </div>
        )}

        {/* Enable/disable toggle */}
        {hasProvider && (
          <div className="flex items-center justify-between">
            <label
              htmlFor={`enabled-${domain}`}
              className="text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Enabled
            </label>
            <Switch
              id={`enabled-${domain}`}
              checked={isEnabled}
              onCheckedChange={handleToggle}
              disabled={!hasProvider || isTogglingEnabled}
            />
          </div>
        )}

        {/* Connection test */}
        {hasProvider && isEnabled && (
          <ConnectionTestButton
            slug={slug}
            domain={domain}
            disabled={!isEnabled || !hasKey}
          />
        )}
      </CardContent>
    </Card>
  );
}
