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
import {
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
    id: "resource_planning",
    name: "Resource Planning",
    description: "Team allocation grid and capacity forecasting.",
    enabled: false,
  },
  {
    id: "bulk_billing",
    name: "Bulk Billing Runs",
    description: "Batch invoice generation across multiple customers.",
    enabled: true,
  },
  {
    id: "automation_builder",
    name: "Automation Rule Builder",
    description: "Create custom workflow automations with triggers.",
    enabled: false,
  },
];

describe("FeaturesSettingsForm", () => {
  it("renders all 3 feature cards with names and descriptions", () => {
    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={true} />,
    );

    expect(screen.getByText("Resource Planning")).toBeInTheDocument();
    expect(screen.getByText("Bulk Billing Runs")).toBeInTheDocument();
    expect(screen.getByText("Automation Rule Builder")).toBeInTheDocument();
    expect(
      screen.getByText("Team allocation grid and capacity forecasting."),
    ).toBeInTheDocument();
  });

  it("shows Switch controls when canManage is true", () => {
    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={true} />,
    );

    const switches = screen.getAllByRole("switch");
    expect(switches).toHaveLength(3);
  });

  it("shows read-only state without switches when canManage is false", () => {
    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={false} />,
    );

    expect(screen.queryAllByRole("switch")).toHaveLength(0);
    // Two disabled + one enabled labels
    expect(screen.getAllByText("Disabled")).toHaveLength(2);
    expect(screen.getByText("Enabled")).toBeInTheDocument();
  });

  it("calls updateModuleSettings with correct payload when toggling on", async () => {
    const user = userEvent.setup();
    vi.mocked(updateModuleSettings).mockResolvedValue({
      success: true,
      data: {
        modules: mockModules.map((m) =>
          m.id === "resource_planning" ? { ...m, enabled: true } : m,
        ),
      },
    });

    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={true} />,
    );

    const resourcePlanningSwitch = screen.getByLabelText(
      "Toggle Resource Planning",
    );
    await user.click(resourcePlanningSwitch);

    await waitFor(() => {
      expect(updateModuleSettings).toHaveBeenCalledWith(
        expect.arrayContaining(["resource_planning", "bulk_billing"]),
      );
    });
  });

  it("calls router.refresh after a successful toggle", async () => {
    const user = userEvent.setup();
    vi.mocked(updateModuleSettings).mockResolvedValue({
      success: true,
      data: {
        modules: mockModules.map((m) =>
          m.id === "resource_planning" ? { ...m, enabled: true } : m,
        ),
      },
    });

    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={true} />,
    );

    await user.click(screen.getByLabelText("Toggle Resource Planning"));

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

    render(
      <FeaturesSettingsForm initialModules={mockModules} canManage={true} />,
    );

    await user.click(screen.getByLabelText("Toggle Resource Planning"));

    await waitFor(() => {
      expect(
        screen.getByText("You do not have permission to manage features."),
      ).toBeInTheDocument();
    });
    expect(mockRefresh).not.toHaveBeenCalled();
  });
});
