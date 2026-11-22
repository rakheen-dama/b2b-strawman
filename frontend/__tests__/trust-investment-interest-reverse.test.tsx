import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { SWRConfig } from "swr";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const mockFetchInvestmentInterest = vi.fn();
const mockReverseInvestmentInterest = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/investments/actions", () => ({
  fetchInvestmentInterest: (...args: unknown[]) => mockFetchInvestmentInterest(...args),
  reverseInvestmentInterest: (...args: unknown[]) => mockReverseInvestmentInterest(...args),
  recordInvestmentInterest: vi.fn(),
  fetchUnmatchedCreditLines: vi.fn().mockResolvedValue([]),
  withdrawInvestment: vi.fn(),
  placeInvestment: vi.fn(),
}));

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh, push: vi.fn() }),
}));

import { InvestmentPageClient } from "@/app/(app)/org/[slug]/trust-accounting/investments/InvestmentPageClient";

function entry(overrides: Record<string, unknown> = {}) {
  return {
    id: "int-1",
    investmentId: "inv-1",
    entryDate: "2026-03-31",
    grossAmount: 100,
    fundShareAmount: 5,
    netAmount: 95,
    presentation: "NET",
    status: "POSTED",
    netTransactionId: "tx-1",
    fundShareTransactionId: null,
    bankStatementLineId: null,
    clientTaxReference: null,
    ...overrides,
  };
}

function renderActions(props: Record<string, unknown> = {}) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <InvestmentPageClient
        accountId="acc-1"
        variant="actions"
        investmentId="inv-1"
        investmentBalance={1000}
        investmentDepositDate="2026-01-01"
        investmentAccountId="ia-1"
        canRecordInterest
        showInterestEntries
        currency="ZAR"
        {...props}
      />
    </SWRConfig>
  );
}

async function openEntries() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Interest entries" }));
  await screen.findByTestId("interest-entries-table");
  return user;
}

describe("Reverse a section 86(4) interest entry (648B F-1)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchInvestmentInterest.mockResolvedValue([entry()]);
  });
  afterEach(() => cleanup());

  it("offers an Interest entries control on a section 86(4) investment", () => {
    renderActions();
    expect(screen.getByRole("button", { name: "Interest entries" })).toBeInTheDocument();
  });

  it("does not offer it when the investment takes no interest entries", () => {
    renderActions({ showInterestEntries: false });
    expect(screen.queryByRole("button", { name: "Interest entries" })).not.toBeInTheDocument();
  });

  it("shows Reverse only on a POSTED bank-advice entry", async () => {
    mockFetchInvestmentInterest.mockResolvedValue([
      entry({ id: "int-posted" }),
      entry({ id: "int-reversed", status: "REVERSED" }),
      entry({ id: "int-line", bankStatementLineId: "line-1" }),
    ]);
    renderActions();
    await openEntries();
    expect(mockFetchInvestmentInterest).toHaveBeenCalledWith("inv-1");
    expect(
      within(screen.getByTestId("interest-entry-int-posted")).getByRole("button", {
        name: "Reverse",
      })
    ).toBeInTheDocument();
    const reversed = screen.getByTestId("interest-entry-int-reversed");
    expect(within(reversed).queryByRole("button", { name: "Reverse" })).not.toBeInTheDocument();
    expect(within(reversed).getByText("Reversed")).toBeInTheDocument();
    const fromLine = screen.getByTestId("interest-entry-int-line");
    expect(within(fromLine).queryByRole("button", { name: "Reverse" })).not.toBeInTheDocument();
    expect(within(fromLine).getByText(/Unmatch the statement line/)).toBeInTheDocument();
  });

  it("asks for confirmation, then calls the action with the entry id and refreshes", async () => {
    mockReverseInvestmentInterest.mockResolvedValue({ success: true });
    renderActions();
    const user = await openEntries();
    await user.click(screen.getByRole("button", { name: "Reverse" }));
    expect(mockReverseInvestmentInterest).not.toHaveBeenCalled();
    mockFetchInvestmentInterest.mockResolvedValue([entry({ status: "REVERSED" })]);
    await user.click(screen.getByTestId("confirm-reverse-interest"));
    await waitFor(() =>
      expect(mockReverseInvestmentInterest).toHaveBeenCalledWith("int-1", expect.any(String))
    );
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());
    await waitFor(() =>
      expect(
        within(screen.getByTestId("interest-entry-int-1")).queryByRole("button", {
          name: "Reverse",
        })
      ).not.toBeInTheDocument()
    );
  });

  it("cancelling the confirmation reverses nothing", async () => {
    renderActions();
    const user = await openEntries();
    await user.click(screen.getByRole("button", { name: "Reverse" }));
    await user.click(screen.getByRole("button", { name: "Keep entry" }));
    expect(mockReverseInvestmentInterest).not.toHaveBeenCalled();
    expect(screen.queryByTestId("confirm-reverse-interest")).not.toBeInTheDocument();
  });

  it("shows the backend detail on a refusal and does not refresh", async () => {
    mockReverseInvestmentInterest.mockResolvedValue({
      success: false,
      error: "This interest entry has already been reversed.",
    });
    renderActions();
    const user = await openEntries();
    await user.click(screen.getByRole("button", { name: "Reverse" }));
    await user.click(screen.getByTestId("confirm-reverse-interest"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This interest entry has already been reversed."
    );
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("disables the confirm button while the request is in flight", async () => {
    let resolve!: (v: { success: boolean }) => void;
    mockReverseInvestmentInterest.mockReturnValue(
      new Promise((r) => {
        resolve = r;
      })
    );
    renderActions();
    const user = await openEntries();
    await user.click(screen.getByRole("button", { name: "Reverse" }));
    await user.click(screen.getByTestId("confirm-reverse-interest"));
    expect(screen.getByTestId("confirm-reverse-interest")).toBeDisabled();
    await user.click(screen.getByTestId("confirm-reverse-interest"));
    expect(mockReverseInvestmentInterest).toHaveBeenCalledTimes(1);
    resolve({ success: true });
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());
  });

  it("shows an error when the entries cannot be loaded", async () => {
    mockFetchInvestmentInterest.mockRejectedValue(new Error("boom"));
    renderActions();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Interest entries" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/Unable to load/);
  });
});
