import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import type { MatterListRow, SavedViewResponse, TagResponse } from "@/lib/types";

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

// OQ #19: capture the props the server page hands MattersDataTable (a client component) while
// still rendering the real table, so the RSC-boundary guard below inspects what the page passes.
const tableProps = vi.hoisted(() => [] as Record<string, unknown>[]);
vi.mock("@/components/matters/matters-table", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/matters/matters-table")>();
  return {
    ...actual,
    MattersDataTable: (props: React.ComponentProps<typeof actual.MattersDataTable>) => {
      tableProps.push(props as unknown as Record<string, unknown>);
      return <actual.MattersDataTable {...props} />;
    },
  };
});

// Import page after mocks are set up
import ProjectsPage from "./page";
import { getTags, getViews } from "@/lib/api";

// Mock NewFromTemplateDialog (client component with hooks)
vi.mock("@/components/templates/NewFromTemplateDialog", () => ({
  NewFromTemplateDialog: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock ViewSelectorClient (client component with hooks)
vi.mock("@/components/views/ViewSelectorClient", () => ({
  ViewSelectorClient: ({ clearOnSavedView }: { clearOnSavedView?: readonly string[] }) => (
    <div
      data-testid="view-selector-client"
      data-clear-on-saved-view={clearOnSavedView?.join(",")}
    />
  ),
}));

// Mock ProjectStatusFilter (client component with useSearchParams)
vi.mock("@/components/projects/project-status-filter", () => ({
  ProjectStatusFilter: () => <div data-testid="project-status-filter" />,
}));

// Mock MatterFiltersSheet (client sheet with hooks); only its presence matters here
vi.mock("@/components/matters/matter-filters-sheet", () => ({
  MatterFiltersSheet: () => <button data-testid="matter-filters-trigger">Filters</button>,
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

// Mock next/link — `scroll` / `prefetch` are next/link props, not DOM attributes: drop them.
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => {
    const anchorProps = { ...props };
    delete anchorProps.scroll;
    delete anchorProps.prefetch;
    return (
      <a href={href} {...anchorProps}>
        {children}
      </a>
    );
  },
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
  tableProps.length = 0;
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

    const headerCell = (label: string) =>
      screen.getAllByRole("columnheader").find((h) => h.textContent?.trim() === label);

    it("drops low-priority columns in narrow containers on the default column set", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(headerCell("Work type")).toHaveClass("hidden");
      expect(headerCell("Opened")).toHaveClass("hidden");
      expect(headerCell("Matter")).not.toHaveClass("hidden");
    });

    it("always renders the columns a saved view chose", async () => {
      asMember();
      vi.mocked(getViews).mockResolvedValueOnce([
        {
          id: "11111111-1111-1111-1111-111111111111",
          entityType: "PROJECT",
          name: "By work type",
          filters: {},
          columns: ["name", "workType", "createdAt"],
          shared: false,
          createdBy: "user_1",
          sortOrder: 0,
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
        },
      ]);
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve({ view: "11111111-1111-1111-1111-111111111111" }),
      });
      render(jsx);

      expect(screen.getAllByRole("columnheader").map((h) => h.textContent?.trim())).toEqual([
        "Matter",
        "Work type",
        "Opened",
        "Actions",
      ]);
      expect(headerCell("Work type")).not.toHaveClass("hidden");
      expect(headerCell("Opened")).not.toHaveClass("hidden");
    });

    it("has the view selector drop the backend-ignored tags param on saved views (F-3)", async () => {
      asMember();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByTestId("view-selector-client")).toHaveAttribute(
        "data-clear-on-saved-view",
        "tags"
      );
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

  // 641A: the list composes PageHeader + MattersDataTable (toolbar / empty / footer slots).
  describe("page composition", () => {
    const asAdmin = () =>
      mockAuth.mockResolvedValue({
        orgId: "org_1",
        orgSlug: "acme",
        userId: "user_1",
        orgRole: "org:admin",
      });

    const SAVED_VIEW_ID = "22222222-2222-2222-2222-222222222222";
    const savedView: SavedViewResponse = {
      id: SAVED_VIEW_ID,
      entityType: "PROJECT",
      name: "Mine",
      filters: {},
      columns: null,
      shared: false,
      createdBy: "user_1",
      sortOrder: 0,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    };
    const tag: TagResponse = {
      id: "t1",
      name: "Urgent",
      slug: "urgent",
      color: null,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    };

    it("renders a single h1 'Matters' in the page header with the create action", async () => {
      asAdmin();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      const headings = screen.getAllByRole("heading", { level: 1 });
      expect(headings).toHaveLength(1);
      expect(headings[0]).toHaveTextContent("Matters");
      const header = screen.getByTestId("page-header");
      expect(header).toContainElement(headings[0]);
      expect(within(header).getByTestId("create-project-dialog")).toBeInTheDocument();
    });

    it("shows the matter count as the page header description", async () => {
      asAdmin();
      mockApiGet.mockResolvedValue([makeProject(), makeProject({ id: "proj-2" })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(within(screen.getByTestId("page-header")).getByText("2 matters")).toBeInTheDocument();
    });

    it("renders the Columns and Export CSV controls and the matters-table testid on the table", async () => {
      asAdmin();
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByRole("button", { name: "Columns" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Export CSV" })).toBeInTheDocument();
      expect(screen.getByTestId("matters-table").tagName).toBe("TABLE");
    });

    it.each([
      ["unfiltered", {}],
      ["filtered", { search: "zzz" }],
    ])("keeps the toolbar on an empty list (%s), with no table or footer", async (_, query) => {
      asAdmin();
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve(query as Record<string, string>),
      });
      render(jsx);

      expect(screen.getByTestId("view-selector-client")).toBeInTheDocument();
      expect(screen.getByTestId("project-status-filter")).toBeInTheDocument();
      expect(screen.queryByRole("table")).toBeNull();
      expect(screen.queryByTestId("matters-footer")).toBeNull();
    });

    it("the filtered empty state links back to the unfiltered list", async () => {
      asAdmin();
      mockApiGet.mockResolvedValue([makeProject({ name: "RAF claim" })]);

      const jsx = await ProjectsPage({
        params,
        searchParams: Promise.resolve({ search: "zzz" }),
      });
      render(jsx);

      expect(screen.getByText("No matters match this view.")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Clear filters" })).toHaveAttribute(
        "href",
        "/org/acme/matters"
      );
    });

    // Pre-rendered ReactNode slots may hold elements (their `type` is a component function, which
    // React serialises as a client reference or server output); every other prop must be data.
    const NODE_SLOTS = new Set(["toolbar", "empty", "footer"]);

    /** Paths of values the RSC boundary cannot carry: functions, and elements outside a slot. */
    function unserializablePaths(value: unknown, path: string, inSlot: boolean): string[] {
      if (typeof value === "function") return [path];
      if (React.isValidElement(value)) return inSlot ? [] : [path];
      if (Array.isArray(value)) {
        return value.flatMap((item, i) => unserializablePaths(item, `${path}[${i}]`, inSlot));
      }
      if (typeof value === "object" && value !== null) {
        return Object.entries(value).flatMap(([key, item]) =>
          unserializablePaths(item, `${path}.${key}`, inSlot)
        );
      }
      return [];
    }

    it.each([
      ["default view with rows", {}, [makeProject({ customFields: { court: "Gauteng" } })]],
      ["saved view", { view: SAVED_VIEW_ID }, [makeProject()]],
      ["filtered empty", { search: "zzz" }, [makeProject({ name: "RAF claim" })]],
      ["first-run empty", {}, []],
    ])(
      "passes MattersDataTable only serializable props (RSC boundary, OQ #19): %s",
      async (_, query, rows) => {
        asAdmin();
        vi.mocked(getTags).mockResolvedValueOnce([tag]);
        vi.mocked(getViews).mockResolvedValueOnce([savedView]);
        mockApiGet.mockResolvedValue(rows);

        render(
          await ProjectsPage({
            params,
            searchParams: Promise.resolve(query as Record<string, string>),
          })
        );

        expect(tableProps).toHaveLength(1);
        const props = tableProps[0];
        // Non-vacuous: the page really passed the data props and the slots.
        expect(props).toEqual(
          expect.objectContaining({
            slug: "acme",
            rows: expect.any(Array),
            sort: expect.any(Object),
          })
        );
        expect(props.empty).toBeTruthy();
        const offending = Object.entries(props).flatMap(([key, item]) =>
          unserializablePaths(item, key, NODE_SLOTS.has(key))
        );
        expect(offending).toEqual([]);
      }
    );

    it("shows the tag filters sheet on the default view only (F-3)", async () => {
      asAdmin();
      vi.mocked(getTags).mockResolvedValueOnce([tag]);
      mockApiGet.mockResolvedValue([makeProject()]);

      render(await ProjectsPage({ params, searchParams }));
      expect(screen.getByTestId("matter-filters-trigger")).toBeInTheDocument();
      cleanup();

      vi.mocked(getTags).mockResolvedValueOnce([tag]);
      vi.mocked(getViews).mockResolvedValueOnce([savedView]);
      render(
        await ProjectsPage({ params, searchParams: Promise.resolve({ view: SAVED_VIEW_ID }) })
      );
      expect(screen.getByTestId("matter-row")).toBeInTheDocument();
      expect(screen.queryByTestId("matter-filters-trigger")).toBeNull();
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
