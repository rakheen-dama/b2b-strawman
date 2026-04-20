import { portalGet } from "@/lib/api-client";

/**
 * Portal deadline API client.
 *
 * Backend (497A): routes live under `/portal/deadlines`. Every endpoint is
 * module-gated server-side — if `deadlines` is disabled for the authenticated
 * customer's tenant, the backend returns **404** (not 403).
 *
 * The controller also returns 400 if `from.isAfter(to)` or if `status` is an
 * unknown enum value.
 */

export type PortalDeadlineStatus =
  | "UPCOMING"
  | "DUE_SOON"
  | "OVERDUE"
  | "COMPLETED"
  | "CANCELLED";

export type PortalDeadlineSourceEntity =
  | "FILING_SCHEDULE"
  | "COURT_DATE"
  | "PRESCRIPTION_TRACKER"
  | "CUSTOM_FIELD_DATE";

export type PortalDeadlineType =
  | "FILING"
  | "COURT_DATE"
  | "PRESCRIPTION"
  | "CUSTOM_DATE";

export interface PortalDeadline {
  /** UUID of the source row — unique per `sourceEntity`, NOT globally unique. */
  id: string;
  /** One of `FILING_SCHEDULE | COURT_DATE | PRESCRIPTION_TRACKER | CUSTOM_FIELD_DATE`. */
  sourceEntity: PortalDeadlineSourceEntity;
  /** One of `FILING | COURT_DATE | PRESCRIPTION | CUSTOM_DATE`. */
  deadlineType: PortalDeadlineType;
  /** Server-sanitised display label (max 160 chars). */
  label: string;
  /** ISO 8601 local date "YYYY-MM-DD". */
  dueDate: string;
  /** One of `UPCOMING | DUE_SOON | OVERDUE | COMPLETED | CANCELLED`. */
  status: PortalDeadlineStatus;
  /** Server-sanitised description (max 140 chars); may be empty string. */
  descriptionSanitised: string;
  /** Optional — only set for COURT_DATE / PRESCRIPTION deadlines. */
  matterId: string | null;
}

export interface ListDeadlinesParams {
  /** ISO 8601 local date "YYYY-MM-DD", optional (defaults server-side to today). */
  from?: string;
  /** ISO 8601 local date "YYYY-MM-DD", optional (defaults server-side to today+60d). */
  to?: string;
  /** Optional status filter — must be a valid enum value or the backend 400s. */
  status?: PortalDeadlineStatus;
}

/**
 * Fetches deadlines visible to the authenticated portal contact's customer.
 * Returns an empty array when no deadlines match; throws when the `deadlines`
 * module is disabled (backend 404 → `Error("The requested resource was not
 * found.")`).
 */
export async function listDeadlines(
  params: ListDeadlinesParams = {},
): Promise<PortalDeadline[]> {
  const qs = new URLSearchParams();
  if (params.from) qs.set("from", params.from);
  if (params.to) qs.set("to", params.to);
  if (params.status) qs.set("status", params.status);
  const query = qs.toString();
  const path = `/portal/deadlines${query ? `?${query}` : ""}`;
  return portalGet<PortalDeadline[]>(path);
}

/**
 * Fetches a single deadline by source entity and id. Throws when the module
 * is disabled or the deadline is not owned by the caller's customer (backend
 * 404 → `Error("The requested resource was not found.")`).
 */
export async function getDeadline(
  sourceEntity: string,
  id: string,
): Promise<PortalDeadline> {
  const path = `/portal/deadlines/${encodeURIComponent(sourceEntity)}/${encodeURIComponent(id)}`;
  return portalGet<PortalDeadline>(path);
}
