/**
 * Backend API helpers for default-config spec setup (Phase 88 628F).
 *
 * Setup only: a spec drives the behaviour it is named after through the UI and uses these to build
 * its own run-unique entities (628F R-5 — the boot-seed rows in `seed.ts` are read-only). `apiOk`
 * and the builders assert the answer, so a refused setup step fails the test where it happened.
 * Tokens come from the mock IDP via `fixtures/auth.ts` `getApiToken` — the same token `loginAs`
 * puts in the browser.
 */
import { expect } from "@playwright/test";
import { getApiToken } from "../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";

export type SeedUser = NonNullable<Parameters<typeof getApiToken>[0]>;

export interface ApiInit {
  method?: string;
  /** JSON-serialised when present. */
  body?: unknown;
}

/** A raw backend call as `user` (JSON body). */
export async function api(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, {
    method: init.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await getApiToken(user)}`,
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
}

/** A backend call that must answer 2xx; returns the parsed JSON body (or null when empty). */
export async function apiOk<T = Record<string, unknown>>(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<T> {
  const res = await api(path, init, user);
  const text = await res.text();
  expect(res.ok, `${init.method ?? "GET"} ${path} → ${res.status} ${text}`).toBe(true);
  return (text ? JSON.parse(text) : null) as T;
}

/** A created / read backend entity: its id plus whatever fields the caller asserts on. */
export type ApiEntity = { id: string } & Record<string, unknown>;

/**
 * A backend call whose status the caller asserts (a specific 2xx, or a refusal); returns the status
 * and the parsed JSON body (null when empty or not JSON).
 */
export async function apiStatus<T = ApiEntity>(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<{ status: number; data: T }> {
  const res = await api(path, init, user);
  const text = await res.text();
  let data = null as T;
  try {
    data = (text ? JSON.parse(text) : null) as T;
  } catch {
    // A non-JSON error body: the caller asserts on the status
  }
  return { status: res.status, data };
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
 * Settles every onboarding checklist item: skip it when it needs a linked document or depends on a
 * skipped item, otherwise complete it; any other refusal fails loudly. Settling the last item
 * auto-activates a client whose activation prerequisites (billing address) are on file.
 */
export async function settleChecklists(clientId: string): Promise<void> {
  const checklists = await apiOk<
    {
      items?: {
        id: string;
        status: string;
        requiresDocument: boolean;
        dependsOnItemId: string | null;
      }[];
    }[]
  >(`/api/clients/${clientId}/checklists`);
  expect(checklists.length, "an onboarding checklist was instantiated").toBeGreaterThan(0);
  for (const checklist of checklists) {
    const items = checklist.items ?? [];
    const skipped = new Set(items.filter((i) => i.status === "SKIPPED").map((i) => i.id));
    for (const item of items) {
      if (item.status === "COMPLETED" || item.status === "SKIPPED") continue;
      // A dependent of a skipped item can't be completed (409 "Checklist item blocked"), so it is
      // skipped too.
      if (item.requiresDocument || (item.dependsOnItemId && skipped.has(item.dependsOnItemId))) {
        skipped.add(item.id);
        await apiOk(`/api/checklist-items/${item.id}/skip`, {
          method: "PUT",
          body: { reason: "E2E: no document to link" },
        });
      } else {
        await apiOk(`/api/checklist-items/${item.id}/complete`, {
          method: "PUT",
          body: { notes: "E2E auto-complete" },
        });
      }
    }
  }
}

export interface CreatedClient {
  id: string;
  name: string;
  lifecycleStatus: string;
}

/**
 * A fresh PROSPECT individual client. With a billing address by default — LIFECYCLE_ACTIVATION and
 * fee notes need address line 1 / city / country; `withAddress: false` leaves it off.
 */
export async function createClient(
  name: string,
  email: string,
  { withAddress = true }: { withAddress?: boolean } = {}
): Promise<CreatedClient> {
  const { status, data } = await apiStatus<CreatedClient>("/api/clients", {
    method: "POST",
    body: {
      name,
      email,
      phone: "+27-11-555-0199",
      ...(withAddress
        ? { addressLine1: "12 Rivonia Road", city: "Sandton", postalCode: "2196", country: "ZA" }
        : {}),
    },
  });
  expect(status, `POST /api/clients "${name}"`).toBe(201);
  return data;
}

/** A client's lifecycle status, read from the API. */
export async function clientStatus(clientId: string): Promise<string> {
  return (await apiOk<{ lifecycleStatus: string }>(`/api/clients/${clientId}`)).lifecycleStatus;
}

/**
 * A fresh ACTIVE individual client with a billing address (fee notes need one) and an
 * auto-provisioned portal contact for `email`: PROSPECT → ONBOARDING → checklist auto-activation.
 */
export async function createActiveClient(name: string, email: string): Promise<string> {
  const client = await createClient(name, email);
  await apiOk(`/api/clients/${client.id}/transition`, {
    method: "POST",
    body: { targetStatus: "ONBOARDING", notes: "E2E setup" },
  });
  await settleChecklists(client.id);
  await expect.poll(() => clientStatus(client.id)).toBe("ACTIVE");
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

export interface FeeNoteLine {
  id: string;
  description: string;
  amount: number;
  unitPrice: number;
}

export interface FeeNote {
  id: string;
  status: string;
  subtotal: number;
  total: number;
  lines: FeeNoteLine[];
}

/** A fresh DRAFT ZAR fee note for a client, with no time entries and no lines. */
export async function createDraftFeeNote(clientId: string, notes: string): Promise<FeeNote> {
  const { status, data } = await apiStatus<FeeNote>("/api/invoices", {
    method: "POST",
    body: { clientId, currency: "ZAR", timeEntryIds: [], notes },
  });
  expect(status, "POST /api/invoices").toBe(201);
  return data;
}

/** Adds a manual line to a DRAFT fee note through the API; returns the updated fee note. */
export async function addFeeNoteLineViaApi(
  feeNoteId: string,
  description: string,
  quantity: number,
  unitPrice: number
): Promise<FeeNote> {
  const { status, data } = await apiStatus<FeeNote>(`/api/invoices/${feeNoteId}/lines`, {
    method: "POST",
    body: { description, quantity, unitPrice, sortOrder: 0 },
  });
  expect(status, `POST /api/invoices/${feeNoteId}/lines`).toBe(201);
  return data;
}

/** Approves a DRAFT fee note, then sends it without overriding send warnings. */
export async function approveAndSendFeeNote(feeNoteId: string): Promise<void> {
  await apiOk(`/api/invoices/${feeNoteId}/approve`, { method: "POST" });
  await apiOk(`/api/invoices/${feeNoteId}/send`, {
    method: "POST",
    body: { overrideWarnings: false },
  });
}
