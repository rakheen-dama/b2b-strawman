"use client";

import { useCallback, useEffect, useState } from "react";
import { Download, Send, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  fetchGeneratedDocumentsAction,
  deleteGeneratedDocumentAction,
  downloadGeneratedDocumentAction,
} from "@/app/(app)/org/[slug]/settings/templates/actions";
import { formatDate } from "@/lib/format";
import type { TemplateEntityType, GeneratedDocumentListResponse } from "@/lib/types";
import { getAcceptanceRequests } from "@/lib/actions/acceptance-actions";
import type { AcceptanceRequestResponse } from "@/lib/actions/acceptance-actions";
import { AcceptanceStatusBadge } from "@/components/acceptance/AcceptanceStatusBadge";
import { SendForAcceptanceDialog } from "@/components/acceptance/SendForAcceptanceDialog";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface GeneratedDocumentsListProps {
  entityType: TemplateEntityType;
  entityId: string;
  slug: string;
  isAdmin?: boolean;
  refreshKey?: number;
  customerId?: string;
}

export function GeneratedDocumentsList({
  entityType,
  entityId,
  isAdmin = false,
  refreshKey = 0,
  customerId,
}: GeneratedDocumentsListProps) {
  const [documents, setDocuments] = useState<GeneratedDocumentListResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);

  // Acceptance state
  const [acceptanceMap, setAcceptanceMap] = useState<
    Record<string, AcceptanceRequestResponse>
  >({});
  const [sendDialogDocId, setSendDialogDocId] = useState<string | null>(null);
  const [sendDialogOpen, setSendDialogOpen] = useState(false);

  const showAcceptanceColumn = isAdmin && !!customerId;

  const fetchDocuments = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchGeneratedDocumentsAction(entityType, entityId);
      if (result.success && result.data) {
        setDocuments(result.data);
      } else {
        setError(result.error ?? "Failed to load generated documents.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }, [entityType, entityId]);

  const fetchAcceptanceStatuses = useCallback(async () => {
    if (!customerId) return;
    try {
      const requests = await getAcceptanceRequests({ customerId });
      const map: Record<string, AcceptanceRequestResponse> = {};
      for (const req of requests) {
        // Keep the most recent request per document
        if (
          !map[req.generatedDocumentId] ||
          new Date(req.createdAt) > new Date(map[req.generatedDocumentId].createdAt)
        ) {
          map[req.generatedDocumentId] = req;
        }
      }
      setAcceptanceMap(map);
    } catch {
      // Acceptance status is non-critical — silently fail
    }
  }, [customerId]);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments, refreshKey]);

  useEffect(() => {
    fetchAcceptanceStatuses();
  }, [fetchAcceptanceStatuses, refreshKey]);

  // Listen for custom event dispatched after "Save to Documents"
  useEffect(() => {
    function handleRefresh() {
      fetchDocuments();
    }
    window.addEventListener("generated-documents-refresh", handleRefresh);
    return () => {
      window.removeEventListener("generated-documents-refresh", handleRefresh);
    };
  }, [fetchDocuments]);

  // Listen for acceptance refresh events
  useEffect(() => {
    function handleAcceptanceRefresh() {
      fetchAcceptanceStatuses();
    }
    window.addEventListener("acceptance-requests-refresh", handleAcceptanceRefresh);
    return () => {
      window.removeEventListener("acceptance-requests-refresh", handleAcceptanceRefresh);
    };
  }, [fetchAcceptanceStatuses]);

  async function handleDownload(doc: GeneratedDocumentListResponse) {
    setError(null);
    try {
      const result = await downloadGeneratedDocumentAction(doc.id);
      if (result.success && result.pdfBase64) {
        const byteCharacters = atob(result.pdfBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: "application/pdf" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = doc.fileName || `${doc.templateName}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      } else {
        setError(result.error ?? "Failed to download document.");
      }
    } catch {
      setError("Failed to download document.");
    }
  }

  async function handleDelete() {
    if (!deleteTargetId) return;
    setDeletingId(deleteTargetId);
    try {
      const result = await deleteGeneratedDocumentAction(deleteTargetId);
      if (result.success) {
        setDocuments((prev) => prev.filter((d) => d.id !== deleteTargetId));
      } else {
        setError(result.error ?? "Failed to delete document.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setDeletingId(null);
      setDeleteDialogOpen(false);
      setDeleteTargetId(null);
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <p className="text-sm text-slate-500">Loading generated documents...</p>
      </div>
    );
  }

  if (documents.length === 0) {
    return (
      <div className="flex items-center justify-center py-12">
        <p className="text-sm text-slate-500">No documents generated yet</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {error && <p className="text-sm text-destructive">{error}</p>}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Template Name</TableHead>
            <TableHead>Generated By</TableHead>
            <TableHead>Date</TableHead>
            <TableHead>File Size</TableHead>
            {showAcceptanceColumn && <TableHead>Acceptance</TableHead>}
            <TableHead className="w-[100px]">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {documents.map((doc) => {
            const acceptanceRequest = acceptanceMap[doc.id];
            return (
              <TableRow key={doc.id}>
                <TableCell className="font-medium">{doc.templateName}</TableCell>
                <TableCell>{doc.generatedByName}</TableCell>
                <TableCell>{formatDate(doc.generatedAt)}</TableCell>
                <TableCell>{formatFileSize(doc.fileSize)}</TableCell>
                {showAcceptanceColumn && (
                  <TableCell>
                    {acceptanceRequest ? (
                      <AcceptanceStatusBadge status={acceptanceRequest.status} />
                    ) : null}
                  </TableCell>
                )}
                <TableCell>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-8"
                      onClick={() => handleDownload(doc)}
                      title="Download"
                    >
                      <Download className="size-4" />
                    </Button>
                    {isAdmin && customerId && (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-8"
                        onClick={() => {
                          setSendDialogDocId(doc.id);
                          setSendDialogOpen(true);
                        }}
                        title="Send for Acceptance"
                      >
                        <Send className="size-4" />
                      </Button>
                    )}
                    {isAdmin && (
                      <AlertDialog
                        open={deleteDialogOpen && deleteTargetId === doc.id}
                        onOpenChange={(open) => {
                          setDeleteDialogOpen(open);
                          if (!open) setDeleteTargetId(null);
                        }}
                      >
                        <AlertDialogTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-8 text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                            onClick={() => {
                              setDeleteTargetId(doc.id);
                              setDeleteDialogOpen(true);
                            }}
                            title="Delete"
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Delete Generated Document</AlertDialogTitle>
                            <AlertDialogDescription>
                              Are you sure you want to delete this generated document? This
                              action cannot be undone.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel disabled={!!deletingId}>
                              Cancel
                            </AlertDialogCancel>
                            <Button
                              variant="destructive"
                              onClick={handleDelete}
                              disabled={!!deletingId}
                            >
                              {deletingId ? "Deleting..." : "Delete"}
                            </Button>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {sendDialogDocId && customerId && (
        <SendForAcceptanceDialog
          generatedDocumentId={sendDialogDocId}
          customerId={customerId}
          documentName={
            documents.find((d) => d.id === sendDialogDocId)?.templateName ?? "Document"
          }
          open={sendDialogOpen}
          onOpenChange={(open) => {
            setSendDialogOpen(open);
            if (!open) setSendDialogDocId(null);
          }}
        />
      )}
    </div>
  );
}
