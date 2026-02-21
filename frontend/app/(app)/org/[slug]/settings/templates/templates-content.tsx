"use client";

import Link from "next/link";
import { Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TemplateActionsMenu } from "@/components/templates/TemplateActionsMenu";
import { BrandingSection } from "./branding-section";
import {
  uploadLogoAction,
  deleteLogoAction,
  saveBrandingAction,
} from "./actions";
import type {
  TemplateListResponse,
  TemplateCategory,
  OrgSettings,
} from "@/lib/types";

const CATEGORY_LABELS: Record<TemplateCategory, string> = {
  ENGAGEMENT_LETTER: "Engagement Letter",
  STATEMENT_OF_WORK: "Statement of Work",
  COVER_LETTER: "Cover Letter",
  PROJECT_SUMMARY: "Project Summary",
  NDA: "NDA",
};

interface TemplatesContentProps {
  slug: string;
  templates: TemplateListResponse[];
  settings: OrgSettings | null;
  canManage: boolean;
}

export function TemplatesContent({
  slug,
  templates,
  settings,
  canManage,
}: TemplatesContentProps) {
  // Group templates by category
  const grouped = templates.reduce<
    Record<string, TemplateListResponse[]>
  >((acc, t) => {
    const key = t.category;
    if (!acc[key]) acc[key] = [];
    acc[key].push(t);
    return acc;
  }, {});

  const categories = Object.keys(grouped).sort();

  return (
    <div className="space-y-8">
      {canManage && (
        <div className="flex justify-end">
          <Link href={`/org/${slug}/settings/templates/new`}>
            <Button size="sm">
              <Plus className="mr-1 size-4" />
              New Template
            </Button>
          </Link>
        </div>
      )}

      {templates.length === 0 ? (
        <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          No templates found. Create one or wait for platform templates to be
          seeded.
        </p>
      ) : (
        categories.map((cat) => (
          <div key={cat} className="space-y-3">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
              {CATEGORY_LABELS[cat as TemplateCategory] ?? cat}
            </h2>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Entity Type</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Status</TableHead>
                  {canManage && (
                    <TableHead className="w-12">Actions</TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {grouped[cat].map((template) => (
                  <TableRow key={template.id}>
                    <TableCell>
                      <Link
                        href={`/org/${slug}/settings/templates/${template.id}/edit`}
                        className="font-medium text-slate-950 hover:text-teal-600 hover:underline dark:text-slate-50 dark:hover:text-teal-400"
                      >
                        {template.name}
                      </Link>
                      {template.description && (
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {template.description}
                        </p>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant="neutral">
                        {template.primaryEntityType}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {template.source === "PLATFORM" ? (
                        <Badge variant="pro">Platform</Badge>
                      ) : (
                        <Badge variant="lead">Custom</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      {template.active ? (
                        <Badge variant="success">Active</Badge>
                      ) : (
                        <Badge variant="neutral">Inactive</Badge>
                      )}
                    </TableCell>
                    {canManage && (
                      <TableCell>
                        <TemplateActionsMenu
                          slug={slug}
                          template={template}
                        />
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ))
      )}

      {canManage && settings && (
        <BrandingSection
          settings={settings}
          onUploadLogo={async (file: File) => {
            const formData = new FormData();
            formData.append("file", file);
            return uploadLogoAction(slug, formData);
          }}
          onDeleteLogo={async () => deleteLogoAction(slug)}
          onSaveBranding={async (brandColor: string, footerText: string) =>
            saveBrandingAction(slug, brandColor, footerText)
          }
        />
      )}
    </div>
  );
}
