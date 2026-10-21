import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { MatterListRow } from "@/lib/types";

// Mock server-only before any imports that pull it in
vi.mock("server-only", () => ({}));

// Mock auth — getAuthContext() returns context
const mockGetAuthContext = vi.fn();
vi.mock("@/lib/auth", () => ({
  getAuthContext: () => mockGetAuthContext(),
  AUTH_MODE: "mock",
}));

const mockAuth = {
  mockResolvedValue: (val: {
    orgId: string;
    orgSlug: string;
    userId: string;
    orgRole?: string;
  }) => {
    mockGetAuthContext.mockResolvedValue({
      orgId: val.orgId,
      orgSlug: val.orgSlug,
      userId: val.userId,
      groups: [],
    });
    const role = val.orgRole?.replace("org:", "") ?? "member";
    mockFetchCaps.mockResolvedValue({
      capabilities: [],
      role,
      isAdmin: role === "admin",
      isOwner: role === "owner",
    });
  },
};

// Mock API client
const mockApiGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
  handleApiError: vi.fn(),
  getFieldDefinitions: vi.fn().mockResolvedValue([]),
  getViews: vi.fn().mockResolvedValue([]),
  getSavedView: vi.fn().mockResolvedValue(null),
  getTags: vi.fn().mockResolvedValue([]),
}));

// Mock templates API (has "server-only" import)
// Phase 87 610B: the page reads matters/clients/budget-status through lib/api/{matters,clients},
// which call `api` from "@/lib/api/client".
vi.mock("@/lib/api/client", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
}));

vi.mock("@/lib/api/templates", () => ({
  getProjectTemplates: vi.fn().mockResolvedValue([]),
}));

// 615C: the Trust column depends on the trust_accounting module check
const mockIsModuleEnabled = vi.fn().mockResolvedValue(false);
vi.mock("@/lib/api/settings", () => ({
  isModuleEnabledServer: (...args: unknown[]) => mockIsModuleEnabled(...args),
}));

// 615C: the table row, search and filter sheet are client components using the router
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/matters",
}));

// Mock fetchMyCapabilities (page now uses capabilities instead of orgRole)
const mockFetchCaps = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: () => mockFetchCaps(),
}));

// Mock capabilities (RequiresCapability wraps create buttons on projects page)
vi.mock("@/lib/capabilities", () => ({
  RequiresCapability: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useCapabilities: () => ({
    capabilities: new Set(),
    role: "Admin",
    isAdmin: true,
    isOwner: false,
    isLoading: false,
    hasCapability: () => true,
  }),
}));

// Import page after mocks are set up
import ProjectsPage from "./page";

// Mock NewFromTemplateDialog (client component with hooks)
vi.mock("@/components/templates/NewFromTemplateDialog", () => ({
  NewFromTemplateDialog: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock ViewSelectorClient (client component with hooks)
vi.mock("@/components/views/ViewSelectorClient", () => ({
  ViewSelectorClient: () => <div data-testid="view-selector-client" />,
}));

// Mock ProjectStatusFilter (client component with useSearchParams)
vi.mock("@/components/projects/project-status-filter", () => ({
  ProjectStatusFilter: () => <div data-testid="project-status-filter" />,
}));

// Mock view-actions
vi.mock("./view-actions", () => ({
  createSavedViewAction: vi.fn(),
}));

// Mock CreateProjectDialog (client component with dialog portal)
vi.mock("@/components/projects/create-project-dialog", () => ({
  CreateProjectDialog: ({ slug }: { slug: string }) => (
    <button data-testid="create-project-dialog" data-slug={slug}>
      New Project
    </button>
  ),
}));

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

// 615C: GET /api/matters returns the dense MatterListRow projection
const makeProject = (overrides: Partial<MatterListRow> = {}): MatterListRow => ({
  id: "proj-1",
  name: "Test Matter",
  description: "A test matter",
  referenceNumber: null,
  status: "ACTIVE",
  workType: null,
  priority: null,
  dueDate: null,
  createdAt: "2026-01-15T10:00:00Z",
  clientId: null,
  clientName: null,
  feeEarnerId: null,
  feeEarnerName: null,
  matterRole: null,
  unbilledMinutes: 0,
  unbilledValue: null,
  currency: "ZAR",
  trustBalance: null,
  nextDiaryDate: null,
  nextDiaryKind: null,
  openTaskCount: 0,
  tags: [],
  customFields: {},
  ...overrides,
});

const params = Promise.resolve({ slug: "acme" });
const searchParams = Promise.resolve({} as Record<string, string | string[] | undefined>);

afterEach(() => cleanup());

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ProjectsPage", () => {
  describe("New Project button", () => {
    it("renders for org:member role", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:member",
      });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });

    it("renders for org:admin role", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:admin",
      });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });

    it("renders for org:owner role", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:owner",
      });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });
  });

  // 615C (F-9): the card grid (and its Lead/Member role badges) became a dense table; the
  // §12.6.4 column set has no role column. These assertions replace the card role-badge ones.
  describe("matters table", () => {
    const asMember = () =>
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:member",
      });

    it("renders one table row per matter with the default column headers", async () => {
      asMember();
      mockApiGet.mockResolvedValue([
        makeProject(),
        makeProject({ id: "proj-2", name: "Second Matter" }),
      ]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(screen.getAllByTestId("matter-row")).toHaveLength(2);
      const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
      expect(headers).toContain("Reference");
      expect(headers).toContain("Client");
      expect(headers).toContain("Fee earner");
      expect(screen.getByTestId("matters-footer")).toHaveTextContent("Showing 2 matters");
    });

    it("shows — for null financials and omits Trust when trust accounting is off", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
      expect(headers).not.toContain("Trust");
      expect(screen.getAllByText("—").length).toBeGreaterThan(0);
    });

    it("shows the Trust column when trust accounting is on", async () => {
      asMember();
      mockIsModuleEnabled.mockResolvedValueOnce(true);
      mockApiGet.mockResolvedValue([makeProject({ trustBalance: null })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
      expect(headers).toContain("Trust");
    });

    it("requests the default Open matters view and makes no per-row budget calls", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject(), makeProject({ id: "proj-2" })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      const urls = mockApiGet.mock.calls.map((c) => String(c[0]));
      expect(urls).toContain("/api/matters?status=ACTIVE");
      expect(urls.some((u) => u.includes("/budget"))).toBe(false);
    });

    it("forwards a system view's params and lets the URL status override it", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve({ view: "system:my-matters", status: "CLOSED" }),
      });
      render(jsx);

      const urls = mockApiGet.mock.calls.map((c) => String(c[0]));
      expect(urls).toContain("/api/matters?mine=true&status=CLOSED");
    });

    it("filters by ?search= and keeps the header count in step", async () => {
      asMember();
      mockApiGet.mockResolvedValue([
        makeProject({ id: "a", name: "RAF claim" }),
        makeProject({ id: "b", name: "Estate late Moroka" }),
      ]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve({ search: "raf" }),
      });
      render(jsx);

      expect(screen.getAllByTestId("matter-row")).toHaveLength(1);
      expect(screen.getByText("RAF claim")).toBeInTheDocument();
      expect(screen.getByText("1 matter")).toBeInTheDocument();
    });

    it("shows a no-match message (not the first-run empty state) when filters exclude everything", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject({ name: "RAF claim" })]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve({ search: "zzz" }),
      });
      render(jsx);

      expect(screen.getByText("No matters match this view.")).toBeInTheDocument();
      expect(screen.queryByText("No matters yet")).not.toBeInTheDocument();
    });
  });

  describe("empty state", () => {
    it("shows catalog empty state message for org:member", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:member",
      });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText("No matters yet")).toBeInTheDocument();
      expect(screen.getByText(/not on any matters yet/)).toBeInTheDocument();
    });

    it("shows catalog empty state message for org:admin", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:admin",
      });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText("No matters yet")).toBeInTheDocument();
      expect(
        screen.getByText("Open your first matter to start recording time.")
      ).toBeInTheDocument();
    });

    it("shows New Project button in empty state for members", async () => {
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:member",
      });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      // Header button + empty state button = 2
      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(2);
    });
  });
});
