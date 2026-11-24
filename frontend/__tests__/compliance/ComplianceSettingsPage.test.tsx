import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComplianceSettingsForm } from "@/components/compliance/ComplianceSettingsForm";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions", () => ({
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
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
    const inputs = screen.getAllByRole("spinbutton");
    expect(inputs).toHaveLength(5);
    expect(inputs[0]).toHaveValue(365);
    expect(inputs[1]).toHaveValue(30);
  });

  it("renders the three FICA review intervals, labelled by risk as the firm's RMCP choice (655B)", () => {
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
    expect(screen.getByRole("group", { name: "FICA review intervals" })).toBeInTheDocument();
    expect(screen.getByLabelText("Low risk (months)")).toHaveValue(36);
    expect(screen.getByLabelText("Medium risk (months)")).toHaveValue(24);
    expect(screen.getByLabelText("High risk (months)")).toHaveValue(12);
    for (const key of ["low", "medium", "high"]) {
      const input = screen.getByTestId(`fica-review-months-${key}`);
      expect(input).toHaveAttribute("min", "1");
      expect(input).toHaveAttribute("max", "120");
    }
    expect(
      screen.getByText(/Risk Management and Compliance Programme \(RMCP\)/)
    ).toBeInTheDocument();
  });

  it("clamps a review interval to 1–120 months", () => {
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
    const high = screen.getByLabelText("High risk (months)");
    fireEvent.change(high, { target: { value: "500" } });
    expect(high).toHaveValue(120);
    fireEvent.change(high, { target: { value: "0" } });
    expect(high).toHaveValue(1);
  });

  it("renders save button", () => {
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
    expect(screen.getByRole("button", { name: /Save Settings/i })).toBeInTheDocument();
  });

  it("calls updateComplianceSettings when save is clicked", async () => {
    const { updateComplianceSettings } =
      await import("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions");
    const user = userEvent.setup();
    render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
    await user.click(screen.getByRole("button", { name: /Save Settings/i }));
    expect(updateComplianceSettings).toHaveBeenCalledWith("acme", {
      dormancyThresholdDays: 365,
      dataRequestDeadlineDays: 30,
      ficaReviewMonthsLow: 36,
      ficaReviewMonthsMedium: 24,
      ficaReviewMonthsHigh: 12,
    });
  });
});
