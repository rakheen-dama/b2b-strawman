"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Bot, Check, Copy, ShieldCheck } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@b2mash/ui/card";
import { Separator } from "@b2mash/ui/separator";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { formatDateTime } from "@/lib/format";
import { enableMcpAction, revokeMcpAction } from "./actions";
import { MCP_CONSENT_VERSION, type McpStatus } from "@/lib/types";

interface McpConnectorCardProps {
  status: McpStatus | null;
  slug: string;
  serverUrl: string;
}

export function McpConnectorCard({ status, slug, serverUrl }: McpConnectorCardProps) {
  const router = useRouter();
  const [consentOpen, setConsentOpen] = useState(false);
  const [revokeOpen, setRevokeOpen] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  const enabled = status?.effectivelyEnabled ?? false;
  const consent = status?.consent ?? null;

  const configSnippet = `{
  "mcpServers": {
    "kazi": {
      "url": "${serverUrl}"
    }
  }
}`;

  async function handleCopy(key: string, value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      setTimeout(() => setCopied((c) => (c === key ? null : c)), 2000);
    } catch {
      // Clipboard API may not be available
    }
  }

  function handleToggle(checked: boolean) {
    setError(null);
    if (checked) {
      setConsentOpen(true);
    } else {
      setRevokeOpen(true);
    }
  }

  async function handleAcknowledge() {
    setIsPending(true);
    setError(null);
    try {
      const result = await enableMcpAction(MCP_CONSENT_VERSION, slug);
      if (result.success) {
        setConsentOpen(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to enable the MCP connector.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  async function handleRevoke() {
    setIsPending(true);
    setError(null);
    try {
      const result = await revokeMcpAction(slug);
      if (result.success) {
        setRevokeOpen(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to revoke the MCP connector.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <Bot className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">Claude / MCP Connector</CardTitle>
          </div>
          {enabled ? (
            <Badge variant="success">Enabled</Badge>
          ) : (
            <Badge variant="neutral">Disabled</Badge>
          )}
        </div>
        <CardDescription>
          Let your firm&apos;s own Claude (Desktop or Code) securely read your Kazi matters,
          clients, and documents over the Model Context Protocol.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-6">
        {error && (
          <p className="text-destructive text-sm" role="alert">
            {error}
          </p>
        )}

        {/* Enablement toggle */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
              {enabled ? "Connector enabled" : "Connector disabled"}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {enabled
                ? "Client data can flow into your external AI context."
                : "Enable to acknowledge POPIA responsibility and connect Claude."}
            </p>
          </div>
          <Switch
            aria-label="Enabled"
            checked={enabled}
            onCheckedChange={handleToggle}
            disabled={isPending}
          />
        </div>

        {enabled && (
          <>
            <Separator />

            {/* Server URL */}
            <div className="space-y-2">
              <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                MCP server URL
              </p>
              <div className="flex items-center gap-2">
                <code className="flex-1 truncate rounded-md bg-slate-100 px-3 py-2 font-mono text-xs text-slate-800 dark:bg-slate-800 dark:text-slate-200">
                  {serverUrl}
                </code>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label="Copy server URL"
                  onClick={() => handleCopy("url", serverUrl)}
                >
                  {copied === "url" ? (
                    <Check className="size-4" />
                  ) : (
                    <Copy className="size-4" />
                  )}
                </Button>
              </div>
            </div>

            {/* Connection instructions */}
            <div className="space-y-2">
              <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Connect from Claude Desktop or Claude Code
              </p>
              <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-600 dark:text-slate-400">
                <li>Open Claude Desktop settings (or your Claude Code config).</li>
                <li>Add a new MCP server using the URL above.</li>
                <li>
                  Complete the OAuth sign-in flow when prompted — Claude authenticates against
                  your Kazi organization before any data is read.
                </li>
              </ol>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Config snippet
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    aria-label="Copy config snippet"
                    onClick={() => handleCopy("snippet", configSnippet)}
                  >
                    {copied === "snippet" ? (
                      <>
                        <Check className="mr-2 size-4" />
                        Copied
                      </>
                    ) : (
                      <>
                        <Copy className="mr-2 size-4" />
                        Copy
                      </>
                    )}
                  </Button>
                </div>
                <pre className="overflow-x-auto rounded-md bg-slate-100 p-3 font-mono text-xs text-slate-800 dark:bg-slate-800 dark:text-slate-200">
                  {configSnippet}
                </pre>
              </div>
            </div>

            {/* Consent metadata */}
            {consent?.granted && (
              <>
                <Separator />
                <div className="space-y-1 text-sm text-slate-600 dark:text-slate-400">
                  <p className="font-medium text-slate-700 dark:text-slate-300">
                    POPIA consent
                  </p>
                  {consent.version && (
                    <p>
                      Version: <span className="font-mono">{consent.version}</span>
                    </p>
                  )}
                  {consent.consentedBy && <p>Acknowledged by: {consent.consentedBy}</p>}
                  {consent.consentedAt && (
                    <p>Acknowledged on: {formatDateTime(consent.consentedAt)}</p>
                  )}
                </div>
              </>
            )}

            {/* Revoke control */}
            <div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setRevokeOpen(true)}
                className="text-destructive hover:text-destructive"
              >
                Revoke access
              </Button>
            </div>
          </>
        )}
      </CardContent>

      {/* POPIA consent acknowledgement modal (controlled — no asChild trigger) */}
      <Dialog open={consentOpen} onOpenChange={(o) => !isPending && setConsentOpen(o)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <div className="flex justify-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-teal-100 dark:bg-teal-950">
                <ShieldCheck className="size-6 text-teal-600 dark:text-teal-400" />
              </div>
            </div>
            <DialogTitle className="text-center">Acknowledge POPIA responsibility</DialogTitle>
            <DialogDescription className="text-center">
              Enabling the MCP connector lets your firm&apos;s external AI context (Claude
              Desktop or Code) read client personal information held in Kazi.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 text-sm text-slate-600 dark:text-slate-400">
            <p>
              Under POPIA, your firm is the responsible party for client personal information.
              When Claude reads matters, clients, and documents through this connector, that
              data flows into your firm&apos;s own AI environment outside Kazi.
            </p>
            <p>
              By acknowledging, you confirm the firm accepts responsibility for the lawful and
              secure handling of this data within its AI tooling.
            </p>
          </div>
          {error && (
            <p className="text-destructive text-sm" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setConsentOpen(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button type="button" onClick={handleAcknowledge} disabled={isPending}>
              {isPending ? "Enabling..." : "I acknowledge & enable"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Revoke confirmation */}
      <AlertDialog open={revokeOpen} onOpenChange={(o) => !isPending && setRevokeOpen(o)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Revoke MCP connector access?</AlertDialogTitle>
            <AlertDialogDescription>
              Claude will no longer be able to read your Kazi data through the connector. A
              REVOKED consent record is kept for your audit trail. You can re-enable at any
              time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && (
            <p className="text-destructive text-sm" role="alert">
              {error}
            </p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                void handleRevoke();
              }}
              disabled={isPending}
            >
              {isPending ? "Revoking..." : "Revoke access"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
