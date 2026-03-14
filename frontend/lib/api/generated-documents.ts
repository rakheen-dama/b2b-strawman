import "server-only";

import { redirect } from "next/navigation";
import { api, ApiError, API_BASE, getAuthFetchOptions } from "./client";
import type {
  GenerateDocumentResponse,
  GeneratedDocumentListResponse,
  TemplateEntityType,
} from "@/lib/types";

// ---- Generated Documents ----

export async function generateDocument(
  templateId: string,
  entityId: string,
  saveToDocuments: boolean,
  acknowledgeWarnings: boolean = false,
  clauses?: Array<{ clauseId: string; sortOrder: number }>,
): Promise<GenerateDocumentResponse | Blob> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("POST");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${API_BASE}/api/templates/${templateId}/generate`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authOptions.headers,
      },
      body: JSON.stringify({ entityId, saveToDocuments, acknowledgeWarnings, ...(clauses?.length ? { clauses } : {}) }),
      credentials: authOptions.credentials,
    },
  );

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  if (saveToDocuments) {
    return response.json() as Promise<GenerateDocumentResponse>;
  }

  return response.blob();
}

export async function fetchGeneratedDocuments(
  entityType: TemplateEntityType,
  entityId: string,
): Promise<GeneratedDocumentListResponse[]> {
  return api.get<GeneratedDocumentListResponse[]>(
    `/api/generated-documents?entityType=${entityType}&entityId=${entityId}`,
  );
}

export async function deleteGeneratedDocument(id: string): Promise<void> {
  return api.delete<void>(`/api/generated-documents/${id}`);
}

export async function downloadGeneratedDocument(id: string): Promise<Blob> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${API_BASE}/api/generated-documents/${id}/download`,
    {
      method: "GET",
      headers: {
        ...authOptions.headers,
      },
      redirect: "follow",
      credentials: authOptions.credentials,
    },
  );

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  return response.blob();
}

export async function downloadDocxGeneratedDocument(id: string): Promise<Blob> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${API_BASE}/api/generated-documents/${id}/download-docx`,
    {
      method: "GET",
      headers: {
        ...authOptions.headers,
      },
      redirect: "follow",
      credentials: authOptions.credentials,
    },
  );

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  return response.blob();
}

export async function downloadCertificateBlob(id: string): Promise<Blob> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${API_BASE}/api/acceptance-requests/${id}/certificate`,
    {
      method: "GET",
      headers: {
        ...authOptions.headers,
      },
      redirect: "follow",
      credentials: authOptions.credentials,
    },
  );

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  return response.blob();
}
