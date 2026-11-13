import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock capabilities API (fetchMyCapabilities used for page-level gating)
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["FINANCIAL_VISIBILITY"],
    role: "Admin",
    isAdmin: true,
    isOwner: false,
  }),
}));

// Mock the reports API module
const mockGetReportDefinitions = vi.fn();

vi.mock("@/lib/api/reports", () => ({
  getReportDefinitions: (...args: unknown[]) => mockGetReportDefinitions(...args),
}));

// Mock next/link to render plain <a> tags
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Must import page AFTER vi.mock declarations
import ReportsPage from "@/app/(app)/org/[slug]/reports/page";
import { fetchMyCapabilities } from "@/lib/api/capabilities";

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
    expect(screen.getByText("Overview of revenue by period")).toBeInTheDocument();
    expect(screen.getByText("Outstanding Invoices")).toBeInTheDocument();
    expect(screen.getByText("Team Utilization")).toBeInTheDocument();
  });

  it("links each card to the correct report URL", async () => {
    await renderPage();

    const revenueLink = screen.getByText("Revenue Summary").closest("a");
    expect(revenueLink).toHaveAttribute("href", "/org/acme/reports/revenue-summary");

    const utilizationLink = screen.getByText("Team Utilization").closest("a");
    expect(utilizationLink).toHaveAttribute("href", "/org/acme/reports/utilization");
  });

  it("renders empty state when no categories", async () => {
    await renderPage([]);

    expect(screen.getByText("No reports available")).toBeInTheDocument();
  });

  // Phase 87 S6a (612A): Profitability's single UI entry is a card on the Reports index.
  it("links a Profitability card to /reports/profitability", async () => {
    await renderPage();

    const link = screen.getByText("Profitability").closest("a");
    expect(link).toHaveAttribute("href", "/org/acme/reports/profitability");
  });

  it("keeps the Profitability card when no report definitions are configured", async () => {
    await renderPage([]);

    expect(screen.getByText("Profitability").closest("a")).toHaveAttribute(
      "href",
      "/org/acme/reports/profitability"
    );
  });

  // 643C: the title block is the shared PageHeader; the cards and their links are unchanged.
  it("renders exactly one h1 'Reports' inside the PageHeader", async () => {
    await renderPage();

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Reports");
    const header = screen.getByTestId("page-header");
    expect(header).toHaveAttribute("data-slot", "page-header");
    expect(header).toContainElement(headings[0]);
    // No actions or links in the header: the first report link is still the Profitability card.
    expect(header.querySelector("a")).toBeNull();
  });

  it("keeps the Firm performance and category h2s", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { level: 2, name: "Firm performance" })).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Financial Reports" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Operational Reports" })
    ).toBeInTheDocument();
  });

  it("each card link's accessible name starts with its title and wraps a Card", async () => {
    await renderPage();

    const cases: [RegExp, string][] = [
      [/^Profitability/, "/org/acme/reports/profitability"],
      [/^Revenue Summary/, "/org/acme/reports/revenue-summary"],
      [/^Outstanding Invoices/, "/org/acme/reports/outstanding-invoices"],
      [/^Team Utilization/, "/org/acme/reports/utilization"],
    ];
    for (const [name, href] of cases) {
      const link = screen.getByRole("link", { name });
      expect(link).toHaveAttribute("href", href);
      expect(link.querySelector('[data-slot="card"]')).not.toBeNull();
      expect(link).toHaveTextContent("Run report →");
    }
    expect(screen.getAllByRole("link")).toHaveLength(cases.length);
  });

  it("hides the Profitability card when the user lacks FINANCIAL_VISIBILITY", async () => {
    vi.mocked(fetchMyCapabilities).mockResolvedValueOnce({
      capabilities: [],
      role: "Member",
      isAdmin: false,
      isOwner: false,
      memberId: "member-1",
    } as Awaited<ReturnType<typeof fetchMyCapabilities>>);
    await renderPage();

    expect(screen.queryByText("Profitability")).not.toBeInTheDocument();
    expect(document.querySelector('a[href="/org/acme/reports/profitability"]')).toBeNull();
  });
});
