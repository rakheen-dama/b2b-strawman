"use client";

import { useEffect, useState, useCallback } from "react";
import { Loader2 } from "lucide-react";
import { PortalDocumentTable } from "@/components/portal/portal-document-table";
import type { PortalDocument } from "@/lib/types";

const TOKEN_KEY = "portal_token";

export default function PortalDocumentsPage() {
  const [documents, setDocuments] = useState<PortalDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchDocuments() {
      const token = sessionStorage.getItem(TOKEN_KEY);
      if (!token) {
        setError("Not authenticated. Please sign in.");
        setLoading(false);
        return;
      }

      try {
        const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
        const res = await fetch(`${backendUrl}/portal/documents`, {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!res.ok) {
          if (res.status === 401 || res.status === 403) {
            setError("Session expired. Please sign in again.");
          } else {
            setError("Failed to load documents.");
          }
          return;
        }

        const data = await res.json();
        setDocuments(data);
      } catch {
        setError("Failed to connect to server.");
      } finally {
        setLoading(false);
      }
    }

    fetchDocuments();
  }, []);

  const handleDownload = useCallback(async (documentId: string) => {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (!token) return;

    try {
      const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
      const res = await fetch(
        `${backendUrl}/portal/documents/${documentId}/presign-download`,
        { headers: { Authorization: `Bearer ${token}` } }
      );

      if (!res.ok) {
        throw new Error("Failed to get download link");
      }

      const data = await res.json();
      window.open(data.presignedUrl, "_blank");
    } catch {
      // Silently fail -- could add toast notification here
    }
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="size-6 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Documents
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Documents shared with you by your service provider.
        </p>
      </div>

      <PortalDocumentTable documents={documents} onDownload={handleDownload} />
    </div>
  );
}
