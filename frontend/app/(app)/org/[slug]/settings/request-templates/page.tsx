import Link from "next/link";
import { ChevronLeft, ClipboardList, Plus } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Button } from "@/components/ui/button";
import { TemplateSourceBadge } from "@/components/information-requests/template-source-badge";
import { RequestTemplateActions } from "@/components/information-requests/request-template-actions";
import { listRequestTemplates, type RequestTemplateResponse } from "@/lib/api/information-requests";

export default async function RequestTemplatesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

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
          Request Templates
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage request templates. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  let templates: RequestTemplateResponse[] = [];

  try {
    templates = await listRequestTemplates();
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
          Request Templates
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create and manage reusable information request templates.
        </p>
      </div>

      <div className="flex justify-end">
        <Link href={`/org/${slug}/settings/request-templates/new`}>
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            New Template
          </Button>
        </Link>
      </div>

      {templates.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <ClipboardList className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="font-display mt-4 text-lg text-slate-900 dark:text-slate-100">
            No request templates yet
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Create your first request template to streamline information gathering.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Source
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Items
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Status
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {templates.map((template) => (
                <tr
                  key={template.id}
                  data-testid="request-template-row"
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/settings/request-templates/${template.id}`}
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
                    <TemplateSourceBadge source={template.source} />
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {template.items.length}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        template.active
                          ? "text-sm text-green-600 dark:text-green-400"
                          : "text-sm text-slate-400 dark:text-slate-600"
                      }
                    >
                      {template.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <RequestTemplateActions
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
