"use client";

import { ModuleGate } from "@/components/module-gate";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Scale } from "lucide-react";

export function TrustBalanceCard() {
  return (
    <ModuleGate module="trust_accounting">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <Scale className="size-5 text-slate-400" />
            <CardTitle>Trust Balance</CardTitle>
            <Badge variant="neutral">Coming Soon</Badge>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Trust Accounting module will display client trust balances here.
          </p>
        </CardContent>
      </Card>
    </ModuleGate>
  );
}
