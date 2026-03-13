"use client";

import { useState, useCallback, useTransition, useEffect, useRef } from "react";
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
  Upload,
  Copy,
  Check,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { HelpTip } from "@/components/help-tip";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import {
  updateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/templates/template-crud-actions";
import {
  fetchRequiredFieldPacksAction,
  replaceDocxFileAction,
  downloadDocxTemplateAction,
  fetchVariableMetadataAction,
} from "@/app/(app)/org/[slug]/settings/templates/template-support-actions";
import type { FieldPackStatus } from "@/app/(app)/org/[slug]/settings/templates/template-support-actions";
import type { VariableMetadataResponse } from "@/components/editor/actions";
import { getClause } from "@/lib/actions/clause-actions";
import { FieldDiscoveryResults } from "@/app/(app)/org/[slug]/settings/templates/FieldDiscoveryResults";
import { formatFileSize } from "@/lib/format";
import type {
  TemplateDetailResponse,
  TemplateCategory,
  TemplateEntityType,
} from "@/lib/types";

const DOCX_MIME_TYPE =
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const MAX_DOCX_SIZE = 10 * 1024 * 1024; // 10MB
const MAX_DOCX_SIZE_LABEL = "10 MB";

function validateDocxFile(f: File): string | null {
  if (f.type !== DOCX_MIME_TYPE && !f.name.toLowerCase().endsWith(".docx")) {
    return "Only .docx files are accepted.";
  }
  if (f.size > MAX_DOCX_SIZE) {
    return `File size exceeds ${MAX_DOCX_SIZE_LABEL}.`;
  }
  return null;
}

const CATEGORIES: { value: TemplateCategory; label: string }[] = [
  { value: "ENGAGEMENT_LETTER", label: "Engagement Letter" },
  { value: "STATEMENT_OF_WORK", label: "Statement of Work" },
  { value: "COVER_LETTER", label: "Cover Letter" },
  { value: "PROJECT_SUMMARY", label: "Project Summary" },
  { value: "NDA", label: "NDA" },
];

const ENTITY_TYPES: { value: TemplateEntityType; label: string }[] = [
  { value: "PROJECT", label: "Project" },
  { value: "CUSTOMER", label: "Customer" },
  { value: "INVOICE", label: "Invoice" },
];

interface TemplateEditorClientProps {
  slug: string;
  template: TemplateDetailResponse;
  readOnly: boolean;
}

export function TemplateEditorClient({
  slug,
  template,
  readOnly,
}: TemplateEditorClientProps) {
  const router = useRouter();
  const isDocx = template.format === "DOCX";

  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(template.description ?? "");
  const category = template.category;
  const entityType = template.primaryEntityType;
  const [css, setCss] = useState(template.css ?? "");
  const [editorContent, setEditorContent] = useState<Record<string, unknown>>(
    template.content,
  );
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [legacyExpanded, setLegacyExpanded] = useState(false);
  const [entityPickerOpen, setEntityPickerOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [missingVariables, setMissingVariables] = useState<Set<string>>(
    new Set(),
  );
  const [previewLoading, startPreviewTransition] = useTransition();
  const [fieldPacks, setFieldPacks] = useState<FieldPackStatus[]>([]);

  // DOCX-specific state
  const [replaceDialogOpen, setReplaceDialogOpen] = useState(false);
  const [replaceFile, setReplaceFile] = useState<File | null>(null);
  const [replaceFileError, setReplaceFileError] = useState<string | null>(null);
  const [isReplacing, setIsReplacing] = useState(false);
  const [replaceError, setReplaceError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const replaceInputRef = useRef<HTMLInputElement>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [variableMetadata, setVariableMetadata] =
    useState<VariableMetadataResponse | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  useEffect(() => {
    fetchRequiredFieldPacksAction(template.id).then((result) => {
      if (result.success && result.data) {
        setFieldPacks(result.data);
      }
    });
  }, [template.id]);

  // Fetch variable metadata for DOCX templates
  useEffect(() => {
    if (isDocx) {
      fetchVariableMetadataAction(template.primaryEntityType).then(
        (data) => setVariableMetadata(data),
      ).catch(() => setVariableMetadata(null));
    }
  }, [isDocx, template.primaryEntityType]);

  const handleEditorUpdate = useCallback(
    (json: Record<string, unknown>) => {
      setEditorContent(json);
      setMissingVariables(new Set());
    },
    [],
  );

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
        if (clause?.body) {
          clausesMap.set(clauseIds[i], clause.body as unknown as TiptapNode);
        }
      }

      // Identify variables that resolve to empty with the selected entity
      // (walks clause bodies too so clause-embedded variables are flagged)
      const missing = findMissingVariables(doc, context, clausesMap);
      setMissingVariables(missing);

      const html = renderTiptapToHtml(doc, context, clausesMap, css || undefined);
      setPreviewHtml(html);
      setPreviewOpen(true);
    });
  }

  // DOCX-specific handlers
  const handleReplaceFileSelected = useCallback((f: File) => {
    const validationError = validateDocxFile(f);
    if (validationError) {
      setReplaceFileError(validationError);
      setReplaceFile(null);
    } else {
      setReplaceFileError(null);
      setReplaceFile(f);
    }
  }, []);

  const handleReplaceDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleReplaceDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleReplaceDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleReplaceDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) {
      handleReplaceFileSelected(files[0]);
    }
  }, [handleReplaceFileSelected]);

  const handleReplaceInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files ?? []);
      if (files.length > 0) {
        handleReplaceFileSelected(files[0]);
      }
      e.target.value = "";
    },
    [handleReplaceFileSelected],
  );

  async function handleReplaceSubmit() {
    if (!replaceFile) return;
    setIsReplacing(true);
    setReplaceError(null);

    try {
      const formData = new FormData();
      formData.append("file", replaceFile);
      const result = await replaceDocxFileAction(slug, template.id, formData);

      if (result.success) {
        setReplaceDialogOpen(false);
        setReplaceFile(null);
        setReplaceFileError(null);
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

  function handleCopyVariable(key: string) {
    navigator.clipboard.writeText(`{{${key}}}`).then(() => {
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 2000);
    }).catch(() => { /* clipboard access denied — silently ignore */ });
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
            <h1 className="flex items-center gap-2 font-display text-lg text-slate-950 dark:text-slate-50">
              {name}
              <HelpTip code="templates.variables" />
            </h1>
          ) : (
            <div className="flex items-center gap-2">
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="h-8 w-64 font-display text-lg"
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
          {successMsg && (
            <span className="text-sm text-teal-600">{successMsg}</span>
          )}
          {error && <span className="text-sm text-destructive">{error}</span>}
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
          <div className="flex items-center gap-2 mb-3">
            <Package className="size-4 text-slate-500 dark:text-slate-400" />
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Required Field Packs
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {fieldPacks.map((pack) => (
              <div key={pack.packId} className="flex flex-col gap-1">
                <Badge
                  variant={pack.applied ? "success" : "warning"}
                  className="gap-1.5"
                >
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
                    <span className="font-medium">{pack.packId}</span> which
                    hasn&apos;t been applied to your organisation.
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
          {/* File Info Panel */}
          <div
            className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900"
            data-testid="docx-file-info"
          >
            <div className="flex items-center gap-2 mb-3">
              <FileText className="size-4 text-slate-500 dark:text-slate-400" />
              <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                File Information
              </span>
            </div>
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <dt className="text-xs text-slate-500 dark:text-slate-400">
                  File Name
                </dt>
                <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
                  {template.docxFileName ?? "N/A"}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500 dark:text-slate-400">
                  File Size
                </dt>
                <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
                  {template.docxFileSize != null
                    ? formatFileSize(template.docxFileSize)
                    : "N/A"}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500 dark:text-slate-400">
                  Uploaded
                </dt>
                <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
                  {new Date(template.createdAt).toLocaleDateString()}
                </dd>
              </div>
            </dl>
          </div>

          {/* Discovered Fields */}
          {template.discoveredFields && (
            <FieldDiscoveryResults fields={template.discoveredFields} />
          )}

          {/* Variable Reference Panel */}
          {variableMetadata && variableMetadata.groups.length > 0 && (
            <div
              className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900"
              data-testid="variable-reference-panel"
            >
              <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50 mb-3">
                Available Variables
              </h3>
              <div className="space-y-4">
                {variableMetadata.groups.map((group) => (
                  <div key={group.prefix}>
                    <h4 className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      {group.label}
                    </h4>
                    <ul className="space-y-1">
                      {group.variables.map((variable) => (
                        <li
                          key={variable.key}
                          className="flex items-center justify-between rounded px-2 py-1 hover:bg-slate-100 dark:hover:bg-slate-800"
                        >
                          <div className="min-w-0 flex-1">
                            <span className="font-mono text-xs text-teal-700 dark:text-teal-300">
                              {"{{"}
                              {variable.key}
                              {"}}"}
                            </span>
                            <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                              {variable.label}
                            </span>
                          </div>
                          <button
                            type="button"
                            onClick={() => handleCopyVariable(variable.key)}
                            className="ml-2 shrink-0 rounded p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                            title="Copy variable"
                          >
                            {copiedKey === variable.key ? (
                              <Check className="size-3 text-teal-600" />
                            ) : (
                              <Copy className="size-3" />
                            )}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            </div>
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
                    This template was migrated from legacy HTML content. The
                    original HTML is preserved below for reference.
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
      <div className="mt-4">
        <button
          type="button"
          onClick={() => setSettingsOpen(!settingsOpen)}
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
          data-testid="settings-toggle"
        >
          {settingsOpen ? (
            <ChevronUp className="size-4" />
          ) : (
            <ChevronDown className="size-4" />
          )}
          Settings
        </button>

        {settingsOpen && (
          <div className="mt-3 space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="settings-name">Name</Label>
                <Input
                  id="settings-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Template name"
                  disabled={readOnly}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="settings-category">Category</Label>
                <Select value={category} disabled>
                  <SelectTrigger id="settings-category">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="settings-entity-type">Entity Type</Label>
                <Select value={entityType} disabled>
                  <SelectTrigger id="settings-entity-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ENTITY_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="settings-description">Description</Label>
                <Textarea
                  id="settings-description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Brief description of this template"
                  rows={2}
                  disabled={readOnly}
                />
              </div>
            </div>

            {/* Advanced section — only for Tiptap templates */}
            {!isDocx && (
              <div>
                <button
                  type="button"
                  onClick={() => setAdvancedOpen(!advancedOpen)}
                  className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
                >
                  {advancedOpen ? (
                    <ChevronUp className="size-4" />
                  ) : (
                    <ChevronDown className="size-4" />
                  )}
                  Advanced
                </button>

                {advancedOpen && (
                  <div className="mt-3 space-y-2">
                    <Label htmlFor="settings-css">Custom CSS</Label>
                    <Textarea
                      id="settings-css"
                      value={css}
                      onChange={(e) => setCss(e.target.value)}
                      placeholder="/* Custom styles */"
                      rows={8}
                      className="font-mono text-sm"
                      disabled={readOnly}
                    />
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Tiptap-only sections */}
      {!isDocx && (
        <>
          {/* Missing variables indicator */}
          {missingVariables.size > 0 && (
            <div className="mt-4 flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 dark:border-amber-800 dark:bg-amber-950">
              <div className="flex items-center gap-2">
                <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
                <span className="text-sm text-amber-800 dark:text-amber-200">
                  {missingVariables.size} variable{missingVariables.size !== 1 ? "s" : ""}{" "}
                  {missingVariables.size !== 1 ? "have" : "has"} no value for the
                  selected entity
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

          {/* Document Editor */}
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

          {/* Client-side preview entity picker */}
          <EntityPicker
            entityType={template.primaryEntityType}
            open={entityPickerOpen}
            onOpenChange={setEntityPickerOpen}
            onSelect={handleEntitySelect}
          />

          {/* Client-side preview dialog */}
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
        <Dialog
          open={replaceDialogOpen}
          onOpenChange={(open) => {
            setReplaceDialogOpen(open);
            if (!open) {
              setReplaceFile(null);
              setReplaceFileError(null);
              setReplaceError(null);
              setIsDragOver(false);
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Replace Template File</DialogTitle>
              <DialogDescription>
                This will update the template file. Existing generated documents
                are not affected.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4">
              <div>
                <Label>File</Label>
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => replaceInputRef.current?.click()}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      replaceInputRef.current?.click();
                    }
                  }}
                  onDragEnter={handleReplaceDragEnter}
                  onDragOver={handleReplaceDragOver}
                  onDragLeave={handleReplaceDragLeave}
                  onDrop={handleReplaceDrop}
                  className={cn(
                    "mt-1 cursor-pointer rounded-lg border-2 border-dashed p-6 text-center transition-colors",
                    isDragOver
                      ? "border-primary bg-primary/5"
                      : "border-slate-300 hover:border-slate-400 dark:border-slate-600 dark:hover:border-slate-500",
                    isReplacing && "cursor-not-allowed opacity-50",
                  )}
                >
                  <input
                    ref={replaceInputRef}
                    type="file"
                    accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    onChange={handleReplaceInputChange}
                    className="hidden"
                    disabled={isReplacing}
                    data-testid="replace-file-input"
                  />
                  {replaceFile ? (
                    <div>
                      <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                        {replaceFile.name}
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {formatFileSize(replaceFile.size)}
                      </p>
                    </div>
                  ) : (
                    <>
                      <Upload className="mx-auto size-8 text-slate-400" />
                      <p className="mt-2 text-sm font-medium">
                        Drag and drop a .docx file, or click to browse
                      </p>
                      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                        Max {MAX_DOCX_SIZE_LABEL}
                      </p>
                    </>
                  )}
                </div>
                {replaceFileError && (
                  <p className="mt-1 text-sm text-destructive">
                    {replaceFileError}
                  </p>
                )}
              </div>

              {replaceError && (
                <p className="text-sm text-destructive">{replaceError}</p>
              )}
            </div>

            <DialogFooter>
              <Button
                variant="soft"
                onClick={() => setReplaceDialogOpen(false)}
                disabled={isReplacing}
              >
                Cancel
              </Button>
              <Button
                onClick={handleReplaceSubmit}
                disabled={isReplacing || !replaceFile}
              >
                {isReplacing ? "Replacing..." : "Replace File"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
