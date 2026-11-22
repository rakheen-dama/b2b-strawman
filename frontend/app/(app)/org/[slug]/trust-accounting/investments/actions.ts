"use server";

import { api } from "@/lib/api";
import type {
  BankStatementLineResponse,
  BankStatementResponse,
  InvestmentInterest,
  PlaceInvestmentRequest,
  RecordInvestmentInterestRequest,
  TrustInvestment,
} from "@/lib/types/trust";
import type {
  PlaceInvestmentFormData,
  RecordInvestmentInterestFormData,
  WithdrawInvestmentFormData,
} from "@/lib/schemas/trust";

// -- Investments ---------------------------------------------------------

type InvestmentPage = {
  content: TrustInvestment[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
};

function investmentsUrl(accountId: string, page: number, investmentBasis?: string): string {
  let url = `/api/trust-accounts/${accountId}/investments?page=${page}&size=100&sort=depositDate,desc`;
  if (investmentBasis) {
    url += `&investmentBasis=${encodeURIComponent(investmentBasis)}`;
  }
  return url;
}

export async function fetchInvestments(
  accountId: string,
  investmentBasis?: string
): Promise<TrustInvestment[]> {
  const response = await api.get<InvestmentPage>(investmentsUrl(accountId, 0, investmentBasis));
  return response.content;
}

/**
 * The funding account's section 86(4) investments that can take an interest entry (648B): ACTIVE,
 * with an approved placement. Reads every page — the list endpoint has no status filter, so an
 * eligible placement older than the newest 100 must not be missed.
 */
export async function fetchInterestEligibleInvestments(
  accountId: string
): Promise<TrustInvestment[]> {
  const all: TrustInvestment[] = [];
  for (let page = 0; ; page++) {
    const response = await api.get<InvestmentPage>(
      investmentsUrl(accountId, page, "CLIENT_INSTRUCTION")
    );
    all.push(...response.content);
    if (page + 1 >= response.page.totalPages) break;
  }
  return all.filter((inv) => inv.status === "ACTIVE" && !inv.awaitingApproval);
}

/**
 * Places an investment from the funding trust banking account `accountId` into the chosen
 * investment account (648A). A section 86(4) investment names the client and the matter whose
 * money it is; a section 86(3) investment names neither (the backend refuses either with 400).
 */
export async function placeInvestment(
  accountId: string,
  data: PlaceInvestmentFormData
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const payload: PlaceInvestmentRequest = {
      investmentAccountId: data.investmentAccountId,
      institution: data.institution,
      accountNumber: data.accountNumber,
      principal: data.principal,
      // Backend stores interestRate as decimal (0.075 = 7.5%), form collects percentage
      interestRate: data.interestRate / 100,
      depositDate: data.depositDate,
      maturityDate: data.maturityDate || null,
      notes: data.notes || null,
      investmentBasis: data.investmentBasis,
    };
    if (data.investmentBasis === "CLIENT_INSTRUCTION") {
      payload.clientId = data.customerId;
      // G89-F28: the matter whose trust money is invested (required)
      payload.matterId = data.projectId;
    }
    const investment = await api.post<TrustInvestment>(
      `/api/trust-accounts/${accountId}/investments`,
      payload
    );
    return { success: true, investment };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to place investment";
    return { success: false, error: message };
  }
}

// -- Section 86(4) interest entries (648B, ADR-379) ------------------------

/**
 * Records one interest entry on a section 86(4) investment: the gross, the Fund's 5% and the
 * client's 95%. From a statement line (the investment account's UNMATCHED credit) or a bank advice
 * (no line — NET only, OQ-4). The server is the authority for every rule.
 */
export async function recordInvestmentInterest(
  investmentId: string,
  data: RecordInvestmentInterestFormData
): Promise<{ success: boolean; interest?: InvestmentInterest; error?: string }> {
  try {
    const payload: RecordInvestmentInterestRequest = {
      entryDate: data.entryDate,
      grossAmount: data.grossAmount,
      presentation: data.presentation,
    };
    if (data.fundShareAmount !== undefined) {
      payload.fundShareAmount = data.fundShareAmount;
    }
    if (data.bankStatementLineId) {
      payload.bankStatementLineId = data.bankStatementLineId;
    }
    const interest = await api.post<InvestmentInterest>(
      `/api/trust-investments/${investmentId}/interest`,
      payload
    );
    return { success: true, interest };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to record interest";
    return { success: false, error: message };
  }
}

/** Every interest entry of one investment, oldest first (the listing is POSTED and REVERSED). */
export async function fetchInvestmentInterest(investmentId: string): Promise<InvestmentInterest[]> {
  return api.get<InvestmentInterest[]>(`/api/trust-investments/${investmentId}/interest`);
}

/**
 * Reverses one POSTED bank-advice interest entry (648B F-1). The endpoint takes no body: no
 * reason, no date. The server refuses an entry already reversed, one recorded from a statement
 * line (unmatch the line instead), a withdrawn investment and a closed account; its detail is
 * returned as `error`.
 */
export async function reverseInvestmentInterest(
  interestId: string
): Promise<{ success: boolean; interest?: InvestmentInterest; error?: string }> {
  try {
    const interest = await api.post<InvestmentInterest>(
      `/api/trust-investment-interest/${interestId}/reverse`
    );
    return { success: true, interest };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to reverse interest entry";
    return { success: false, error: message };
  }
}

/**
 * The investment account's UNMATCHED credit lines, newest first — the lines an interest entry can
 * be recorded from (OQ-11). Only statements with an unresolved line are opened.
 */
export async function fetchUnmatchedCreditLines(
  investmentAccountId: string
): Promise<BankStatementLineResponse[]> {
  const statements = await api.get<BankStatementResponse[]>(
    `/api/trust-accounts/${investmentAccountId}/bank-statements`
  );
  const open = statements.filter((s) => s.matchedCount < s.lineCount);
  const details = await Promise.all(
    open.map((s) => api.get<BankStatementResponse>(`/api/bank-statements/${s.id}`))
  );
  return details
    .flatMap((s) => s.lines)
    .filter((line) => line.matchStatus === "UNMATCHED" && line.amount > 0)
    .sort((a, b) => b.transactionDate.localeCompare(a.transactionDate));
}

/** The whole investment returns on the date (648A); the amount is derived, never typed. */
export async function withdrawInvestment(
  investmentId: string,
  data: WithdrawInvestmentFormData
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const investment = await api.post<TrustInvestment>(
      `/api/trust-investments/${investmentId}/withdraw`,
      { withdrawalDate: data.withdrawalDate }
    );
    return { success: true, investment };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to withdraw investment";
    return { success: false, error: message };
  }
}

export async function fetchMaturing(accountId: string): Promise<TrustInvestment[]> {
  return api.get<TrustInvestment[]>(`/api/trust-accounts/${accountId}/investments/maturing`);
}
