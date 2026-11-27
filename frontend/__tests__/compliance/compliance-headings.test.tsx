import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// Phase 91 669A: every compliance page branch renders exactly one h1 through PageHeader (DA-1 the
// detail exception keeps its own h1 on the PageHeader title recipe). K-84: /compliance reads
// "FICA & compliance" (founder-approved copy); the other gate h1s keep their text (K-78 backlog).

vi.mock("server-only", () => ({}));

const { mockCaps, mockDashboard, mockGetDataRequests, mockGetDataRequest } = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockDashboard: vi.fn(),
  mockGetDataRequests: vi.fn(),
  mockGetDataRequest: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api/ai", () => ({ getAiProfile: vi.fn() }));
vi.mock("@/lib/api/compliance-audit", () => ({ getAuditReports: vi.fn() }));
vi.mock("@/app/(app)/org/[slug]/compliance/actions", () => ({
  getComplianceDashboardData: mockDashboard,
}));
vi.mock("@/lib/compliance-api", () => ({
  getDataRequests: mockGetDataRequests,
  getDataRequest: mockGetDataRequest,
}));
vi.mock("@/lib/api", () => ({ handleApiError: vi.fn() }));
vi.mock("@/components/compliance/LifecycleDistributionSection", () => ({
  LifecycleDistributionSection: () => <div data-testid="lifecycle-section" />,
}));
vi.mock("@/components/compliance/OnboardingPipelineSection", () => ({
  OnboardingPipelineSection: () => <div data-testid="onboarding-section" />,
}));
vi.mock("@/components/compliance/DataRequestsSection", () => ({
  DataRequestsSection: () => <div data-testid="data-requests-section" />,
}));
vi.mock("@/components/compliance/DormancyCheckSection", () => ({
  DormancyCheckSection: () => <div data-testid="dormancy-section" />,
}));
vi.mock("@/components/ai/compliance-audit-tab", () => ({
  ComplianceAuditTab: () => <div data-testid="ai-audit-tab" />,
}));
vi.mock("@/components/compliance/CreateDataRequestDialog", () => ({
  CreateDataRequestDialog: () => <button type="button">New Request</button>,
}));
vi.mock("@/components/compliance/DataRequestTable", () => ({
  DataRequestTable: () => <table data-testid="data-request-table" />,
}));
vi.mock("@/components/compliance/DataRequestTimeline", () => ({
  DataRequestTimeline: () => <div data-testid="timeline" />,
}));
vi.mock("@/components/compliance/DataRequestActions", () => ({
  DataRequestActions: () => <div data-testid="actions" />,
}));
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/compliance",
  useSearchParams: () => new URLSearchParams(),
}));

import ComplianceDashboardPage from "@/app/(app)/org/[slug]/compliance/page";
import DataRequestsPage from "@/app/(app)/org/[slug]/compliance/requests/page";
import DataRequestDetailPage from "@/app/(app)/org/[slug]/compliance/requests/[id]/page";
import type { DataRequestResponse } from "@/lib/types";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const params = Promise.resolve({ slug: "acme" });

function dataRequest(overrides: Partial<DataRequestResponse> = {}): DataRequestResponse {
  return {
    id: "req-1",
    clientId: "cust-1",
    clientName: "Acme Corp",
    requestType: "ACCESS",
    status: "RECEIVED",
    description: "Copy of my data",
    rejectionReason: null,
    deadline: "2099-12-27",
    requestedAt: "2026-11-27T08:29:12Z",
    requestedBy: "member-1",
    requestedByName: null,
    completedAt: null,
    completedBy: null,
    completedByName: null,
    hasExport: false,
    notes: null,
    createdAt: "2026-11-27T08:29:12Z",
    ...overrides,
  };
}

function onlyH1(): HTMLElement {
  const headings = screen.getAllByRole("heading", { level: 1 });
  expect(headings).toHaveLength(1);
  return headings[0];
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => cleanup());

describe("/compliance", () => {
  it('renders one h1 "FICA & compliance" (K-84)', async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockDashboard.mockResolvedValue({
      success: true,
      data: {
        lifecycleCounts: {},
        onboardingCustomers: [],
        openDataRequests: { total: 0, urgent: [] },
      },
    });
    render(await ComplianceDashboardPage({ params }));
    expect(onlyH1().textContent).toBe("FICA & compliance");
    expect(screen.getByRole("tab", { name: "Overview" })).toBeInTheDocument();
  });

  it("keeps the same single h1 on the load-error branch", async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockDashboard.mockResolvedValue({ success: false, error: "Boom" });
    render(await ComplianceDashboardPage({ params }));
    expect(onlyH1().textContent).toBe("FICA & compliance");
    expect(screen.getByText("Boom")).toBeInTheDocument();
  });

  it("is not found for a member without CUSTOMER_MANAGEMENT", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await expect(ComplianceDashboardPage({ params })).rejects.toThrow("NEXT_NOT_FOUND");
  });
});

describe("/compliance/requests", () => {
  it('admin: one h1 named exactly "Data Requests"; the count pill sits outside it (DA-2)', async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetDataRequests.mockResolvedValue([dataRequest(), dataRequest({ id: "req-2" })]);
    render(await DataRequestsPage({ params, searchParams: Promise.resolve({}) }));
    const h1 = onlyH1();
    expect(h1.textContent).toBe("Data Requests");
    const header = screen.getByTestId("page-header");
    expect(within(header).getByText("2")).toHaveClass("bg-muted", "text-muted-foreground");
    expect(within(header).getByRole("button", { name: "New Request" })).toBeInTheDocument();
    expect(screen.getByTestId("data-request-table")).toBeInTheDocument();
  });

  it("admin with no requests: the default EmptyState under the single h1", async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetDataRequests.mockResolvedValue([]);
    const { container } = render(
      await DataRequestsPage({ params, searchParams: Promise.resolve({ status: "COMPLETED" }) })
    );
    expect(onlyH1().textContent).toBe("Data Requests");
    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "default"
    );
    expect(screen.getByRole("heading", { level: 2, name: "No data requests found" })).toBeVisible();
    expect(screen.getByText("No completed data requests.")).toBeInTheDocument();
    expect(within(screen.getByTestId("page-header")).queryByText("0")).not.toBeInTheDocument();
  });

  it('non-admin: one h1 "Compliance" and the permission sentence (K-78 backlog)', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    render(await DataRequestsPage({ params, searchParams: Promise.resolve({}) }));
    expect(onlyH1().textContent).toBe("Compliance");
    expect(
      screen.getByText(/You do not have permission to view compliance data\./)
    ).toBeInTheDocument();
    expect(mockGetDataRequests).not.toHaveBeenCalled();
  });
});

describe("/compliance/requests/[id]", () => {
  const detailParams = Promise.resolve({ slug: "acme", id: "req-1" });

  it("admin: one h1 (the client name) on the PageHeader title recipe, not bold (K-82)", async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetDataRequest.mockResolvedValue(dataRequest());
    render(await DataRequestDetailPage({ params: detailParams }));
    const h1 = onlyH1();
    expect(h1.textContent).toBe("Acme Corp");
    expect(h1).toHaveClass("font-display", "text-2xl", "font-semibold", "tracking-tight");
    expect(h1).not.toHaveClass("font-bold");
  });

  it('non-admin: one h1 "Data Request" and the permission sentence', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    render(await DataRequestDetailPage({ params: detailParams }));
    expect(onlyH1().textContent).toBe("Data Request");
    expect(screen.getByText(/You do not have permission to view data requests\./)).toBeVisible();
  });
});
