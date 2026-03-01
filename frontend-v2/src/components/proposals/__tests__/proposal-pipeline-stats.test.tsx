import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

import type { ProposalStats } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { ProposalPipelineStats } from "@/components/proposals/proposal-pipeline-stats";

afterEach(() => cleanup());

const mockStats: ProposalStats = {
  totalDraft: 3,
  totalSent: 5,
  totalAccepted: 8,
  totalDeclined: 2,
  totalExpired: 1,
  conversionRate: 72.5,
  averageDaysToAccept: 4.3,
};

describe("ProposalPipelineStats", () => {
  it("renders all stat cards", () => {
    render(<ProposalPipelineStats stats={mockStats} />);

    expect(screen.getByText("Total Open")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("Accepted")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("Declined")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Conversion Rate")).toBeInTheDocument();
    expect(screen.getByText("73%")).toBeInTheDocument();
    expect(screen.getByText("Avg Days to Accept")).toBeInTheDocument();
    expect(screen.getByText("4d")).toBeInTheDocument();
  });

  it("shows zero conversion rate when no data", () => {
    const zeroStats: ProposalStats = {
      totalDraft: 0,
      totalSent: 0,
      totalAccepted: 0,
      totalDeclined: 0,
      totalExpired: 0,
      conversionRate: 0,
      averageDaysToAccept: 0,
    };

    render(<ProposalPipelineStats stats={zeroStats} />);

    expect(screen.getByText("0%")).toBeInTheDocument();
    // averageDaysToAccept is 0, so should show em dash
    expect(screen.getByText("\u2014")).toBeInTheDocument();
  });
});
