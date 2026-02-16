import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { api } from "@/lib/api";
import { ChecklistTemplateEditor } from "@/components/settings/ChecklistTemplateEditor";
import { CompliancePacks } from "@/components/settings/CompliancePacks";
import type {
  ChecklistTemplateResponse,
  PackStatusDto,
  CompliancePacksResponse,
} from "@/lib/types";

export default async function ChecklistsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Checklist Templates
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage checklist templates. Only admins and
          owners can access this page.
        </p>
      </div>
    );
  }

  let templates: ChecklistTemplateResponse[] = [];
  try {
    templates = await api.get<ChecklistTemplateResponse[]>("/api/checklist-templates");
  } catch {
    // Non-fatal: show empty state
  }

  let compliancePacks: PackStatusDto[] = [];
  try {
    const response = await api.get<CompliancePacksResponse>(
      "/api/settings/compliance-packs"
    );
    compliancePacks = response.packs;
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Checklist Templates
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage onboarding and compliance checklist templates
        </p>
      </div>

      {compliancePacks.length > 0 && (
        <CompliancePacks packs={compliancePacks} />
      )}

      <ChecklistTemplateEditor
        slug={slug}
        templates={templates}
        canManage={isAdmin}
      />
    </div>
  );
}
