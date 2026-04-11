import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock swr FIRST (before component import)
vi.mock("swr", () => ({ default: vi.fn() }));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock server action
vi.mock("@/app/(app)/org/[slug]/deadlines/actions", () => ({
  fetchDeadlineSummary: vi.fn(),
}));

import useSWR from "swr";
import { DeadlineWidget } from "@/components/dashboard/deadline-widget";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import type { DeadlineSummary } from "@/lib/types";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function mockSWRWithData(summaries: DeadlineSummary[]) {
  vi.mocked(useSWR).mockReturnValue({
    data: { summaries },
    error: undefined,
    isLoading: false,
    mutate: vi.fn(),
  } as ReturnType<typeof useSWR>);
}

describe("DeadlineWidget", () => {
  it("renders with summary data showing aggregated counts", () => {
    mockSWRWithData([
      {
        month: "2026-03",
        category: "tax",
        total: 8,
        filed: 5,
        pending: 2,
        overdue: 1,
      },
      {
        month: "2026-03",
        category: "vat",
        total: 4,
        filed: 3,
        pending: 1,
        overdue: 0,
      },
    ]);

    render(<DeadlineWidget orgSlug="acme" />);

    expect(screen.getByText("Deadlines")).toBeInTheDocument();
    // Aggregated: total=12, filed=8, pending=3, overdue=1
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("renders a View All link pointing to /deadlines", () => {
    mockSWRWithData([
      {
        month: "2026-03",
        category: "tax",
        total: 5,
        filed: 3,
        pending: 2,
        overdue: 0,
      },
    ]);

    render(<DeadlineWidget orgSlug="acme" />);

    const link = screen.getByRole("link", { name: /view all/i });
    expect(link).toHaveAttribute("href", expect.stringContaining("/deadlines"));
    expect(link).toHaveAttribute("href", "/org/acme/deadlines");
  });

  it("is not rendered when regulatory_deadlines module is disabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <ModuleGate module="regulatory_deadlines">
          <DeadlineWidget orgSlug="acme" />
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.queryByText("Deadlines")).not.toBeInTheDocument();
  });

  it("renders deadline-widget data-testid", () => {
    mockSWRWithData([
      {
        month: "2026-03",
        category: "tax",
        total: 3,
        filed: 1,
        pending: 1,
        overdue: 1,
      },
    ]);

    render(<DeadlineWidget orgSlug="acme" />);
    expect(screen.getByTestId("deadline-widget")).toBeInTheDocument();
  });
});
