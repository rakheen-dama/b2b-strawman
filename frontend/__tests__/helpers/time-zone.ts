import { expect, vi } from "vitest";

/**
 * Moves the process zone (Date and Intl follow: Node re-reads `process.env.TZ` on assignment),
 * guarding against a vacuous stub. Callers `vi.unstubAllEnvs()` in `afterEach`. Shared by the
 * date-across-zones tests (Phase 90 642A review follow-up, extracted in 642B). Not a test file.
 */
export function inZone(zone: string) {
  vi.stubEnv("TZ", zone);
  expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(zone);
}
