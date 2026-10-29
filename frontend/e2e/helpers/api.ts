/**
 * Backend API helpers for default-config spec setup (Phase 88 628F).
 *
 * Setup only: a spec drives the behaviour it is named after through the UI and uses these to build
 * its own run-unique entities (628F R-5 — the boot-seed rows in `seed.ts` are read-only). Every
 * helper asserts the answer, so a refused setup step fails the test where it happened.
 */
import { expect } from "@playwright/test";
import { seedToken } from "./seed";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";

type SeedUser = "alice" | "bob" | "carol";

/** A raw backend call as `user` (JSON body). */
export async function api(
  path: string,
  init: { method?: string; body?: unknown } = {},
  user: SeedUser = "alice"
): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, {
    method: init.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await seedToken(user)}`,
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
}

/** A backend call that must answer 2xx; returns the parsed JSON body (or null when empty). */
export async function apiOk<T = Record<string, unknown>>(
  path: string,
  init: { method?: string; body?: unknown } = {},
  user: SeedUser = "alice"
): Promise<T> {
  const res = await api(path, init, user);
  const text = await res.text();
  expect(res.ok, `${init.method ?? "GET"} ${path} → ${res.status} ${text}`).toBe(true);
  return (text ? JSON.parse(text) : null) as T;
}

export interface Member {
  id: string;
  name: string;
  email: string;
}

/** A seed member (Alice / Bob / Carol) by first name. */
export async function memberNamed(first: "Alice" | "Bob" | "Carol"): Promise<Member> {
  const members = await apiOk<Member[]>("/api/members");
  const hit = members.find((m) => m.name?.startsWith(first));
  expect(hit, `seed member ${first}`).toBeDefined();
  return hit!;
}

/**
 * Settles every onboarding checklist item: complete it or — when the item needs a linked document —
 * skip it (the boot seed does the same). Settling the last item auto-activates the client.
 */
async function settleChecklists(clientId: string): Promise<void> {
  const checklists = await apiOk<{ items?: { id: string; status: string }[] }[]>(
    `/api/clients/${clientId}/checklists`
  );
  expect(checklists.length, "an onboarding checklist was instantiated").toBeGreaterThan(0);
  for (const checklist of checklists) {
    for (const item of checklist.items ?? []) {
      if (item.status === "COMPLETED" || item.status === "SKIPPED") continue;
      const complete = await api(`/api/checklist-items/${item.id}/complete`, {
        method: "PUT",
        body: { notes: "E2E auto-complete" },
      });
      if (!complete.ok) {
        await apiOk(`/api/checklist-items/${item.id}/skip`, {
          method: "PUT",
          body: { reason: "E2E: no document to link" },
        });
      }
    }
  }
}

/**
 * A fresh ACTIVE individual client with a billing address (fee notes need one) and an
 * auto-provisioned portal contact for `email`: PROSPECT → ONBOARDING → checklist auto-activation.
 */
export async function createActiveClient(name: string, email: string): Promise<string> {
  const client = await apiOk<{ id: string }>("/api/clients", {
    method: "POST",
    body: {
      name,
      email,
      phone: "+27-11-555-0199",
      addressLine1: "12 Rivonia Road",
      city: "Sandton",
      postalCode: "2196",
      country: "ZA",
    },
  });
  await apiOk(`/api/clients/${client.id}/transition`, {
    method: "POST",
    body: { targetStatus: "ONBOARDING", notes: "E2E setup" },
  });
  await settleChecklists(client.id);
  await expect
    .poll(
      async () =>
        (await apiOk<{ lifecycleStatus: string }>(`/api/clients/${client.id}`)).lifecycleStatus
    )
    .toBe("ACTIVE");
  return client.id;
}

/** A fresh matter (optionally for a client); returns its id. */
export async function createMatter(name: string, clientId?: string): Promise<string> {
  const matter = await apiOk<{ id: string }>("/api/matters", {
    method: "POST",
    body: { name, description: "E2E setup", ...(clientId ? { clientId } : {}) },
  });
  return matter.id;
}

/** Adds a member to a matter as a contributor. */
export async function addMatterMember(matterId: string, memberId: string): Promise<void> {
  await apiOk(`/api/matters/${matterId}/members`, {
    method: "POST",
    body: { memberId, projectRole: "contributor" },
  });
}

/** A fresh task on a matter; returns its id. */
export async function createTask(matterId: string, title: string): Promise<string> {
  const task = await apiOk<{ id: string }>(`/api/matters/${matterId}/tasks`, {
    method: "POST",
    body: { title },
  });
  return task.id;
}

/** Today as YYYY-MM-DD in UTC (the frontend and backend containers run on UTC). */
export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

/** A matter-scoped ZAR hourly billing rate for a member, effective from the 1st of this month. */
export async function createMatterRate(
  matterId: string,
  memberId: string,
  hourlyRate: number
): Promise<void> {
  await apiOk("/api/billing-rates", {
    method: "POST",
    body: {
      memberId,
      matterId,
      currency: "ZAR",
      hourlyRate,
      effectiveFrom: `${todayUtc().slice(0, 8)}01`,
    },
  });
}

/** Logs billable time on a task as `user`; returns the time entry id. */
export async function logTime(
  taskId: string,
  minutes: number,
  description: string,
  user: SeedUser = "alice"
): Promise<string> {
  const entry = await apiOk<{ id: string }>(
    `/api/tasks/${taskId}/time-entries`,
    {
      method: "POST",
      body: { date: todayUtc(), durationMinutes: minutes, billable: true, description },
    },
    user
  );
  return entry.id;
}
