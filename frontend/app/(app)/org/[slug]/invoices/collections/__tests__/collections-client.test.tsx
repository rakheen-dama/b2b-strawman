import React from "react";
import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CollectionsClient } from "../collections-client";
import type { DebtorResponse } from "@/lib/api/collections";

// CollectionsClient hosts ReminderQueue, which imports the "use server" actions module
// (and its server-only lib chain). Stub it so the client tree loads in happy-dom.
vi.mock("@/app/(app)/org/[slug]/invoices/collections/actions", () => ({
  batchApproveGatesAction: vi.fn(),
  rejectReminderGateAction: vi.fn(),
}));

afterEach(() => {
  cleanup();
});

function makeDebtor(overrides: Partial<DebtorResponse> = {}): DebtorResponse {
  return {
    customerId: "cust-1",
    customerName: "Naidoo & Co",
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
        customerId: "cust-2",
        customerName: "Exempt Holdings",
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
  });
});
