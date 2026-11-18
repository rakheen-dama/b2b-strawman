"use client";

import useSWR from "swr";
import { fetchInterestEligibleInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import type { TrustAccountSection, TrustInvestment } from "@/lib/types";

const NONE: TrustInvestment[] = [];

/** Nothing loaded: there is no investment to record against. */
export const INVESTMENTS_NOT_LOADED =
  "The account's investments could not be loaded. Section 86(4) interest cannot be recorded until they load.";
/** A revalidation failed after a load: SWR keeps the last list, and recording against it stays open. */
export const INVESTMENTS_NOT_REFRESHED =
  "Couldn't refresh the investments — showing the last loaded list.";

/**
 * 648B: a section 86(4) account's interest is recorded against one of its investments — the
 * ACTIVE ones whose placement is approved. Keyed by account, so a switch never shows another
 * account's investments; no fetch for any other section. A failure is surfaced with a retry, and
 * its message says what the pickers can do: nothing without a list, or use the last loaded list.
 */
export function useInterestEligibleInvestments(
  accountId: string,
  section: TrustAccountSection | undefined
) {
  const key = section === "S86_4" && accountId ? `s86-4-interest-investments-${accountId}` : null;
  const { data, error, isLoading, mutate } = useSWR<TrustInvestment[]>(key, () =>
    fetchInterestEligibleInvestments(accountId)
  );
  return {
    investments: data ?? NONE,
    isLoading,
    error: error ? (data === undefined ? INVESTMENTS_NOT_LOADED : INVESTMENTS_NOT_REFRESHED) : null,
    retry: () => void mutate(),
  };
}
