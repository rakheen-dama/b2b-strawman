import { readFileSync, writeFileSync, existsSync } from "fs";

const STATE_FILE = "/tmp/e2e-keycloak-state.json";

export interface E2eState {
  runId: string;
  ownerEmail: string;
  ownerPassword: string;
  orgSlug: string;
}

export function saveState(state: E2eState): void {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

export function loadState(): E2eState {
  if (!existsSync(STATE_FILE)) {
    throw new Error(`E2E state file not found at ${STATE_FILE}. Run onboarding.spec.ts first.`);
  }
  return JSON.parse(readFileSync(STATE_FILE, "utf-8"));
}

export function hasState(): boolean {
  return existsSync(STATE_FILE);
}
