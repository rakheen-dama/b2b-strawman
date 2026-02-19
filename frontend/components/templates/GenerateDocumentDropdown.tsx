"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
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
  const searchParams = useSearchParams();
  const [selectedTemplate, setSelectedTemplate] =
    useState<TemplateListResponse | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  // Auto-open dialog when ?generateTemplate=<id> is in the URL
  const generateTemplateId = searchParams.get("generateTemplate");
  useEffect(() => {
    if (!generateTemplateId) return;
    const match = templates.find((t) => t.id === generateTemplateId);
    if (match) {
      setSelectedTemplate(match);
      setDialogOpen(true);
    }
  }, [generateTemplateId, templates]);

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
                // Dropdown focus cleanup takes longer than one rAF —
                // wait for Radix to fully unmount before opening the dialog
                setTimeout(() => setDialogOpen(true), 150);
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
