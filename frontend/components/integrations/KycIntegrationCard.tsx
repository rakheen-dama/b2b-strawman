"use client";

import { useState } from "react";
import { ShieldCheck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { KycConfigurationDialog } from "@/components/settings/KycConfigurationDialog";
import {
  deleteApiKeyAction,
  toggleIntegrationAction,
  upsertIntegrationAction,
} from "@/app/(app)/org/[slug]/settings/integrations/actions";
import type { OrgIntegration } from "@/lib/types";

const PROVIDER_LABELS: Record<string, string> = {
  verifynow: "VerifyNow",
  checkid: "Check ID SA",
};

interface KycIntegrationCardProps {
  integration: OrgIntegration | null;
  slug: string;
}

export function KycIntegrationCard({ integration, slug }: KycIntegrationCardProps) {
  const [configDialogOpen, setConfigDialogOpen] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);
  const [isToggling, setIsToggling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const hasProvider = !!integration?.providerSlug;
  const isConfigured = hasProvider && integration.enabled;

  function getStatusBadge() {
    if (!integration || !integration.providerSlug) {
      return <Badge variant="neutral">Not Configured</Badge>;
    }
    if (!integration.enabled) {
      return <Badge variant="warning">Disabled</Badge>;
    }
    return <Badge variant="success">Configured</Badge>;
  }

  async function handleToggle(checked: boolean) {
    setIsToggling(true);
    setError(null);
    try {
      const result = await toggleIntegrationAction(slug, "KYC_VERIFICATION", {
        enabled: checked,
      });
      if (!result.success) {
        setError(result.error ?? "Failed to toggle integration.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsToggling(false);
    }
  }

  async function handleRemove() {
    setIsRemoving(true);
    setError(null);
    try {
      // Remove API key first
      const deleteResult = await deleteApiKeyAction(slug, "KYC_VERIFICATION");
      if (!deleteResult.success) {
        setError(deleteResult.error ?? "Failed to remove API key.");
        return;
      }
      // Then reset provider
      const result = await upsertIntegrationAction(slug, "KYC_VERIFICATION", {
        providerSlug: "",
      });
      if (!result.success) {
        setError(result.error ?? "Failed to remove integration.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsRemoving(false);
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
                <ShieldCheck className="size-5 text-slate-600 dark:text-slate-400" />
              </div>
              <CardTitle className="font-display text-lg">KYC Verification</CardTitle>
            </div>
            {getStatusBadge()}
          </div>
          <CardDescription>Automated identity verification for FICA compliance</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <p className="text-destructive text-sm" role="alert">
              {error}
            </p>
          )}

          {isConfigured && integration?.providerSlug && (
            <div className="space-y-1">
              <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Provider</p>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                {PROVIDER_LABELS[integration.providerSlug] ?? integration.providerSlug}
              </p>
            </div>
          )}

          {hasProvider && (
            <div className="flex items-center justify-between">
              <label
                htmlFor="enabled-KYC_VERIFICATION"
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Enabled
              </label>
              <Switch
                id="enabled-KYC_VERIFICATION"
                checked={!!integration?.enabled}
                onCheckedChange={handleToggle}
                disabled={!hasProvider || isToggling}
              />
            </div>
          )}

          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setConfigDialogOpen(true)}>
              Configure
            </Button>
            {hasProvider && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleRemove}
                disabled={isRemoving}
                className="text-destructive hover:text-destructive"
              >
                {isRemoving ? "Removing..." : "Remove"}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <KycConfigurationDialog
        open={configDialogOpen}
        onOpenChange={setConfigDialogOpen}
        slug={slug}
        currentProvider={integration?.providerSlug}
      />
    </>
  );
}
