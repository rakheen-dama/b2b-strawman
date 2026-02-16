"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import type { PackStatusDto } from "@/lib/types";

export function formatPackName(packId: string): string {
  return packId
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

export function CompliancePacks({ packs }: { packs: PackStatusDto[] }) {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Compliance Packs
        </h2>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Pre-configured checklist templates for compliance requirements
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packs.map((pack) => (
          <Card key={pack.packId}>
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between gap-2">
                <CardTitle className="text-base">{formatPackName(pack.packId)}</CardTitle>
                <Badge
                  variant={pack.active ? "default" : "secondary"}
                  className="shrink-0"
                >
                  {pack.active ? "Active" : "Inactive"}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Applied: {formatDate(pack.appliedAt)}
                </p>
                <Switch
                  checked={pack.active}
                  disabled
                  aria-label={`Toggle ${formatPackName(pack.packId)}`}
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
