"use client";

import useSWR from "swr";
import { fetchInterestEligibleInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import type { TrustAccountSection, TrustInvestment } from "@/lib/types";

const NONE: TrustInvestment[] = [];

/**
 * 648B: a section 86(4) account's interest is recorded against one of its investments — the
 * ACTIVE ones whose placement is approved. Keyed by account, so a switch never shows another
 * account's investments; no fetch for any other section. A failure is surfaced with a retry.
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
    error: error ? "The account's investments could not be loaded." : null,
    retry: () => void mutate(),
  };
}
