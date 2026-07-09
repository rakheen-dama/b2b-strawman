import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { CollectionsSettingsForm } from "@/components/settings/collections-settings-form";
import { CollectionsExemptionToggle } from "@/components/customers/collections-exemption-toggle";

const mockUpdateCollectionsSettings = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/collections/actions", () => ({
  updateCollectionsSettings: (...args: unknown[]) => mockUpdateCollectionsSettings(...args),
}));

const mockSetCollectionsExemptionAction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  setCollectionsExemptionAction: (...args: unknown[]) => mockSetCollectionsExemptionAction(...args),
}));

afterEach(() => {
  cleanup();
  mockUpdateCollectionsSettings.mockReset();
  mockSetCollectionsExemptionAction.mockReset();
});

function renderForm(overrides: Partial<React.ComponentProps<typeof CollectionsSettingsForm>> = {}) {
  const defaultProps: React.ComponentProps<typeof CollectionsSettingsForm> = {
    slug: "acme",
    collectionsEnabled: false,
    stage1DaysOverdue: 7,
    stage2DaysOverdue: 21,
    stage3DaysOverdue: 45,
    escalateDaysOverdue: 60,
    ...overrides,
  };
  return render(<CollectionsSettingsForm {...defaultProps} />);
}

describe("CollectionsSettingsForm", () => {
  it("renders the enable switch and 4 threshold inputs with backend defaults", () => {
    renderForm();

    expect(screen.getByLabelText("Enable collections reminders")).toBeInTheDocument();

    expect(screen.getByLabelText("Stage 1 (days overdue)")).toHaveValue(7);
    expect(screen.getByLabelText("Stage 2 (days overdue)")).toHaveValue(21);
    expect(screen.getByLabelText("Stage 3 (days overdue)")).toHaveValue(45);
    expect(screen.getByLabelText("Escalation (days overdue)")).toHaveValue(60);
  });

  it("blocks submit when thresholds are not strictly increasing", async () => {
    renderForm();

    // stage2 below stage1 breaks the increasing-order rule.
    fireEvent.change(screen.getByLabelText("Stage 2 (days overdue)"), {
      target: { value: "5" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(screen.getByText(/stage 1 < stage 2 < stage 3 < escalation/i)).toBeInTheDocument();
    });
    expect(mockUpdateCollectionsSettings).not.toHaveBeenCalled();
  });

  it("blocks submit when a threshold is below 1", async () => {
    renderForm();

    fireEvent.change(screen.getByLabelText("Stage 1 (days overdue)"), {
      target: { value: "0" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(screen.getByText(/at least 1 day/i)).toBeInTheDocument();
    });
    expect(mockUpdateCollectionsSettings).not.toHaveBeenCalled();
  });

  it("submits the full 5-field DTO on a valid save", async () => {
    mockUpdateCollectionsSettings.mockResolvedValue({ success: true });
    renderForm();

    fireEvent.click(screen.getByLabelText("Enable collections reminders"));
    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockUpdateCollectionsSettings).toHaveBeenCalledWith("acme", {
        collectionsEnabled: true,
        stage1DaysOverdue: 7,
        stage2DaysOverdue: 21,
        stage3DaysOverdue: 45,
        escalateDaysOverdue: 60,
      });
    });
  });
});

describe("CollectionsExemptionToggle", () => {
  it("calls the exemption action when an admin toggles the switch", async () => {
    mockSetCollectionsExemptionAction.mockResolvedValue({ success: true });
    render(
      <CollectionsExemptionToggle slug="acme" customerId="cust-1" isAdmin={true} exempt={false} />
    );

    fireEvent.click(screen.getByLabelText("Exclude from collections"));

    await waitFor(() => {
      expect(mockSetCollectionsExemptionAction).toHaveBeenCalledWith("acme", "cust-1", true);
    });
  });

  it("disables the switch for non-admin members", () => {
    render(
      <CollectionsExemptionToggle slug="acme" customerId="cust-1" isAdmin={false} exempt={true} />
    );

    expect(screen.getByLabelText("Exclude from collections")).toBeDisabled();
    expect(mockSetCollectionsExemptionAction).not.toHaveBeenCalled();
  });
});
