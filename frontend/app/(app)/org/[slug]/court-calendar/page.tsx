import { notFound } from "next/navigation";
import { Gavel } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default async function CourtCalendarPage({
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
  if (!enabledModules.includes("court_calendar")) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Court Calendar
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Court date tracking and deadline management
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <Gavel className="size-5 text-slate-400" />
            <CardTitle>Court Calendar</CardTitle>
            <Badge variant="neutral">Coming Soon</Badge>
          </div>
          <CardDescription>
            Track court dates, filing deadlines, and hearing schedules. Receive
            reminders for upcoming appearances and manage calendar conflicts
            across matters.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This module is enabled for your organization and will be available in
            a future release. Court calendar features will include date tracking,
            deadline alerts, and calendar synchronization.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
