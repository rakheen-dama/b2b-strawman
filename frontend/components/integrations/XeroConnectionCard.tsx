"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ExternalLink, Link2Off, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
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
import {
  initiateXeroConnectAction,
  disconnectXeroAction,
} from "@/app/(app)/org/[slug]/settings/integrations/xero/actions";
import type { XeroConnectionResponse, XeroConnectionStatus } from "@/lib/types";

interface XeroConnectionCardProps {
  connection: XeroConnectionResponse | null;
  slug: string;
}

function getStatusBadge(status: XeroConnectionStatus | null) {
  switch (status) {
    case "CONNECTED":
      return <Badge variant="success">Connected</Badge>;
    case "TOKEN_EXPIRED":
      return <Badge variant="warning">Reconnect Required</Badge>;
    case "REVOKED":
      return <Badge variant="destructive">Revoked</Badge>;
    default:
      return <Badge variant="neutral">Not Connected</Badge>;
  }
}

export function XeroConnectionCard({ connection, slug }: XeroConnectionCardProps) {
  const router = useRouter();
  const [isConnecting, setIsConnecting] = useState(false);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [showDisconnectDialog, setShowDisconnectDialog] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const status = connection?.status ?? null;
  const isConnected = status === "CONNECTED";
  const needsReconnect = status === "TOKEN_EXPIRED" || status === "REVOKED";

  async function handleConnect() {
    setIsConnecting(true);
    setError(null);
    try {
      const result = await initiateXeroConnectAction(slug);
      if (result.success && result.data) {
        window.location.href = result.data.authorizationUrl;
      } else {
        setError(result.error ?? "Failed to initiate connection.");
        setIsConnecting(false);
      }
    } catch {
      setError("An unexpected error occurred.");
      setIsConnecting(false);
    }
  }

  async function handleDisconnect() {
    setIsDisconnecting(true);
    setError(null);
    try {
      const result = await disconnectXeroAction(slug);
      if (result.success) {
        toast.success("Disconnected from Xero.");
        setShowDisconnectDialog(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to disconnect.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDisconnecting(false);
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-lg bg-[#13B5EA]/10">
                <span className="text-lg font-bold text-[#13B5EA]">X</span>
              </div>
              <CardTitle className="font-display text-lg">Xero Connection</CardTitle>
            </div>
            {getStatusBadge(status)}
          </div>
          <CardDescription>
            {isConnected
              ? `Connected to ${connection?.xeroOrgName}`
              : needsReconnect
                ? "Your Xero connection needs to be re-established."
                : "Connect your Xero account to sync invoices and contacts."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <p className="text-destructive text-sm" role="alert">
              {error}
            </p>
          )}

          {isConnected && connection && (
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Organization</span>
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {connection.xeroOrgName}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Connected</span>
                <span className="font-mono text-slate-900 tabular-nums dark:text-slate-100">
                  {new Date(connection.connectedAt).toLocaleDateString()}
                </span>
              </div>
            </div>
          )}

          <div className="flex gap-2">
            {!isConnected && (
              <Button
                onClick={handleConnect}
                disabled={isConnecting}
                className="bg-[#13B5EA] text-white hover:bg-[#0e9fd0]"
              >
                {isConnecting ? (
                  <>
                    <RefreshCw className="mr-2 size-4 animate-spin" />
                    Connecting...
                  </>
                ) : (
                  <>
                    <ExternalLink className="mr-2 size-4" />
                    {needsReconnect ? "Reconnect to Xero" : "Connect to Xero"}
                  </>
                )}
              </Button>
            )}

            {(isConnected || needsReconnect) && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowDisconnectDialog(true)}
                className="text-destructive hover:text-destructive"
              >
                <Link2Off className="mr-2 size-4" />
                Disconnect
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <AlertDialog open={showDisconnectDialog} onOpenChange={setShowDisconnectDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Disconnect from Xero?</AlertDialogTitle>
            <AlertDialogDescription>
              This will stop all invoice syncing and remove the connection to{" "}
              {connection?.xeroOrgName ?? "Xero"}. You can reconnect at any time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDisconnecting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDisconnect}
              disabled={isDisconnecting}
              className="bg-destructive hover:bg-destructive/90 text-white"
            >
              {isDisconnecting ? "Disconnecting..." : "Disconnect"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
