import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, renderHook } from "@testing-library/react";

// 648B F-11: every trust write carries an Idempotency-Key. The key is one per submit intent: the
// same key on a resubmit whose outcome is unknown (so the backend replays instead of posting
// twice), a new key after any definite answer or for a changed request — and the server action
// sends it as the `Idempotency-Key` header.

vi.mock("server-only", () => ({}));

const mockPost = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { post: (...args: unknown[]) => mockPost(...args), get: vi.fn() },
}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { createIdempotencyKeys, useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { isOutcomeUnknown, outcomeUnknownOf } from "@/lib/idempotency";
import { recordDeposit } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";

const DEPOSIT = {
  customerId: "client-1",
  projectId: "matter-1",
  amount: 1000,
  reference: "DEP-1",
  transactionDate: "2026-03-01",
};

function sequentialKeys() {
  let n = 0;
  return () => `key-${++n}`;
}

afterEach(() => cleanup());

describe("useIdempotencyKey (648B F-11)", () => {
  it("keeps the same key when a resubmit follows a network failure", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    const seen: string[] = [];
    await expect(
      keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
        seen.push(key);
        throw new TypeError("Failed to fetch");
      })
    ).rejects.toThrow("Failed to fetch");
    await keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
      seen.push(key);
      return { success: true };
    });
    expect(seen).toEqual(["key-1", "key-1"]);
  });

  it("keeps the same key when the action reports the outcome unknown (a 5xx, in progress)", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    const seen: string[] = [];
    for (let i = 0; i < 3; i++) {
      await keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
        seen.push(key);
        return { success: false, outcomeUnknown: true };
      });
    }
    expect(seen).toEqual(["key-1", "key-1", "key-1"]);
  });

  it("takes a new key after a success and after a definite refusal", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    const seen: string[] = [];
    const submit = (result: { success: boolean; outcomeUnknown?: boolean }) =>
      keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
        seen.push(key);
        return result;
      });
    await submit({ success: true });
    await submit({ success: true }); // an identical second deposit is a new intent
    await submit({ success: false }); // refused: nothing posted
    await submit({ success: false });
    expect(seen).toEqual(["key-1", "key-2", "key-3", "key-4"]);
  });

  it("takes a new key for a changed request, even after an unknown outcome", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    const seen: string[] = [];
    await keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
      seen.push(key);
      return { success: false, outcomeUnknown: true };
    });
    await keys.run(["recordDeposit", "acc-1", { ...DEPOSIT, amount: 2000 }], async (key) => {
      seen.push(key);
      return { success: true };
    });
    // the first request's key is still kept for its own retry
    await keys.run(["recordDeposit", "acc-1", DEPOSIT], async (key) => {
      seen.push(key);
      return { success: true };
    });
    expect(seen).toEqual(["key-1", "key-2", "key-1"]);
  });

  it("gives concurrent identical submits their own keys (each is an intent)", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    const seen: string[] = [];
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));
    const first = keys.run(["calculateReconciliation", "r-1", "s-1"], async (key) => {
      seen.push(key);
      await gate;
      return { success: true };
    });
    const second = keys.run(["calculateReconciliation", "r-1", "s-1"], async (key) => {
      seen.push(key);
      return { success: true };
    });
    release();
    await Promise.all([first, second]);
    expect(seen).toEqual(["key-1", "key-2"]);
  });

  it("keeps one key store for the component's lifetime, with UUID keys", async () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey());
    const store = result.current;
    rerender();
    expect(result.current).toBe(store);
    let key = "";
    await act(async () => {
      await result.current.run(["approveTransaction", "tx-1"], async (k) => {
        key = k;
        return { success: true };
      });
    });
    expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });
});

describe("outcome classification (648B F-11)", () => {
  it("treats no response, a 5xx and the in-progress 409 as unknown; any other answer as definite", () => {
    expect(isOutcomeUnknown(undefined)).toBe(true);
    expect(isOutcomeUnknown(500)).toBe(true);
    expect(isOutcomeUnknown(503)).toBe(true);
    expect(isOutcomeUnknown(409, "idempotency_in_progress")).toBe(true);
    expect(isOutcomeUnknown(409, "idempotency_key_reused")).toBe(false);
    expect(isOutcomeUnknown(409)).toBe(false);
    expect(isOutcomeUnknown(400)).toBe(false);
    expect(outcomeUnknownOf(new TypeError("fetch failed"))).toBe(true);
    expect(outcomeUnknownOf(Object.assign(new Error("Refused"), { status: 400 }))).toBe(false);
    expect(
      outcomeUnknownOf(
        Object.assign(new Error("In progress"), {
          status: 409,
          detail: { code: "idempotency_in_progress" },
        })
      )
    ).toBe(true);
  });
});

describe("trust write server action (648B F-11)", () => {
  beforeEach(() => {
    mockPost.mockReset();
  });

  it("sends the key as the Idempotency-Key header", async () => {
    mockPost.mockResolvedValue({});

    await recordDeposit("acc-1", DEPOSIT, "3b241101-e2bb-4255-8caf-4136c566a962");

    expect(mockPost).toHaveBeenCalledWith(
      "/api/trust-accounts/acc-1/transactions/deposit",
      expect.objectContaining({ clientId: "client-1", amount: 1000 }),
      { headers: { "Idempotency-Key": "3b241101-e2bb-4255-8caf-4136c566a962" } }
    );
  });

  it("flags an unknown outcome on a network failure and not on a refusal", async () => {
    mockPost.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect(await recordDeposit("acc-1", DEPOSIT, "k")).toEqual({
      success: false,
      error: "fetch failed",
      outcomeUnknown: true,
    });

    mockPost.mockRejectedValueOnce(
      Object.assign(new Error("Insufficient trust balance"), { status: 400 })
    );
    expect(await recordDeposit("acc-1", DEPOSIT, "k")).toEqual({
      success: false,
      error: "Insufficient trust balance",
    });
  });

  it("shows the in-progress refusal's instruction and keeps the key (outcome unknown)", async () => {
    const detail =
      "An earlier submit of this entry is still in progress, or its outcome is unknown. Check the" +
      " ledger to see whether the entry was recorded; if it was not, reload the page and submit" +
      " again.";
    mockPost.mockRejectedValueOnce(
      Object.assign(new Error(detail), {
        status: 409,
        detail: { title: "Request still in progress", detail, code: "idempotency_in_progress" },
      })
    );

    expect(await recordDeposit("acc-1", DEPOSIT, "k")).toEqual({
      success: false,
      error: detail,
      outcomeUnknown: true,
    });
  });

  it("a resubmit through the hook after a lost response reuses the key on the wire", async () => {
    const keys = createIdempotencyKeys(sequentialKeys());
    mockPost.mockRejectedValueOnce(new TypeError("fetch failed")).mockResolvedValueOnce({});

    await keys.run(["recordDeposit", "acc-1", DEPOSIT], (key) =>
      recordDeposit("acc-1", DEPOSIT, key)
    );
    await keys.run(["recordDeposit", "acc-1", DEPOSIT], (key) =>
      recordDeposit("acc-1", DEPOSIT, key)
    );

    const sent = mockPost.mock.calls.map((call) => call[2].headers["Idempotency-Key"]);
    expect(sent).toEqual(["key-1", "key-1"]);
  });
});
