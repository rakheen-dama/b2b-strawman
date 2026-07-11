import React from "react";
import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CollectionsHistoryTable } from "../collections-history-table";
import { CollectionsHistoryPagination } from "../collections-history-pagination";
import type { CollectionActivityResponse } from "@/lib/api/collections";

afterEach(() => {
  cleanup();
});

function makeActivity(
  overrides: Partial<CollectionActivityResponse> = {}
): CollectionActivityResponse {
  return {
    id: "act-1",
    invoiceId: "inv-1",
    stage: "STAGE_1",
    status: "SENT",
    reason: null,
    gateId: null,
    daysOverdueAtAction: 12,
    createdAt: "2026-07-02T08:11:00Z",
    updatedAt: "2026-07-02T09:00:00Z",
    ...overrides,
  };
}

describe("CollectionsHistoryTable", () => {
  it("renders the full status/reason set the ledger can surface", () => {
    const activities = [
      makeActivity({ id: "a-sent", stage: "STAGE_1", status: "SENT" }),
      makeActivity({
        id: "a-skipped",
        stage: "STAGE_2",
        status: "SKIPPED",
        reason: "rate_limited",
      }),
      makeActivity({
        id: "a-failed",
        stage: "STAGE_3",
        status: "SEND_FAILED",
        reason: "provider_failure",
      }),
      makeActivity({ id: "a-flagged", stage: "ESCALATION", status: "FLAGGED" }),
    ];

    render(<CollectionsHistoryTable activities={activities} slug="acme" />);

    // Statuses render, humanised (title-cased per word).
    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Skipped")).toBeInTheDocument();
    expect(screen.getByText("Send Failed")).toBeInTheDocument();
    expect(screen.getByText("Flagged")).toBeInTheDocument();

    // Reasons render humanised beside their status.
    expect(screen.getByText("Rate Limited")).toBeInTheDocument();
    expect(screen.getByText("Provider Failure")).toBeInTheDocument();

    // Stages render humanised.
    expect(screen.getByText("Stage 1")).toBeInTheDocument();
    expect(screen.getByText("Escalation")).toBeInTheDocument();

    // Days-overdue-at-action is shown.
    expect(screen.getAllByText("12d overdue").length).toBeGreaterThan(0);
  });

  it("shows the gate Review link only on PROPOSED rows that carry a gateId", () => {
    const activities = [
      makeActivity({ id: "a-proposed", status: "PROPOSED", gateId: "gate-1" }),
      makeActivity({ id: "a-proposed-nogate", status: "PROPOSED", gateId: null }),
      makeActivity({ id: "a-sent", status: "SENT", gateId: "gate-2" }),
    ];

    render(<CollectionsHistoryTable activities={activities} slug="acme" />);

    // Exactly one Review link — for the PROPOSED-with-gateId row.
    const links = screen.getAllByRole("link", { name: "Review" });
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("href", "/org/acme/ai/reviews");
  });

  it("renders the empty state when there are no activities", () => {
    render(
      <CollectionsHistoryTable activities={[]} slug="acme" emptyMessage="Nothing here yet." />
    );
    expect(screen.getByText("Nothing here yet.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("negative days-overdue reads as not-yet-due", () => {
    render(
      <CollectionsHistoryTable
        activities={[makeActivity({ daysOverdueAtAction: -3 })]}
        slug="acme"
      />
    );
    expect(screen.getByText("3d until due")).toBeInTheDocument();
  });
});

describe("CollectionsHistoryPagination", () => {
  it("renders nothing when there is a single page", () => {
    const { container } = render(
      <CollectionsHistoryPagination slug="acme" customerId="cust-1" number={0} totalPages={1} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("links prev/next to the right chasePage while preserving the details tab", () => {
    render(
      <CollectionsHistoryPagination slug="acme" customerId="cust-1" number={1} totalPages={3} />
    );

    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();

    const prev = screen.getByRole("link", { name: "Previous" });
    const next = screen.getByRole("link", { name: "Next" });
    expect(prev).toHaveAttribute("href", "/org/acme/customers/cust-1?tab=details&chasePage=0");
    expect(next).toHaveAttribute("href", "/org/acme/customers/cust-1?tab=details&chasePage=2");
  });

  it("disables Previous on the first page (renders it as non-link text)", () => {
    render(
      <CollectionsHistoryPagination slug="acme" customerId="cust-1" number={0} totalPages={3} />
    );
    expect(screen.queryByRole("link", { name: "Previous" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Next" })).toBeInTheDocument();
  });

  it("disables Next on the last page", () => {
    render(
      <CollectionsHistoryPagination slug="acme" customerId="cust-1" number={2} totalPages={3} />
    );
    expect(screen.getByRole("link", { name: "Previous" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Next" })).not.toBeInTheDocument();
  });
});
