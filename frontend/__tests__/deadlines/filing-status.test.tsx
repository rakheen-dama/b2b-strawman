import React from "react";
import { afterEach, beforeEach, vi, describe, it, expect } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks ---

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const mockUpdateFilingStatus = vi
  .fn()
  .mockResolvedValue({ success: true, results: [] });

vi.mock("@/app/(app)/org/[slug]/deadlines/actions", () => ({
  updateFilingStatus: (...args: unknown[]) => mockUpdateFilingStatus(...args),
  fetchDeadlines: vi.fn().mockResolvedValue({ deadlines: [], total: 0 }),
  fetchDeadlineSummary: vi.fn().mockResolvedValue({ summaries: [] }),
}));

// --- Imports after mocks ---

import { FilingStatusDialog } from "@/components/deadlines/FilingStatusDialog";
import { BatchFilingActions } from "@/components/deadlines/BatchFilingActions";
import { DeadlineSummaryCards } from "@/components/deadlines/DeadlineSummaryCards";
import type { CalculatedDeadline, DeadlineSummary } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

function makeDeadline(
  overrides: Partial<CalculatedDeadline> = {}
): CalculatedDeadline {
  return {
    customerId: "cust-1",
    customerName: "Acme Pty Ltd",
    deadlineTypeSlug: "sars_provisional_1",
    deadlineTypeName: "Provisional Tax — 1st Payment",
    category: "tax",
    dueDate: "2026-03-31",
    status: "pending",
    linkedProjectId: null,
    filingStatusId: null,
    ...overrides,
  };
}

// --- Tests ---

describe("FilingStatusDialog", () => {
  it("renders with form fields", () => {
    const deadline = makeDeadline();
    render(
      <FilingStatusDialog
        open={true}
        onOpenChange={() => {}}
        deadlines={[deadline]}
        slug="acme"
        onSuccess={() => {}}
      />
    );
    // Dialog title + submit button both say "Mark as Filed"
    const markAsFiledElements = screen.getAllByText("Mark as Filed");
    expect(markAsFiledElements.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText(/reference number/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/notes/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /mark as filed/i })
    ).toBeInTheDocument();
  });

  it("submit calls updateFilingStatus with correct payload", async () => {
    const user = userEvent.setup();
    const deadline = makeDeadline();
    const onSuccess = vi.fn();
    render(
      <FilingStatusDialog
        open={true}
        onOpenChange={() => {}}
        deadlines={[deadline]}
        slug="acme"
        onSuccess={onSuccess}
      />
    );

    const submitBtn = screen.getByRole("button", { name: /mark as filed/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(mockUpdateFilingStatus).toHaveBeenCalledWith(
        "acme",
        expect.arrayContaining([
          expect.objectContaining({
            customerId: "cust-1",
            deadlineTypeSlug: "sars_provisional_1",
            status: "filed",
          }),
        ])
      );
    });

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    });
  });
});

describe("BatchFilingActions", () => {
  it("shows count and action buttons when items selected", () => {
    const deadline = makeDeadline();
    const key = `${deadline.customerId}__${deadline.deadlineTypeSlug}__${deadline.dueDate}`;
    render(
      <BatchFilingActions
        selectedIds={new Set([key])}
        deadlines={[deadline]}
        slug="acme"
        onClearSelection={() => {}}
        onFilingSuccess={() => {}}
      />
    );
    expect(screen.getByText("1 selected")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /mark as filed/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /mark as n\/a/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /clear/i })
    ).toBeInTheDocument();
  });
});

describe("DeadlineSummaryCards", () => {
  it("renders category cards with correct counts", () => {
    const summaries: DeadlineSummary[] = [
      {
        month: "2026-03",
        category: "tax",
        total: 10,
        filed: 4,
        pending: 5,
        overdue: 1,
      },
      {
        month: "2026-03",
        category: "vat",
        total: 6,
        filed: 3,
        pending: 3,
        overdue: 0,
      },
    ];
    render(<DeadlineSummaryCards summaries={summaries} />);
    expect(screen.getByText("Tax")).toBeInTheDocument();
    expect(screen.getByText("VAT")).toBeInTheDocument();

    // Helper: find the stat value next to a given label within a card
    function getStatValue(card: HTMLElement, label: string): string {
      const cardScope = within(card);
      const labelEl = cardScope.getByText(label);
      // The label and value share a parent div.text-center; value is the first <p>
      const statContainer = labelEl.closest(".text-center")!;
      return within(statContainer as HTMLElement).getAllByText(/\d+/)[0]
        .textContent!;
    }

    const taxCard = screen.getByText("Tax").closest("[data-slot='card']")!;
    const vatCard = screen.getByText("VAT").closest("[data-slot='card']")!;

    // Tax: total=10, filed=4, pending=5, overdue=1
    expect(getStatValue(taxCard as HTMLElement, "Total")).toBe("10");
    expect(getStatValue(taxCard as HTMLElement, "Filed")).toBe("4");
    expect(getStatValue(taxCard as HTMLElement, "Pending")).toBe("5");
    expect(getStatValue(taxCard as HTMLElement, "Overdue")).toBe("1");

    // VAT: total=6, filed=3, pending=3, overdue=0
    expect(getStatValue(vatCard as HTMLElement, "Total")).toBe("6");
    expect(getStatValue(vatCard as HTMLElement, "Filed")).toBe("3");
    expect(getStatValue(vatCard as HTMLElement, "Pending")).toBe("3");
    expect(getStatValue(vatCard as HTMLElement, "Overdue")).toBe("0");
  });
});
