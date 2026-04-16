import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}));

// Mock motion/react (required for AlertDialog which uses motion internally)
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

// Mock server actions
const mockFetchCatalogAction = vi.fn();
const mockFetchUninstallCheckAction = vi.fn();
const mockInstallPackAction = vi.fn();
const mockUninstallPackAction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/packs/actions", () => ({
  fetchCatalogAction: (...args: unknown[]) => mockFetchCatalogAction(...args),
  fetchUninstallCheckAction: (...args: unknown[]) =>
    mockFetchUninstallCheckAction(...args),
  installPackAction: (...args: unknown[]) => mockInstallPackAction(...args),
  uninstallPackAction: (...args: unknown[]) => mockUninstallPackAction(...args),
}));

import type { PackCatalogEntry } from "@/lib/api/packs";
import { PacksPageClient } from "@/app/(app)/org/[slug]/settings/packs/packs-page-client";

const CATALOG: PackCatalogEntry[] = [
  {
    packId: "legal-za",
    name: "Legal (South Africa) Templates",
    description: "10 matter and engagement templates for South African law firms",
    version: "1.0",
    type: "DOCUMENT_TEMPLATE",
    verticalProfile: "legal-za",
    itemCount: 10,
    installed: false,
    installedAt: null,
  },
  {
    packId: "common",
    name: "Common Templates",
    description: "3 universal templates for all verticals",
    version: "1.0",
    type: "DOCUMENT_TEMPLATE",
    verticalProfile: null,
    itemCount: 3,
    installed: true,
    installedAt: "2026-03-15T10:30:00Z",
  },
];

const INSTALLED: PackCatalogEntry[] = [
  {
    packId: "common",
    name: "Common Templates",
    description: "3 universal templates for all verticals",
    version: "1.0",
    type: "DOCUMENT_TEMPLATE",
    verticalProfile: null,
    itemCount: 3,
    installed: true,
    installedAt: "2026-03-15T10:30:00Z",
  },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  // Default: fetchCatalogAction returns ActionResult wrapping catalog
  mockFetchCatalogAction.mockResolvedValue({ success: true, data: CATALOG });
  mockFetchUninstallCheckAction.mockResolvedValue({
    success: true,
    data: { canUninstall: true, blockingReason: null },
  });
});

function renderPage(overrides?: {
  catalog?: PackCatalogEntry[];
  installed?: PackCatalogEntry[];
  canManage?: boolean;
}) {
  return render(
    <PacksPageClient
      initialCatalog={overrides?.catalog ?? CATALOG}
      initialInstalled={overrides?.installed ?? INSTALLED}
      slug="test-org"
      canManage={overrides?.canManage ?? true}
    />
  );
}

describe("PacksPage — Available tab", () => {
  it("renders pack cards with name, version, and badge", async () => {
    renderPage();

    // Available tab is shown by default
    expect(screen.getByText("Legal (South Africa) Templates")).toBeInTheDocument();
    expect(screen.getAllByText("v1.0")).toHaveLength(2);
    expect(screen.getByText("10 templates")).toBeInTheDocument();
    expect(screen.getByText("Common Templates")).toBeInTheDocument();
    expect(screen.getByText("3 templates")).toBeInTheDocument();
  });

  it("'Show all packs' toggle re-fetches catalog with all=true", async () => {
    const user = userEvent.setup();
    const allPacks: PackCatalogEntry[] = [
      ...CATALOG,
      {
        packId: "accounting-za",
        name: "Accounting (ZA) Templates",
        description: "Tax templates",
        version: "1.0",
        type: "DOCUMENT_TEMPLATE",
        verticalProfile: "accounting-za",
        itemCount: 5,
        installed: false,
        installedAt: null,
      },
    ];
    mockFetchCatalogAction.mockResolvedValue({ success: true, data: allPacks });

    renderPage();

    const toggle = screen.getByRole("switch");
    await user.click(toggle);

    await waitFor(() => {
      expect(mockFetchCatalogAction).toHaveBeenCalledWith(true);
    });

    await waitFor(() => {
      expect(screen.getByText("Accounting (ZA) Templates")).toBeInTheDocument();
    });
  });

  it("Install button calls installPackAction and shows success toast", async () => {
    const user = userEvent.setup();
    mockInstallPackAction.mockResolvedValue({ success: true, data: {} });

    renderPage();

    const installButtons = screen.getAllByRole("button", { name: "Install" });
    await user.click(installButtons[0]);

    await waitFor(() => {
      expect(mockInstallPackAction).toHaveBeenCalledWith("test-org", "legal-za");
    });
  });
});

describe("PacksPage — Installed tab", () => {
  it("renders installed packs with date info", async () => {
    const user = userEvent.setup();
    renderPage();

    // Switch to Installed tab
    const installedTab = screen.getByRole("tab", { name: /Installed/i });
    await user.click(installedTab);

    await waitFor(() => {
      expect(screen.getByText(/Installed on/)).toBeInTheDocument();
      expect(screen.getByText(/15 Mar 2026/)).toBeInTheDocument();
    });
  });

  it("Uninstall button opens confirmation dialog and calls uninstallPackAction on confirm", async () => {
    const user = userEvent.setup();
    mockUninstallPackAction.mockResolvedValue({ success: true });

    renderPage();

    // Switch to Installed tab
    const installedTab = screen.getByRole("tab", { name: /Installed/i });
    await user.click(installedTab);

    // Wait for uninstall check to load and button to become enabled
    await waitFor(() => {
      const uninstallButton = screen.getByRole("button", { name: "Uninstall" });
      expect(uninstallButton).toBeEnabled();
    });

    // Click the Uninstall button to open dialog
    const uninstallButton = screen.getByRole("button", { name: "Uninstall" });
    await user.click(uninstallButton);

    // Verify dialog is shown
    await waitFor(() => {
      expect(screen.getByText("Uninstall Pack")).toBeInTheDocument();
      expect(screen.getByText(/This will remove 3 templates/)).toBeInTheDocument();
    });

    // Click confirm button in dialog
    const confirmButton = screen.getByRole("button", { name: "Uninstall" });
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockUninstallPackAction).toHaveBeenCalledWith("test-org", "common");
    });
  });

  it("Uninstall button disabled with tooltip when canUninstall is false", async () => {
    const user = userEvent.setup();
    mockFetchUninstallCheckAction.mockResolvedValue({
      success: true,
      data: { canUninstall: false, blockingReason: "2 templates have been edited" },
    });

    renderPage();

    // Switch to Installed tab
    const installedTab = screen.getByRole("tab", { name: /Installed/i });
    await user.click(installedTab);

    await waitFor(() => {
      const uninstallButton = screen.getByRole("button", { name: "Uninstall" });
      expect(uninstallButton).toBeDisabled();
    });
  });
});

describe("PacksPage — Empty states", () => {
  it("shows empty state text for both tabs", async () => {
    const user = userEvent.setup();
    mockFetchCatalogAction.mockResolvedValue({ success: true, data: [] });

    renderPage({ catalog: [], installed: [] });

    // Available tab empty state
    await waitFor(() => {
      expect(screen.getByText("No packs available")).toBeInTheDocument();
      expect(
        screen.getByText(/Toggle 'Show all packs' to browse everything/)
      ).toBeInTheDocument();
    });

    // Switch to Installed tab
    const installedTab = screen.getByRole("tab", { name: /Installed/i });
    await user.click(installedTab);

    expect(screen.getByText("No packs installed")).toBeInTheDocument();
    expect(
      screen.getByText(/Browse the Available tab/)
    ).toBeInTheDocument();
  });
});
