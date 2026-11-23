import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, renderHook, screen, waitFor } from "@testing-library/react";

// Phase 92 residual §1.5 (648B F-11 residual, ruled option (a)): an Idempotency-Key whose outcome
// is unknown survives a page reload. It is kept in sessionStorage, keyed by the form's request
// (its endpoint identifier and normalised content), so a reload followed by the same submit reuses
// it — the backend replays the first result (or answers "in progress") and never posts twice.
// Settled (success, definite refusal) → removed; older than the TTL → ignored and removed; storage
// unavailable → the in-memory behaviour of F-11.

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api", () => ({ api: { post: vi.fn(), get: vi.fn() } }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  recordPayment: vi.fn(),
}));

import { createIdempotencyKeys, useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { RecordPaymentDialog } from "@/components/trust/RecordPaymentDialog";

const PAYMENT = {
  customerId: "client-1",
  projectId: "matter-1",
  amount: 1150,
  reference: "PAY-1",
  description: "",
  transactionDate: "2026-03-05",
};
const REQUEST = ["recordPayment", "acc-1", PAYMENT];

const HINT =
  "A submit of this entry may already have been recorded — check the ledger before submitting again.";

/** A Storage stand-in shared by "page loads" (the tab's sessionStorage survives a reload). */
function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    clear: () => data.clear(),
    getItem: (k: string) => (data.has(k) ? data.get(k)! : null),
    key: (i: number) => Array.from(data.keys())[i] ?? null,
    removeItem: (k: string) => void data.delete(k),
    setItem: (k: string, v: string) => void data.set(k, String(v)),
  };
}

function sequentialKeys(prefix = "key") {
  let n = 0;
  return () => `${prefix}-${++n}`;
}

const unknown = async () => ({ success: false, outcomeUnknown: true });

afterEach(() => cleanup());

describe("an unsettled Idempotency-Key survives a reload (§1.5)", () => {
  it("a resubmit after a reload (a new key store, same storage, same content) reuses the key", async () => {
    const storage = memoryStorage();
    const seen: string[] = [];
    const firstPage = createIdempotencyKeys(sequentialKeys("page1"), { storage });
    await expect(
      firstPage.run(REQUEST, async (key) => {
        seen.push(key);
        throw new TypeError("Failed to fetch"); // the response was lost; then the page reloads
      })
    ).rejects.toThrow("Failed to fetch");

    const afterReload = createIdempotencyKeys(sequentialKeys("page2"), { storage });
    await afterReload.run(["recordPayment", "acc-1", { ...PAYMENT }], async (key) => {
      seen.push(key);
      return { success: true };
    });

    expect(seen).toEqual(["page1-1", "page1-1"]);
  });

  it("keeps the key while the first submit is still in flight (a reload mid-request)", async () => {
    const storage = memoryStorage();
    const seen: string[] = [];
    const firstPage = createIdempotencyKeys(sequentialKeys("page1"), { storage });
    void firstPage.run(REQUEST, (key) => {
      seen.push(key);
      return new Promise<{ success: boolean }>(() => {}); // never answers: the page goes away
    });
    const afterReload = createIdempotencyKeys(sequentialKeys("page2"), { storage });
    await afterReload.run(REQUEST, async (key) => {
      seen.push(key);
      return { success: true };
    });
    expect(seen).toEqual(["page1-1", "page1-1"]);
  });

  it("a success clears the stored key; the next identical submit is a new intent", async () => {
    const storage = memoryStorage();
    const seen: string[] = [];
    const keys = createIdempotencyKeys(sequentialKeys(), { storage });
    await keys.run(REQUEST, async (key) => (seen.push(key), unknown()));
    await keys.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(storage.length).toBe(0);
    const afterReload = createIdempotencyKeys(sequentialKeys("page2"), { storage });
    await afterReload.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(seen).toEqual(["key-1", "key-1", "page2-1"]);
  });

  it("a definite refusal clears the stored key", async () => {
    const storage = memoryStorage();
    const keys = createIdempotencyKeys(sequentialKeys(), { storage });
    await keys.run(REQUEST, unknown);
    expect(storage.length).toBe(1);
    await keys.run(REQUEST, async () => ({ success: false })); // e.g. 400 Insufficient trust balance
    expect(storage.length).toBe(0);
    expect(keys.hasUnsettled(["recordPayment", "acc-1"])).toBe(false);
  });

  it("changed content gets a new key, and the first request's key stays for its own retry", async () => {
    const storage = memoryStorage();
    const seen: string[] = [];
    const firstPage = createIdempotencyKeys(sequentialKeys("page1"), { storage });
    await firstPage.run(REQUEST, async (key) => (seen.push(key), unknown()));
    const afterReload = createIdempotencyKeys(sequentialKeys("page2"), { storage });
    await afterReload.run(
      ["recordPayment", "acc-1", { ...PAYMENT, amount: 2000 }],
      async (key) => (seen.push(key), { success: true })
    );
    await afterReload.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(seen).toEqual(["page1-1", "page2-1", "page1-1"]);
  });

  it("ignores and removes an entry older than the TTL (24 hours)", async () => {
    const storage = memoryStorage();
    let now = Date.UTC(2026, 10, 23, 8, 0, 0);
    const clock = () => now;
    const seen: string[] = [];
    const firstPage = createIdempotencyKeys(sequentialKeys("page1"), { storage, now: clock });
    await firstPage.run(REQUEST, async (key) => (seen.push(key), unknown()));

    now += 24 * 60 * 60 * 1000 + 1;
    const nextDay = createIdempotencyKeys(sequentialKeys("page2"), { storage, now: clock });
    expect(nextDay.hasUnsettled(["recordPayment", "acc-1"])).toBe(false);
    expect(storage.length).toBe(0);
    await nextDay.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(seen).toEqual(["page1-1", "page2-1"]);
  });

  it("falls back to in-memory keys when storage is unavailable (every access throws)", async () => {
    const broken = new Proxy({} as Storage, {
      get() {
        throw new DOMException("The operation is insecure.", "SecurityError");
      },
    });
    const seen: string[] = [];
    const keys = createIdempotencyKeys(sequentialKeys(), { storage: broken });
    await keys.run(REQUEST, async (key) => (seen.push(key), unknown()));
    await keys.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    await keys.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(seen).toEqual(["key-1", "key-1", "key-2"]);
    expect(keys.hasUnsettled(["recordPayment", "acc-1"])).toBe(false);
  });

  it("uses the tab's sessionStorage by default", async () => {
    window.sessionStorage.clear();
    const seen: string[] = [];
    await createIdempotencyKeys(sequentialKeys("page1")).run(
      REQUEST,
      async (key) => (seen.push(key), unknown())
    );
    await createIdempotencyKeys(sequentialKeys("page2")).run(
      REQUEST,
      async (key) => (seen.push(key), { success: true })
    );
    expect(seen).toEqual(["page1-1", "page1-1"]);
    expect(window.sessionStorage.length).toBe(0);
  });
});

describe("the unsettled-submit hint (§1.5)", () => {
  beforeEach(() => window.sessionStorage.clear());

  it("useIdempotencyKey reports a stored unsettled key for its form's scope", async () => {
    await createIdempotencyKeys(sequentialKeys()).run(REQUEST, unknown);
    const { result } = renderHook(() => useIdempotencyKey(["recordPayment", "acc-1"]));
    await waitFor(() => expect(result.current.unsettled).toBe(true));
    const other = renderHook(() => useIdempotencyKey(["recordPayment", "acc-2"]));
    expect(other.result.current.unsettled).toBe(false);

    await act(async () => {
      await result.current.run(REQUEST, async () => ({ success: true }));
    });
    expect(result.current.unsettled).toBe(false);
  });

  it("shows the hint in the payment dialog when its form has a stored unsettled key", async () => {
    await createIdempotencyKeys(sequentialKeys()).run(REQUEST, unknown);
    render(
      <RecordPaymentDialog
        accountId="acc-1"
        open
        onOpenChange={() => {}}
        customers={[]}
        lockedThrough={null}
      />
    );
    expect(await screen.findByText(HINT)).toBeInTheDocument();
  });

  it("shows no hint when nothing is unsettled", async () => {
    render(
      <RecordPaymentDialog
        accountId="acc-1"
        open
        onOpenChange={() => {}}
        customers={[]}
        lockedThrough={null}
      />
    );
    await screen.findByRole("heading", { name: "Record Payment" });
    expect(screen.queryByText(HINT)).not.toBeInTheDocument();
  });
});
