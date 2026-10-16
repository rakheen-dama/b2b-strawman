import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}));

const mockUpdateCadence = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/general/portal-actions", () => ({
  updatePortalDigestCadence: (...args: unknown[]) => mockUpdateCadence(...args),
}));

import { PortalSettingsSection } from "@/components/settings/portal-settings-section";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("PortalSettingsSection — cadence select", () => {
  it("renders current cadence value", () => {
    render(<PortalSettingsSection slug="acme" currentCadence="WEEKLY" />);

    // The cadence select renders as a combobox (Radix).
    const comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes).toHaveLength(1);

    // Current cadence label visible on trigger.
    expect(screen.getByText("Weekly")).toBeInTheDocument();
  });

  it("persists firm-side cadence change via PATCH action", async () => {
    mockUpdateCadence.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<PortalSettingsSection slug="acme" currentCadence="WEEKLY" />);

    const [cadenceTrigger] = screen.getAllByRole("combobox");
    await user.click(cadenceTrigger);
    await user.click(screen.getByRole("option", { name: /Bi-weekly/i }));

    await waitFor(() => {
      expect(mockUpdateCadence).toHaveBeenCalledTimes(1);
    });
    expect(mockUpdateCadence).toHaveBeenCalledWith("acme", "BIWEEKLY");
  });

  it("shows error when cadence update fails", async () => {
    mockUpdateCadence.mockResolvedValue({
      success: false,
      error: "Only admins can update",
    });
    const user = userEvent.setup();

    render(<PortalSettingsSection slug="acme" currentCadence="WEEKLY" />);

    const [cadenceTrigger] = screen.getAllByRole("combobox");
    await user.click(cadenceTrigger);
    await user.click(screen.getByRole("option", { name: /Off/i }));

    await screen.findByText("Only admins can update");
  });
});
