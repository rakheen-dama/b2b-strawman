import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComplianceSettingsForm } from "@/components/compliance/ComplianceSettingsForm";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/settings/compliance/actions", () => ({
  updateComplianceSettings: vi.fn().mockResolvedValue({ success: true }),
}));

describe("ComplianceSettingsForm", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders dormancy and deadline inputs with default values", () => {
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
      />,
    );
    const inputs = screen.getAllByRole("spinbutton");
    expect(inputs[0]).toHaveValue(365);
    expect(inputs[1]).toHaveValue(30);
  });

  it("renders save button", () => {
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
      />,
    );
    expect(screen.getByRole("button", { name: /Save Settings/i })).toBeInTheDocument();
  });

  it("calls updateComplianceSettings when save is clicked", async () => {
    const { updateComplianceSettings } = await import(
      "@/app/(app)/org/[slug]/settings/compliance/actions"
    );
    const user = userEvent.setup();
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
      />,
    );
    await user.click(screen.getByRole("button", { name: /Save Settings/i }));
    expect(updateComplianceSettings).toHaveBeenCalledWith("acme", {
      dormancyThresholdDays: 365,
      dataRequestDeadlineDays: 30,
    });
  });
});
