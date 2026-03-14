"use client";

import { ChevronDown, ChevronUp } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TemplateCategory, TemplateEntityType } from "@/lib/types";

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

interface TemplateEditorSettingsProps {
  settingsOpen: boolean;
  onSettingsToggle: () => void;
  name: string;
  onNameChange: (value: string) => void;
  description: string;
  onDescriptionChange: (value: string) => void;
  category: TemplateCategory;
  entityType: TemplateEntityType;
  readOnly: boolean;
  isDocx: boolean;
  // Advanced (Tiptap only)
  advancedOpen: boolean;
  onAdvancedToggle: () => void;
  css: string;
  onCssChange: (value: string) => void;
}

export function TemplateEditorSettings({
  settingsOpen,
  onSettingsToggle,
  name,
  onNameChange,
  description,
  onDescriptionChange,
  category,
  entityType,
  readOnly,
  isDocx,
  advancedOpen,
  onAdvancedToggle,
  css,
  onCssChange,
}: TemplateEditorSettingsProps) {
  return (
    <div className="mt-4">
      <button
        type="button"
        onClick={onSettingsToggle}
        className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
        data-testid="settings-toggle"
      >
        {settingsOpen ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
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
                onChange={(e) => onNameChange(e.target.value)}
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
                    <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>
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
                    <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="settings-description">Description</Label>
              <Textarea
                id="settings-description"
                value={description}
                onChange={(e) => onDescriptionChange(e.target.value)}
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
                onClick={onAdvancedToggle}
                className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
              >
                {advancedOpen ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
                Advanced
              </button>

              {advancedOpen && (
                <div className="mt-3 space-y-2">
                  <Label htmlFor="settings-css">Custom CSS</Label>
                  <Textarea
                    id="settings-css"
                    value={css}
                    onChange={(e) => onCssChange(e.target.value)}
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
  );
}
