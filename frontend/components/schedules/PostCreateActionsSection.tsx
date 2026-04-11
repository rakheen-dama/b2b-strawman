"use client";

import { useState, useCallback } from "react";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { PostCreateActions } from "@/lib/api/schedules";

export type { PostCreateActions };

export interface DocumentTemplateOption {
  slug: string;
  name: string;
}

export interface RequestTemplateOption {
  slug: string;
  name: string;
}

interface PostCreateActionsSectionProps {
  documentTemplates: DocumentTemplateOption[];
  requestTemplates: RequestTemplateOption[];
  value: PostCreateActions | null;
  onChange: (value: PostCreateActions | null) => void;
}

export function PostCreateActionsSection({
  documentTemplates,
  requestTemplates,
  value,
  onChange,
}: PostCreateActionsSectionProps) {
  const [generateDocumentEnabled, setGenerateDocumentEnabled] = useState(!!value?.generateDocument);
  const [selectedDocTemplateSlug, setSelectedDocTemplateSlug] = useState(
    value?.generateDocument?.templateSlug ?? documentTemplates[0]?.slug ?? ""
  );
  const [sendInfoRequestEnabled, setSendInfoRequestEnabled] = useState(!!value?.sendInfoRequest);
  const [selectedRequestTemplateSlug, setSelectedRequestTemplateSlug] = useState(
    value?.sendInfoRequest?.requestTemplateSlug ?? requestTemplates[0]?.slug ?? ""
  );
  const [dueDays, setDueDays] = useState(value?.sendInfoRequest?.dueDays ?? 14);

  const emitOnChange = useCallback(
    (overrides: {
      genEnabled?: boolean;
      docSlug?: string;
      infoEnabled?: boolean;
      reqSlug?: string;
      days?: number;
    }) => {
      const genEnabled = overrides.genEnabled ?? generateDocumentEnabled;
      const docSlug = overrides.docSlug ?? selectedDocTemplateSlug;
      const infoEnabled = overrides.infoEnabled ?? sendInfoRequestEnabled;
      const reqSlug = overrides.reqSlug ?? selectedRequestTemplateSlug;
      const days = overrides.days ?? dueDays;

      const actions: PostCreateActions = {};

      if (genEnabled && docSlug) {
        actions.generateDocument = {
          templateSlug: docSlug,
          autoSend: false,
        };
      }

      if (infoEnabled && reqSlug) {
        actions.sendInfoRequest = {
          requestTemplateSlug: reqSlug,
          dueDays: days > 0 ? days : 14,
        };
      }

      const hasActions = Object.keys(actions).length > 0;
      onChange(hasActions ? actions : null);
    },
    [
      generateDocumentEnabled,
      selectedDocTemplateSlug,
      sendInfoRequestEnabled,
      selectedRequestTemplateSlug,
      dueDays,
      onChange,
    ]
  );

  return (
    <div className="space-y-3 border-t border-slate-200 pt-4 dark:border-slate-800">
      <div>
        <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          After Creation (Optional)
        </p>
        <p className="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
          These actions run automatically each time this schedule creates an engagement.
        </p>
      </div>

      {/* Toggle row 1: Generate Document */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Switch
            id="post-create-generate-doc"
            checked={generateDocumentEnabled}
            onCheckedChange={(checked) => {
              setGenerateDocumentEnabled(checked);
              emitOnChange({ genEnabled: checked });
            }}
            size="sm"
          />
          <Label htmlFor="post-create-generate-doc" className="cursor-pointer text-sm">
            Generate document
          </Label>
        </div>

        {generateDocumentEnabled && (
          <div className="ml-8 space-y-2">
            {documentTemplates.length === 0 ? (
              <p className="text-xs text-slate-400">No document templates available.</p>
            ) : (
              <Select
                value={selectedDocTemplateSlug}
                onValueChange={(slug) => {
                  setSelectedDocTemplateSlug(slug);
                  emitOnChange({ docSlug: slug });
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select a document template..." />
                </SelectTrigger>
                <SelectContent>
                  {documentTemplates.map((t) => (
                    <SelectItem key={t.slug} value={t.slug}>
                      {t.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {/* Auto-send -- disabled for v1 */}
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <div className="flex cursor-not-allowed items-center gap-2 opacity-50">
                    <input
                      type="checkbox"
                      id="post-create-auto-send"
                      disabled
                      checked={false}
                      readOnly
                      className="h-3.5 w-3.5"
                    />
                    <label
                      htmlFor="post-create-auto-send"
                      className="cursor-not-allowed text-xs text-slate-500 dark:text-slate-400"
                    >
                      Auto-send
                    </label>
                  </div>
                </TooltipTrigger>
                <TooltipContent>Coming soon</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        )}
      </div>

      {/* Toggle row 2: Send Information Request */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Switch
            id="post-create-send-info-request"
            checked={sendInfoRequestEnabled}
            onCheckedChange={(checked) => {
              setSendInfoRequestEnabled(checked);
              emitOnChange({ infoEnabled: checked });
            }}
            size="sm"
          />
          <Label htmlFor="post-create-send-info-request" className="cursor-pointer text-sm">
            Send information request
          </Label>
        </div>

        {sendInfoRequestEnabled && (
          <div className="ml-8 space-y-2">
            {requestTemplates.length === 0 ? (
              <p className="text-xs text-slate-400">No request templates available.</p>
            ) : (
              <Select
                value={selectedRequestTemplateSlug}
                onValueChange={(slug) => {
                  setSelectedRequestTemplateSlug(slug);
                  emitOnChange({ reqSlug: slug });
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select a request template..." />
                </SelectTrigger>
                <SelectContent>
                  {requestTemplates.map((t) => (
                    <SelectItem key={t.slug} value={t.slug}>
                      {t.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            <div className="flex items-center gap-2">
              <Label
                htmlFor="post-create-due-days"
                className="text-xs whitespace-nowrap text-slate-600 dark:text-slate-400"
              >
                Due in
              </Label>
              <Input
                id="post-create-due-days"
                type="number"
                min={1}
                max={365}
                value={dueDays}
                onChange={(e) => {
                  const newDays = Math.max(1, parseInt(e.target.value) || 14);
                  setDueDays(newDays);
                  emitOnChange({ days: newDays });
                }}
                className="w-20"
              />
              <span className="text-xs text-slate-500 dark:text-slate-400">days</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
