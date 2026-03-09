"use client";

import { useState } from "react";
import Link from "next/link";
import { FileText, FileUp, LayoutTemplate, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TemplateActionsMenu } from "@/components/templates/TemplateActionsMenu";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { UploadDocxDialog } from "./UploadDocxDialog";
import { BrandingSection } from "./branding-section";
import {
  uploadLogoAction,
  deleteLogoAction,
  saveBrandingAction,
} from "./actions";
import { formatFileSize } from "@/lib/format";
import type {
  TemplateListResponse,
  TemplateCategory,
  TemplateFormat,
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
  const [formatFilter, setFormatFilter] = useState<TemplateFormat | "ALL">("ALL");
  const { t } = createMessages("empty-states");

  // Filter templates by format
  const filteredTemplates =
    formatFilter === "ALL"
      ? templates
      : templates.filter((t) => t.format === formatFilter);

  // Group templates by category
  const grouped = filteredTemplates.reduce<
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
      <div className="flex items-center justify-between">
        <Select
          value={formatFilter}
          onValueChange={(v) => setFormatFilter(v as TemplateFormat | "ALL")}
        >
          <SelectTrigger className="w-40" data-testid="format-filter">
            <SelectValue placeholder="All Formats" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Formats</SelectItem>
            <SelectItem value="TIPTAP">Tiptap</SelectItem>
            <SelectItem value="DOCX">Word</SelectItem>
          </SelectContent>
        </Select>
        {canManage && (
          <div className="flex gap-2">
            <UploadDocxDialog slug={slug}>
              <Button size="sm" variant="soft">
                <FileUp className="mr-1 size-4" />
                Upload Word Template
              </Button>
            </UploadDocxDialog>
            <Link href={`/org/${slug}/settings/templates/new`}>
              <Button size="sm">
                <Plus className="mr-1 size-4" />
                New Template
              </Button>
            </Link>
          </div>
        )}
      </div>

      {filteredTemplates.length === 0 ? (
        formatFilter !== "ALL" ? (
          <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
            No templates match the selected format.
          </p>
        ) : (
          <EmptyState
            icon={LayoutTemplate}
            title={t("templates.list.heading")}
            description={t("templates.list.description")}
            actionLabel={t("templates.list.cta")}
            actionHref={`/org/${slug}/settings/templates/new`}
          />
        )
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
                  <TableHead>Format</TableHead>
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
                      {template.format === "DOCX" && template.docxFileName && (
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {template.docxFileName}
                          {template.docxFileSize != null &&
                            ` (${formatFileSize(template.docxFileSize)})`}
                        </p>
                      )}
                    </TableCell>
                    <TableCell>
                      {template.format === "DOCX" ? (
                        <Badge variant="success">
                          <FileText className="size-3" />
                          Word
                        </Badge>
                      ) : (
                        <Badge variant="neutral">Tiptap</Badge>
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
