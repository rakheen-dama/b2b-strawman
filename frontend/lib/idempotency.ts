/**
 * Trust-write idempotency (Phase 92 648B F-11). Every trust write carries a client-generated
 * `Idempotency-Key`: a retry with the same key and the same request returns the first result
 * without a second posting; the same key with a different request is refused (409).
 *
 * Shared by the server actions (which send the header) and `useIdempotencyKey` (which keeps one
 * key per submit intent). No server-only imports.
 *
 * Key policy: the backend replays a stored success or 4xx refusal for a repeated key and refuses a
 * key whose first request is still in progress (or ended in a 5xx) — so the client reuses a key
 * only while its request's outcome is unknown, and takes a new key after any definite answer.
 */

export const IDEMPOTENCY_HEADER = "Idempotency-Key";

/** The backend's 409 `code` for a key whose first request is still running or ended unknown. */
export const IDEMPOTENCY_IN_PROGRESS_CODE = "idempotency_in_progress";

/**
 * A fresh key for a trust write that posts nothing and may safely run again — reconciliation
 * calculate, which only recomputes and stores the figures. Every other trust write keeps one key per
 * submit intent (`useIdempotencyKey`).
 */
export function freshIdempotencyKey(): string {
  return crypto.randomUUID();
}

/** The header to send on a trust write. */
export function idempotencyHeaders(key: string): Record<string, string> {
  return { [IDEMPOTENCY_HEADER]: key };
}

/**
 * Whether a failed trust write's outcome is unknown, so a resubmit must reuse its key: no response
 * at all (network failure, timeout), a 5xx, or the backend's "still in progress" 409. Any other
 * response is a definite refusal (nothing was posted) and the next submit gets a new key.
 */
export function isOutcomeUnknown(status: number | undefined, code?: unknown): boolean {
  if (status === undefined) return true;
  return status >= 500 || code === IDEMPOTENCY_IN_PROGRESS_CODE;
}

/** `api.post/put` options carrying the key (server actions). */
export function idempotent(key: string): { headers: Record<string, string> } {
  return { headers: idempotencyHeaders(key) };
}

/**
 * Whether `error` (thrown by `api.*` in a server action) leaves the write's outcome unknown. An
 * `ApiError` carries the response's `status` and ProblemDetail `detail`; anything without a status
 * (a network failure, a timeout, an unreadable body) had no response. Read structurally so this
 * module stays free of the server-only API client.
 */
export function outcomeUnknownOf(error: unknown): boolean {
  const { status, detail } = (error ?? {}) as { status?: unknown; detail?: { code?: unknown } };
  return isOutcomeUnknown(typeof status === "number" ? status : undefined, detail?.code);
}

/** Spread into a failed action's result: `{ outcomeUnknown: true }` when unknown, else nothing. */
export function unknownOutcome(error: unknown): { outcomeUnknown?: true } {
  return outcomeUnknownOf(error) ? { outcomeUnknown: true } : {};
}

/** The part of a trust-write action result the key policy reads. */
export interface TrustWriteOutcome {
  success: boolean;
  /** Set on a failure whose outcome is unknown (see {@link isOutcomeUnknown}). */
  outcomeUnknown?: boolean;
}
