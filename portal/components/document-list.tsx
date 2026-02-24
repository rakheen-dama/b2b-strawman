"use client";

import { FileText, Download, FileImage, FileSpreadsheet, File } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatFileSize, formatDate } from "@/lib/format";
import { portalGet } from "@/lib/api-client";
import type { PortalDocument, PortalPresignDownload } from "@/lib/types";

interface DocumentListProps {
  documents: PortalDocument[];
}

function getFileIcon(contentType: string) {
  if (contentType.startsWith("image/")) return FileImage;
  if (
    contentType.includes("spreadsheet") ||
    contentType.includes("csv") ||
    contentType.includes("excel")
  )
    return FileSpreadsheet;
  if (contentType.includes("pdf") || contentType.includes("text"))
    return FileText;
  return File;
}

async function handleDownload(documentId: string) {
  const data = await portalGet<PortalPresignDownload>(
    `/portal/documents/${documentId}/presign-download`,
  );
  window.open(data.presignedUrl, "_blank");
}

export function DocumentList({ documents }: DocumentListProps) {
  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <FileText className="mb-4 size-10 text-slate-300" />
        <p className="text-sm text-slate-500">No documents shared yet.</p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200/80">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
          <tr>
            <th className="px-4 py-3">File</th>
            <th className="px-4 py-3">Size</th>
            <th className="px-4 py-3">Uploaded</th>
            <th className="px-4 py-3 text-right">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {documents.map((doc) => {
            const Icon = getFileIcon(doc.contentType);
            return (
              <tr key={doc.id} className="bg-white">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <Icon className="size-4 shrink-0 text-slate-400" />
                    <span className="font-medium text-slate-900">
                      {doc.fileName}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {formatFileSize(doc.size)}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {formatDate(doc.createdAt)}
                </td>
                <td className="px-4 py-3 text-right">
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    onClick={() => handleDownload(doc.id)}
                    aria-label={`Download ${doc.fileName}`}
                  >
                    <Download className="size-4" />
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
