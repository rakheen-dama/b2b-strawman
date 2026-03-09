"use client";

import { useCallback, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { uploadDocxTemplateAction } from "./actions";
import type { TemplateCategory, TemplateEntityType } from "@/lib/types";

const DOCX_MIME_TYPE =
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const MAX_DOCX_SIZE = 10 * 1024 * 1024; // 10MB
const MAX_DOCX_SIZE_LABEL = "10 MB";

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

interface UploadDocxDialogProps {
  slug: string;
  children: React.ReactNode;
}

export function UploadDocxDialog({ slug, children }: UploadDocxDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<TemplateCategory>("ENGAGEMENT_LETTER");
  const [entityType, setEntityType] = useState<TemplateEntityType>("PROJECT");
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function resetForm() {
    setFile(null);
    setFileError(null);
    setName("");
    setDescription("");
    setCategory("ENGAGEMENT_LETTER");
    setEntityType("PROJECT");
    setIsUploading(false);
    setError(null);
    setIsDragOver(false);
  }

  function validateFile(f: File): string | null {
    if (f.type !== DOCX_MIME_TYPE && !f.name.toLowerCase().endsWith(".docx")) {
      return "Only .docx files are accepted.";
    }
    if (f.size > MAX_DOCX_SIZE) {
      return `File size exceeds ${MAX_DOCX_SIZE_LABEL}.`;
    }
    return null;
  }

  function handleFileSelected(f: File) {
    const validationError = validateFile(f);
    if (validationError) {
      setFileError(validationError);
      setFile(null);
    } else {
      setFileError(null);
      setFile(f);
      // Auto-populate name from filename if empty
      if (!name) {
        const baseName = f.name.replace(/\.docx$/i, "");
        setName(baseName);
      }
    }
  }

  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);
      const files = Array.from(e.dataTransfer.files);
      if (files.length > 0) {
        handleFileSelected(files[0]);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [name],
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files ?? []);
      if (files.length > 0) {
        handleFileSelected(files[0]);
      }
      e.target.value = "";
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [name],
  );

  async function handleSubmit() {
    if (!file || !name.trim()) return;

    setIsUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("name", name.trim());
      formData.append("category", category);
      formData.append("entityType", entityType);
      if (description.trim()) {
        formData.append("description", description.trim());
      }

      const result = await uploadDocxTemplateAction(slug, formData);

      if (result.success && result.data) {
        setOpen(false);
        resetForm();
        router.push(
          `/org/${slug}/settings/templates/${result.data.id}/edit`,
        );
      } else {
        setError(result.error ?? "Failed to upload template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(newOpen) => {
        setOpen(newOpen);
        if (!newOpen) resetForm();
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Upload Word Template</DialogTitle>
          <DialogDescription>
            Upload a .docx file with merge fields (e.g.{" "}
            {"{{customer.name}}"}) to create a template.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* File drop zone */}
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
              onDragEnter={handleDragEnter}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              className={cn(
                "mt-1 cursor-pointer rounded-lg border-2 border-dashed p-6 text-center transition-colors",
                isDragOver
                  ? "border-primary bg-primary/5"
                  : "border-slate-300 hover:border-slate-400 dark:border-slate-600 dark:hover:border-slate-500",
                isUploading && "cursor-not-allowed opacity-50",
              )}
            >
              <input
                ref={inputRef}
                type="file"
                accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={handleInputChange}
                className="hidden"
                disabled={isUploading}
                data-testid="docx-file-input"
              />
              {file ? (
                <div>
                  <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    {file.name}
                  </p>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    {(file.size / 1024).toFixed(1)} KB
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

          {/* Name */}
          <div>
            <Label htmlFor="docx-template-name">Name</Label>
            <Input
              id="docx-template-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Template name"
              disabled={isUploading}
              className="mt-1"
            />
          </div>

          {/* Description */}
          <div>
            <Label htmlFor="docx-template-description">Description</Label>
            <Textarea
              id="docx-template-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional description"
              disabled={isUploading}
              className="mt-1"
              rows={2}
            />
          </div>

          {/* Category */}
          <div>
            <Label htmlFor="docx-template-category">Category</Label>
            <select
              id="docx-template-category"
              value={category}
              onChange={(e) => setCategory(e.target.value as TemplateCategory)}
              disabled={isUploading}
              className="mt-1 w-full rounded-md border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600"
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          {/* Entity Type */}
          <div>
            <Label htmlFor="docx-template-entity-type">Entity Type</Label>
            <select
              id="docx-template-entity-type"
              value={entityType}
              onChange={(e) =>
                setEntityType(e.target.value as TemplateEntityType)
              }
              disabled={isUploading}
              className="mt-1 w-full rounded-md border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600"
            >
              {ENTITY_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button
            variant="soft"
            onClick={() => {
              setOpen(false);
              resetForm();
            }}
            disabled={isUploading}
          >
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isUploading || !file || !name.trim()}
          >
            {isUploading ? "Uploading..." : "Upload Template"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
