"use client";

import { useState, useEffect, useCallback } from "react";
import { CreditCard, KeyRound, Copy, Check, Info } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { SetApiKeyDialog } from "@/components/integrations/SetApiKeyDialog";
import { ConnectionTestButton } from "@/components/integrations/ConnectionTestButton";
import {
  upsertIntegrationAction,
  toggleIntegrationAction,
  deleteApiKeyAction,
} from "@/app/(app)/org/[slug]/settings/integrations/actions";
import type {
  OrgIntegration,
  StripePaymentConfig,
  PayFastPaymentConfig,
} from "@/lib/types";

const PROVIDER_LABELS: Record<string, string> = {
  stripe: "Stripe",
  payfast: "PayFast",
};

interface PaymentIntegrationCardProps {
  integration: OrgIntegration | null;
  providers: string[];
  slug: string;
}

export function PaymentIntegrationCard({
  integration,
  providers,
  slug,
}: PaymentIntegrationCardProps) {
  const [isTogglingProvider, setIsTogglingProvider] = useState(false);
  const [isTogglingEnabled, setIsTogglingEnabled] = useState(false);
  const [isDeletingKey, setIsDeletingKey] = useState(false);
  const [isSavingConfig, setIsSavingConfig] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Stripe config state
  const [webhookSigningSecret, setWebhookSigningSecret] = useState("");

  // PayFast config state
  const [merchantId, setMerchantId] = useState("");
  const [merchantKey, setMerchantKey] = useState("");
  const [sandbox, setSandbox] = useState(false);

  const provider = integration?.providerSlug ?? null;
  const hasProvider = !!provider;
  const isEnabled = !!integration?.enabled;
  const hasKey = !!integration?.keySuffix;

  const initializeConfig = useCallback(() => {
    if (!integration?.configJson) {
      setWebhookSigningSecret("");
      setMerchantId("");
      setMerchantKey("");
      setSandbox(false);
      return;
    }
    try {
      const parsed = JSON.parse(integration.configJson);
      if (provider === "stripe") {
        const config = parsed as StripePaymentConfig;
        setWebhookSigningSecret(config.webhookSigningSecret ?? "");
      } else if (provider === "payfast") {
        const config = parsed as PayFastPaymentConfig;
        setMerchantId(config.merchantId ?? "");
        setMerchantKey(config.merchantKey ?? "");
        setSandbox(config.sandbox ?? false);
      }
    } catch {
      // Invalid JSON, ignore
    }
  }, [integration?.configJson, provider]);

  useEffect(() => {
    initializeConfig();
  }, [initializeConfig]);

  function getStatusBadge() {
    if (!integration || !integration.providerSlug) {
      return <Badge variant="neutral">Manual Payments Only</Badge>;
    }
    if (!integration.enabled) {
      return <Badge variant="warning">Disabled</Badge>;
    }
    return <Badge variant="success">Active</Badge>;
  }

  async function handleProviderChange(value: string) {
    setIsTogglingProvider(true);
    setError(null);
    try {
      if (value === "none") {
        // Disconnect: clear provider, config, and key
        const result = await upsertIntegrationAction(slug, "PAYMENT", {
          providerSlug: "",
          configJson: "{}",
        });
        if (!result.success) {
          setError(result.error ?? "Failed to disconnect provider.");
          return;
        }
        // Also remove any stored API key
        if (hasKey) {
          await deleteApiKeyAction(slug, "PAYMENT");
        }
      } else {
        const result = await upsertIntegrationAction(slug, "PAYMENT", {
          providerSlug: value,
        });
        if (!result.success) {
          setError(result.error ?? "Failed to update provider.");
        }
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
      const result = await toggleIntegrationAction(slug, "PAYMENT", {
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
      const result = await deleteApiKeyAction(slug, "PAYMENT");
      if (!result.success) {
        setError(result.error ?? "Failed to remove API key.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeletingKey(false);
    }
  }

  async function handleSaveConfig() {
    if (!provider) return;
    setIsSavingConfig(true);
    setError(null);
    try {
      let configJson: string;
      if (provider === "stripe") {
        const config: StripePaymentConfig = {};
        if (webhookSigningSecret) {
          config.webhookSigningSecret = webhookSigningSecret;
        }
        configJson = JSON.stringify(config);
      } else {
        const config: PayFastPaymentConfig = {
          merchantId: merchantId || undefined,
          merchantKey: merchantKey || undefined,
          sandbox,
        };
        configJson = JSON.stringify(config);
      }
      const result = await upsertIntegrationAction(slug, "PAYMENT", {
        providerSlug: provider,
        configJson,
      });
      if (!result.success) {
        setError(result.error ?? "Failed to save configuration.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSavingConfig(false);
    }
  }

  async function handleCopyUrl(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API may not be available
    }
  }

  const baseUrl =
    process.env.NEXT_PUBLIC_BACKEND_URL ??
    (typeof window !== "undefined" ? window.location.origin : "");
  const webhookUrl = baseUrl
    ? `${baseUrl}/api/webhooks/payment/${provider}`
    : "";

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <CreditCard className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">
              Payment Gateway
            </CardTitle>
          </div>
          {getStatusBadge()}
        </div>
        <CardDescription>
          {hasProvider
            ? "Accept online payments from customers"
            : "Connect a payment provider to enable online invoice payments for your clients."}
        </CardDescription>
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
            htmlFor="provider-PAYMENT"
            className="text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Provider
          </label>
          <Select
            value={provider ?? "none"}
            onValueChange={handleProviderChange}
            disabled={isTogglingProvider}
          >
            <SelectTrigger id="provider-PAYMENT" className="w-full">
              <SelectValue placeholder="Select provider" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">None</SelectItem>
              {providers.map((p) => (
                <SelectItem key={p} value={p}>
                  {PROVIDER_LABELS[p] ?? p}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Stripe-specific fields */}
        {provider === "stripe" && (
          <>
            {/* Secret Key (via SecretStore) */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Secret Key (sk_live_... or sk_test_...)
              </label>
              <div className="flex items-center gap-3">
                {hasKey ? (
                  <>
                    <span className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                      <KeyRound className="size-4" />
                      {"••••" + integration!.keySuffix}
                    </span>
                    <SetApiKeyDialog slug={slug} domain="PAYMENT">
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
                  <SetApiKeyDialog slug={slug} domain="PAYMENT">
                    <Button variant="outline" size="sm">
                      Set API Key
                    </Button>
                  </SetApiKeyDialog>
                )}
              </div>
            </div>

            {/* TODO: Move webhookSigningSecret to SecretStore when backend supports named keys per domain */}
            <div className="space-y-2">
              <label
                htmlFor="stripe-webhook-secret"
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Webhook Signing Secret (whsec_...)
              </label>
              <Input
                id="stripe-webhook-secret"
                type="password"
                value={webhookSigningSecret}
                onChange={(e) => setWebhookSigningSecret(e.target.value)}
                placeholder="whsec_..."
              />
            </div>

            {/* Webhook URL (read-only) */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Webhook URL
              </label>
              <div className="flex items-center gap-2">
                <Input value={webhookUrl} readOnly className="bg-slate-50 dark:bg-slate-900" />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleCopyUrl(webhookUrl)}
                  className="shrink-0"
                >
                  {copied ? (
                    <Check className="size-4" />
                  ) : (
                    <Copy className="size-4" />
                  )}
                </Button>
              </div>
            </div>

            {/* Save Configuration */}
            <Button
              variant="outline"
              size="sm"
              onClick={handleSaveConfig}
              disabled={isSavingConfig}
            >
              {isSavingConfig ? "Saving..." : "Save Configuration"}
            </Button>
          </>
        )}

        {/* PayFast-specific fields */}
        {provider === "payfast" && (
          <>
            {/* Merchant ID */}
            <div className="space-y-2">
              <label
                htmlFor="payfast-merchant-id"
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Merchant ID
              </label>
              <Input
                id="payfast-merchant-id"
                type="text"
                value={merchantId}
                onChange={(e) => setMerchantId(e.target.value)}
                placeholder="Enter your Merchant ID"
              />
            </div>

            {/* Merchant Key */}
            <div className="space-y-2">
              <label
                htmlFor="payfast-merchant-key"
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Merchant Key
              </label>
              {/* TODO: Move merchantKey to SecretStore when backend supports named keys per domain */}
              <Input
                id="payfast-merchant-key"
                type="password"
                value={merchantKey}
                onChange={(e) => setMerchantKey(e.target.value)}
                placeholder="Enter your Merchant Key"
              />
            </div>

            {/* Passphrase (via SecretStore) */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Passphrase
              </label>
              <div className="flex items-center gap-3">
                {hasKey ? (
                  <>
                    <span className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                      <KeyRound className="size-4" />
                      {"••••" + integration!.keySuffix}
                    </span>
                    <SetApiKeyDialog slug={slug} domain="PAYMENT">
                      <Button variant="outline" size="sm">
                        Update Passphrase
                      </Button>
                    </SetApiKeyDialog>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleDeleteKey}
                      disabled={isDeletingKey}
                      className="text-destructive hover:text-destructive"
                    >
                      {isDeletingKey ? "Removing..." : "Remove"}
                    </Button>
                  </>
                ) : (
                  <SetApiKeyDialog slug={slug} domain="PAYMENT">
                    <Button variant="outline" size="sm">
                      Set Passphrase
                    </Button>
                  </SetApiKeyDialog>
                )}
              </div>
            </div>

            {/* Sandbox toggle */}
            <div className="flex items-center justify-between">
              <label
                htmlFor="payfast-sandbox"
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Use PayFast Sandbox for testing
              </label>
              <Switch
                id="payfast-sandbox"
                checked={sandbox}
                onCheckedChange={setSandbox}
              />
            </div>

            {/* ITN Callback URL (read-only) */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                ITN Callback URL
              </label>
              <div className="flex items-center gap-2">
                <Input value={webhookUrl} readOnly className="bg-slate-50 dark:bg-slate-900" />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleCopyUrl(webhookUrl)}
                  className="shrink-0"
                >
                  {copied ? (
                    <Check className="size-4" />
                  ) : (
                    <Copy className="size-4" />
                  )}
                </Button>
              </div>
            </div>

            {/* Save Configuration */}
            <Button
              variant="outline"
              size="sm"
              onClick={handleSaveConfig}
              disabled={isSavingConfig}
            >
              {isSavingConfig ? "Saving..." : "Save Configuration"}
            </Button>
          </>
        )}

        {/* Enable/disable toggle */}
        {hasProvider && (
          <div className="flex items-center justify-between">
            <label
              htmlFor="enabled-PAYMENT"
              className="text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Enabled
            </label>
            <Switch
              id="enabled-PAYMENT"
              checked={isEnabled}
              onCheckedChange={handleToggle}
              disabled={!hasProvider || isTogglingEnabled}
            />
          </div>
        )}

        {/* Connection test / advisory */}
        {hasProvider && isEnabled && provider === "stripe" && (
          <ConnectionTestButton
            slug={slug}
            domain="PAYMENT"
            disabled={!hasKey}
          />
        )}
        {hasProvider && isEnabled && provider === "payfast" && (
          <p className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
            <Info className="size-4 shrink-0" />
            Configuration saved. Send a test payment to verify.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
