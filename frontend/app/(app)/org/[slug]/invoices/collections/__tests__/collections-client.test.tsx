import React from "react";
import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";
import { CollectionsClient } from "../collections-client";
import type { DebtorResponse } from "@/lib/api/collections";

// CollectionsClient hosts ReminderQueue, which imports the "use server" actions module
// (and its server-only lib chain). Stub it so the client tree loads in happy-dom.
vi.mock("@/app/(app)/org/[slug]/invoices/collections/actions", () => ({
  batchApproveGatesAction: vi.fn(),
  rejectReminderGateAction: vi.fn(),
}));

// Phase 91 666A: the Debtor book is a DataTable, which calls useRouter().
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices/collections",
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  cleanup();
});

function makeDebtor(overrides: Partial<DebtorResponse> = {}): DebtorResponse {
  return {
    clientId: "cust-1",
    clientName: "Naidoo & Co",
    outstandingTotal: 412000,
    currency: "ZAR",
    invoiceCount: 3,
    oldestDaysOverdue: 62,
    buckets: { current: 0, d30: 120000, d60: 180000, d90plus: 112000 },
    signals: [],
    signalDetails: {},
    collectionsExempt: false,
    lastActivity: { stage: "STAGE_3", status: "SENT", at: "2026-07-02T08:11:00Z" },
    ...overrides,
  };
}

describe("CollectionsClient — debtor book", () => {
  it("renders the debtor rows from the API shape", () => {
    const debtors = [
      makeDebtor(),
      makeDebtor({
        clientId: "cust-2",
        clientName: "Exempt Holdings",
        outstandingTotal: 5000,
        oldestDaysOverdue: -3, // not yet due
        collectionsExempt: true,
        lastActivity: null,
      }),
    ];

    render(<CollectionsClient slug="acme" debtors={debtors} gates={null} previews={{}} />);

    // Customer names render as links.
    expect(screen.getByText("Naidoo & Co")).toBeInTheDocument();
    expect(screen.getByText("Exempt Holdings")).toBeInTheDocument();

    // Currency-formatted outstanding total (en-ZA groups thousands).
    expect(
      screen.getByText((content) => content.replace(/\s| /g, "").includes("412000"))
    ).toBeInTheDocument();

    // Days-overdue formatting (positive and negative/not-yet-due).
    expect(screen.getByText("62d overdue")).toBeInTheDocument();
    expect(screen.getByText("3d until due")).toBeInTheDocument();

    // Exempt customer surfaces the Exempt badge.
    expect(screen.getByText("Exempt")).toBeInTheDocument();

    // Null last-activity renders the no-history placeholder.
    expect(screen.getByText("No chase history")).toBeInTheDocument();

    // Present last-activity renders its status badge.
    expect(screen.getByText("Sent")).toBeInTheDocument();
  });

  it("renders triage signal badges with human labels and the trust badge", () => {
    render(
      <CollectionsClient
        slug="acme"
        debtors={[
          makeDebtor({
            signals: ["GONE_QUIET", "TRUST_FUNDS_AVAILABLE"],
            signalDetails: { TRUST_FUNDS_AVAILABLE: "R 84 200,00 held in trust" },
          }),
        ]}
        gates={null}
        previews={{}}
      />
    );
    expect(screen.getByText("Gone quiet")).toBeInTheDocument();
    expect(screen.getByText("Trust funds available")).toBeInTheDocument();
    // Raw enum names must not render.
    expect(screen.queryByText("TRUST_FUNDS_AVAILABLE")).not.toBeInTheDocument();
  });

  it("hides the pending-reminder queue section when gates is null", () => {
    render(<CollectionsClient slug="acme" debtors={[makeDebtor()]} gates={null} previews={{}} />);
    expect(screen.queryByText("Pending reminders")).not.toBeInTheDocument();
  });

  it("renders the pending-reminder queue section for AI_REVIEW holders", () => {
    render(<CollectionsClient slug="acme" debtors={[makeDebtor()]} gates={[]} previews={{}} />);
    expect(screen.getByText("Pending reminders")).toBeInTheDocument();
  });

  it("shows an empty debtor book when there are no debtors", () => {
    render(<CollectionsClient slug="acme" debtors={[]} gates={null} previews={{}} />);
    expect(screen.getByText(/No outstanding balances/)).toBeInTheDocument();
    expect(screen.getByText("Everything is settled.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  // take-on.spec.ts reads debtor cells by index (1 = Outstanding, 3–6 = the buckets).
  it("keeps the debtor book column order and the K-71 container-query buckets", () => {
    render(<CollectionsClient slug="acme" debtors={[makeDebtor()]} gates={null} previews={{}} />);

    const headers = screen.getAllByRole("columnheader").map((h) => h.textContent);
    expect(headers).toEqual([
      "Client",
      "Outstanding",
      "Oldest",
      "Current",
      "30d",
      "60d",
      "90d+",
      "Signals",
      "Last activity",
    ]);
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(cells[1].textContent).toBe(formatCurrency(412000, "ZAR"));
    expect(cells[3].textContent).toBe(formatCurrency(0, "ZAR"));
    expect(cells[6].textContent).toBe(formatCurrency(112000, "ZAR"));
    for (const index of [3, 4, 5, 6]) {
      expect(cells[index]).toHaveClass("hidden", "@4xl:table-cell");
      expect(cells[index].firstElementChild).toHaveClass("font-sans");
      expect(cells[index].firstElementChild).not.toHaveClass("font-mono");
    }
    expect(screen.getByRole("link", { name: "Naidoo & Co" })).toHaveAttribute(
      "href",
      "/org/acme/clients/cust-1"
    );
  });
});
