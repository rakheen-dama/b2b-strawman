"use client";

import { useState } from "react";
import { FileText, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import type { TemplateListResponse, TemplateEntityType } from "@/lib/types";

interface GenerateDocumentDropdownProps {
  templates: TemplateListResponse[];
  entityId: string;
  entityType: TemplateEntityType;
  onDocumentSaved?: () => void;
}

export function GenerateDocumentDropdown({
  templates,
  entityId,
  entityType,
  onDocumentSaved,
}: GenerateDocumentDropdownProps) {
  const [selectedTemplate, setSelectedTemplate] =
    useState<TemplateListResponse | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  if (templates.length === 0) {
    return (
      <Button variant="outline" size="sm" disabled>
        <FileText className="mr-1.5 size-4" />
        Generate Document
      </Button>
    );
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm">
            <FileText className="mr-1.5 size-4" />
            Generate Document
            <ChevronDown className="ml-1.5 size-3" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {templates.map((tpl) => (
            <DropdownMenuItem
              key={tpl.id}
              onSelect={() => {
                setSelectedTemplate(tpl);
                // Defer dialog open so the dropdown's focus cleanup completes first
                requestAnimationFrame(() => setDialogOpen(true));
              }}
            >
              {tpl.name}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      {selectedTemplate && (
        <GenerateDocumentDialog
          templateId={selectedTemplate.id}
          templateName={selectedTemplate.name}
          entityId={entityId}
          entityType={entityType}
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          onSaved={onDocumentSaved}
        />
      )}
    </>
  );
}
