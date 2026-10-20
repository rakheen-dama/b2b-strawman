import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) => mockFetchMyCapabilities(...args),
}));

const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

const mockGetUnbilledSummary = vi.fn();
vi.mock("@/lib/api/billing-runs", () => ({
  getUnbilledSummary: (...args: unknown[]) => mockGetUnbilledSummary(...args),
}));

const mockReplace = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: mockReplace, refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices/unbilled",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import UnbilledWorkPage from "@/app/(app)/org/[slug]/invoices/unbilled/page";
import { formatCurrency, formatLocalDate } from "@/lib/format";

const OWNER = { capabilities: [], role: "Owner", isAdmin: false, isOwner: true };

function row(overrides: Record<string, unknown> = {}) {
  return {
    clientId: "c-1",
    clientName: "Mokoena Attorneys Client",
    clientEmail: "client@example.com",
    unbilledTimeEntryCount: 2,
    unbilledTimeAmount: 5400,
    unbilledExpenseCount: 1,
    unbilledExpenseAmount: 500,
    totalUnbilledAmount: 5900,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    ...overrides,
  };
}

async function renderPage(searchParams: Record<string, string> = {}) {
  const result = await UnbilledWorkPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

describe("Unbilled work page (Phase 87 612B)", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] }); // only Date — promises/timers stay real
    vi.setSystemTime(new Date(2026, 9, 21, 10, 0, 0)); // 21 Oct 2026, local time
    mockFetchMyCapabilities.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
    mockGetUnbilledSummary.mockResolvedValue([row()]);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("defaults to the last 24 months in the org currency", async () => {
    await renderPage();
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith({
      periodFrom: "2024-10-21",
      periodTo: "2026-10-21",
      currency: "ZAR",
    });
    const help = screen.getByTestId("unbilled-period-help").textContent ?? "";
    expect(help).toContain("24 months");
    expect(help).toContain(formatLocalDate("2024-10-21"));
    expect(help).toContain(formatLocalDate("2026-10-21"));
    expect(screen.getByLabelText("From")).toHaveValue("2024-10-21");
    expect(screen.getByLabelText("To")).toHaveValue("2026-10-21");
  });

  it("uses the org default currency when it is not ZAR", async () => {
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "USD", enabledModules: [] });
    await renderPage();
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith(
      expect.objectContaining({ currency: "USD" })
    );
  });

  it("renders a row per client with its total and a grand total", async () => {
    mockGetUnbilledSummary.mockResolvedValue([
      row(),
      row({
        clientId: "c-2",
        clientName: "Second Client",
        totalUnbilledAmount: 100,
        hasPrerequisiteIssues: true,
        prerequisiteIssueReason: "Client has no billing address",
      }),
    ]);
    await renderPage();
    expect(screen.getByText("Mokoena Attorneys Client").closest("a")).toHaveAttribute(
      "href",
      "/org/acme/clients/c-1"
    );
    expect(screen.getByText("Second Client")).toBeInTheDocument();
    expect(screen.getByText("Client has no billing address")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-grand-total").textContent).toBe(
      formatCurrency(6000, "ZAR")
    );
  });

  it("uses periodFrom/periodTo/currency from the URL", async () => {
    await renderPage({ periodFrom: "2026-01-01", periodTo: "2026-03-31", currency: "USD" });
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith({
      periodFrom: "2026-01-01",
      periodTo: "2026-03-31",
      currency: "USD",
    });
  });

  it("falls back to the defaults for malformed search params", async () => {
    await renderPage({ periodFrom: "yesterday", periodTo: "2026/10/01", currency: "rand" });
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith({
      periodFrom: "2024-10-21",
      periodTo: "2026-10-21",
      currency: "ZAR",
    });
  });

  it("falls back to the default periodFrom for an impossible calendar date", async () => {
    await renderPage({ periodFrom: "2026-02-31", periodTo: "2026-03-31" });
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith({
      periodFrom: "2024-10-21",
      periodTo: "2026-03-31",
      currency: "ZAR",
    });
  });

  it("falls back to both defaults for an inverted range", async () => {
    await renderPage({ periodFrom: "2026-10-15", periodTo: "2025-01-01" });
    expect(mockGetUnbilledSummary).toHaveBeenCalledWith({
      periodFrom: "2024-10-21",
      periodTo: "2026-10-21",
      currency: "ZAR",
    });
  });

  it("applies an edited period to the URL search params", async () => {
    await renderPage();
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-01-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(mockReplace).toHaveBeenCalledWith(
      "/org/acme/invoices/unbilled?periodFrom=2026-01-01&periodTo=2026-10-21&currency=ZAR"
    );
  });

  it("blocks Apply when the start date is after the end date", async () => {
    await renderPage();
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-12-01" } });
    expect(screen.getByRole("button", { name: "Apply" })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The start date must be on or before the end date."
    );
  });

  it("is not found for a user without INVOICING, and never calls the API", async () => {
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: [],
      role: "Member",
      isAdmin: false,
      isOwner: false,
    });
    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockGetUnbilledSummary).not.toHaveBeenCalled();
    expect(mockGetOrgSettings).not.toHaveBeenCalled();
  });

  it("allows a member who holds INVOICING", async () => {
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: ["INVOICING"],
      role: "Member",
      isAdmin: false,
      isOwner: false,
    });
    await renderPage();
    expect(screen.getByRole("heading", { name: "Unbilled work" })).toBeInTheDocument();
  });

  it("shows an empty state when nothing is unbilled", async () => {
    mockGetUnbilledSummary.mockResolvedValue([]);
    await renderPage();
    expect(screen.getByText("No unbilled work")).toBeInTheDocument();
    expect(screen.queryByTestId("unbilled-work-table")).not.toBeInTheDocument();
  });

  it("shows an error state when the summary fails to load", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetUnbilledSummary.mockRejectedValue(new Error("boom"));
    await renderPage();
    expect(screen.getByText("Unable to load unbilled work")).toBeInTheDocument();
  });
});
