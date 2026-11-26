import { FileText } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { OrgDocumentUpload } from "@/components/documents/org-document-upload";
import { OrgDocumentsTable } from "@/components/settings/org-documents-table";
import { SettingsSection } from "@/components/settings/settings-section";
import type { Document } from "@/lib/types";

interface OrgDocumentsSectionProps {
  slug: string;
  documents: Document[];
  isAdmin: boolean;
}

/**
 * Firm › General "Org Documents" (server). The table is a "use client" DataTable wrapper; the
 * empty state renders here (the icon is a component reference, so it never crosses into the
 * client wrapper — only the rendered node does).
 */
export function OrgDocumentsSection({ slug, documents, isAdmin }: OrgDocumentsSectionProps) {
  return (
    <SettingsSection
      data-testid="org-documents-section"
      title="Org Documents"
      description="Organization-level documents shared across all matters."
      actions={
        <div className="flex items-center gap-3">
          <span className="bg-muted text-muted-foreground rounded-full px-2.5 py-0.5 text-sm">
            {documents.length}
          </span>
          {isAdmin && <OrgDocumentUpload slug={slug} />}
        </div>
      }
    >
      <OrgDocumentsTable
        documents={documents}
        empty={
          <EmptyState
            variant="compact"
            icon={FileText}
            title="No organization documents yet"
            description={
              isAdmin
                ? "Upload your first organization-level document to get started."
                : "No organization documents have been uploaded yet."
            }
          />
        }
      />
    </SettingsSection>
  );
}
