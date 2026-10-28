import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { Suspense, startTransition, use, useEffect, useState, type ReactNode } from "react";
import { act, cleanup, render, screen, within } from "@testing-library/react";
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
import { FIRM_TAB_LOADERS } from "@/app/(app)/org/[slug]/settings/firm/_tabs";
import {
  renderSettingsFamilyPage,
  type SettingsSearchParams,
  type SettingsTabLoaders,
} from "@/components/settings/settings-family-page";
import { SettingsFamilyTabs } from "@/components/settings/settings-family-tabs";
import { getSettingsFamily } from "@/lib/settings-nav";

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

function props(searchParams: SettingsSearchParams) {
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

  // Browser-found (1024px: document 1250px wide): the w-max strip must scroll inside its own box.
  // 616A worked around the app shell's missing `min-w-0` with `@container`; 628B put `min-w-0` on
  // the shell column and <main>, so the plain overflow box is enough. jsdom has no layout, so this
  // pins the scroll box (the scrollWidth A/B in the browser is the authority).
  it("keeps the tab strip in its own scroll box", async () => {
    await renderFirm();
    const box = screen.getByRole("tablist", { name: "Firm settings" }).parentElement!;
    expect(box).toHaveClass("overflow-x-auto");
    expect(box).not.toHaveClass("@container");
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

  it("does not fetch org settings — Firm declares no module-gated tab", async () => {
    await renderFirm("compliance");
    expect(screen.getByText("compliance panel")).toBeInTheDocument();
    expect(mockGetOrgSettings).not.toHaveBeenCalled();
  });

  it("declares exactly one loader per Firm tab in settings-nav", () => {
    const tabIds = getSettingsFamily("firm").tabs.map((t) => t.id);
    expect(Object.keys(FIRM_TAB_LOADERS).sort()).toEqual([...tabIds].sort());
    for (const id of tabIds) {
      expect(FIRM_TAB_LOADERS[id as keyof typeof FIRM_TAB_LOADERS]).toBe(
        loaders[id as keyof typeof loaders]
      );
    }
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

  // The shared shell 616B–616D reuse, exercised with a module-gated family (Fees & Trust).
  describe("renderSettingsFamilyPage with module-gated tabs", () => {
    const feeLoaders: SettingsTabLoaders<"fees"> = {
      tariffs: async () => "tariffs panel",
      tax: async () => "tax panel",
      "time-recording": async () => "time recording panel",
      "fee-runs": async () => "fee runs panel",
      collections: async () => "collections panel",
      "trust-accounting": async () => "trust accounting panel",
    };

    async function renderFees(tab?: string) {
      const page = await renderSettingsFamilyPage(
        "fees",
        feeLoaders,
        props(tab === undefined ? {} : { tab })
      );
      render(page);
    }

    function feeTabNames() {
      const list = screen.getByRole("tablist", { name: "Fees & Trust settings" });
      return within(list)
        .getAllByRole("tab")
        .map((t) => t.textContent);
    }

    it("fetches org settings and shows only the tabs whose module is enabled", async () => {
      mockGetOrgSettings.mockResolvedValue({
        defaultCurrency: "ZAR",
        enabledModules: ["trust_accounting"],
      });
      await renderFees("trust-accounting");
      expect(mockGetOrgSettings).toHaveBeenCalledTimes(1);
      expect(feeTabNames()).toEqual([
        "Rates",
        "Tax",
        "Time recording",
        "Collections",
        "Trust accounting",
      ]);
      expect(screen.getByText("trust accounting panel")).toBeInTheDocument();
    });

    it("treats modules as disabled when GET /api/settings fails, and still renders", async () => {
      mockGetOrgSettings.mockRejectedValue(new Error("network"));
      await renderFees("trust-accounting");
      expect(feeTabNames()).toEqual(["Rates", "Tax", "Time recording", "Collections"]);
      expect(screen.getByText("tariffs panel")).toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent(
        /don.t have access to Trust accounting — showing Rates instead/
      );
    });
  });
});

// 616E.2 (§12.7.4): Radix Tabs roles + focus on user-initiated tab change only. Pinned once here —
// SettingsFamilyTabs is the one strip all five family pages render.
describe("616E.2 a11y — SettingsFamilyTabs (shared by all five families)", () => {
  const TABS = [
    { id: "general", label: "General" },
    { id: "roles", label: "Roles" },
    { id: "audit-trail", label: "Audit trail" },
  ];

  const PANELS: Record<string, ReactNode> = {
    general: <button type="button">Save general</button>,
    roles: (
      <>
        <h1>Roles</h1>
        <input aria-label="Role name" />
      </>
    ),
    "audit-trail": <input aria-label="Audit filter" />,
  };

  // Models Next's App Router (app-router-instance `dispatchAction` + `useActionQueue`): the active
  // tab lives in router state; router.replace puts a pending promise there inside a nested
  // startTransition and the page reads it with `use()`, so the strip's useTransition stays pending
  // until the navigation settles — `settle(tab)` commits that tab (or the old one, for an aborted
  // or superseded navigation). Back/forward is a synchronous router-state update: `urlChange`.
  let setRouterState: ((state: string | Promise<string>) => void) | null = null;
  let navigations: Array<(tab: string) => void> = [];

  function Page({ initialTab, panels }: { initialTab: string; panels: Record<string, ReactNode> }) {
    const [state, setState] = useState<string | Promise<string>>(initialTab);
    useEffect(() => {
      setRouterState = setState;
    }, []);
    const tab = typeof state === "string" ? state : use(state);
    return (
      <SettingsFamilyTabs label="Firm settings" tabs={TABS} activeTab={tab}>
        {panels[tab]}
      </SettingsFamilyTabs>
    );
  }

  function renderStrip(initialTab: string, panels: Record<string, ReactNode> = PANELS) {
    render(
      <Suspense fallback={null}>
        <Page initialTab={initialTab} panels={panels} />
      </Suspense>
    );
  }

  /** Settles the oldest pending navigation by committing `tab`. */
  async function settle(tab: string) {
    const resolve = navigations.shift();
    await act(async () => resolve?.(tab));
  }

  function urlChange(tab: string) {
    act(() => setRouterState?.(tab));
  }

  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
    mockReplace.mockImplementation(() => {
      const navigation = new Promise<string>((resolve) => navigations.push(resolve));
      startTransition(() => setRouterState?.(navigation));
    });
  });

  afterEach(() => {
    cleanup();
    mockReplace.mockReset();
    vi.clearAllMocks();
    setRouterState = null;
    navigations = [];
  });

  it("names the tablist, marks aria-selected and labels the tabpanel by the active tab", async () => {
    await renderFirm("roles");
    expect(screen.getByRole("tablist", { name: "Firm settings" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "General" })).toHaveAttribute("aria-selected", "false");
    expect(screen.getByRole("tabpanel", { name: "Roles" })).toHaveTextContent("roles panel");
  });

  it("does not move focus into the panel on load", () => {
    renderStrip("general");
    expect(screen.getByRole("button", { name: "Save general" })).not.toHaveFocus();
    expect(document.body).toHaveFocus();
  });

  it("focuses the new panel's first control once a clicked tab commits", async () => {
    const user = userEvent.setup();
    renderStrip("general");
    await user.click(screen.getByRole("tab", { name: "Roles" }));
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/firm?tab=roles", {
      scroll: false,
    });
    await settle("roles");
    expect(screen.getByRole("textbox", { name: "Role name" })).toHaveFocus();
  });

  it("focuses the new panel's first control after a keyboard selection (arrow + Enter)", async () => {
    const user = userEvent.setup();
    renderStrip("general");
    await user.click(screen.getByRole("tab", { name: "General" }));
    await user.keyboard("{ArrowRight}");
    // Awaited act: a transition that suspends inside user-event's synchronous keydown act() scope
    // is otherwise dropped by React's act queue ("suspended inside an `act` scope…not awaited").
    await act(async () => {
      await user.keyboard("{Enter}");
    });
    await settle("roles");
    expect(screen.getByRole("textbox", { name: "Role name" })).toHaveFocus();
  });

  it("focuses the panel itself when it has no focusable control", async () => {
    const user = userEvent.setup();
    renderStrip("general", { ...PANELS, roles: <p>No roles yet</p> });
    await user.click(screen.getByRole("tab", { name: "Roles" }));
    await settle("roles");
    expect(screen.getByRole("tabpanel", { name: "Roles" })).toHaveFocus();
  });

  it("does not move focus on a URL-driven change (back/forward, rail link, ?tab= link)", () => {
    renderStrip("general");
    urlChange("roles");
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("textbox", { name: "Role name" })).not.toHaveFocus();
    expect(document.body).toHaveFocus();
  });

  it("on a fast second selection, focuses only when the second tab commits", async () => {
    const user = userEvent.setup();
    renderStrip("general");
    await user.click(screen.getByRole("tab", { name: "Roles" }));
    await user.click(screen.getByRole("tab", { name: "Audit trail" }));
    await settle("roles");
    expect(screen.getByRole("tabpanel", { name: "General" })).toBeInTheDocument();
    await settle("audit-trail");
    expect(screen.getByRole("textbox", { name: "Audit filter" })).toHaveFocus();
  });

  it("drops a selection whose navigation settles without committing it", async () => {
    const user = userEvent.setup();
    renderStrip("general");
    await user.click(screen.getByRole("tab", { name: "Roles" }));
    // Aborted/superseded: the navigation settles with General still the active tab.
    await settle("general");
    expect(screen.getByRole("tab", { name: "General" })).toHaveAttribute("aria-selected", "true");
    // A later URL-driven change to Roles (back/forward, rail, ?tab= link) must not move focus.
    urlChange("roles");
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("textbox", { name: "Role name" })).not.toHaveFocus();
  });
});
