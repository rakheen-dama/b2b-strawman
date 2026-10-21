import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Must mock server-only before importing modules that use it
vi.mock("server-only", () => ({}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["FINANCIAL_VISIBILITY"],
    role: "Admin",
    isAdmin: true,
    isOwner: false,
  }),
}));

const mockApiGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
}));

vi.mock("@/components/profitability/profitability-content", () => ({
  ProfitabilityContent: () => <div data-testid="profitability-content-stub" />,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Must import page AFTER vi.mock declarations
import ReportsProfitabilityPage from "@/app/(app)/org/[slug]/reports/profitability/page";

const NOTE = "Costs reflect logged expenses only; disbursements are shown as fee-note lines";

async function renderPage() {
  const result = await ReportsProfitabilityPage({ params: Promise.resolve({ slug: "acme" }) });
  render(result);
}

afterEach(() => {
  cleanup();
  mockApiGet.mockReset();
});

// Phase 87 ADR-340 / Open Question #1 (615E): the cost leg still reads Expense until the
// follow-up lands, so the page says so in one line.
describe("Profitability page cost note (615E)", () => {
  it("shows the note above the data state", async () => {
    mockApiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith("/api/reports/utilization")
          ? { from: "2026-10-01", to: "2026-10-22", members: [{ memberId: "m1" }] }
          : { matters: [] }
      )
    );

    await renderPage();

    expect(screen.getByTestId("profitability-content-stub")).toBeInTheDocument();
    expect(screen.getByTestId("profitability-cost-note")).toHaveTextContent(NOTE);
  });

  it("shows the note in the empty state too", async () => {
    mockApiGet.mockRejectedValue(new Error("backend down"));

    await renderPage();

    expect(screen.queryByTestId("profitability-content-stub")).not.toBeInTheDocument();
    expect(screen.getByTestId("profitability-cost-note")).toHaveTextContent(NOTE);
  });
});
