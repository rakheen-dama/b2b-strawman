"use client";

import { use, useCallback, useEffect, useState } from "react";
import { portalFetch, portalGet, portalPost } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

interface RequestItem {
  id: string;
  name: string;
  description: string | null;
  responseType: string;
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  status: string;
  rejectionReason: string | null;
  documentId: string | null;
  textResponse: string | null;
}

interface RequestDetail {
  id: string;
  requestNumber: string;
  status: string;
  projectId: string;
  projectName: string;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  sentAt: string | null;
  completedAt: string | null;
  items: RequestItem[];
}

interface UploadInitResponse {
  documentId: string;
  uploadUrl: string;
  expiresAt: string;
}

export default function RequestDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [detail, setDetail] = useState<RequestDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);

  const refresh = useCallback(() => setRefreshToken((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    portalGet<RequestDetail>(`/portal/requests/${id}`)
      .then((data) => {
        if (!cancelled) {
          // Clear any stale error from a previous failed fetch so a successful
          // refresh does not leave the page stuck on the error panel.
          setError(null);
          setDetail(data);
        }
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : "Failed to load");
      });
    return () => {
      cancelled = true;
    };
  }, [id, refreshToken]);

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        {error}
      </div>
    );
  }
  if (!detail) {
    return <p className="text-sm text-slate-500">Loading…</p>;
  }

  return (
    <div>
      <div className="mb-6">
        <p className="font-mono text-sm text-slate-500">
          {detail.requestNumber}
        </p>
        <h1 className="font-display mt-1 text-2xl font-semibold text-slate-900">
          {detail.projectName}
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          {detail.submittedItems}/{detail.totalItems} submitted • status{" "}
          {detail.status}
        </p>
      </div>
      <ul className="space-y-4">
        {detail.items.map((item) => (
          <li key={item.id}>
            <ItemCard requestId={id} item={item} onSubmitted={refresh} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function ItemCard({
  requestId,
  item,
  onSubmitted,
}: {
  requestId: string;
  item: RequestItem;
  onSubmitted: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const done = item.status === "SUBMITTED" || item.status === "ACCEPTED";

  async function handleUpload() {
    if (!file) return;
    setBusy(true);
    setErr(null);
    try {
      const init = await portalPost<UploadInitResponse>(
        `/portal/requests/${requestId}/items/${item.id}/upload`,
        {
          fileName: file.name,
          contentType: file.type || "application/octet-stream",
          size: file.size,
        },
      );
      const put = await fetch(init.uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type || "application/octet-stream" },
        body: file,
      });
      if (!put.ok) throw new Error(`Upload failed (${put.status})`);
      // Submit returns empty body, so use portalFetch directly to skip JSON parsing
      const submitRes = await portalFetch(
        `/portal/requests/${requestId}/items/${item.id}/submit`,
        {
          method: "POST",
          body: JSON.stringify({
            documentId: init.documentId,
            textResponse: null,
          }),
        },
      );
      if (!submitRes.ok) {
        throw new Error(`Submit failed (${submitRes.status})`);
      }
      onSubmitted();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base font-semibold text-slate-900">
          {item.name}
          {item.required && (
            <span className="ml-2 text-xs text-red-600">required</span>
          )}
        </CardTitle>
        {item.description && (
          <p className="mt-1 text-sm text-slate-600">{item.description}</p>
        )}
        {item.fileTypeHints && (
          <p className="mt-1 text-xs text-slate-500">
            Accepts: {item.fileTypeHints}
          </p>
        )}
      </CardHeader>
      <CardContent>
        {done ? (
          <p className="text-sm text-teal-700">
            Submitted — status: {item.status}
          </p>
        ) : item.responseType === "FILE_UPLOAD" ? (
          <div className="flex flex-col gap-2">
            <label htmlFor={`file-upload-${item.id}`} className="sr-only">
              Upload file for {item.name}
            </label>
            <input
              id={`file-upload-${item.id}`}
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-sm"
              disabled={busy}
            />
            <Button
              type="button"
              onClick={handleUpload}
              disabled={!file || busy}
              className="self-start"
            >
              {busy ? "Uploading…" : "Upload and submit"}
            </Button>
            {err && <p className="text-sm text-red-700">{err}</p>}
          </div>
        ) : (
          <p className="text-sm text-slate-500">
            Unsupported response type: {item.responseType}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
