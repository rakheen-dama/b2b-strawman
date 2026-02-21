import Link from "next/link";
import { ChevronLeft, Pencil } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { getChecklistTemplateDetail } from "../queries";
import type { ChecklistTemplateResponse } from "@/lib/types";

export default async function ChecklistTemplateDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let template: ChecklistTemplateResponse | null = null;
  let notFound = false;

  try {
    template = await getChecklistTemplateDetail(id);
  } catch (error) {
    const err = error as { status?: number };
    if (err?.status === 404) {
      notFound = true;
    }
  }

  if (notFound || !template) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/checklists`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Checklists
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Template Not Found
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          The checklist template you are looking for does not exist or has been removed.
        </p>
      </div>
    );
  }

  const sortedItems = [...template.items].sort(
    (a, b) => a.sortOrder - b.sortOrder,
  );

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/checklists`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Checklists
      </Link>

      <div className="flex items-start justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            {template.name}
          </h1>
          {template.description && (
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              {template.description}
            </p>
          )}
        </div>

        {isAdmin && template.source === "ORG_CUSTOM" && (
          <Link href={`/org/${slug}/settings/checklists/${id}/edit`}>
            <Button size="sm">
              <Pencil className="mr-1.5 size-4" />
              Edit
            </Button>
          </Link>
        )}
      </div>

      {/* Metadata badges */}
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={template.source === "ORG_CUSTOM" ? "success" : "neutral"}>
          {template.source === "PLATFORM" ? "Platform" : "Custom"}
        </Badge>
        <Badge variant="neutral">{template.customerType}</Badge>
        <Badge variant={template.active ? "success" : "destructive"}>
          {template.active ? "Active" : "Inactive"}
        </Badge>
        {template.autoInstantiate && (
          <Badge variant="neutral">Auto-instantiate</Badge>
        )}
        {template.packId && (
          <Badge variant="neutral">Pack: {template.packId}</Badge>
        )}
      </div>

      {template.source === "PLATFORM" && (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          This is a platform template and cannot be edited. Clone it to create a customizable copy.
        </p>
      )}

      {/* Items table */}
      <Card>
        <CardHeader>
          <CardTitle>
            Checklist Items ({sortedItems.length})
          </CardTitle>
        </CardHeader>
        <CardContent>
          {sortedItems.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
              No items in this template.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-800">
                    <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                      #
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                      Name
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                      Description
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                      Required
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                      Document Required
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {sortedItems.map((item, index) => (
                    <tr
                      key={item.id}
                      className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                    >
                      <td className="px-3 py-2 text-sm text-slate-500 dark:text-slate-400">
                        {index + 1}
                      </td>
                      <td className="px-3 py-2 text-sm font-medium text-slate-900 dark:text-slate-100">
                        {item.name}
                      </td>
                      <td className="px-3 py-2 text-sm text-slate-600 dark:text-slate-400">
                        {item.description || "\u2014"}
                      </td>
                      <td className="px-3 py-2">
                        {item.required ? (
                          <Badge variant="destructive">Required</Badge>
                        ) : (
                          <span className="text-sm text-slate-400">Optional</span>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        {item.requiresDocument ? (
                          <Badge variant="neutral">
                            {item.requiredDocumentLabel || "Yes"}
                          </Badge>
                        ) : (
                          <span className="text-sm text-slate-400">No</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
