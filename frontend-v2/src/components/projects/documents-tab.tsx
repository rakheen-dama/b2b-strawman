import type { Document } from "@/lib/types";
import { formatDate, formatFileSize } from "@/lib/format";
import { FileText, File } from "lucide-react";

interface DocumentsTabProps {
  documents: Document[];
}

export function DocumentsTab({ documents }: DocumentsTabProps) {
  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center py-16 text-center">
        <FileText className="size-12 text-slate-300" />
        <h3 className="mt-4 font-display text-lg text-slate-900">
          No documents
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Upload documents to organize project files.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {documents.map((doc) => (
        <div
          key={doc.id}
          className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:bg-slate-50"
        >
          <File className="size-8 shrink-0 text-slate-400" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-slate-900">
              {doc.fileName}
            </p>
            <p className="text-xs text-slate-500">
              {formatFileSize(doc.size)} &middot;{" "}
              {doc.uploadedAt ? formatDate(doc.uploadedAt) : formatDate(doc.createdAt)}
              {doc.uploadedByName && (
                <> &middot; {doc.uploadedByName}</>
              )}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
