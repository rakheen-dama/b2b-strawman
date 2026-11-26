import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockFetchCaps = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: () => mockFetchCaps(),
}));

const mockGetReportDefinition = vi.fn();
vi.mock("@/lib/api/reports", () => ({
  getReportDefinition: (...args: unknown[]) => mockGetReportDefinition(...args),
}));

vi.mock("@/lib/api", () => ({
  handleApiError: vi.fn((error: unknown) => {
    throw error;
  }),
}));

vi.mock("@/components/reports/report-runner", () => ({
  ReportRunner: () => <div data-testid="report-runner" />,
}));

vi.mock("@/components/breadcrumb-label-provider", () => ({
  BreadcrumbLabel: () => null,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

import ReportDetailPage from "@/app/(app)/org/[slug]/reports/[reportSlug]/page";

// K-64 (663C): GET /api/report-definitions/{slug} needs FINANCIAL_VISIBILITY, so a member's deep
// link 403'd and handleApiError re-threw it (crash panel). The runner now copies the /reports index
// gate: no FINANCIAL_VISIBILITY → PermissionDenied, and no definition read.

const DEFINITION = {
  slug: "revenue-summary",
  name: "Revenue Summary",
  description: "Overview of revenue by period",
  category: "FINANCIAL",
  parameterSchema: { parameters: [] },
  columnDefinitions: { columns: [] },
  isSystem: true,
};

function asRole(role: "owner" | "member", capabilities: string[] = []) {
  mockFetchCaps.mockResolvedValue({
    capabilities,
    role,
    isAdmin: false,
    isOwner: role === "owner",
  });
}

async function renderPage() {
  render(
    await ReportDetailPage({
      params: Promise.resolve({ slug: "acme", reportSlug: "revenue-summary" }),
    })
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockGetReportDefinition.mockResolvedValue(DEFINITION);
});

afterEach(() => cleanup());

describe("reports/[reportSlug] — FINANCIAL_VISIBILITY gate (K-64)", () => {
  it("a member without FINANCIAL_VISIBILITY gets PermissionDenied and no definition read", async () => {
    asRole("member");
    await renderPage();
    expect(screen.getByText("You don't have access to Reports")).toBeInTheDocument();
    expect(mockGetReportDefinition).not.toHaveBeenCalled();
    expect(screen.queryByTestId("report-runner")).not.toBeInTheDocument();
  });

  it("a member with FINANCIAL_VISIBILITY gets the runner", async () => {
    asRole("member", ["FINANCIAL_VISIBILITY"]);
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Revenue Summary" })).toBeInTheDocument();
    expect(screen.getByTestId("report-runner")).toBeInTheDocument();
    expect(mockGetReportDefinition).toHaveBeenCalledWith("revenue-summary");
  });

  it("an owner gets the runner", async () => {
    asRole("owner");
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Revenue Summary" })).toBeInTheDocument();
    expect(screen.getByTestId("report-runner")).toBeInTheDocument();
  });
});
