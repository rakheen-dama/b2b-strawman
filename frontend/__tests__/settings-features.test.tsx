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

vi.mock("@/lib/actions/module-settings", () => ({
  getModuleSettings: vi.fn(),
  updateModuleSettings: vi.fn(),
}));

// --- Imports after mocks ---

import { FeaturesSettingsForm } from "@/components/settings/features-settings-form";
import { updateModuleSettings, type ModuleStatus } from "@/lib/actions/module-settings";

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
});
