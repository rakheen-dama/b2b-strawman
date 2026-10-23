import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRTestProvider } from "@/lib/swr/test-utils";

const mockFetchUnbilled = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchUnbilledDisbursementsAction: (...args: unknown[]) => mockFetchUnbilled(...args),
}));

import {
  UnbilledDisbursementsSummary,
  useUnbilledDisbursementsSummaryCache,
} from "@/components/legal/unbilled-disbursements-summary";
import type { UnbilledDisbursementsResponse } from "@/lib/api/legal-disbursements";

function makeResponse(
  overrides: Partial<UnbilledDisbursementsResponse> = {}
): UnbilledDisbursementsResponse {
  return {
    matterId: "p1",
    currency: "ZAR",
    items: [
      {
        id: "d1",
        incurredDate: "2026-10-01",
        category: "SHERIFF_FEES",
        description: "Sheriff fee",
        amount: 750,
        vatTreatment: "ZERO_RATED_PASS_THROUGH",
        vatAmount: 0,
        supplierName: "Sheriff",
      },
      {
        id: "d2",
        incurredDate: "2026-10-02",
        category: "DEEDS_OFFICE_FEES",
        description: "Deeds search",
        amount: 1250,
        vatTreatment: "STANDARD_15",
        vatAmount: 300,
        supplierName: "Deeds Office",
      },
    ],
    totalAmount: 2000,
    totalVat: 300,
    ...overrides,
  };
}

// Fires the same in-place refresh the matter tab / firm list use.
function Revalidate({ matterId }: { matterId: string }) {
  const { refresh } = useUnbilledDisbursementsSummaryCache();
  return (
    <button type="button" onClick={() => refresh(matterId)}>
      revalidate-unbilled
    </button>
  );
}

describe("UnbilledDisbursementsSummary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the excl.-VAT total first, then VAT, via the en-ZA formatter", async () => {
    mockFetchUnbilled.mockResolvedValue(makeResponse());

    render(
      <SWRTestProvider>
        <UnbilledDisbursementsSummary matterId="p1" />
      </SWRTestProvider>
    );

    const strip = await screen.findByTestId("unbilled-disbursements-summary");
    expect(mockFetchUnbilled).toHaveBeenCalledWith("p1");
    // Raw textContent — jest-dom's toHaveTextContent collapses NBSP into a plain space.
    expect(screen.getByTestId("unbilled-disbursements-total").textContent).toBe("R 2 000,00");
    expect(screen.getByTestId("unbilled-disbursements-vat").textContent).toBe("R 300,00");
    // The Day 5 depth-ii contract (case-sensitive "Disbursements"; only whitespace before R).
    expect(strip.textContent).toMatch(/Unbilled\s+Disbursements:?\s*R\s?2[\s,]?000[.,]00/);
    // Excl. VAT first, VAT second.
    expect(strip.textContent).toMatch(/Disbursements\s*R[^V]*·\s*VAT\s*R/);
    // No heading — the matter tab's "Disbursements" heading locator must stay unique.
    expect(strip.querySelector("h1, h2, h3, h4, h5, h6, [role='heading']")).toBeNull();
  });

  it("shows the empty state when nothing is unbilled", async () => {
    mockFetchUnbilled.mockResolvedValue(makeResponse({ items: [], totalAmount: 0, totalVat: 0 }));

    render(
      <SWRTestProvider>
        <UnbilledDisbursementsSummary matterId="p1" />
      </SWRTestProvider>
    );

    const strip = await screen.findByTestId("unbilled-disbursements-summary");
    expect(strip).toHaveTextContent("No unbilled disbursements");
    expect(screen.queryByTestId("unbilled-disbursements-total")).not.toBeInTheDocument();
  });

  it("renders nothing when the action fails (returns null)", async () => {
    mockFetchUnbilled.mockResolvedValue(null);

    render(
      <SWRTestProvider>
        <UnbilledDisbursementsSummary matterId="p1" />
      </SWRTestProvider>
    );

    await waitFor(() => {
      expect(mockFetchUnbilled).toHaveBeenCalledWith("p1");
    });
    expect(screen.queryByTestId("unbilled-disbursements-summary")).not.toBeInTheDocument();
  });

  it("revalidates when the shared key is mutated (approve / write-off / restore)", async () => {
    const user = userEvent.setup();
    mockFetchUnbilled
      .mockResolvedValueOnce(makeResponse())
      .mockResolvedValueOnce(makeResponse({ totalAmount: 1250, totalVat: 300 }));

    render(
      <SWRTestProvider>
        <UnbilledDisbursementsSummary matterId="p1" />
        <Revalidate matterId="p1" />
      </SWRTestProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("unbilled-disbursements-total").textContent).toBe("R 2 000,00");
    });

    await user.click(screen.getByText("revalidate-unbilled"));

    await waitFor(() => {
      expect(screen.getByTestId("unbilled-disbursements-total").textContent).toBe("R 1 250,00");
    });
    expect(mockFetchUnbilled).toHaveBeenCalledTimes(2);
  });
});
