"use client";

import { useCallback, useRef, useState } from "react";
import { FileText, Upload, Copy, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { formatFileSize } from "@/lib/format";
import { FieldDiscoveryResults } from "@/app/(app)/org/[slug]/settings/templates/FieldDiscoveryResults";
import type { TemplateDetailResponse } from "@/lib/types";
import type { VariableMetadataResponse } from "@/components/editor/actions";

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

// --- File Info Panel ---

export function DocxFileInfoPanel({ template }: { template: TemplateDetailResponse }) {
  return (
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
          <dt className="text-xs text-slate-500 dark:text-slate-400">File Name</dt>
          <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
            {template.docxFileName ?? "N/A"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500 dark:text-slate-400">File Size</dt>
          <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
            {template.docxFileSize != null ? formatFileSize(template.docxFileSize) : "N/A"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500 dark:text-slate-400">Uploaded</dt>
          <dd className="text-sm font-medium text-slate-950 dark:text-slate-50">
            {new Date(template.createdAt).toLocaleDateString()}
          </dd>
        </div>
      </dl>
    </div>
  );
}

// --- Variable Reference Panel ---

interface VariableReferencePanelProps {
  variableMetadata: VariableMetadataResponse;
}

export function VariableReferencePanel({ variableMetadata }: VariableReferencePanelProps) {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  function handleCopyVariable(key: string) {
    navigator.clipboard.writeText(`{{${key}}}`).then(() => {
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 2000);
    }).catch(() => { /* clipboard access denied */ });
  }

  return (
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
  );
}

// --- Discovered Fields ---

export function DocxDiscoveredFields({ template }: { template: TemplateDetailResponse }) {
  if (!template.discoveredFields) return null;
  return <FieldDiscoveryResults fields={template.discoveredFields} />;
}

// --- Replace File Dialog ---

interface ReplaceFileDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onReplace: (file: File) => Promise<void>;
  isReplacing: boolean;
  replaceError: string | null;
}

export function ReplaceFileDialog({
  open,
  onOpenChange,
  onReplace,
  isReplacing,
  replaceError,
}: ReplaceFileDialogProps) {
  const [replaceFile, setReplaceFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileSelected = useCallback((f: File) => {
    const validationError = validateDocxFile(f);
    if (validationError) {
      setFileError(validationError);
      setReplaceFile(null);
    } else {
      setFileError(null);
      setReplaceFile(f);
    }
  }, []);

  function handleOpenChangeInternal(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      setReplaceFile(null);
      setFileError(null);
      setIsDragOver(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChangeInternal}>
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
              onClick={() => inputRef.current?.click()}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  inputRef.current?.click();
                }
              }}
              onDragEnter={(e) => { e.preventDefault(); e.stopPropagation(); setIsDragOver(true); }}
              onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); setIsDragOver(true); }}
              onDragLeave={(e) => { e.preventDefault(); e.stopPropagation(); setIsDragOver(false); }}
              onDrop={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setIsDragOver(false);
                const files = Array.from(e.dataTransfer.files);
                if (files.length > 0) handleFileSelected(files[0]);
              }}
              className={cn(
                "mt-1 cursor-pointer rounded-lg border-2 border-dashed p-6 text-center transition-colors",
                isDragOver
                  ? "border-primary bg-primary/5"
                  : "border-slate-300 hover:border-slate-400 dark:border-slate-600 dark:hover:border-slate-500",
                isReplacing && "cursor-not-allowed opacity-50",
              )}
            >
              <input
                ref={inputRef}
                type="file"
                accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={(e) => {
                  const files = Array.from(e.target.files ?? []);
                  if (files.length > 0) handleFileSelected(files[0]);
                  e.target.value = "";
                }}
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
            {fileError && (
              <p className="mt-1 text-sm text-destructive">{fileError}</p>
            )}
          </div>

          {replaceError && (
            <p className="text-sm text-destructive">{replaceError}</p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="soft"
            onClick={() => handleOpenChangeInternal(false)}
            disabled={isReplacing}
          >
            Cancel
          </Button>
          <Button
            onClick={() => replaceFile && onReplace(replaceFile)}
            disabled={isReplacing || !replaceFile}
          >
            {isReplacing ? "Replacing..." : "Replace File"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
