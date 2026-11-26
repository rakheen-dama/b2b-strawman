"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { FileUp, LayoutTemplate, Plus } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { EmptyState } from "@/components/empty-state";
import { HelpTip } from "@/components/help-tip";
import { SettingsSection } from "@/components/settings/settings-section";
import { createMessages } from "@/lib/messages";
import { UploadDocxDialog } from "./UploadDocxDialog";
import { BrandingSection } from "./branding-section";
import { TemplatesTable } from "./templates-table";
import { uploadLogoAction, deleteLogoAction, saveBrandingAction } from "./template-support-actions";
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
  PROJECT_SUMMARY: "Matter Summary",
  NDA: "NDA",
  REPORT: "Report",
  COMPLIANCE: "Compliance",
  CLOSURE: "Closure",
  OTHER: "Other",
};

function categoryLabel(category: string) {
  return CATEGORY_LABELS[category as TemplateCategory] ?? category;
}

interface TemplatesContentProps {
  slug: string;
  templates: TemplateListResponse[];
  settings: OrgSettings | null;
  canManage: boolean;
}

export function TemplatesContent({ slug, templates, settings, canManage }: TemplatesContentProps) {
  const [formatFilter, setFormatFilter] = useState<TemplateFormat | "ALL">("ALL");
  const { t } = createMessages("empty-states");

  // Filter by format, then group by category. Memoised: each group is a DataTable's `data`, and a
  // new array identity on every render would reset the table (define-columns.tsx).
  const { filteredCount, grouped, categories } = useMemo(() => {
    const filtered =
      formatFilter === "ALL" ? templates : templates.filter((t) => t.format === formatFilter);
    const groups = filtered.reduce<Record<string, TemplateListResponse[]>>((acc, t) => {
      const key = t.category;
      if (!acc[key]) acc[key] = [];
      acc[key].push(t);
      return acc;
    }, {});
    // K-117: sections in display-label order, not key order.
    const keys = Object.keys(groups).sort((a, b) =>
      categoryLabel(a).localeCompare(categoryLabel(b))
    );
    return { filteredCount: filtered.length, grouped: groups, categories: keys };
  }, [templates, formatFilter]);

  return (
    <div className="space-y-8">
      {/* flex-wrap: at 1024px the filter and both buttons are wider than the content column, so the
          row wraps instead of spilling and clipping "New Template" (G-D15). */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
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
          <HelpTip code="templates.packs" />
        </div>
        {canManage && (
          <div className="flex flex-wrap gap-2">
            <UploadDocxDialog slug={slug}>
              <Button size="sm" variant="soft">
                <FileUp className="mr-1 size-4" />
                Upload Word Template
              </Button>
            </UploadDocxDialog>
            <Link href={`/org/${slug}/settings/documents/templates/new`}>
              <Button size="sm">
                <Plus className="mr-1 size-4" />
                New Template
              </Button>
            </Link>
          </div>
        )}
      </div>

      {filteredCount === 0 ? (
        formatFilter !== "ALL" ? (
          <p className="text-muted-foreground py-8 text-center text-sm">
            No templates match the selected format.
          </p>
        ) : (
          <EmptyState
            variant="default"
            icon={LayoutTemplate}
            title={t("templates.list.heading")}
            description={t("templates.list.description")}
            actionLabel={t("templates.list.cta")}
            actionHref={`/org/${slug}/settings/documents/templates/new`}
          />
        )
      ) : (
        categories.map((cat) => (
          <SettingsSection key={cat} title={categoryLabel(cat)}>
            <TemplatesTable slug={slug} templates={grouped[cat]} canManage={canManage} />
          </SettingsSection>
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
