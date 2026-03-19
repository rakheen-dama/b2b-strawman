import { notFound } from "next/navigation";
import { ShieldAlert } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default async function ConflictCheckPage({
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
  if (!enabledModules.includes("conflict_check")) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Conflict Check
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Matter conflict of interest checks
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <ShieldAlert className="size-5 text-slate-400" />
            <CardTitle>Conflict Check</CardTitle>
            <Badge variant="neutral">Coming Soon</Badge>
          </div>
          <CardDescription>
            Run conflict of interest checks before taking on new matters. Search
            across existing clients, matters, and related parties to identify
            potential conflicts.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This module is enabled for your organization and will be available in
            a future release. Conflict check features will include party search,
            conflict rules, and waiver management.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
