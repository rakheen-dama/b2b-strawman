"use client";

import { useState, useCallback, useTransition, useEffect } from "react";
// TODO(214B): Re-add requiredContextFields management UI in the settings panel.
// The old TemplateEditorForm had UI for this; intentionally omitted in 214A.
// The save handler preserves existing values so they are not lost.
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ChevronLeft,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
  Eye,
  CheckCircle2,
  Package,
  FileText,
  Download,
  RefreshCw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { HelpTip } from "@/components/help-tip";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { DocumentEditor } from "@/components/editor/DocumentEditor";
import { TemplatePreviewDialog } from "@/components/templates/TemplatePreviewDialog";
import {
  EntityPicker,
  PreviewPanel,
  renderTiptapToHtml,
  buildPreviewContext,
  extractClauseIds,
  findMissingVariables,
} from "@/components/editor";
import type { TiptapNode } from "@/components/editor";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { updateTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/template-crud-actions";
import {
  fetchRequiredFieldPacksAction,
  replaceDocxFileAction,
  downloadDocxTemplateAction,
  fetchVariableMetadataAction,
} from "@/app/(app)/org/[slug]/settings/templates/template-support-actions";
import type { FieldPackStatus } from "@/app/(app)/org/[slug]/settings/templates/template-support-actions";
import type { VariableMetadataResponse } from "@/components/editor/actions";
import { getClause } from "@/lib/actions/clause-actions";
import { TemplateEditorSettings } from "@/components/templates/template-editor-settings";
import {
  DocxFileInfoPanel,
  DocxDiscoveredFields,
  VariableReferencePanel,
  ReplaceFileDialog,
} from "@/components/templates/template-docx-sections";
import type { TemplateDetailResponse } from "@/lib/types";

interface TemplateEditorClientProps {
  slug: string;
  template: TemplateDetailResponse;
  readOnly: boolean;
}

export function TemplateEditorClient({ slug, template, readOnly }: TemplateEditorClientProps) {
  const router = useRouter();
  const isDocx = template.format === "DOCX";

  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(template.description ?? "");
  const [css, setCss] = useState(template.css ?? "");
  const [editorContent, setEditorContent] = useState<Record<string, unknown>>(template.content);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [legacyExpanded, setLegacyExpanded] = useState(false);
  const [entityPickerOpen, setEntityPickerOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [missingVariables, setMissingVariables] = useState<Set<string>>(new Set());
  const [previewLoading, startPreviewTransition] = useTransition();
  const [fieldPacks, setFieldPacks] = useState<FieldPackStatus[]>([]);

  // DOCX-specific state
  const [replaceDialogOpen, setReplaceDialogOpen] = useState(false);
  const [isReplacing, setIsReplacing] = useState(false);
  const [replaceError, setReplaceError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [variableMetadata, setVariableMetadata] = useState<VariableMetadataResponse | null>(null);

  useEffect(() => {
    fetchRequiredFieldPacksAction(template.id).then((result) => {
      if (result.success && result.data) setFieldPacks(result.data);
    });
  }, [template.id]);

  useEffect(() => {
    if (isDocx) {
      fetchVariableMetadataAction(template.primaryEntityType)
        .then((data) => setVariableMetadata(data))
        .catch(() => setVariableMetadata(null));
    }
  }, [isDocx, template.primaryEntityType]);

  const handleEditorUpdate = useCallback((json: Record<string, unknown>) => {
    setEditorContent(json);
    setMissingVariables(new Set());
  }, []);

  async function handleSave() {
    setIsSaving(true);
    setError(null);
    setSuccessMsg(null);
    try {
      const result = await updateTemplateAction(slug, template.id, {
        name,
        description: description || undefined,
        content: isDocx ? template.content : editorContent,
        css: isDocx ? undefined : css || undefined,
        requiredContextFields: template.requiredContextFields,
      });
      if (result.success) {
        setSuccessMsg("Template saved successfully.");
        setTimeout(() => setSuccessMsg(null), 3000);
      } else {
        setError(result.error ?? "Failed to save template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  function handleEntitySelect(entityId: string, entityData: Record<string, unknown>) {
    startPreviewTransition(async () => {
      const context = buildPreviewContext(template.primaryEntityType, entityData);
      const doc = editorContent as unknown as TiptapNode;
      const clauseIds = extractClauseIds(doc);
      const clausesMap = new Map<string, TiptapNode>();
      const clauseResults = await Promise.all(clauseIds.map((id) => getClause(id)));
      for (let i = 0; i < clauseIds.length; i++) {
        const clause = clauseResults[i];
        if (clause?.body) clausesMap.set(clauseIds[i], clause.body as unknown as TiptapNode);
      }
      const missing = findMissingVariables(doc, context, clausesMap);
      setMissingVariables(missing);
      const html = renderTiptapToHtml(doc, context, clausesMap, css || undefined);
      setPreviewHtml(html);
      setPreviewOpen(true);
    });
  }

  async function handleReplaceSubmit(file: File) {
    setIsReplacing(true);
    setReplaceError(null);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const result = await replaceDocxFileAction(slug, template.id, formData);
      if (result.success) {
        setReplaceDialogOpen(false);
        router.refresh();
      } else {
        setReplaceError(result.error ?? "Failed to replace file.");
      }
    } catch {
      setReplaceError("An unexpected error occurred.");
    } finally {
      setIsReplacing(false);
    }
  }

  async function handleDownload() {
    setIsDownloading(true);
    try {
      const result = await downloadDocxTemplateAction(template.id);
      if (result.success && result.url) {
        window.open(result.url, "_blank");
      } else {
        setError(result.error ?? "Failed to download template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDownloading(false);
    }
  }

  const hasLegacyContent = template.legacyContent != null;

  return (
    <div className="flex h-full flex-col">
      {/* Top bar */}
      <div className="flex items-center justify-between border-b border-slate-200 pb-4 dark:border-slate-800">
        <div className="flex items-center gap-4">
          <Link
            href={`/org/${slug}/settings/templates`}
            className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
          >
            <ChevronLeft className="size-4" />
            Templates
          </Link>
          {readOnly ? (
            <h1 className="font-display flex items-center gap-2 text-lg text-slate-950 dark:text-slate-50">
              {name}
              <HelpTip code="templates.variables" />
            </h1>
          ) : (
            <div className="flex items-center gap-2">
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="font-display h-8 w-64 text-lg"
                aria-label="Template name"
              />
              <HelpTip code="templates.variables" />
            </div>
          )}
          {isDocx && (
            <Badge variant="success">
              <FileText className="size-3" />
              Word
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-3">
          {!isDocx && (
            <>
              <TemplatePreviewDialog
                templateId={template.id}
                entityType={template.primaryEntityType}
              />
              <Button
                type="button"
                variant="soft"
                size="sm"
                onClick={() => setEntityPickerOpen(true)}
                disabled={previewLoading}
              >
                <Eye className="mr-1 size-4" />
                {previewLoading ? "Loading..." : "Preview with data"}
              </Button>
            </>
          )}
          {isDocx && !readOnly && (
            <>
              <Button
                type="button"
                variant="soft"
                size="sm"
                onClick={handleDownload}
                disabled={isDownloading}
                data-testid="download-template-btn"
              >
                <Download className="mr-1 size-4" />
                {isDownloading ? "Downloading..." : "Download Template"}
              </Button>
              <Button
                type="button"
                variant="soft"
                size="sm"
                onClick={() => setReplaceDialogOpen(true)}
                data-testid="replace-file-btn"
              >
                <RefreshCw className="mr-1 size-4" />
                Replace File
              </Button>
            </>
          )}
          {successMsg && <span className="text-sm text-teal-600">{successMsg}</span>}
          {error && <span className="text-destructive text-sm">{error}</span>}
          {!readOnly && (
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving ? "Saving..." : "Save"}
            </Button>
          )}
        </div>
      </div>

      {/* Required Field Packs */}
      {fieldPacks.length > 0 && (
        <div
          className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900"
          data-testid="field-pack-status"
        >
          <div className="mb-3 flex items-center gap-2">
            <Package className="size-4 text-slate-500 dark:text-slate-400" />
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Required Field Packs
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {fieldPacks.map((pack) => (
              <div key={pack.packId} className="flex flex-col gap-1">
                <Badge variant={pack.applied ? "success" : "warning"} className="gap-1.5">
                  {pack.applied ? (
                    <CheckCircle2 className="size-3" />
                  ) : (
                    <AlertTriangle className="size-3" />
                  )}
                  {pack.packId}
                </Badge>
                {!pack.applied && (
                  <p className="text-xs text-amber-700 dark:text-amber-300">
                    This template references fields from{" "}
                    <span className="font-medium">{pack.packId}</span> which hasn&apos;t been
                    applied to your organisation.
                  </p>
                )}
                {pack.missingFields.length > 0 && (
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Missing: {pack.missingFields.join(", ")}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* DOCX Detail Variant */}
      {isDocx ? (
        <div className="mt-4 space-y-6">
          <DocxFileInfoPanel template={template} />
          <DocxDiscoveredFields template={template} />
          {variableMetadata && variableMetadata.groups.length > 0 && (
            <VariableReferencePanel variableMetadata={variableMetadata} />
          )}
        </div>
      ) : (
        <>
          {/* Legacy content banner */}
          {hasLegacyContent && (
            <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 size-5 text-amber-600 dark:text-amber-400" />
                <div className="flex-1">
                  <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
                    Migration needed
                  </p>
                  <p className="mt-1 text-sm text-amber-700 dark:text-amber-300">
                    This template was migrated from legacy HTML content. The original HTML is
                    preserved below for reference.
                  </p>
                  <button
                    type="button"
                    onClick={() => setLegacyExpanded(!legacyExpanded)}
                    className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-amber-800 hover:text-amber-900 dark:text-amber-200 dark:hover:text-amber-100"
                  >
                    {legacyExpanded ? "Hide" : "Show"} original HTML
                    {legacyExpanded ? (
                      <ChevronUp className="size-4" />
                    ) : (
                      <ChevronDown className="size-4" />
                    )}
                  </button>
                  {legacyExpanded && (
                    <pre className="mt-2 max-h-64 overflow-auto rounded border border-amber-200 bg-white p-3 font-mono text-xs text-slate-800 dark:border-amber-800 dark:bg-slate-900 dark:text-slate-200">
                      {template.legacyContent}
                    </pre>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* Collapsible settings panel */}
      <TemplateEditorSettings
        settingsOpen={settingsOpen}
        onSettingsToggle={() => setSettingsOpen(!settingsOpen)}
        name={name}
        onNameChange={setName}
        description={description}
        onDescriptionChange={setDescription}
        category={template.category}
        entityType={template.primaryEntityType}
        readOnly={readOnly}
        isDocx={isDocx}
        advancedOpen={advancedOpen}
        onAdvancedToggle={() => setAdvancedOpen(!advancedOpen)}
        css={css}
        onCssChange={setCss}
      />

      {/* Tiptap-only sections */}
      {!isDocx && (
        <>
          {missingVariables.size > 0 && (
            <div className="mt-4 flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 dark:border-amber-800 dark:bg-amber-950">
              <div className="flex items-center gap-2">
                <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
                <span className="text-sm text-amber-800 dark:text-amber-200">
                  {missingVariables.size} variable{missingVariables.size !== 1 ? "s" : ""}{" "}
                  {missingVariables.size !== 1 ? "have" : "has"} no value for the selected entity
                </span>
              </div>
              <button
                type="button"
                onClick={() => setMissingVariables(new Set())}
                className="text-xs text-amber-700 hover:text-amber-900 dark:text-amber-300 dark:hover:text-amber-100"
              >
                Dismiss
              </button>
            </div>
          )}

          <div className="mt-4 flex-1">
            <DocumentEditor
              content={editorContent}
              onUpdate={handleEditorUpdate}
              scope="template"
              editable={!readOnly}
              entityType={template.primaryEntityType}
              missingVariables={missingVariables}
            />
          </div>

          <EntityPicker
            entityType={template.primaryEntityType}
            open={entityPickerOpen}
            onOpenChange={setEntityPickerOpen}
            onSelect={handleEntitySelect}
          />

          <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
            <DialogContent className="max-w-3xl">
              <DialogHeader>
                <DialogTitle>Client-Side Preview</DialogTitle>
              </DialogHeader>
              {previewHtml && <PreviewPanel html={previewHtml} />}
            </DialogContent>
          </Dialog>
        </>
      )}

      {/* Replace File Dialog (DOCX only) */}
      {isDocx && (
        <ReplaceFileDialog
          open={replaceDialogOpen}
          onOpenChange={setReplaceDialogOpen}
          onReplace={handleReplaceSubmit}
          isReplacing={isReplacing}
          replaceError={replaceError}
        />
      )}
    </div>
  );
}
