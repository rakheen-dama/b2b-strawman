"use client";

import { FileText } from "lucide-react";

import type { Document } from "@/lib/types";
import { formatDate, formatFileSize } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";

interface CustomerDocsTabProps {
  documents: Document[];
}

export function CustomerDocsTab({ documents }: CustomerDocsTabProps) {
  if (documents.length === 0) {
    return (
      <div className="flex min-h-[200px] flex-col items-center justify-center rounded-lg bg-slate-50/50 px-6 py-12 text-center">
        <FileText className="mb-3 size-10 text-slate-400" />
        <h3 className="text-base font-semibold text-slate-700">
          No documents yet
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Documents scoped to this customer will appear here.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50/50 text-left">
            <th className="px-4 py-2.5 font-medium text-slate-600">Name</th>
            <th className="px-4 py-2.5 font-medium text-slate-600">Status</th>
            <th className="px-4 py-2.5 font-medium text-slate-600">Size</th>
            <th className="px-4 py-2.5 font-medium text-slate-600">
              Uploaded
            </th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc) => (
            <tr
              key={doc.id}
              className="border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors"
            >
              <td className="px-4 py-2.5">
                <div className="flex items-center gap-2">
                  <FileText className="size-4 text-slate-400" />
                  <span className="font-medium text-slate-900">
                    {doc.fileName}
                  </span>
                </div>
              </td>
              <td className="px-4 py-2.5">
                <StatusBadge status={doc.status} />
              </td>
              <td className="px-4 py-2.5 text-slate-500">
                {doc.size ? formatFileSize(doc.size) : "--"}
              </td>
              <td className="px-4 py-2.5 text-slate-500">
                {formatDate(doc.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
