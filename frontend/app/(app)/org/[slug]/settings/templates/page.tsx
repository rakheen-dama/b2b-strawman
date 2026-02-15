import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { getTemplates, getOrgSettings } from "@/lib/api";
import { TemplatesContent } from "./templates-content";
import type { TemplateListResponse, OrgSettings } from "@/lib/types";

export default async function TemplatesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let templates: TemplateListResponse[] = [];
  let settings: OrgSettings | null = null;
  try {
    templates = await getTemplates();
  } catch {
    // Non-fatal: show empty state on API failure
  }
  try {
    settings = await getOrgSettings();
  } catch {
    // Non-fatal: branding section will be hidden
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
          Templates
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage document templates for engagement letters, statements of work,
          and more.
        </p>
      </div>

      <TemplatesContent slug={slug} templates={templates} settings={settings} canManage={isAdmin} />
    </div>
  );
}
