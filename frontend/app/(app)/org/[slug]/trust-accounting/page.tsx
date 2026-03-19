import { notFound } from "next/navigation";
import { Scale } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default async function TrustAccountingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Accounting
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          LSSA-compliant trust account management for client funds
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <Scale className="size-5 text-slate-400" />
            <CardTitle>Trust Accounting</CardTitle>
            <Badge variant="neutral">Coming Soon</Badge>
          </div>
          <CardDescription>
            Manage client trust accounts, track deposits and withdrawals, and
            generate trust accounting reports in compliance with LSSA
            requirements.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This module is enabled for your organization and will be available in
            a future release. Trust accounting features will include ledger
            management, transaction tracking, and regulatory reporting.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
