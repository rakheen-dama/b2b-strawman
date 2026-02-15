import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Document, DocumentScope, DocumentStatus } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { OrgDocumentUpload } from "@/components/documents/org-document-upload";
import { formatDate, formatFileSize } from "@/lib/format";
import { FileText, FileImage, FileSpreadsheet, FileArchive, File } from "lucide-react";

function getFileIcon(contentType: string) {
  if (contentType.startsWith("image/")) return FileImage;
  if (contentType.includes("pdf")) return FileText;
  if (
    contentType.includes("spreadsheet") ||
    contentType.includes("excel") ||
    contentType.includes("csv")
  )
    return FileSpreadsheet;
  if (contentType.includes("zip") || contentType.includes("gzip") || contentType.includes("tar"))
    return FileArchive;
  return File;
}

const STATUS_BADGE: Record<
  DocumentStatus,
  { label: string; variant: "success" | "warning" | "destructive" }
> = {
  UPLOADED: { label: "Uploaded", variant: "success" },
  PENDING: { label: "Pending", variant: "warning" },
  FAILED: { label: "Failed", variant: "destructive" },
};

const SCOPE_BADGE: Record<
  DocumentScope,
  { label: string; variant: "neutral" | "default" | "lead" }
> = {
  ORG: { label: "Org", variant: "neutral" },
  PROJECT: { label: "Project", variant: "default" },
  CUSTOMER: { label: "Customer", variant: "lead" },
};

export default async function OrgDocumentsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let documents: Document[] = [];
  try {
    documents = await api.get<Document[]>("/api/documents?scope=ORG");
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Organization Documents
          </h1>
          <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {documents.length}
          </span>
        </div>
        {isAdmin && <OrgDocumentUpload slug={slug} />}
      </div>

      {/* Document Table or Empty State */}
      {documents.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <FileText className="size-16 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-6 font-display text-xl text-slate-900 dark:text-slate-100">
            No organization documents yet
          </h2>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            {isAdmin
              ? "Upload your first organization-level document to get started."
              : "No organization documents have been uploaded yet."}
          </p>
          {isAdmin && (
            <div className="mt-6">
              <OrgDocumentUpload slug={slug} />
            </div>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  File
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 sm:table-cell">
                  Size
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Scope
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 lg:table-cell">
                  Uploaded
                </th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => {
                const Icon = getFileIcon(doc.contentType);
                const statusBadge = STATUS_BADGE[doc.status];
                const scopeBadge = SCOPE_BADGE[doc.scope];
                return (
                  <tr
                    key={doc.id}
                    className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Icon className="size-4 shrink-0 text-slate-400 dark:text-slate-500" />
                        <span className="truncate text-sm font-medium text-slate-950 dark:text-slate-50">
                          {doc.fileName}
                        </span>
                      </div>
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-600 dark:text-slate-400 sm:table-cell">
                      {formatFileSize(doc.size)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={scopeBadge.variant}>{scopeBadge.label}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-400 dark:text-slate-600 lg:table-cell">
                      {doc.uploadedAt ? formatDate(doc.uploadedAt) : "\u2014"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
