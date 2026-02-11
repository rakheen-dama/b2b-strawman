"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { PortalDocumentTable } from "@/components/portal/portal-document-table";
import type { PortalDocument } from "@/lib/types";

export default function PortalDocumentsPage() {
  const router = useRouter();
  const [documents, setDocuments] = useState<PortalDocument[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchDocuments() {
      try {
        const data = await portalApi.get<PortalDocument[]>("/portal/documents");
        setDocuments(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load documents");
      } finally {
        setIsLoading(false);
      }
    }
    fetchDocuments();
  }, [router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-olive-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <h1 className="font-display text-2xl text-olive-950 dark:text-olive-50">
          Documents
        </h1>
        <Badge variant="neutral">{documents.length}</Badge>
      </div>

      {/* Document table */}
      <PortalDocumentTable
        documents={documents}
        title="All Shared Documents"
        showProject
      />
    </div>
  );
}
