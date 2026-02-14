import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { KpiCard } from "@/components/dashboard/kpi-card";

// Mock next/link to render as a plain anchor
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe("KpiCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders value and label", () => {
    render(<KpiCard label="Total Revenue" value="$45,000" />);

    expect(screen.getByText("Total Revenue")).toBeInTheDocument();
    expect(screen.getByText("$45,000")).toBeInTheDocument();
  });

  it("renders change indicator with green up arrow for positive change", () => {
    render(
      <KpiCard
        label="Revenue"
        value="$50,000"
        changePercent={12}
        changeDirection="positive"
      />
    );

    expect(screen.getByText("12%")).toBeInTheDocument();
    const indicator = screen.getByText("12%").closest("span");
    expect(indicator!.className).toContain("text-green-600");
  });

  it("renders empty state when emptyState is set and value is 0", () => {
    render(
      <KpiCard
        label="Revenue"
        value={0}
        emptyState="No data available"
      />
    );

    expect(screen.getByText("No data available")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("renders as a link when href is provided", () => {
    render(
      <KpiCard
        label="Projects"
        value={12}
        href="/org/acme/projects"
      />
    );

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/org/acme/projects");
    expect(screen.getByText("Projects")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
  });
});
