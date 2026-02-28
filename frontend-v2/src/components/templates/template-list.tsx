"use client";

import Link from "next/link";
import { FileText, Plus, Copy, RotateCcw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import type { TemplateListResponse, OrgSettings } from "@/lib/types";

interface TemplateListProps {
  slug: string;
  templates: TemplateListResponse[];
  settings: OrgSettings | null;
  canManage: boolean;
}

export function TemplateList({
  slug,
  templates,
  settings,
  canManage,
}: TemplateListProps) {
  if (templates.length === 0) {
    return (
      <EmptyState
        icon={FileText}
        title="No templates"
        description="Create your first document template to get started."
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Branding Summary */}
      {settings && (
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-slate-700">
                Brand Color:
              </span>
              <div
                className="size-5 rounded border border-slate-200"
                style={{
                  backgroundColor: settings.brandColor ?? "#0d9488",
                }}
              />
            </div>
            {settings.documentFooterText && (
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-slate-700">
                  Footer:
                </span>
                <span className="truncate text-sm text-slate-500">
                  {settings.documentFooterText}
                </span>
              </div>
            )}
            <Link
              href={`/org/${slug}/settings/branding`}
              className="ml-auto text-sm font-medium text-teal-600 hover:text-teal-500"
            >
              Edit branding
            </Link>
          </div>
        </div>
      )}

      {/* Template Cards */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {templates.map((template) => (
          <Link
            key={template.id}
            href={`/org/${slug}/settings/templates/${template.id}`}
            className="group flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition-all hover:border-slate-300 hover:shadow-md"
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2">
                <FileText className="size-4 text-slate-400" />
                <h3 className="font-medium text-slate-900">
                  {template.name}
                </h3>
              </div>
              <div className="flex gap-1">
                {template.source === "PLATFORM" && (
                  <Badge variant="neutral">System</Badge>
                )}
                <Badge variant="neutral">{template.primaryEntityType}</Badge>
              </div>
            </div>
            <p className="text-sm text-slate-500 line-clamp-2">
              {template.description ?? "No description"}
            </p>
            {canManage && (
              <div className="flex gap-2 pt-1 opacity-0 transition-opacity group-hover:opacity-100">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={(e) => e.preventDefault()}
                >
                  <Copy className="mr-1 size-3.5" />
                  Clone
                </Button>
                {template.source === "PLATFORM" && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={(e) => e.preventDefault()}
                  >
                    <RotateCcw className="mr-1 size-3.5" />
                    Reset
                  </Button>
                )}
              </div>
            )}
          </Link>
        ))}

        {/* Add template card */}
        {canManage && (
          <button className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-slate-200 p-8 text-slate-400 transition-colors hover:border-slate-300 hover:text-slate-500">
            <Plus className="size-6" />
            <span className="text-sm font-medium">Add template</span>
          </button>
        )}
      </div>
    </div>
  );
}
