import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock Clerk auth
vi.mock("@clerk/nextjs/server", () => ({
  auth: vi.fn().mockResolvedValue({
    getToken: vi.fn().mockResolvedValue("mock-token"),
    orgRole: "org:admin",
  }),
}));

// Mock the reports API module
const mockGetReportDefinitions = vi.fn();

vi.mock("@/lib/api/reports", () => ({
  getReportDefinitions: (...args: unknown[]) =>
    mockGetReportDefinitions(...args),
}));

// Mock next/link to render plain <a> tags
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
  }: {
    children: React.ReactNode;
    href: string;
  }) => <a href={href}>{children}</a>,
}));

// Must import page AFTER vi.mock declarations
import ReportsPage from "@/app/(app)/org/[slug]/reports/page";

async function renderPage(categories = defaultCategories()) {
  mockGetReportDefinitions.mockResolvedValue({ categories });
  const result = await ReportsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

function defaultCategories() {
  return [
    {
      category: "FINANCIAL",
      label: "Financial Reports",
      reports: [
        {
          slug: "revenue-summary",
          name: "Revenue Summary",
          description: "Overview of revenue by period",
        },
        {
          slug: "outstanding-invoices",
          name: "Outstanding Invoices",
          description: "All unpaid invoices",
        },
      ],
    },
    {
      category: "OPERATIONAL",
      label: "Operational Reports",
      reports: [
        {
          slug: "utilization",
          name: "Team Utilization",
          description: "Billable vs non-billable hours",
        },
      ],
    },
  ];
}

describe("ReportsPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders category headings", async () => {
    await renderPage();

    expect(screen.getByText("Financial Reports")).toBeInTheDocument();
    expect(screen.getByText("Operational Reports")).toBeInTheDocument();
  });

  it("renders report cards with name and description", async () => {
    await renderPage();

    expect(screen.getByText("Revenue Summary")).toBeInTheDocument();
    expect(
      screen.getByText("Overview of revenue by period"),
    ).toBeInTheDocument();
    expect(screen.getByText("Outstanding Invoices")).toBeInTheDocument();
    expect(screen.getByText("Team Utilization")).toBeInTheDocument();
  });

  it("links each card to the correct report URL", async () => {
    await renderPage();

    const revenueLink = screen.getByText("Revenue Summary").closest("a");
    expect(revenueLink).toHaveAttribute(
      "href",
      "/org/acme/reports/revenue-summary",
    );

    const utilizationLink = screen
      .getByText("Team Utilization")
      .closest("a");
    expect(utilizationLink).toHaveAttribute(
      "href",
      "/org/acme/reports/utilization",
    );
  });

  it("renders empty state when no categories", async () => {
    await renderPage([]);

    expect(screen.getByText("No reports available")).toBeInTheDocument();
  });
});
