"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { FileText, FileType, ChevronDown, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";

const GenerateDocumentDialog = dynamic(
  () =>
    import("@/components/templates/GenerateDocumentDialog").then(
      (mod) => mod.GenerateDocumentDialog
    ),
  { loading: () => <Skeleton className="h-96 w-full" />, ssr: false }
);
import { GenerateDocxDialog } from "@/components/templates/GenerateDocxDialog";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type { PrerequisiteViolation } from "@/components/prerequisite/types";
import type { TemplateListResponse, TemplateEntityType, EntityType } from "@/lib/types";

interface GenerateDocumentDropdownProps {
  templates: TemplateListResponse[];
  entityId: string;
  entityType: TemplateEntityType;
  onDocumentSaved?: () => void;
  customerId?: string;
  isAdmin?: boolean;
  slug?: string;
}

export function GenerateDocumentDropdown({
  templates,
  entityId,
  entityType,
  onDocumentSaved,
  customerId,
  isAdmin,
  slug,
}: GenerateDocumentDropdownProps) {
  const searchParams = useSearchParams();
  const [selectedTemplate, setSelectedTemplate] = useState<TemplateListResponse | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  // Prerequisite gate state
  const [checkingPrereqs, setCheckingPrereqs] = useState(false);
  const [prereqModalOpen, setPrereqModalOpen] = useState(false);
  const [prereqViolations, setPrereqViolations] = useState<PrerequisiteViolation[]>([]);

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
          <Button variant="outline" size="sm" disabled={checkingPrereqs}>
            {checkingPrereqs ? (
              <Loader2 className="mr-1.5 size-4 animate-spin" />
            ) : (
              <FileText className="mr-1.5 size-4" />
            )}
            Generate Document
            <ChevronDown className="ml-1.5 size-3" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {templates.map((tpl) => (
            <DropdownMenuItem
              key={tpl.id}
              onSelect={async () => {
                setSelectedTemplate(tpl);
                // Clear stale violations from a prior failed check
                setPrereqViolations([]);
                // Run prerequisite check before opening dialog
                setCheckingPrereqs(true);
                try {
                  const check = await checkPrerequisitesAction(
                    "DOCUMENT_GENERATION",
                    entityType as EntityType,
                    entityId
                  );
                  if (check.passed) {
                    setTimeout(() => setDialogOpen(true), 150);
                  } else {
                    setPrereqViolations(check.violations);
                    setTimeout(() => setPrereqModalOpen(true), 150);
                  }
                } catch {
                  // Fail-open: proceed to dialog if check throws
                  setTimeout(() => setDialogOpen(true), 150);
                } finally {
                  setCheckingPrereqs(false);
                }
              }}
            >
              {tpl.format === "DOCX" ? (
                <FileType className="mr-1.5 size-4 text-blue-600" />
              ) : (
                <FileText className="mr-1.5 size-4" />
              )}
              {tpl.name}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      {selectedTemplate && selectedTemplate.format !== "DOCX" && (
        <GenerateDocumentDialog
          templateId={selectedTemplate.id}
          templateName={selectedTemplate.name}
          entityId={entityId}
          entityType={entityType}
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          onSaved={onDocumentSaved}
          customerId={customerId}
          isAdmin={isAdmin}
        />
      )}

      {selectedTemplate && selectedTemplate.format === "DOCX" && (
        <GenerateDocxDialog
          templateId={selectedTemplate.id}
          templateName={selectedTemplate.name}
          entityId={entityId}
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          onGenerated={onDocumentSaved}
        />
      )}

      {selectedTemplate && slug && prereqModalOpen && (
        <PrerequisiteModal
          open={prereqModalOpen}
          onOpenChange={setPrereqModalOpen}
          context="DOCUMENT_GENERATION"
          violations={prereqViolations}
          entityType={entityType as EntityType}
          entityId={entityId}
          slug={slug}
          onResolved={() => {
            setPrereqModalOpen(false);
            setDialogOpen(true);
          }}
        />
      )}
    </>
  );
}
