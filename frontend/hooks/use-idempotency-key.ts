"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { TrustWriteOutcome } from "@/lib/idempotency";

/**
 * How long an unsettled key is kept in `sessionStorage` (Phase 92 residual §1.5): 24 hours. A
 * reload-and-retry happens within minutes; the bound must stay well inside the backend's 7-day key
 * retention (a key reused after the backend purged it would run as a new request), and it stops a
 * stale entry from showing the hint for days. `sessionStorage` is per tab and cleared when the tab
 * closes, so most entries go long before this.
 */
export const UNSETTLED_KEY_TTL_MS = 24 * 60 * 60 * 1000;

/** The `sessionStorage` key prefix of an unsettled request's entry. */
const STORAGE_PREFIX = "kazi.trust-idempotency:";

interface StoredKey {
  key: string;
  at: number;
}

export interface IdempotencyKeyOptions {
  /** Where unsettled keys survive a reload; default the tab's `sessionStorage`, null = memory only. */
  storage?: Storage | null;
  /** The clock for the TTL (tests). */
  now?: () => number;
}

/** The tab's `sessionStorage`, or null where it is unavailable (SSR, blocked, private mode). */
function defaultStorage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

/**
 * One `Idempotency-Key` per submit intent (648B F-11; reload-safe since Phase 92 residual §1.5).
 *
 * `run(request, call)` hands `call` a key for this submit:
 * - a fresh key, unless the same request's previous submit ended with its outcome unknown (the
 *   action threw — e.g. the network dropped — or returned `outcomeUnknown`), in which case that
 *   key is reused, so the backend replays the first result instead of posting twice;
 * - after a success or a definite refusal the request is settled, and the next submit — even an
 *   identical one — is a new intent with a new key;
 * - a changed request (another amount, client, line…) is a new intent with a new key.
 *
 * `request` identifies the intent: pass every value the write is made of — the action's name, its
 * ids and the form values. Its JSON form is the intent's identity.
 *
 * §1.5: while a request's outcome is unknown — from the moment it is sent until a definite answer
 * — its key is also kept in `storage` (default `sessionStorage`) under that identity, so a reload
 * (or a remount) followed by the same submit reuses it. A success or a definite refusal removes
 * the entry; an entry older than {@link UNSETTLED_KEY_TTL_MS} is ignored and removed. Every storage
 * access is guarded: when storage is unavailable the keys live in memory only, as in F-11. A
 * second device (or a second tab, whose `sessionStorage` is its own) is not covered.
 */
export function createIdempotencyKeys(
  newKey: () => string = () => crypto.randomUUID(),
  options: IdempotencyKeyOptions = {}
) {
  const storage = "storage" in options ? (options.storage ?? null) : defaultStorage();
  const now = options.now ?? Date.now;
  // Requests whose last submit ended unknown, by their serialised form → the key to reuse.
  const unsettled = new Map<string, string>();
  // Requests of this store in flight: a concurrent identical submit is its own intent.
  const inFlight = new Set<string>();

  function storedKey(id: string): string | undefined {
    try {
      const raw = storage?.getItem(STORAGE_PREFIX + id);
      if (raw == null) return undefined;
      const entry = JSON.parse(raw) as Partial<StoredKey>;
      if (typeof entry.key !== "string" || typeof entry.at !== "number") {
        storage?.removeItem(STORAGE_PREFIX + id);
        return undefined;
      }
      if (now() - entry.at > UNSETTLED_KEY_TTL_MS) {
        storage?.removeItem(STORAGE_PREFIX + id);
        return undefined;
      }
      return entry.key;
    } catch {
      return undefined;
    }
  }

  function store(id: string, key: string) {
    try {
      storage?.setItem(STORAGE_PREFIX + id, JSON.stringify({ key, at: now() } satisfies StoredKey));
    } catch {
      // storage full or blocked: the in-memory map still holds the key for this page
    }
  }

  /** Removes the entry once settled — only if it is still this submit's key. */
  function settle(id: string, key: string) {
    try {
      if (storedKey(id) === key) {
        storage?.removeItem(STORAGE_PREFIX + id);
      }
    } catch {
      // ignore: the TTL ends a stale entry
    }
  }

  async function run<T extends TrustWriteOutcome>(
    request: unknown,
    call: (idempotencyKey: string) => Promise<T>
  ): Promise<T> {
    const id = JSON.stringify(request) ?? "";
    const key = unsettled.get(id) ?? (inFlight.has(id) ? undefined : storedKey(id)) ?? newKey();
    unsettled.delete(id); // in flight: a concurrent identical submit is its own intent
    inFlight.add(id);
    store(id, key); // unknown until answered: a reload mid-request keeps the key
    let result: T;
    try {
      result = await call(key);
    } catch (error) {
      unsettled.set(id, key);
      throw error;
    } finally {
      inFlight.delete(id);
    }
    if (!result.success && result.outcomeUnknown) {
      unsettled.set(id, key);
    } else {
      settle(id, key);
    }
    return result;
  }

  /**
   * Whether a request starting with `scope` (e.g. `["recordPayment", accountId]`, or a whole
   * request) has a stored unsettled key — the §1.5 hint. Expired entries are removed on the way.
   */
  function hasUnsettled(scope: readonly unknown[]): boolean {
    const inScope = scopeMatcher(scope);
    if (Array.from(unsettled.keys()).some(inScope)) return true; // this page (storage or not)
    return storedIds()
      .filter(inScope)
      .some((id) => storedKey(id) !== undefined);
  }

  /**
   * The §1.5 escape action ("Submit as a new entry"), a deliberate step after the person has
   * checked the ledger: forgets every unsettled key under `scope` — in this page and in storage —
   * so the next submit of that form takes a fresh key and runs as a new request. Never called
   * automatically.
   */
  function discard(scope: readonly unknown[]) {
    const inScope = scopeMatcher(scope);
    for (const id of Array.from(unsettled.keys())) {
      if (inScope(id)) unsettled.delete(id);
    }
    for (const id of storedIds().filter(inScope)) {
      try {
        storage?.removeItem(STORAGE_PREFIX + id);
      } catch {
        // ignore: the TTL ends a stale entry
      }
    }
  }

  /** The ids of the stored entries (empty when storage is unavailable). */
  function storedIds(): string[] {
    const ids: string[] = [];
    try {
      if (!storage) return ids;
      for (let i = 0; i < storage.length; i++) {
        const name = storage.key(i);
        if (name?.startsWith(STORAGE_PREFIX)) {
          ids.push(name.slice(STORAGE_PREFIX.length));
        }
      }
    } catch {
      // storage unavailable: nothing stored
    }
    return ids;
  }

  return { run, hasUnsettled, discard };
}

/** A request id (`JSON.stringify(request)`) is in `scope` when it equals it or extends it. */
function scopeMatcher(scope: readonly unknown[]): (id: string) => boolean {
  const whole = JSON.stringify(scope) ?? "";
  const prefix = whole.slice(0, -1) + ",";
  return (id) => id === whole || id.startsWith(prefix);
}

export type IdempotencyKeys = ReturnType<typeof createIdempotencyKeys>;

/**
 * {@link createIdempotencyKeys} kept for the component's lifetime, plus `unsettled`: whether a
 * submit of this form may already have been recorded (§1.5) — a stored unsettled key under
 * `scope` (read after mount, so the server render never touches storage), or, after a submit, its
 * own request left unsettled. Show {@code UnsettledSubmitNotice} when it is true.
 */
export function useIdempotencyKey(scope?: readonly unknown[]): IdempotencyKeys & {
  unsettled: boolean;
  discardUnsettled: () => void;
} {
  const [keys] = useState(() => createIdempotencyKeys());
  const [unsettled, setUnsettled] = useState(false);
  const scopeId = scope ? JSON.stringify(scope) : null;
  // The last request this form ran (the wizard has no single scope)
  const lastRequest = useRef<unknown[] | null>(null);

  useEffect(() => {
    if (scopeId !== null) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- reads sessionStorage after mount
      setUnsettled(keys.hasUnsettled(JSON.parse(scopeId) as unknown[]));
    }
  }, [keys, scopeId]);

  const run = useCallback(
    async <T extends TrustWriteOutcome>(
      request: unknown,
      call: (idempotencyKey: string) => Promise<T>
    ): Promise<T> => {
      // The form's scope when it has one, else this request itself (an array, as every call site
      // passes one)
      const refresh = () =>
        setUnsettled(
          scopeId !== null
            ? keys.hasUnsettled(JSON.parse(scopeId) as unknown[])
            : Array.isArray(request) && keys.hasUnsettled(request)
        );
      lastRequest.current = Array.isArray(request) ? request : null;
      try {
        const result = await keys.run(request, call);
        refresh();
        return result;
      } catch (error) {
        refresh();
        throw error;
      }
    },
    [keys, scopeId]
  );

  // §1.5 escape action: forget this form's unsettled keys (its scope, and the last request it
  // ran), so its next submit is a new request with a fresh key. Only ever on the person's click.
  const discardUnsettled = useCallback(() => {
    if (scopeId !== null) keys.discard(JSON.parse(scopeId) as unknown[]);
    if (lastRequest.current) keys.discard(lastRequest.current);
    setUnsettled(false);
  }, [keys, scopeId]);

  // Stable between renders (callers list it in hook deps); changes only when `unsettled` does
  return useMemo(
    () => ({
      run,
      hasUnsettled: keys.hasUnsettled,
      discard: keys.discard,
      unsettled,
      discardUnsettled,
    }),
    [run, keys, unsettled, discardUnsettled]
  );
}
