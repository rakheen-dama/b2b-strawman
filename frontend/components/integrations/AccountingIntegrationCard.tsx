"use client";

import Link from "next/link";
import { Calculator, ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { XeroConnectionStatus } from "@/lib/types";

interface AccountingIntegrationCardProps {
  slug: string;
  xeroStatus: XeroConnectionStatus | null;
  xeroOrgName: string | null;
}

export function AccountingIntegrationCard({
  slug,
  xeroStatus,
  xeroOrgName,
}: AccountingIntegrationCardProps) {
  function getStatusBadge() {
    switch (xeroStatus) {
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

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <Calculator className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">Accounting</CardTitle>
          </div>
          {getStatusBadge()}
        </div>
        <CardDescription>
          {xeroStatus === "CONNECTED" && xeroOrgName
            ? `Connected to ${xeroOrgName} via Xero`
            : "Connect your accounting software for invoice sync"}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" size="sm" asChild>
          <Link href={`/org/${slug}/settings/integrations/xero`}>
            <ExternalLink className="mr-2 size-4" />
            Configure Xero
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
