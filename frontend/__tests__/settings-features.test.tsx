import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (before component imports) ---

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

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: mockRefresh }),
  usePathname: () => "/org/acme/settings/features",
}));

vi.mock("server-only", () => ({}));
const { mockCaps } = vi.hoisted(() => ({ mockCaps: vi.fn() }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));

vi.mock("@/lib/actions/module-settings", () => ({
  getModuleSettings: vi.fn(),
  updateModuleSettings: vi.fn(),
}));

// --- Imports after mocks ---

import { FeaturesSettingsForm } from "@/components/settings/features-settings-form";
import { renderFeaturesTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/features";
import {
  getModuleSettings,
  updateModuleSettings,
  type ModuleStatus,
} from "@/lib/actions/module-settings";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
  mockRefresh.mockReset();
});

const mockModules: ModuleStatus[] = [
  {
    id: "information_requests",
    name: "Information Requests",
    description: "Customer document-request workflows with portal pickup and acceptance",
    enabled: false,
  },
  {
    id: "bulk_billing",
    name: "Bulk Billing Runs",
    description: "Batch invoice generation across multiple customers.",
    enabled: true,
  },
];

describe("FeaturesSettingsForm", () => {
  it("renders both feature cards with names and descriptions", () => {
    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    expect(screen.getByText("Information Requests")).toBeInTheDocument();
    expect(screen.getByText("Bulk Billing Runs")).toBeInTheDocument();
    expect(
      screen.getByText("Customer document-request workflows with portal pickup and acceptance")
    ).toBeInTheDocument();
  });

  it("shows Switch controls when canManage is true", () => {
    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    const switches = screen.getAllByRole("switch");
    expect(switches).toHaveLength(2);
  });

  it("shows read-only state without switches when canManage is false", () => {
    render(<FeaturesSettingsForm initialModules={mockModules} canManage={false} />);

    expect(screen.queryAllByRole("switch")).toHaveLength(0);
    // One disabled + one enabled label
    expect(screen.getAllByText("Disabled")).toHaveLength(1);
    expect(screen.getByText("Enabled")).toBeInTheDocument();
  });

  it("calls updateModuleSettings with correct payload when toggling on", async () => {
    const user = userEvent.setup();
    vi.mocked(updateModuleSettings).mockResolvedValue({
      success: true,
      data: {
        modules: mockModules.map((m) =>
          m.id === "information_requests" ? { ...m, enabled: true } : m
        ),
      },
    });

    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    const informationRequestsSwitch = screen.getByLabelText("Toggle Information Requests");
    await user.click(informationRequestsSwitch);

    await waitFor(() => {
      expect(updateModuleSettings).toHaveBeenCalledTimes(1);
    });
    // Verify exact payload — only the toggled module joins the existing enabled list.
    const callArg = vi.mocked(updateModuleSettings).mock.calls[0][0];
    const sorted = [...callArg].sort();
    expect(sorted).toEqual(["bulk_billing", "information_requests"]);
  });

  it("calls router.refresh after a successful toggle", async () => {
    const user = userEvent.setup();
    vi.mocked(updateModuleSettings).mockResolvedValue({
      success: true,
      data: {
        modules: mockModules.map((m) =>
          m.id === "information_requests" ? { ...m, enabled: true } : m
        ),
      },
    });

    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    await user.click(screen.getByLabelText("Toggle Information Requests"));

    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  it("shows error message and reverts on failure", async () => {
    const user = userEvent.setup();
    vi.mocked(updateModuleSettings).mockResolvedValue({
      success: false,
      error: "You do not have permission to manage features.",
    });

    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    await user.click(screen.getByLabelText("Toggle Information Requests"));

    await waitFor(() => {
      expect(
        screen.getByText("You do not have permission to manage features.")
      ).toBeInTheDocument();
    });
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("665A: each module is an untitled settings section with no footer (instant save)", () => {
    render(<FeaturesSettingsForm initialModules={mockModules} canManage={true} />);

    for (const mod of mockModules) {
      const card = screen.getByTestId(`feature-card-${mod.id}`);
      expect(card).toHaveAttribute("data-slot", "settings-section");
      expect(card.querySelector('[data-slot="card-footer"]')).toBeNull();
      expect(card.querySelector('[data-slot="card-header"]')).toBeNull();
      expect(card).toContainElement(screen.getByRole("switch", { name: `Toggle ${mod.name}` }));
    }
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });
});

describe("renderFeaturesTab (665A PageHeader)", () => {
  beforeEach(() => {
    vi.mocked(getModuleSettings).mockResolvedValue({ modules: mockModules } as Awaited<
      ReturnType<typeof getModuleSettings>
    >);
  });

  it("admin: one h1 'Features' with its description, no read-only note", async () => {
    mockCaps.mockResolvedValue({ capabilities: [], role: "admin", isAdmin: true, isOwner: false });

    render(await renderFeaturesTab());

    const h1s = screen.getAllByRole("heading", { level: 1 });
    expect(h1s).toHaveLength(1);
    expect(h1s[0]).toHaveTextContent("Features");
    expect(h1s[0].closest('[data-slot="page-header"]')).not.toBeNull();
    expect(
      screen.getByText(
        "Enable additional features for your organization. These can be turned on or off at any time."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
    expect(
      screen.queryByText("Only admins and owners can change these settings.")
    ).not.toBeInTheDocument();
  });

  it("member: keeps the read-only note under the header", async () => {
    mockCaps.mockResolvedValue({
      capabilities: [],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });

    render(await renderFeaturesTab());

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByText("Only admins and owners can change these settings.")).toBeVisible();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });
});
