"use client";

import { useState } from "react";
import type { TrustWriteOutcome } from "@/lib/idempotency";

/**
 * One `Idempotency-Key` per submit intent (648B F-11).
 *
 * `run(request, call)` hands `call` a key for this submit:
 * - a fresh key, unless the same request's previous submit ended with its outcome unknown (the
 *   action threw — e.g. the network dropped — or returned `outcomeUnknown`), in which case that
 *   key is reused, so the backend replays the first result instead of posting twice;
 * - after a success or a definite refusal the request is settled, and the next submit — even an
 *   identical one — is a new intent with a new key;
 * - a changed request (another amount, client, line…) is a new intent with a new key.
 *
 * `request` identifies the intent: pass every value the write is made of (ids and form values).
 */
export function createIdempotencyKeys(newKey: () => string = () => crypto.randomUUID()) {
  // Requests whose last submit ended unknown, by their serialised form → the key to reuse.
  const unsettled = new Map<string, string>();

  async function run<T extends TrustWriteOutcome>(
    request: unknown,
    call: (idempotencyKey: string) => Promise<T>
  ): Promise<T> {
    const id = JSON.stringify(request) ?? "";
    const key = unsettled.get(id) ?? newKey();
    unsettled.delete(id); // in flight: a concurrent identical submit is its own intent
    let result: T;
    try {
      result = await call(key);
    } catch (error) {
      unsettled.set(id, key);
      throw error;
    }
    if (!result.success && result.outcomeUnknown) {
      unsettled.set(id, key);
    }
    return result;
  }

  return { run };
}

export type IdempotencyKeys = ReturnType<typeof createIdempotencyKeys>;

/** {@link createIdempotencyKeys} kept for the component's lifetime. */
export function useIdempotencyKey(): IdempotencyKeys {
  const [keys] = useState(() => createIdempotencyKeys());
  return keys;
}
