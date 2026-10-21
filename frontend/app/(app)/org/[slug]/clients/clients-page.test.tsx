import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import type { ClientListRow, SavedViewResponse } from "@/lib/types";

vi.mock("server-only", () => ({}));

// 615D: the page reads GET /api/clients through lib/api/clients → `api` from "@/lib/api/client"
const mockApiGet = vi.fn();
vi.mock("@/lib/api/client", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
}));

const mockGetViews = vi.fn();
vi.mock("@/lib/api", () => ({
  handleApiError: vi.fn(),
  getFieldDefinitions: vi.fn().mockResolvedValue([]),
  getViews: (...args: unknown[]) => mockGetViews(...args),
  getTags: vi.fn().mockResolvedValue([]),
}));

const mockIsModuleEnabled = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  isModuleEnabledServer: (...args: unknown[]) => mockIsModuleEnabled(...args),
}));

const mockFetchCaps = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: () => mockFetchCaps(),
}));

vi.mock("@/lib/capabilities", () => ({
  RequiresCapability: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/clients",
}));

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

vi.mock("@/components/views/ViewSelectorClient", () => ({
  ViewSelectorClient: ({
    systemViews,
    defaultViewId,
    clearOnSavedView,
  }: {
    systemViews?: readonly { name: string }[];
    defaultViewId?: string;
    clearOnSavedView?: readonly string[];
  }) => (
    <div
      data-testid="view-selector-client"
      data-system-views={systemViews?.map((v) => v.name).join("|")}
      data-default-view={defaultViewId}
      data-clear-on-saved-view={clearOnSavedView?.join(",")}
    />
  ),
}));

vi.mock("@/components/customers/create-customer-dialog", () => ({
  CreateCustomerDialog: ({ slug }: { slug: string }) => (
    <button data-testid="create-customer-dialog" data-slug={slug}>
      New Client
    </button>
  ),
}));

vi.mock("./view-actions", () => ({
  createSavedViewAction: vi.fn(),
}));

import ClientsPage from "./page";

function makeRow(overrides: Partial<ClientListRow> = {}): ClientListRow {
  return {
    id: "c1",
    name: "Sipho Ndlovu",
    email: "sipho@example.com",
    phone: null,
    clientType: "INDIVIDUAL",
    lifecycleStatus: "PROSPECT",
    status: "ACTIVE",
    createdAt: "2026-05-01T08:00:00Z",
    lifecycleStatusChangedAt: null,
    ficaStatus: "NOT_STARTED",
    ficaLastVerifiedAt: null,
    outstandingFeeNoteTotal: null,
    outstandingFeeNoteCount: 0,
    currency: "ZAR",
    trustBalance: null,
    openMatterCount: 0,
    completenessPercent: 50,
    tags: [],
    customFields: {},
    ...overrides,
  };
}

const SAVED_ID = "11111111-1111-1111-1111-111111111111";
function savedView(overrides: Partial<SavedViewResponse> = {}): SavedViewResponse {
  return {
    id: SAVED_ID,
    entityType: "CUSTOMER",
    name: "Saved actives",
    filters: {},
    columns: null,
    shared: false,
    createdBy: "u1",
    sortOrder: 0,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

function asRole(role: "owner" | "admin" | "member", capabilities: string[] = []) {
  mockFetchCaps.mockResolvedValue({
    capabilities,
    role,
    isAdmin: role === "admin",
    isOwner: role === "owner",
  });
}

const params = Promise.resolve({ slug: "acme" });
async function renderPage(search: Record<string, string> = {}) {
  const jsx = await ClientsPage({ params, searchParams: Promise.resolve(search) });
  return render(jsx);
}

const requestedUrls = () => mockApiGet.mock.calls.map((c) => String(c[0]));
const rowNames = () => screen.getAllByTestId("client-link").map((l) => l.textContent?.trim() ?? "");

afterEach(() => cleanup());

beforeEach(() => {
  vi.clearAllMocks();
  mockGetViews.mockResolvedValue([]);
  mockIsModuleEnabled.mockResolvedValue(false);
  mockApiGet.mockResolvedValue([makeRow()]);
  asRole("owner");
});

describe("ClientsPage — permission gate", () => {
  it("shows permission denied for a member without CUSTOMER_MANAGEMENT", async () => {
    asRole("member");
    await renderPage();
    expect(screen.getByText("You don't have access to Clients")).toBeInTheDocument();
    expect(mockApiGet).not.toHaveBeenCalled();
  });

  it("lets a member with CUSTOMER_MANAGEMENT in", async () => {
    asRole("member", ["CUSTOMER_MANAGEMENT"]);
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Clients" })).toBeInTheDocument();
    expect(screen.getByTestId("clients-table")).toBeInTheDocument();
  });
});

describe("ClientsPage — list request", () => {
  it("D-1(B): no view → all clients, no params, and no completeness-summary call", async () => {
    mockApiGet.mockResolvedValue([
      makeRow({ id: "a", name: "Prospect Co", lifecycleStatus: "PROSPECT" }),
      makeRow({ id: "b", name: "Active Co", lifecycleStatus: "ACTIVE" }),
    ]);
    await renderPage();
    expect(requestedUrls()).toEqual(["/api/clients"]);
    expect(screen.getAllByTestId("client-row")).toHaveLength(2);
    expect(screen.getByText("2 clients")).toBeInTheDocument();
    expect(screen.getByTestId("clients-footer")).toHaveTextContent("Showing 2 clients");
  });

  it("wires All clients + the client system views, default All, clearing tags + lifecycle on saved views", async () => {
    await renderPage();
    const selector = screen.getByTestId("view-selector-client");
    expect(selector).toHaveAttribute(
      "data-system-views",
      "All clients|Active|FICA outstanding|With trust balance"
    );
    expect(selector).toHaveAttribute("data-default-view", "system:all-clients");
    expect(selector).toHaveAttribute("data-clear-on-saved-view", "tags,lifecycleStatus");
  });

  it.each([
    [{ view: "system:active" }, "/api/clients?lifecycleStatus=ACTIVE"],
    [{ view: "system:fica-outstanding" }, "/api/clients?ficaStatus=OUTSTANDING"],
    [{ view: "system:with-trust-balance" }, "/api/clients?hasTrustBalance=true"],
    [{ showIncomplete: "true" }, "/api/clients?showIncomplete=true"],
    [{ lifecycleStatus: "PROSPECT" }, "/api/clients?lifecycleStatus=PROSPECT"],
    [{ view: "system:active", lifecycleStatus: "ALL" }, "/api/clients"],
    [{ view: "system:active", lifecycleStatus: "DORMANT" }, "/api/clients?lifecycleStatus=DORMANT"],
    [{ view: "not-a-uuid" }, "/api/clients"],
  ])("%o → %s", async (search, url) => {
    await renderPage(search);
    expect(requestedUrls()).toEqual([url]);
  });

  it("saved view: view=<uuid> + backend-applied filters; lifecycle pills and tags hidden", async () => {
    mockGetViews.mockResolvedValue([
      savedView({ filters: { ficaStatus: "OUTSTANDING", lifecycleStatus: "ACTIVE" } }),
    ]);
    mockApiGet.mockResolvedValue([
      makeRow({ id: "a", name: "Active Co", lifecycleStatus: "ACTIVE" }),
      makeRow({ id: "b", name: "Prospect Co", lifecycleStatus: "PROSPECT" }),
    ]);
    await renderPage({ view: SAVED_ID, lifecycleStatus: "PROSPECT", tags: "vip" });
    expect(requestedUrls()).toEqual([`/api/clients?view=${SAVED_ID}&ficaStatus=OUTSTANDING`]);
    // The stored lifecycle is applied to the rows (the backend ignores it on the view path).
    expect(rowNames()).toEqual(["Active Co"]);
    expect(screen.queryByTestId("client-lifecycle-filter")).not.toBeInTheDocument();
    expect(screen.getByTestId("client-show-incomplete")).toBeInTheDocument();
  });
});

describe("ClientsPage — table", () => {
  it("sorts by name by default and by ?sortBy=completeness", async () => {
    mockApiGet.mockResolvedValue([
      makeRow({ id: "a", name: "Charlie", completenessPercent: 20 }),
      makeRow({ id: "b", name: "Alpha", completenessPercent: null }),
      makeRow({ id: "c", name: "Bravo", completenessPercent: 90 }),
    ]);
    await renderPage();
    expect(rowNames()).toEqual(["Alpha", "Bravo", "Charlie"]);
    cleanup();
    await renderPage({ sortBy: "completeness", sortDir: "desc" });
    expect(rowNames()).toEqual(["Bravo", "Charlie", "Alpha"]);
  });

  it("filters by ?search= over name, email and phone, keeping the count in step", async () => {
    mockApiGet.mockResolvedValue([
      makeRow({ id: "a", name: "Apex Holdings", email: "info@apex.co.za" }),
      makeRow({ id: "b", name: "Moroka Family Trust", email: "trust@moroka.co.za" }),
    ]);
    await renderPage({ search: "MOROKA.CO" });
    expect(rowNames()).toEqual(["Moroka Family Trust"]);
    expect(screen.getByText("1 client")).toBeInTheDocument();
  });

  it("shows the Trust balance column only when trust accounting is on (F-8)", async () => {
    await renderPage();
    expect(screen.queryByRole("columnheader", { name: /Trust balance/ })).not.toBeInTheDocument();
    cleanup();
    mockIsModuleEnabled.mockResolvedValue(true);
    await renderPage();
    expect(screen.getByRole("columnheader", { name: /Trust balance/ })).toBeInTheDocument();
  });

  it("keeps the New Client button in the header", async () => {
    await renderPage();
    expect(screen.getByRole("button", { name: "New Client" })).toBeInTheDocument();
  });

  it("renders the lifecycle pills and Show incomplete on system views", async () => {
    await renderPage({ view: "system:active" });
    const group = screen.getByRole("group", { name: "Lifecycle" });
    expect(within(group).getByRole("link", { name: "Active" })).toHaveAttribute(
      "aria-current",
      "true"
    );
    expect(within(group).getByRole("link", { name: "All" })).toHaveAttribute(
      "href",
      "/org/acme/clients?view=system%3Aactive&lifecycleStatus=ALL"
    );
  });
});

describe("ClientsPage — Show incomplete toggle", () => {
  it("default: an inactive toggle link that turns the filter on", async () => {
    await renderPage();
    const toggle = screen.getByTestId("client-show-incomplete");
    expect(toggle.tagName).toBe("A");
    expect(toggle).not.toHaveAttribute("aria-current");
    expect(toggle).toHaveAttribute("href", "/org/acme/clients?showIncomplete=true");
  });

  it("?showIncomplete=true: an active toggle link that turns the filter off", async () => {
    await renderPage({ showIncomplete: "true" });
    const toggle = screen.getByTestId("client-show-incomplete");
    expect(toggle.tagName).toBe("A");
    expect(toggle).toHaveAttribute("aria-current", "true");
    expect(toggle).toHaveAttribute("href", "/org/acme/clients");
  });

  it("saved view without the stored filter: the URL param still toggles it", async () => {
    mockGetViews.mockResolvedValue([savedView()]);
    await renderPage({ view: SAVED_ID, showIncomplete: "true" });
    expect(requestedUrls()).toEqual([`/api/clients?view=${SAVED_ID}&showIncomplete=true`]);
    const toggle = screen.getByTestId("client-show-incomplete");
    expect(toggle.tagName).toBe("A");
    expect(toggle).toHaveAttribute("aria-current", "true");
    expect(toggle).toHaveAttribute("href", `/org/acme/clients?view=${SAVED_ID}`);
  });

  it("saved view storing showIncomplete: an active, non-toggleable indicator", async () => {
    mockGetViews.mockResolvedValue([savedView({ filters: { showIncomplete: true } })]);
    mockApiGet.mockResolvedValue([]);
    await renderPage({ view: SAVED_ID });
    expect(requestedUrls()).toEqual([`/api/clients?view=${SAVED_ID}&showIncomplete=true`]);
    const indicator = screen.getByTestId("client-show-incomplete");
    expect(indicator.tagName).toBe("SPAN");
    expect(indicator).toHaveAttribute("aria-current", "true");
    expect(indicator).not.toHaveAttribute("href");
    expect(screen.queryByRole("link", { name: "Show incomplete" })).not.toBeInTheDocument();
  });
});

describe("ClientsPage — empty states", () => {
  it("no clients at all: first-run empty state with the create action for admins", async () => {
    mockApiGet.mockResolvedValue([]);
    await renderPage();
    expect(screen.getByText("No clients yet")).toBeInTheDocument();
    // Header button + empty-state action
    expect(screen.getAllByTestId("create-customer-dialog")).toHaveLength(2);
  });

  it("no clients, member: member copy and no empty-state action", async () => {
    asRole("member", ["CUSTOMER_MANAGEMENT"]);
    mockApiGet.mockResolvedValue([]);
    await renderPage();
    expect(screen.getByText("No clients have been added yet.")).toBeInTheDocument();
    expect(screen.getAllByTestId("create-customer-dialog")).toHaveLength(1);
  });

  it("Show incomplete with nothing incomplete: all-complete message", async () => {
    mockApiGet.mockResolvedValue([]);
    await renderPage({ showIncomplete: "true" });
    expect(screen.getByText("All clients are complete")).toBeInTheDocument();
  });

  it("a filtered view with no rows: no-match message, not the first-run state", async () => {
    mockApiGet.mockResolvedValue([]);
    await renderPage({ view: "system:fica-outstanding" });
    expect(screen.getByText("No clients match this view.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Clear filters" })).toHaveAttribute(
      "href",
      "/org/acme/clients"
    );
    expect(screen.queryByText("No clients yet")).not.toBeInTheDocument();
  });
});
