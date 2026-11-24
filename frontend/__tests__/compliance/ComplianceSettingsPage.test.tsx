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

  function renderForm() {
    return render(
      <ComplianceSettingsForm
        slug="acme"
        dormancyThresholdDays={365}
        dataRequestDeadlineDays={30}
        ficaReviewMonthsLow={36}
        ficaReviewMonthsMedium={24}
        ficaReviewMonthsHigh={12}
      />
    );
  }

  it("a review interval can be cleared and retyped (no snapping while typing)", async () => {
    const user = userEvent.setup();
    renderForm();
    const high = screen.getByLabelText("High risk (months)");
    await user.clear(high);
    expect(high).toHaveValue(null);
    await user.type(high, "6");
    expect(high).toHaveValue(6);
    await user.tab();
    expect(screen.queryByTestId("fica-review-months-high-error")).not.toBeInTheDocument();
  });

  it("a cleared interval shows the inline message on blur", async () => {
    const user = userEvent.setup();
    renderForm();
    const low = screen.getByLabelText("Low risk (months)");
    await user.clear(low);
    await user.tab();
    expect(screen.getByTestId("fica-review-months-low-error")).toHaveTextContent(
      "Enter a whole number of months from 1 to 120."
    );
    expect(low).toHaveAttribute("aria-invalid", "true");
  });

  it.each(["0", "121"])("refuses %s months: inline message, nothing saved", async (value) => {
    const { updateComplianceSettings } =
      await import("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions");
    vi.mocked(updateComplianceSettings).mockClear();
    const user = userEvent.setup();
    renderForm();
    fireEvent.change(screen.getByLabelText("Medium risk (months)"), { target: { value } });
    await user.click(screen.getByRole("button", { name: /Save Settings/i }));
    expect(updateComplianceSettings).not.toHaveBeenCalled();
    expect(screen.getByTestId("fica-review-months-medium-error")).toHaveTextContent(
      "Enter a whole number of months from 1 to 120."
    );
  });

  it("the boundaries 1 and 120 are valid and saved as numbers", async () => {
    const { updateComplianceSettings } =
      await import("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions");
    vi.mocked(updateComplianceSettings).mockClear();
    const user = userEvent.setup();
    renderForm();
    fireEvent.change(screen.getByLabelText("Low risk (months)"), { target: { value: "120" } });
    fireEvent.change(screen.getByLabelText("High risk (months)"), { target: { value: "1" } });
    await user.click(screen.getByRole("button", { name: /Save Settings/i }));
    expect(updateComplianceSettings).toHaveBeenCalledWith("acme", {
      dormancyThresholdDays: 365,
      dataRequestDeadlineDays: 30,
      ficaReviewMonthsLow: 120,
      ficaReviewMonthsMedium: 24,
      ficaReviewMonthsHigh: 1,
    });
    expect(screen.queryByText("Enter a whole number of months from 1 to 120.")).toBeNull();
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
