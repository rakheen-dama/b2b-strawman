import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// Phase 91 669B: Team, AI Reviews and the three platform-admin pages render exactly one h1 through
// PageHeader with today's h1 text, on every branch (Team admin / member, AI Reviews allowed /
// denied). The platform-admin pages are rendered inside their layout, so the h1 is checked inside
// the layout's new main#main-content (K-98), with one width and one h1 style (K-99).

vi.mock("server-only", () => ({}));

const {
  mockCaps,
  mockApiGet,
  mockFetchOrgRoles,
  mockGetAiGates,
  mockSessionIdentity,
  mockListAccessRequests,
  mockListBillingTenants,
  mockListDemoTenants,
} = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockApiGet: vi.fn(),
  mockFetchOrgRoles: vi.fn(),
  mockGetAiGates: vi.fn(),
  mockSessionIdentity: vi.fn(),
  mockListAccessRequests: vi.fn(),
  mockListBillingTenants: vi.fn(),
  mockListDemoTenants: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api", () => ({ api: { get: mockApiGet } }));
vi.mock("@/lib/api/org-roles", () => ({ fetchOrgRoles: mockFetchOrgRoles }));
vi.mock("@/lib/api/ai", () => ({ getAiGates: mockGetAiGates, getAiGate: vi.fn() }));
vi.mock("@/lib/auth", () => ({ getSessionIdentity: mockSessionIdentity }));

vi.mock("@/components/team/team-tabs", () => ({
  TeamTabs: () => <div data-testid="team-tabs" />,
}));
vi.mock("@/components/team/invite-member-form", () => ({
  InviteMemberForm: () => <div data-testid="invite-member-form" />,
}));
vi.mock("@/app/(app)/org/[slug]/ai/reviews/reviews-client", () => ({
  AiReviewsClient: () => <div data-testid="ai-reviews-client" />,
}));

vi.mock("@/app/(app)/platform-admin/access-requests/actions", () => ({
  listAccessRequests: mockListAccessRequests,
}));
vi.mock("@/app/(app)/platform-admin/billing/actions", () => ({
  listBillingTenants: mockListBillingTenants,
}));
vi.mock("@/app/(app)/platform-admin/demo/actions", () => ({
  listDemoTenants: mockListDemoTenants,
}));
vi.mock("@/components/access-request/access-requests-table", () => ({
  AccessRequestsTable: () => <div data-testid="access-requests-page" />,
}));
vi.mock("@/components/billing/billing-tenants-table", () => ({
  BillingTenantsTable: () => <div data-testid="billing-tenants-page" />,
}));
vi.mock("@/components/billing/demo-tenants-table", () => ({
  DemoTenantsTable: () => <div data-testid="demo-tenants" />,
}));
vi.mock("@/app/(app)/platform-admin/demo/demo-provision-form", () => ({
  DemoProvisionForm: () => <div data-testid="demo-provision-form" />,
}));

const { mockPathname } = vi.hoisted(() => ({ mockPathname: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: mockPathname,
  useSearchParams: () => new URLSearchParams(),
}));

import TeamPage from "@/app/(app)/org/[slug]/team/page";
import AiReviewsPage from "@/app/(app)/org/[slug]/ai/reviews/page";
import PlatformAdminLayout from "@/app/(app)/platform-admin/layout";
import AccessRequestsPage from "@/app/(app)/platform-admin/access-requests/page";
import BillingPage from "@/app/(app)/platform-admin/billing/page";
import DemoProvisionPage from "@/app/(app)/platform-admin/demo/page";

const params = Promise.resolve({ slug: "acme" });

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: ["VIEW_LEGAL"], role: "member", isAdmin: false, isOwner: false };
const REVIEWER = { capabilities: ["AI_REVIEW"], role: "member", isAdmin: false, isOwner: false };

function onlyH1(scope: HTMLElement = document.body): HTMLElement {
  const headings = within(scope).getAllByRole("heading", { level: 1 });
  expect(headings).toHaveLength(1);
  return headings[0];
}

beforeEach(() => {
  vi.clearAllMocks();
  mockApiGet.mockResolvedValue({ limits: { currentMembers: 3, maxMembers: 10 } });
  mockFetchOrgRoles.mockResolvedValue([]);
  mockGetAiGates.mockResolvedValue({ content: [] });
  mockSessionIdentity.mockResolvedValue({ userId: "user_1", groups: ["platform-admins"] });
  mockListAccessRequests.mockResolvedValue({ success: true, data: [] });
  mockListBillingTenants.mockResolvedValue({ success: true, data: [] });
  mockListDemoTenants.mockResolvedValue({ success: true, data: [] });
});
afterEach(() => cleanup());

describe("/team (K-60 Team part)", () => {
  it('renders one h1 "Team" with its HelpTip, the member pill outside the h1 (admin)', async () => {
    mockCaps.mockResolvedValue(OWNER);
    render(await TeamPage({ params }));

    const h1 = onlyH1();
    expect(h1.textContent).toBe("Team");
    expect(h1).toHaveAttribute("data-slot", "page-header-title");
    expect(within(h1).getByRole("button", { name: /^Help:/ })).toBeInTheDocument();
    const pill = screen.getByText("3 members");
    expect(h1).not.toContainElement(pill);
    expect(screen.getByRole("heading", { level: 2, name: "Invite a team member" })).toBeVisible();
    expect(screen.getByTestId("team-tabs")).toBeInTheDocument();
  });

  it('renders one h1 "Team" and no invite card for a member', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    mockApiGet.mockResolvedValue({ limits: { currentMembers: 1, maxMembers: 10 } });
    render(await TeamPage({ params }));

    expect(onlyH1().textContent).toBe("Team");
    expect(screen.getByText("1 member")).toBeInTheDocument();
    expect(screen.queryByTestId("invite-member-form")).toBeNull();
  });
});

describe("/ai/reviews", () => {
  it('renders one h1 "AI Reviews" with its description when allowed', async () => {
    mockCaps.mockResolvedValue(REVIEWER);
    render(await AiReviewsPage({ params }));

    expect(onlyH1().textContent).toBe("AI Reviews");
    expect(
      screen.getByText("Review and approve AI-proposed actions before they are executed.")
    ).toBeVisible();
    expect(screen.getByTestId("ai-reviews-client")).toBeInTheDocument();
  });

  it('renders one h1 "AI Reviews" and the permission sentence when denied', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    render(await AiReviewsPage({ params }));

    expect(onlyH1().textContent).toBe("AI Reviews");
    expect(
      screen.getByText(
        "You do not have permission to review AI actions. Contact your administrator."
      )
    ).toBeVisible();
    expect(screen.queryByTestId("ai-reviews-client")).toBeNull();
    expect(mockGetAiGates).not.toHaveBeenCalled();
  });
});

describe("/platform-admin (K-98, K-99)", () => {
  async function renderInLayout(page: React.ReactNode, pathname: string) {
    mockPathname.mockReturnValue(pathname);
    const { container } = render(await PlatformAdminLayout({ children: page }));
    const main = container.querySelector<HTMLElement>("main#main-content");
    expect(main).not.toBeNull();
    expect(container.querySelectorAll("main")).toHaveLength(1);
    return main!;
  }

  it.each([
    {
      name: "access-requests",
      path: "/platform-admin/access-requests",
      render: () => AccessRequestsPage(),
      h1: "Access Requests",
      description: "Review and manage organization access requests.",
      link: "Access Requests",
      child: "access-requests-page",
    },
    {
      name: "billing",
      path: "/platform-admin/billing",
      render: () => BillingPage(),
      h1: "Billing Management",
      description: "View and manage tenant billing status, methods, and trial periods.",
      link: "Billing",
      child: "billing-tenants-page",
    },
    {
      name: "demo",
      path: "/platform-admin/demo",
      render: () => DemoProvisionPage(),
      h1: "Demo Provisioning",
      description: "Create a demo tenant with optional seed data for demonstrations and testing.",
      link: "Demo",
      child: "demo-tenants",
    },
  ])("$name: one PageHeader h1 inside main, max-w-7xl, its nav link current", async (c) => {
    const main = await renderInLayout(await c.render(), c.path);

    const h1 = onlyH1(main);
    expect(h1.textContent).toBe(c.h1);
    expect(h1).toHaveAttribute("data-slot", "page-header-title");
    expect(within(main).getByText(c.description)).toBeVisible();
    expect(within(main).getByTestId(c.child)).toBeInTheDocument();
    // K-99: one content width on every platform-admin page.
    expect(main.firstElementChild).toHaveClass("mx-auto", "max-w-7xl", "px-6", "py-10");
    // K-98: the current page's link only.
    const current = screen
      .getAllByRole("link")
      .filter((l) => l.getAttribute("aria-current") === "page");
    expect(current.map((l) => l.textContent)).toEqual([c.link]);
  });

  it("demo keeps its Demo Tenants h2 section under the one h1", async () => {
    const main = await renderInLayout(await DemoProvisionPage(), "/platform-admin/demo");

    expect(within(main).getByRole("heading", { level: 2, name: "Demo Tenants" })).toBeVisible();
    expect(within(main).getByTestId("demo-provision-form")).toBeInTheDocument();
  });
});
