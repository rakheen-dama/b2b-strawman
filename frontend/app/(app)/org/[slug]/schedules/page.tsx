import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ScheduleList } from "@/components/schedules/ScheduleList";
import { ScheduleCreateDialog } from "@/components/schedules/ScheduleCreateDialog";
import { getSchedules } from "@/lib/api/schedules";
import { getProjectTemplates } from "@/lib/api/templates";
import { getTemplates } from "@/lib/api/document-templates";
import { listRequestTemplates } from "@/lib/api/information-requests";
import { api } from "@/lib/api";
import type { ScheduleResponse } from "@/lib/api/schedules";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

export default async function SchedulesPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.capabilities.includes("PROJECT_MANAGEMENT") && !caps.isAdmin && !caps.isOwner) {
    notFound();
  }

  const isAdmin = caps.isAdmin || caps.isOwner;

  let schedules: ScheduleResponse[] = [];
  let activeTemplates: ProjectTemplateResponse[] = [];
  let orgMembers: OrgMember[] = [];
  let customers: Customer[] = [];
  let documentTemplates: Array<{ slug: string; name: string }> = [];
  let requestTemplateOptions: Array<{ slug: string; name: string }> = [];

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

    try {
      const allDocTemplates = await getTemplates(undefined, "PROJECT");
      documentTemplates = allDocTemplates
        .filter((t) => t.active)
        .map((t) => ({ slug: t.slug, name: t.name }));
    } catch (e) {
      console.error("Failed to fetch document templates", e);
    }

    try {
      const allRequestTemplates = await listRequestTemplates(true);
      requestTemplateOptions = allRequestTemplates.map((t) => ({
        slug: t.id,
        name: t.name,
      }));
    } catch (e) {
      console.error("Failed to fetch request templates", e);
    }
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
            documentTemplates={documentTemplates}
            requestTemplates={requestTemplateOptions}
          />
        </div>
      )}

      <ScheduleList slug={slug} schedules={schedules} />
    </div>
  );
}
