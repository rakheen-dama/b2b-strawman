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
const mockReverseTransaction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  recordPayment: vi.fn(),
  reverseTransaction: (...args: unknown[]) => mockReverseTransaction(...args),
}));

import userEvent from "@testing-library/user-event";
import { createIdempotencyKeys, useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { RecordPaymentDialog } from "@/components/trust/RecordPaymentDialog";
import { ReversalButton } from "@/components/trust/reversal-button";

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
    // Expired: removed from storage, never reused — and the hint still shows, since that submit's
    // outcome was never known (CodeRabbit round 1 ruling)
    expect(nextDay.hasUnsettled(["recordPayment", "acc-1"])).toBe(true);
    expect(storage.length).toBe(0);
    await nextDay.run(REQUEST, async (key) => (seen.push(key), { success: true }));
    expect(seen).toEqual(["page1-1", "page2-1"]);
  });

  // CodeRabbit round 1 (Major): the TTL is anchored to the key's FIRST store. A retry must never
  // refresh it, or retries under 24 h apart keep a key alive past the backend's 7-day purge —
  // after which the same key runs as a new request (a double post if the first one committed).
  describe("the 24 h bound is anchored to the first store", () => {
    const HOUR = 60 * 60 * 1000;
    const T0 = Date.UTC(2026, 10, 23, 8, 0, 0);

    function storedAt(storage: Storage): number[] {
      const at: number[] = [];
      for (let i = 0; i < storage.length; i++) {
        at.push(JSON.parse(storage.getItem(storage.key(i)!)!).at);
      }
      return at;
    }

    it("a retry within 24 h (after a reload) keeps the original timestamp", async () => {
      const storage = memoryStorage();
      let now = T0;
      const clock = () => now;
      await createIdempotencyKeys(sequentialKeys("page1"), { storage, now: clock }).run(
        REQUEST,
        unknown
      );
      now = T0 + HOUR;
      await createIdempotencyKeys(sequentialKeys("page2"), { storage, now: clock }).run(
        REQUEST,
        unknown
      );
      expect(storedAt(storage)).toEqual([T0]);
    });

    it("a retry within 24 h (same page, in-memory key) keeps the original timestamp", async () => {
      const storage = memoryStorage();
      let now = T0;
      const keys = createIdempotencyKeys(sequentialKeys(), { storage, now: () => now });
      await keys.run(REQUEST, unknown);
      now = T0 + HOUR;
      await keys.run(REQUEST, unknown);
      expect(storedAt(storage)).toEqual([T0]);
    });

    it("retries cannot stretch the bound: 24 h after the first store the stored key expires", async () => {
      const storage = memoryStorage();
      let now = T0;
      const clock = () => now;
      const seen: string[] = [];
      await createIdempotencyKeys(sequentialKeys("page1"), { storage, now: clock }).run(
        REQUEST,
        async (key) => (seen.push(key), unknown())
      );
      now = T0 + 12 * HOUR; // a retry half-way through, after a reload
      await createIdempotencyKeys(sequentialKeys("page2"), { storage, now: clock }).run(
        REQUEST,
        async (key) => (seen.push(key), unknown())
      );
      now = T0 + 24 * HOUR + 1;
      const page3 = createIdempotencyKeys(sequentialKeys("page3"), { storage, now: clock });
      expect(page3.hasUnsettled(["recordPayment", "acc-1"])).toBe(true); // the hint still shows
      await page3.run(REQUEST, async (key) => (seen.push(key), { success: true }));
      expect(seen).toEqual(["page1-1", "page1-1", "page3-1"]);
    });

    it("the in-memory key expires on the same bound: a fresh key plus the hint", async () => {
      let now = T0;
      const seen: string[] = [];
      const keys = createIdempotencyKeys(sequentialKeys(), { storage: null, now: () => now });
      await keys.run(REQUEST, async (key) => (seen.push(key), unknown()));
      now = T0 + 12 * HOUR;
      await keys.run(REQUEST, async (key) => (seen.push(key), unknown()));
      now = T0 + 24 * HOUR + 1;
      expect(keys.hasUnsettled(["recordPayment", "acc-1"])).toBe(true);
      await keys.run(REQUEST, async (key) => (seen.push(key), { success: true }));
      expect(seen).toEqual(["key-1", "key-1", "key-2"]);
    });
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

describe("the 'Submit as a new entry' escape action (§1.5 ruling)", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    mockReverseTransaction.mockReset();
  });

  const ESCAPE = { name: "Submit as a new entry" };
  const REVERSAL = { reason: "Paid twice", reversalDate: "" };

  async function openReversal() {
    const user = userEvent.setup();
    render(<ReversalButton transactionId="tx-1" />);
    await user.click(screen.getByTestId("reverse-button"));
    await user.type(screen.getByTestId("reversal-reason-input"), REVERSAL.reason);
    return user;
  }

  function storedKeys(): string[] {
    const keys: string[] = [];
    for (let i = 0; i < window.sessionStorage.length; i++) {
      const name = window.sessionStorage.key(i)!;
      keys.push(JSON.parse(window.sessionStorage.getItem(name)!).key);
    }
    return keys;
  }

  it("appears on the in-progress 409; using it sends a fresh key and clears the stored one", async () => {
    mockReverseTransaction
      .mockResolvedValueOnce({ success: false, outcomeUnknown: true, error: "in progress" })
      .mockResolvedValueOnce({ success: true });
    const user = await openReversal();
    await user.click(screen.getByTestId("confirm-reverse-button"));

    expect(await screen.findByRole("button", ESCAPE)).toBeInTheDocument();
    expect(screen.getByText(/checked the ledger/)).toBeInTheDocument();
    const stuckKey = mockReverseTransaction.mock.calls[0][2] as string;
    expect(storedKeys()).toEqual([stuckKey]);

    await user.click(screen.getByRole("button", ESCAPE));

    await waitFor(() => expect(mockReverseTransaction).toHaveBeenCalledTimes(2));
    const [, data, freshKey] = mockReverseTransaction.mock.calls[1];
    expect(data).toEqual(REVERSAL);
    expect(freshKey).not.toBe(stuckKey);
    expect(storedKeys()).toEqual([]);
  });

  it("appears when the form opens with a stored unsettled key; it never reuses that key", async () => {
    await createIdempotencyKeys(() => "stale-key").run(
      ["reverseTransaction", "tx-1", REVERSAL],
      unknown
    );
    mockReverseTransaction.mockResolvedValueOnce({ success: true });
    const user = await openReversal();

    expect(screen.getByText(HINT)).toBeInTheDocument();
    await user.click(screen.getByRole("button", ESCAPE));

    await waitFor(() => expect(mockReverseTransaction).toHaveBeenCalledTimes(1));
    expect(mockReverseTransaction.mock.calls[0][2]).not.toBe("stale-key");
    expect(storedKeys()).toEqual([]);
  });

  it("is never automatic: a plain resubmit after the 409 reuses the stuck key", async () => {
    mockReverseTransaction.mockResolvedValue({ success: false, outcomeUnknown: true, error: "x" });
    const user = await openReversal();
    await user.click(screen.getByTestId("confirm-reverse-button"));
    await screen.findByRole("button", ESCAPE);
    await user.click(screen.getByTestId("confirm-reverse-button"));
    await waitFor(() => expect(mockReverseTransaction).toHaveBeenCalledTimes(2));
    expect(mockReverseTransaction.mock.calls[1][2]).toBe(mockReverseTransaction.mock.calls[0][2]);
  });

  it("is absent when nothing is unsettled, and after a definite refusal", async () => {
    mockReverseTransaction.mockResolvedValueOnce({
      success: false,
      error: "Insufficient trust balance",
    });
    const user = await openReversal();
    expect(screen.queryByRole("button", ESCAPE)).not.toBeInTheDocument();
    await user.click(screen.getByTestId("confirm-reverse-button"));
    expect(await screen.findByText("Insufficient trust balance")).toBeInTheDocument();
    expect(screen.queryByRole("button", ESCAPE)).not.toBeInTheDocument();
    expect(storedKeys()).toEqual([]);
  });
});
