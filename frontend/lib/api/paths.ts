/**
 * Backend wire path segments for matters and clients — the ONE place the two collection
 * segments are spelled (Phase 87 §12.10.2 row 4; ADR-337 wire vocabulary).
 *
 * Deliberately dependency-free (no `server-only`, no imports): `./matters.ts` and
 * `./clients.ts` import `./tags.ts`, so `tags.ts#entityApiPrefix` cannot import those modules
 * without a cycle — all three import from here instead. `matters.ts` / `clients.ts` re-export
 * `MATTERS_PATH` / `matterPath` and `CLIENTS_PATH` / `clientPath`, so callers keep importing
 * from them. Not re-exported from the barrel (would duplicate those re-exports).
 *
 * 610C's no-inline-literal scan should whitelist this file alongside `matters.ts` and
 * `clients.ts`.
 */

export const MATTERS_SEGMENT = "matters";
export const CLIENTS_SEGMENT = "clients";

export const MATTERS_PATH = `/api/${MATTERS_SEGMENT}`;
export const CLIENTS_PATH = `/api/${CLIENTS_SEGMENT}`;

/**
 * `/api/matters`, `/api/matters/{id}` or `/api/matters/{id}/{sub}`.
 * `sub` is appended verbatim (no leading slash): `matterPath(id, "closure/evaluate")`.
 */
export function matterPath(matterId?: string, sub?: string): string {
  if (!matterId) return MATTERS_PATH;
  return sub ? `${MATTERS_PATH}/${matterId}/${sub}` : `${MATTERS_PATH}/${matterId}`;
}

/**
 * `/api/clients`, `/api/clients/{id}` or `/api/clients/{id}/{sub}`.
 * `sub` is appended verbatim (no leading slash): `clientPath(id, "fica-status")`.
 */
export function clientPath(clientId?: string, sub?: string): string {
  if (!clientId) return CLIENTS_PATH;
  return sub ? `${CLIENTS_PATH}/${clientId}/${sub}` : `${CLIENTS_PATH}/${clientId}`;
}
