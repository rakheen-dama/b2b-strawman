"use server";

import { api, API_BASE, ApiError, getAuthFetchOptions, getFieldDefinitions } from "@/lib/api";
import { takeOnFileError } from "@/lib/schemas/take-on";
import type {
  TakeOnAttestationRequest,
  TakeOnBatch,
  TakeOnCommitRequest,
  TakeOnCustomFieldEntityType,
  TakeOnDataset,
  TakeOnMappingRequest,
  TakeOnPage,
  TakeOnPortalContactsResult,
  TakeOnPositionRequest,
  TakeOnPreview,
  TakeOnRow,
  TakeOnRowStatus,
  TakeOnSection,
  TakeOnSheetRequest,
  TakeOnUploadResult,
  TrustAccount,
} from "@/lib/types";

// Client and trust take-on (Phase 93 653A, architecture §17.4.1). Every action returns a result
// object, never throws: the wizard shows `error` (the backend's plain-English `detail`) and may
// branch on `code` (the ProblemDetail `code`, e.g. TAKE_ON_WRONG_STATE) — never shows the code.
// Mutations return the updated batch; the wizard keeps it in state (no revalidatePath here — the
// tab is dynamic and re-fetches on navigation).

export interface TakeOnActionResult<T> {
  success: boolean;
  data?: T;
  error?: string;
  code?: string;
}

const UNEXPECTED = "Something went wrong. Try again.";

function failure<T>(error: unknown): TakeOnActionResult<T> {
  if (error instanceof ApiError) {
    const code = typeof error.detail?.code === "string" ? error.detail.code : undefined;
    return {
      success: false,
      error: error.detail?.detail || error.detail?.title || error.message || UNEXPECTED,
      ...(code ? { code } : {}),
    };
  }
  return { success: false, error: UNEXPECTED };
}

async function run<T>(call: () => Promise<T>): Promise<TakeOnActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return failure<T>(error);
  }
}

const batchPath = (batchId: string) => `/api/take-on/batches/${encodeURIComponent(batchId)}`;
const sourcePath = (batchId: string, sourceId: string) =>
  `${batchPath(batchId)}/sources/${encodeURIComponent(sourceId)}`;

/** Multipart POST (mirrors uploadBankStatement): no Content-Type — fetch sets the boundary. */
async function postFile(
  endpoint: string,
  file: File,
  fields: Record<string, string | undefined>
): Promise<TakeOnActionResult<TakeOnUploadResult>> {
  // Server-side checks too — the input's `accept` attribute is not a security boundary.
  const refusal = takeOnFileError(file);
  if (refusal) {
    return { success: false, error: refusal };
  }
  try {
    const authOptions = await getAuthFetchOptions("POST");
    const formData = new FormData();
    formData.append("file", file);
    for (const [key, value] of Object.entries(fields)) {
      if (value) formData.append(key, value);
    }
    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: "POST",
      headers: { ...authOptions.headers },
      body: formData,
      credentials: authOptions.credentials,
    });
    if (!response.ok) {
      let message = response.statusText || UNEXPECTED;
      let code: string | undefined;
      try {
        const detail = await response.json();
        if (typeof detail?.code === "string") code = detail.code;
        message = detail?.detail || detail?.title || message;
      } catch {
        // not JSON — keep the status text
      }
      return { success: false, error: message, ...(code ? { code } : {}) };
    }
    return { success: true, data: (await response.json()) as TakeOnUploadResult };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "The file could not be uploaded.",
    };
  }
}

// ── Writes ──────────────────────────────────────────────────────────────

export interface TakeOnUploadInput {
  dataset: TakeOnDataset;
  section?: TakeOnSection;
  trustAccountId?: string;
  name?: string;
  file: File;
}

/** POST /api/take-on/batches — a new batch with its first file. */
export async function uploadTakeOnFile(
  input: TakeOnUploadInput
): Promise<TakeOnActionResult<TakeOnUploadResult>> {
  return postFile("/api/take-on/batches", input.file, {
    dataset: input.dataset,
    section: input.section,
    trustAccountId: input.trustAccountId,
    name: input.name?.trim() || undefined,
  });
}

/** POST …/sources — another trust section, or a replacement file after a removal. */
export async function addTakeOnSource(
  batchId: string,
  section: TakeOnSection | null,
  file: File
): Promise<TakeOnActionResult<TakeOnUploadResult>> {
  return postFile(`${batchPath(batchId)}/sources`, file, { section: section ?? undefined });
}

export async function removeTakeOnSource(
  batchId: string,
  sourceId: string
): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.delete<TakeOnBatch>(sourcePath(batchId, sourceId)));
}

export async function setSheet(
  batchId: string,
  sourceId: string,
  request: TakeOnSheetRequest
): Promise<TakeOnActionResult<TakeOnUploadResult>> {
  return run(() => api.put<TakeOnUploadResult>(`${sourcePath(batchId, sourceId)}/sheet`, request));
}

export async function saveMapping(
  batchId: string,
  sourceId: string,
  request: TakeOnMappingRequest
): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.put<TakeOnBatch>(`${sourcePath(batchId, sourceId)}/mapping`, request));
}

/** PUT …/position — the cutover date only in 653A (P-11). */
export async function savePosition(
  batchId: string,
  request: TakeOnPositionRequest
): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.put<TakeOnBatch>(`${batchPath(batchId)}/position`, request));
}

/** POST …/validate — the dry run. Nothing is created. */
export async function validateBatch(batchId: string): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.post<TakeOnBatch>(`${batchPath(batchId)}/validate`));
}

/** POST …/cancel — cancels the batch and deletes its files and row values. */
export async function cancelBatch(batchId: string): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.post<TakeOnBatch>(`${batchPath(batchId)}/cancel`));
}

// ── Attestation, commit, results (655A) ────────────────────────────────

/**
 * POST …/attestation — the FICA attestation of a validated clients batch. `defaults` are the
 * source's mapping defaults exactly as shown; a refusal's `detail` is shown verbatim.
 */
export async function attest(
  batchId: string,
  request: TakeOnAttestationRequest
): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.post<TakeOnBatch>(`${batchPath(batchId)}/attestation`, request));
}

/** DELETE …/sign-offs/{id} — withdraws a live attestation while the batch is still VALIDATED. */
export async function revokeSignOff(
  batchId: string,
  signOffId: string
): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() =>
    api.delete<TakeOnBatch>(`${batchPath(batchId)}/sign-offs/${encodeURIComponent(signOffId)}`)
  );
}

/** POST …/commit — 202 with the batch COMMITTING; the background drain creates the records. */
export async function commitBatch(
  batchId: string,
  validationHash: string
): Promise<TakeOnActionResult<TakeOnBatch>> {
  const request: TakeOnCommitRequest = { validationHash };
  return run(() => api.post<TakeOnBatch>(`${batchPath(batchId)}/commit`, request));
}

/** POST …/portal-contacts — a portal contact per client taken on. Sends no email. */
export async function createPortalContacts(
  batchId: string
): Promise<TakeOnActionResult<TakeOnPortalContactsResult>> {
  return run(() => api.post<TakeOnPortalContactsResult>(`${batchPath(batchId)}/portal-contacts`));
}

/** GET …/result.csv — one line per row with its outcome. */
export async function downloadTakeOnResult(
  batchId: string
): Promise<TakeOnActionResult<TakeOnCsvFile>> {
  return downloadTakeOnCsv(
    `${batchPath(batchId)}/result.csv`,
    `take-on-result-${batchId}.csv`,
    "The result report could not be downloaded. Try again."
  );
}

// ── Reads for the client steps (SWR fetchers call these) ───────────────

export async function fetchTakeOnBatch(batchId: string): Promise<TakeOnActionResult<TakeOnBatch>> {
  return run(() => api.get<TakeOnBatch>(batchPath(batchId)));
}

export async function fetchTakeOnPreview(
  batchId: string,
  sourceId: string,
  limit = 20
): Promise<TakeOnActionResult<TakeOnPreview>> {
  return run(() =>
    api.get<TakeOnPreview>(`${sourcePath(batchId, sourceId)}/preview?limit=${Math.trunc(limit)}`)
  );
}

export interface TakeOnRowFilters {
  status?: TakeOnRowStatus;
  section?: TakeOnSection;
  blocksOnly?: boolean;
  page: number;
  size: number;
}

export async function fetchTakeOnRows(
  batchId: string,
  filters: TakeOnRowFilters
): Promise<TakeOnActionResult<TakeOnPage<TakeOnRow>>> {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.section) params.set("section", filters.section);
  if (filters.blocksOnly) params.set("blocksOnly", "true");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return run(() => api.get<TakeOnPage<TakeOnRow>>(`${batchPath(batchId)}/rows?${params}`));
}

export interface TakeOnCustomFieldOption {
  slug: string;
  name: string;
}

/** The entity's active custom fields — mapping targets for leftover columns. */
export async function fetchTakeOnCustomFields(
  entityType: TakeOnCustomFieldEntityType
): Promise<TakeOnActionResult<TakeOnCustomFieldOption[]>> {
  return run(async () => {
    const definitions = await getFieldDefinitions(entityType);
    return definitions.filter((d) => d.active).map((d) => ({ slug: d.slug, name: d.name }));
  });
}

export interface TakeOnBankingAccount {
  id: string;
  accountName: string;
}

/** Open trust banking accounts (investment accounts are taken on through their rows). */
export async function fetchTakeOnBankingAccounts(): Promise<
  TakeOnActionResult<TakeOnBankingAccount[]>
> {
  try {
    const accounts = await api.get<TrustAccount[]>("/api/trust-accounts");
    return {
      success: true,
      data: accounts
        .filter((a) => a.accountType !== "INVESTMENT" && a.status === "ACTIVE")
        .map((a) => ({ id: a.id, accountName: a.accountName })),
    };
  } catch (error) {
    // The module guard also answers 403 (ModuleNotEnabledException, ProblemDetail `moduleId`);
    // only a 403 that is not a module refusal is a missing permission — a trust take-on needs
    // MANAGE_TRUST and VIEW_TRUST (the backend's TakeOnAccessPolicy).
    const moduleOff = error instanceof ApiError && error.detail?.moduleId === "trust_accounting";
    const forbidden = error instanceof ApiError && error.status === 403 && !moduleOff;
    return {
      success: false,
      data: [],
      error: forbidden
        ? "A trust take-on needs permission to view and manage trust accounting. Ask an owner or admin."
        : "Turn on trust accounting and add a trust account first.",
    };
  }
}

/** GET /api/take-on/templates/{dataset} — a CSV with the columns and one fictional row. */
export async function downloadTakeOnTemplate(
  dataset: TakeOnDataset,
  section?: TakeOnSection
): Promise<TakeOnActionResult<TakeOnCsvFile>> {
  const query = section ? `?section=${encodeURIComponent(section)}` : "";
  return downloadTakeOnCsv(
    `/api/take-on/templates/${dataset}${query}`,
    `take-on-${dataset.toLowerCase().replace(/_/g, "-")}.csv`,
    "The template could not be downloaded. Try again."
  );
}

/** A downloaded CSV: the browser saves `content` as `fileName` from a Blob (D-43). */
export interface TakeOnCsvFile {
  fileName: string;
  content: string;
}

/**
 * Reads a take-on CSV download (raw fetch: the bytes, with the file name from
 * Content-Disposition, else `fallbackName`). A refusal shows the backend's `detail` when it sends
 * one, else `refusedText`.
 */
async function downloadTakeOnCsv(
  path: string,
  fallbackName: string,
  refusedText: string
): Promise<TakeOnActionResult<TakeOnCsvFile>> {
  try {
    const authOptions = await getAuthFetchOptions("GET");
    const response = await fetch(`${API_BASE}${path}`, {
      method: "GET",
      headers: { ...authOptions.headers },
      credentials: authOptions.credentials,
      cache: "no-store",
    });
    if (!response.ok) {
      let message = refusedText;
      try {
        const detail = await response.json();
        message = detail?.detail || message;
      } catch {
        // not JSON — keep the generic text
      }
      return { success: false, error: message };
    }
    const disposition = response.headers.get("content-disposition") ?? "";
    const match = disposition.match(/filename="?([^";]+)"?/i);
    return {
      success: true,
      data: { fileName: match?.[1] ?? fallbackName, content: await response.text() },
    };
  } catch {
    return { success: false, error: refusedText };
  }
}
