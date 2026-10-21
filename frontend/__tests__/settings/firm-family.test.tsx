import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

// Phase 87 S9 (616A, ADR-341): the Firm settings family page. Tab loaders are mocked — each
// returns a marker string — so the test pins tab resolution and "only the active tab loads".
const { loaders, mockCaps, mockGetOrgSettings, mockReplace } = vi.hoisted(() => ({
  loaders: {
    general: vi.fn(async () => "general panel"),
    roles: vi.fn(async () => "roles panel"),
    "audit-trail": vi.fn(async () => "audit panel"),
    "data-protection": vi.fn(async () => "data protection panel"),
    "data-requests": vi.fn(async () => "data requests panel"),
    compliance: vi.fn(async () => "compliance panel"),
    features: vi.fn(async () => "features panel"),
    subscription: vi.fn(async () => "subscription panel"),
  },
  mockCaps: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockReplace: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: () => mockGetOrgSettings() }));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/general", () => ({
  renderGeneralTab: loaders.general,
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/roles", () => ({
  renderRolesTab: loaders.roles,
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/audit-trail", () => ({
  renderAuditTrailTab: loaders["audit-trail"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection", () => ({
  renderDataProtectionTab: loaders["data-protection"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/data-requests", () => ({
  renderDataRequestsTab: loaders["data-requests"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance", () => ({
  renderComplianceTab: loaders.compliance,
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/features", () => ({
  renderFeaturesTab: loaders.features,
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/subscription", () => ({
  renderSubscriptionTab: loaders.subscription,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/firm",
  useSearchParams: () => new URLSearchParams(),
}));

import FirmSettingsPage, { generateMetadata } from "@/app/(app)/org/[slug]/settings/firm/page";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

const ALL_FIRM_TABS = [
  "General",
  "Roles",
  "Audit trail",
  "Data protection",
  "Data requests",
  "Compliance",
  "Features",
  "Subscription",
];

function props(searchParams: Record<string, string | string[]>) {
  return {
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  };
}

async function renderFirm(tab?: string | string[]) {
  const searchParams = tab === undefined ? {} : { tab };
  render(await FirmSettingsPage(props(searchParams)));
}

function tabNames() {
  const list = screen.getByRole("tablist", { name: "Firm settings" });
  return within(list)
    .getAllByRole("tab")
    .map((t) => t.textContent);
}

function calledLoaders() {
  return Object.entries(loaders)
    .filter(([, fn]) => fn.mock.calls.length > 0)
    .map(([id]) => id);
}

describe("Firm settings family page", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all eight tabs in §12.7.1 order for an owner and loads only General by default", async () => {
    await renderFirm();
    expect(tabNames()).toEqual(ALL_FIRM_TABS);
    expect(screen.getByRole("tab", { name: "General" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("general panel")).toBeInTheDocument();
    expect(calledLoaders()).toEqual(["general"]);
    expect(loaders.general).toHaveBeenCalledTimes(1);
  });

  it("gives an admin the same eight tabs", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    await renderFirm();
    expect(tabNames()).toEqual(ALL_FIRM_TABS);
  });

  it("renders the requested tab's panel and passes the loader slug and query", async () => {
    await renderFirm("roles");
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("roles panel")).toBeInTheDocument();
    expect(loaders.roles).toHaveBeenCalledWith({ slug: "acme", searchParams: { tab: "roles" } });
    expect(calledLoaders()).toEqual(["roles"]);
  });

  it("falls back to General for an unknown tab silently — never a 404", async () => {
    await renderFirm("nope");
    expect(screen.getByText("general panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(calledLoaders()).toEqual(["general"]);
  });

  it("shows a member only the permitted tabs — admin-only tabs are absent, not disabled", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderFirm();
    expect(tabNames()).toEqual(["General", "Subscription"]);
    expect(screen.queryByRole("tab", { name: "Roles" })).not.toBeInTheDocument();
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).not.toHaveAttribute("data-disabled");
      expect(tab).not.toBeDisabled();
    }
  });

  it("shows the Audit trail tab to a member holding TEAM_OVERSIGHT (ruling D5)", async () => {
    mockCaps.mockResolvedValue({ ...MEMBER, capabilities: ["TEAM_OVERSIGHT"] });
    await renderFirm("audit-trail");
    expect(tabNames()).toEqual(["General", "Audit trail", "Subscription"]);
    expect(screen.getByText("audit panel")).toBeInTheDocument();
  });

  it("falls back to General with a notice when a member asks for a forbidden tab", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderFirm("roles");
    expect(screen.getByText("general panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /don.t have access to Roles — showing General instead/
    );
    expect(loaders.roles).not.toHaveBeenCalled();
    expect(calledLoaders()).toEqual(["general"]);
  });

  it("takes the first value of a repeated ?tab= and hands the loader the full query", async () => {
    const searchParams = { tab: ["audit-trail", "x"], severities: "CRITICAL" };
    render(await FirmSettingsPage(props(searchParams)));
    expect(screen.getByText("audit panel")).toBeInTheDocument();
    expect(loaders["audit-trail"]).toHaveBeenCalledWith({ slug: "acme", searchParams });
  });

  it("navigates to ?tab=<id> with router.replace (scroll: false) on tab change", async () => {
    const user = userEvent.setup();
    await renderFirm();
    await user.click(screen.getByRole("tab", { name: "Features" }));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/firm?tab=features", {
      scroll: false,
    });
  });

  it("moves focus with the arrow keys without navigating; Enter selects (manual activation)", async () => {
    const user = userEvent.setup();
    await renderFirm();
    await user.click(screen.getByRole("tab", { name: "General" }));
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveFocus();
    expect(mockReplace).not.toHaveBeenCalled();
    await user.keyboard("{Enter}");
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/firm?tab=roles", {
      scroll: false,
    });
  });

  it("does not navigate when the active tab is clicked again", async () => {
    const user = userEvent.setup();
    await renderFirm("subscription");
    await user.click(screen.getByRole("tab", { name: "Subscription" }));
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("still resolves tabs when GET /api/settings fails (module gates only)", async () => {
    mockGetOrgSettings.mockRejectedValue(new Error("network"));
    await renderFirm("compliance");
    expect(screen.getByText("compliance panel")).toBeInTheDocument();
  });

  describe("generateMetadata", () => {
    it("titles the page Firm — <Tab> · Settings", async () => {
      expect(await generateMetadata(props({ tab: "data-requests" }))).toEqual({
        title: "Firm — Data requests · Settings",
      });
    });

    it("uses the resolved tab for a member's forbidden tab and for no tab", async () => {
      mockCaps.mockResolvedValue(MEMBER);
      expect(await generateMetadata(props({ tab: "roles" }))).toEqual({
        title: "Firm — General · Settings",
      });
      expect(await generateMetadata(props({}))).toEqual({ title: "Firm — General · Settings" });
    });
  });
});
