import Link from "next/link";
import { ChevronLeft, ClipboardCheck, Plus } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { api } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ChecklistTemplateActions } from "@/components/compliance/ChecklistTemplateActions";
import type { ChecklistTemplateResponse } from "@/lib/types";

export default async function ChecklistsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
          Checklists
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
          Checklists
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage checklist templates used during customer onboarding.
        </p>
      </div>

      <div className="flex justify-end">
        <Link href={`/org/${slug}/settings/checklists/new`}>
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            Create Template
          </Button>
        </Link>
      </div>

      {templates.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <ClipboardCheck className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No checklist templates yet
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Create your first checklist template to streamline customer onboarding.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Customer Type
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Source
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Auto-Instantiate
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Items
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {templates.map((template) => (
                <tr
                  key={template.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/settings/checklists/${template.id}`}
                      className="font-medium text-slate-900 hover:underline dark:text-slate-100"
                    >
                      {template.name}
                    </Link>
                    {template.description && (
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {template.description}
                      </p>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant="neutral">{template.customerType}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge
                      variant={template.source === "ORG_CUSTOM" ? "success" : "neutral"}
                    >
                      {template.source === "PLATFORM" ? "Platform" : "Custom"}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        template.autoInstantiate
                          ? "text-green-600 dark:text-green-400"
                          : "text-slate-400 dark:text-slate-600"
                      }
                    >
                      {template.autoInstantiate ? "Yes" : "No"}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {template.items.length}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <ChecklistTemplateActions
                      slug={slug}
                      templateId={template.id}
                      templateName={template.name}
                      source={template.source}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
