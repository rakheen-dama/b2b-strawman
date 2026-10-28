import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

// Phase 87 S9 (616D, ADR-341): the Connections settings family page — six tabs from seven former
// pages (integrations + integrations/xero → xero). No tab is module-gated, so the shared seam must
// not fetch org settings. Tab loaders are mocked marker strings.
const { loaders, mockCaps, mockGetOrgSettings, mockReplace } = vi.hoisted(() => ({
  loaders: {
    xero: vi.fn(async () => "xero panel"),
    mcp: vi.fn(async () => "mcp panel"),
    ai: vi.fn(async () => "ai panel"),
    notifications: vi.fn(async () => "notifications panel"),
    packs: vi.fn(async () => "packs panel"),
    automations: vi.fn(async () => "automations panel"),
  },
  mockCaps: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockReplace: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: () => mockGetOrgSettings() }));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/xero", () => ({
  renderXeroTab: loaders.xero,
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/mcp", () => ({
  renderMcpTab: loaders.mcp,
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/ai", () => ({
  renderAiTab: loaders.ai,
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/notifications", () => ({
  renderNotificationsTab: loaders.notifications,
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/packs", () => ({
  renderPacksTab: loaders.packs,
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/automations", () => ({
  renderAutomationsTab: loaders.automations,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/connections",
  useSearchParams: () => new URLSearchParams(),
}));

import ConnectionsSettingsPage, {
  generateMetadata,
} from "@/app/(app)/org/[slug]/settings/connections/page";
import { CONNECTIONS_TAB_LOADERS } from "@/app/(app)/org/[slug]/settings/connections/_tabs";
import type { SettingsSearchParams } from "@/components/settings/settings-family-page";
import { getSettingsFamily } from "@/lib/settings-nav";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const INTEGRATION_MANAGER = {
  capabilities: ["INTEGRATION_MANAGE"],
  role: "member",
  isAdmin: false,
  isOwner: false,
};

const ALL_CONNECTIONS_TABS = ["Integrations", "MCP", "AI", "Notifications", "Packs", "Automations"];

function props(searchParams: SettingsSearchParams) {
  return {
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  };
}

async function renderConnections(tab?: string | string[]) {
  const searchParams = tab === undefined ? {} : { tab };
  render(await ConnectionsSettingsPage(props(searchParams)));
}

function tabNames() {
  const list = screen.getByRole("tablist", { name: "Connections settings" });
  return within(list)
    .getAllByRole("tab")
    .map((t) => t.textContent);
}

function calledLoaders() {
  return Object.entries(loaders)
    .filter(([, fn]) => fn.mock.calls.length > 0)
    .map(([id]) => id);
}

describe("Connections settings family page", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all six tabs in §12.7.1 order for an owner and loads only Integrations", async () => {
    await renderConnections();
    expect(tabNames()).toEqual(ALL_CONNECTIONS_TABS);
    expect(screen.getByRole("tab", { name: "Integrations" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(screen.getByText("xero panel")).toBeInTheDocument();
    expect(calledLoaders()).toEqual(["xero"]);
  });

  it("renders all six tabs for an admin", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    await renderConnections();
    expect(tabNames()).toEqual(ALL_CONNECTIONS_TABS);
  });

  it("does not fetch org settings — Connections declares no module-gated tab", async () => {
    await renderConnections();
    expect(mockGetOrgSettings).not.toHaveBeenCalled();
  });

  it("renders the requested tab's panel and passes the loader slug and query", async () => {
    await renderConnections("packs");
    expect(screen.getByRole("tab", { name: "Packs" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("packs panel")).toBeInTheDocument();
    expect(loaders.packs).toHaveBeenCalledWith({ slug: "acme", searchParams: { tab: "packs" } });
    expect(calledLoaders()).toEqual(["packs"]);
  });

  it("falls back to Integrations for an unknown tab silently — never a 404", async () => {
    await renderConnections("integrations"); // the old page name is not a tab id
    expect(screen.getByText("xero panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  // Frozen old-nav flags (D24 ruling): ai, packs and automations stay adminOnly; mcp is gated by
  // INTEGRATION_MANAGE (D7); xero (first tab, D20) and notifications are ungated.
  it("shows a plain member only Integrations and Notifications — gated tabs are absent, not disabled", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderConnections();
    expect(tabNames()).toEqual(["Integrations", "Notifications"]);
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).not.toHaveAttribute("data-disabled");
      expect(tab).not.toBeDisabled();
    }
  });

  it("adds MCP for a member holding INTEGRATION_MANAGE (D7)", async () => {
    mockCaps.mockResolvedValue(INTEGRATION_MANAGER);
    await renderConnections();
    expect(tabNames()).toEqual(["Integrations", "MCP", "Notifications"]);
  });

  it.each([
    ["automations", "Automations"],
    ["ai", "AI"],
    ["packs", "Packs"],
    ["mcp", "MCP"],
  ] as const)(
    "falls back to Integrations with a notice when a member asks for %s",
    async (tabId, label) => {
      mockCaps.mockResolvedValue(MEMBER);
      await renderConnections(tabId);
      expect(screen.getByText("xero panel")).toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent(
        new RegExp(`don.t have access to ${label} — showing Integrations instead`)
      );
      expect(loaders[tabId]).not.toHaveBeenCalled();
    }
  );

  it("navigates to ?tab=<id> with router.replace (scroll: false) on tab change", async () => {
    const user = userEvent.setup();
    await renderConnections();
    await user.click(screen.getByRole("tab", { name: "Notifications" }));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/connections?tab=notifications", {
      scroll: false,
    });
  });

  // 616E.2 (§12.7.4): Radix Tabs roles; the focus behaviour is pinned once in firm-family.
  it("names the tablist, marks aria-selected and labels the tabpanel by the active tab (616E.2)", async () => {
    await renderConnections("notifications");
    expect(screen.getByRole("tablist", { name: "Connections settings" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Notifications" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(screen.getByRole("tab", { name: "Integrations" })).toHaveAttribute(
      "aria-selected",
      "false"
    );
    expect(screen.getByRole("tabpanel", { name: "Notifications" })).toHaveTextContent(
      "notifications panel"
    );
  });

  it("declares exactly one loader per Connections tab in settings-nav", () => {
    const tabIds = getSettingsFamily("connections").tabs.map((t) => t.id);
    expect(Object.keys(CONNECTIONS_TAB_LOADERS).sort()).toEqual([...tabIds].sort());
    for (const id of tabIds) {
      expect(CONNECTIONS_TAB_LOADERS[id as keyof typeof CONNECTIONS_TAB_LOADERS]).toBe(
        loaders[id as keyof typeof loaders]
      );
    }
  });

  describe("generateMetadata", () => {
    it("titles the page Connections — <Tab> · Settings", async () => {
      expect(await generateMetadata(props({ tab: "mcp" }))).toEqual({
        title: "Connections — MCP · Settings",
      });
      expect(await generateMetadata(props({}))).toEqual({
        title: "Connections — Integrations · Settings",
      });
    });

    it("uses the resolved tab for a member's forbidden tab", async () => {
      mockCaps.mockResolvedValue(MEMBER);
      expect(await generateMetadata(props({ tab: "packs" }))).toEqual({
        title: "Connections — Integrations · Settings",
      });
    });
  });
});
