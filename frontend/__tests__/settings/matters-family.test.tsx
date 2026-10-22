import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

// Phase 87 S9 (616B, ADR-341): the Matters settings family page. Tab loaders are mocked — each
// returns a marker string — so the test pins tab resolution and "only the active tab loads".
const { loaders, mockCaps, mockGetOrgSettings, mockReplace } = vi.hoisted(() => ({
  loaders: {
    templates: vi.fn(async () => "templates panel"),
    naming: vi.fn(async () => "naming panel"),
    "custom-fields": vi.fn(async () => "custom fields panel"),
    tags: vi.fn(async () => "tags panel"),
    checklists: vi.fn(async () => "checklists panel"),
    "request-templates": vi.fn(async () => "request templates panel"),
    "request-settings": vi.fn(async () => "request settings panel"),
  },
  mockCaps: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockReplace: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: () => mockGetOrgSettings() }));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/templates", () => ({
  renderTemplatesTab: loaders.templates,
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/naming", () => ({
  renderNamingTab: loaders.naming,
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields", () => ({
  renderCustomFieldsTab: loaders["custom-fields"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/tags", () => ({
  renderTagsTab: loaders.tags,
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/checklists", () => ({
  renderChecklistsTab: loaders.checklists,
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/request-templates", () => ({
  renderRequestTemplatesTab: loaders["request-templates"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/request-settings", () => ({
  renderRequestSettingsTab: loaders["request-settings"],
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/matters",
  useSearchParams: () => new URLSearchParams(),
}));

import MattersSettingsPage, {
  generateMetadata,
} from "@/app/(app)/org/[slug]/settings/matters/page";
import { MATTERS_TAB_LOADERS } from "@/app/(app)/org/[slug]/settings/matters/_tabs";
import type { SettingsSearchParams } from "@/components/settings/settings-family-page";
import { getSettingsFamily } from "@/lib/settings-nav";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

const ALL_MATTERS_TABS = [
  "Templates",
  "Naming",
  "Custom fields",
  "Tags",
  "Checklists",
  "Request templates",
  "Request settings",
];

function props(searchParams: SettingsSearchParams) {
  return {
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  };
}

async function renderMatters(tab?: string | string[]) {
  const searchParams = tab === undefined ? {} : { tab };
  render(await MattersSettingsPage(props(searchParams)));
}

function tabNames() {
  const list = screen.getByRole("tablist", { name: "Matters settings" });
  return within(list)
    .getAllByRole("tab")
    .map((t) => t.textContent);
}

function calledLoaders() {
  return Object.entries(loaders)
    .filter(([, fn]) => fn.mock.calls.length > 0)
    .map(([id]) => id);
}

describe("Matters settings family page", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all seven tabs in §12.7.1 order for an owner and loads only Templates by default", async () => {
    await renderMatters();
    expect(tabNames()).toEqual(ALL_MATTERS_TABS);
    expect(screen.getByRole("tab", { name: "Templates" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  it("gives an admin the same seven tabs", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    await renderMatters();
    expect(tabNames()).toEqual(ALL_MATTERS_TABS);
  });

  it("renders the requested tab's panel and passes the loader slug and query", async () => {
    await renderMatters("checklists");
    expect(screen.getByRole("tab", { name: "Checklists" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(screen.getByText("checklists panel")).toBeInTheDocument();
    expect(loaders.checklists).toHaveBeenCalledWith({
      slug: "acme",
      searchParams: { tab: "checklists" },
    });
    expect(calledLoaders()).toEqual(["checklists"]);
  });

  it("falls back to Templates for an unknown tab silently — never a 404", async () => {
    await renderMatters("take-on"); // Phase 84's future tab is not declared yet
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  // Option A (ruling D18 — D6 rule): the five former pages that denied non-admins are admin-only
  // tabs; Templates and Tags stay visible (their former pages rendered read-only for members).
  it("shows a member only the permitted tabs — admin-only tabs are absent, not disabled", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderMatters();
    expect(tabNames()).toEqual(["Templates", "Tags"]);
    expect(screen.queryByRole("tab", { name: "Custom fields" })).not.toBeInTheDocument();
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).not.toHaveAttribute("data-disabled");
      expect(tab).not.toBeDisabled();
    }
  });

  it("falls back to Templates with a notice when a member asks for a forbidden tab", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderMatters("custom-fields");
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /don.t have access to Custom fields — showing Templates instead/
    );
    expect(loaders["custom-fields"]).not.toHaveBeenCalled();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  it("lets a member open the Tags tab", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderMatters("tags");
    expect(screen.getByText("tags panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("navigates to ?tab=<id> with router.replace (scroll: false) on tab change", async () => {
    const user = userEvent.setup();
    await renderMatters();
    await user.click(screen.getByRole("tab", { name: "Request settings" }));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/matters?tab=request-settings", {
      scroll: false,
    });
  });

  it("does not fetch org settings — Matters declares no module-gated tab", async () => {
    await renderMatters("tags");
    expect(mockGetOrgSettings).not.toHaveBeenCalled();
  });

  it("declares exactly one loader per Matters tab in settings-nav", () => {
    const tabIds = getSettingsFamily("matters").tabs.map((t) => t.id);
    expect(Object.keys(MATTERS_TAB_LOADERS).sort()).toEqual([...tabIds].sort());
    for (const id of tabIds) {
      expect(MATTERS_TAB_LOADERS[id as keyof typeof MATTERS_TAB_LOADERS]).toBe(
        loaders[id as keyof typeof loaders]
      );
    }
  });

  describe("generateMetadata", () => {
    it("titles the page Matters — <Tab> · Settings", async () => {
      expect(await generateMetadata(props({ tab: "custom-fields" }))).toEqual({
        title: "Matters — Custom fields · Settings",
      });
      expect(await generateMetadata(props({}))).toEqual({
        title: "Matters — Templates · Settings",
      });
    });

    it("uses the resolved tab for a member's forbidden tab", async () => {
      mockCaps.mockResolvedValue(MEMBER);
      expect(await generateMetadata(props({ tab: "naming" }))).toEqual({
        title: "Matters — Templates · Settings",
      });
    });
  });
});
