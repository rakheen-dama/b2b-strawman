import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

// Phase 87 S9 (616C, ADR-341): the Documents settings family page. Tab loaders are mocked — each
// returns a marker string — so the test pins tab resolution and "only the active tab loads".
const { loaders, mockCaps, mockGetOrgSettings, mockReplace } = vi.hoisted(() => ({
  loaders: {
    templates: vi.fn(async () => "templates panel"),
    clauses: vi.fn(async () => "clauses panel"),
    acceptance: vi.fn(async () => "acceptance panel"),
    email: vi.fn(async () => "email panel"),
  },
  mockCaps: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockReplace: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: () => mockGetOrgSettings() }));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/templates", () => ({
  renderTemplatesTab: loaders.templates,
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/clauses", () => ({
  renderClausesTab: loaders.clauses,
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/acceptance", () => ({
  renderAcceptanceTab: loaders.acceptance,
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/email", () => ({
  renderEmailTab: loaders.email,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/documents",
  useSearchParams: () => new URLSearchParams(),
}));

import DocumentsSettingsPage, {
  generateMetadata,
} from "@/app/(app)/org/[slug]/settings/documents/page";
import { DOCUMENTS_TAB_LOADERS } from "@/app/(app)/org/[slug]/settings/documents/_tabs";
import type { SettingsSearchParams } from "@/components/settings/settings-family-page";
import { getSettingsFamily } from "@/lib/settings-nav";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

const ALL_DOCUMENTS_TABS = ["Templates", "Clauses", "Acceptance", "Email"];

function props(searchParams: SettingsSearchParams) {
  return {
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  };
}

async function renderDocuments(tab?: string | string[]) {
  const searchParams = tab === undefined ? {} : { tab };
  render(await DocumentsSettingsPage(props(searchParams)));
}

function tabNames() {
  const list = screen.getByRole("tablist", { name: "Documents settings" });
  return within(list)
    .getAllByRole("tab")
    .map((t) => t.textContent);
}

function calledLoaders() {
  return Object.entries(loaders)
    .filter(([, fn]) => fn.mock.calls.length > 0)
    .map(([id]) => id);
}

describe("Documents settings family page", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules: [] });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all four tabs in §12.7.1 order for an owner and loads only Templates by default", async () => {
    await renderDocuments();
    expect(tabNames()).toEqual(ALL_DOCUMENTS_TABS);
    expect(screen.getByRole("tab", { name: "Templates" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  it("gives an admin the same four tabs", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    await renderDocuments();
    expect(tabNames()).toEqual(ALL_DOCUMENTS_TABS);
  });

  it("renders the requested tab's panel and passes the loader slug and query", async () => {
    await renderDocuments("clauses");
    expect(screen.getByRole("tab", { name: "Clauses" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("clauses panel")).toBeInTheDocument();
    expect(loaders.clauses).toHaveBeenCalledWith({
      slug: "acme",
      searchParams: { tab: "clauses" },
    });
    expect(calledLoaders()).toEqual(["clauses"]);
  });

  it("falls back to Templates for an unknown tab silently — never a 404", async () => {
    await renderDocuments("nope");
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  // D19 (D6/D18 rule): Acceptance's former page denied non-admins → adminOnly; Email was already
  // adminOnly. Templates and Clauses stay visible (their former pages rendered read-only).
  it("shows a member only the permitted tabs — admin-only tabs are absent, not disabled", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderDocuments();
    expect(tabNames()).toEqual(["Templates", "Clauses"]);
    expect(screen.queryByRole("tab", { name: "Email" })).not.toBeInTheDocument();
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).not.toHaveAttribute("data-disabled");
      expect(tab).not.toBeDisabled();
    }
  });

  it("falls back to Templates with a notice when a member asks for a forbidden tab", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderDocuments("email");
    expect(screen.getByText("templates panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /don.t have access to Email — showing Templates instead/
    );
    expect(loaders.email).not.toHaveBeenCalled();
    expect(calledLoaders()).toEqual(["templates"]);
  });

  it("lets a member open the Clauses tab", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderDocuments("clauses");
    expect(screen.getByText("clauses panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("navigates to ?tab=<id> with router.replace (scroll: false) on tab change", async () => {
    const user = userEvent.setup();
    await renderDocuments();
    await user.click(screen.getByRole("tab", { name: "Acceptance" }));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/documents?tab=acceptance", {
      scroll: false,
    });
  });

  it("does not fetch org settings — Documents declares no module-gated tab", async () => {
    await renderDocuments("clauses");
    expect(mockGetOrgSettings).not.toHaveBeenCalled();
  });

  it("declares exactly one loader per Documents tab in settings-nav", () => {
    const tabIds = getSettingsFamily("documents").tabs.map((t) => t.id);
    expect(Object.keys(DOCUMENTS_TAB_LOADERS).sort()).toEqual([...tabIds].sort());
    for (const id of tabIds) {
      expect(DOCUMENTS_TAB_LOADERS[id as keyof typeof DOCUMENTS_TAB_LOADERS]).toBe(
        loaders[id as keyof typeof loaders]
      );
    }
  });

  describe("generateMetadata", () => {
    it("titles the page Documents — <Tab> · Settings", async () => {
      expect(await generateMetadata(props({ tab: "acceptance" }))).toEqual({
        title: "Documents — Acceptance · Settings",
      });
      expect(await generateMetadata(props({}))).toEqual({
        title: "Documents — Templates · Settings",
      });
    });

    it("uses the resolved tab for a member's forbidden tab", async () => {
      mockCaps.mockResolvedValue(MEMBER);
      expect(await generateMetadata(props({ tab: "acceptance" }))).toEqual({
        title: "Documents — Templates · Settings",
      });
    });
  });
});
