import { Plus } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { Button } from "@/components/ui/button";
import { ScheduleList } from "@/components/schedules/ScheduleList";
import { ScheduleCreateDialog } from "@/components/schedules/ScheduleCreateDialog";
import { getSchedules } from "@/lib/api/schedules";
import { getProjectTemplates } from "@/lib/api/templates";
import { api } from "@/lib/api";
import type { ScheduleResponse } from "@/lib/api/schedules";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

export default async function SchedulesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let schedules: ScheduleResponse[] = [];
  let activeTemplates: ProjectTemplateResponse[] = [];
  let orgMembers: OrgMember[] = [];
  let customers: Customer[] = [];

  try {
    schedules = await getSchedules();
  } catch (e) {
    console.error("Failed to fetch schedules", e);
  }

  if (isAdmin) {
    try {
      const allTemplates = await getProjectTemplates();
      activeTemplates = allTemplates.filter((t) => t.active);
    } catch (e) {
      console.error("Failed to fetch templates", e);
    }

    const [membersResult, customersResult] = await Promise.allSettled([
      api.get<OrgMember[]>("/api/members"),
      api.get<Customer[]>("/api/customers"),
    ]);
    if (membersResult.status === "fulfilled") orgMembers = membersResult.value;
    if (customersResult.status === "fulfilled") customers = customersResult.value;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Recurring Schedules
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Automate project creation with recurring schedules based on templates.
        </p>
      </div>

      {isAdmin && (
        <div className="flex justify-end">
          <ScheduleCreateDialog
            slug={slug}
            templates={activeTemplates}
            customers={customers}
            orgMembers={orgMembers}
          >
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              New Schedule
            </Button>
          </ScheduleCreateDialog>
        </div>
      )}

      <ScheduleList slug={slug} schedules={schedules} />
    </div>
  );
}
