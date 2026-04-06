"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import {
  upsertIntegrationAction,
  setApiKeyAction,
  testConnectionAction,
} from "@/app/(app)/org/[slug]/settings/integrations/actions";

interface KycConfigurationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  currentProvider?: string | null;
}

export function KycConfigurationDialog({
  open,
  onOpenChange,
  slug,
  currentProvider,
}: KycConfigurationDialogProps) {
  const [provider, setProvider] = useState(currentProvider ?? "");
  const [apiKey, setApiKey] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testStatus, setTestStatus] = useState<
    "idle" | "success" | "error"
  >("idle");
  const [testMessage, setTestMessage] = useState<string | null>(null);

  function handleOpenChange(nextOpen: boolean) {
    onOpenChange(nextOpen);
    if (nextOpen) {
      setProvider(currentProvider ?? "");
      setApiKey("");
      setError(null);
      setTestStatus("idle");
      setTestMessage(null);
    }
  }

  async function handleTestConnection() {
    if (!provider) {
      setError("Please select a provider first.");
      return;
    }
    setIsTesting(true);
    setTestStatus("idle");
    setTestMessage(null);
    setError(null);

    try {
      const result = await testConnectionAction(slug, "KYC_VERIFICATION");
      if (result.success && result.data) {
        if (result.data.success) {
          setTestStatus("success");
          setTestMessage("Connection successful");
        } else {
          setTestStatus("error");
          setTestMessage(
            result.data.errorMessage ?? "Connection test failed.",
          );
        }
      } else {
        setTestStatus("error");
        setTestMessage(result.error ?? "Connection test failed.");
      }
    } catch {
      setTestStatus("error");
      setTestMessage("An unexpected error occurred.");
    } finally {
      setIsTesting(false);
    }
  }

  async function handleSave() {
    if (!provider) {
      setError("Please select a provider.");
      return;
    }
    setIsSubmitting(true);
    setError(null);

    try {
      // Upsert integration with selected provider
      const upsertResult = await upsertIntegrationAction(
        slug,
        "KYC_VERIFICATION",
        { providerSlug: provider },
      );
      if (!upsertResult.success) {
        setError(upsertResult.error ?? "Failed to save configuration.");
        return;
      }

      // Set API key if provided
      if (apiKey.trim()) {
        const keyResult = await setApiKeyAction(slug, "KYC_VERIFICATION", {
          apiKey: apiKey.trim(),
        });
        if (!keyResult.success) {
          setError(keyResult.error ?? "Failed to save API key.");
          return;
        }
      }

      onOpenChange(false);
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Configure KYC Verification</DialogTitle>
          <DialogDescription>
            Set up your KYC verification provider for automated identity checks.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="kyc-provider">Provider</Label>
            <Select
              value={provider}
              onValueChange={setProvider}
              disabled={isSubmitting}
            >
              <SelectTrigger id="kyc-provider">
                <SelectValue placeholder="Select provider" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="verifynow">VerifyNow</SelectItem>
                <SelectItem value="checkid">Check ID SA</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="kyc-api-key">API Key</Label>
            <Input
              id="kyc-api-key"
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="Enter your API key"
              disabled={isSubmitting}
            />
          </div>

          {/* Test Connection — tests the saved integration config on the server,
              not the unsaved form values. Save first, then test. */}
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleTestConnection}
              disabled={isTesting || !provider}
            >
              {isTesting && <Loader2 className="mr-2 size-4 animate-spin" />}
              Test Connection
            </Button>
            {testStatus === "success" && testMessage && (
              <span className="flex items-center gap-1 text-sm text-emerald-600 dark:text-emerald-400">
                <CheckCircle2 className="size-4" />
                {testMessage}
              </span>
            )}
            {testStatus === "error" && testMessage && (
              <span className="flex items-center gap-1 text-sm text-destructive">
                <XCircle className="size-4" />
                {testMessage}
              </span>
            )}
          </div>

          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSave}
            disabled={isSubmitting || !provider}
          >
            {isSubmitting ? "Saving..." : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
