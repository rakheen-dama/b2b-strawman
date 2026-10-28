import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MatterRetentionSettingsForm } from "@/components/compliance/MatterRetentionSettingsForm";
import { matterRetentionYearsSchema } from "@/lib/schemas";

// Phase 88 U17 (ADR-354): Firm › Compliance "Matter retention period (years)", 1–30 whole years,
// saved through the owner-only data-protection settings PATCH.

const mockUpdateMatterRetentionYears = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions", () => ({
  updateMatterRetentionYears: (...args: unknown[]) => mockUpdateMatterRetentionYears(...args),
}));

const LABEL = "Matter retention period (years)";

afterEach(() => {
  cleanup();
  mockUpdateMatterRetentionYears.mockReset();
});

function renderForm(
  overrides: Partial<React.ComponentProps<typeof MatterRetentionSettingsForm>> = {}
) {
  const props: React.ComponentProps<typeof MatterRetentionSettingsForm> = {
    slug: "acme",
    legalMatterRetentionYears: 5,
    canEdit: true,
    ...overrides,
  };
  return render(<MatterRetentionSettingsForm {...props} />);
}

function save(value: string) {
  fireEvent.change(screen.getByLabelText(LABEL), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "Save retention period" }));
}

describe("MatterRetentionSettingsForm", () => {
  it("shows the loaded value (5) under the Matter retention heading", () => {
    renderForm();
    expect(screen.getByRole("heading", { name: "Matter retention" })).toBeInTheDocument();
    expect(screen.getByLabelText(LABEL)).toHaveValue(5);
    expect(screen.getByLabelText(LABEL)).toBeEnabled();
    expect(
      screen.getByText(/matters already closed keep their scheduled deletion date/)
    ).toBeInTheDocument();
  });

  it("saves 7 through the action and confirms", async () => {
    mockUpdateMatterRetentionYears.mockResolvedValue({ success: true });
    renderForm();

    save("7");

    await waitFor(() => {
      expect(mockUpdateMatterRetentionYears).toHaveBeenCalledWith("acme", 7);
    });
    expect(await screen.findByText("Matter retention period updated.")).toBeInTheDocument();
  });

  it.each([
    ["1", 1],
    ["30", 30],
  ])("accepts the bound %s", async (value, years) => {
    mockUpdateMatterRetentionYears.mockResolvedValue({ success: true });
    renderForm();

    save(value);

    await waitFor(() => {
      expect(mockUpdateMatterRetentionYears).toHaveBeenCalledWith("acme", years);
    });
    expect(screen.getByLabelText(LABEL)).not.toHaveAttribute("aria-invalid");
  });

  it.each([
    ["0", "Retention period must be between 1 and 30 years."],
    ["31", "Retention period must be between 1 and 30 years."],
    ["2.5", "Retention period must be a whole number of years."],
    ["", "Enter the number of years."],
  ])("rejects %j with an inline error and no action call", async (value, error) => {
    renderForm();

    save(value);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(error);
    const input = screen.getByLabelText(LABEL);
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", alert.id);
    expect(input).toHaveAccessibleDescription(error);
    expect(mockUpdateMatterRetentionYears).not.toHaveBeenCalled();
  });

  it("clears the field error once a valid value is saved", async () => {
    mockUpdateMatterRetentionYears.mockResolvedValue({ success: true });
    renderForm();

    save("31");
    await screen.findByRole("alert");

    save("7");
    await waitFor(() => expect(mockUpdateMatterRetentionYears).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByLabelText(LABEL)).not.toHaveAttribute("aria-invalid");
  });

  it("renders the action's error", async () => {
    mockUpdateMatterRetentionYears.mockResolvedValue({
      success: false,
      error: "Only the firm owner can change the matter retention period.",
    });
    renderForm();

    save("7");

    expect(
      await screen.findByText("Only the firm owner can change the matter retention period.")
    ).toBeInTheDocument();
    expect(screen.queryByText("Matter retention period updated.")).not.toBeInTheDocument();
  });

  it("non-owner: field and button disabled, with the owner-only note", () => {
    renderForm({ canEdit: false, legalMatterRetentionYears: 7 });

    expect(screen.getByLabelText(LABEL)).toHaveValue(7);
    expect(screen.getByLabelText(LABEL)).toBeDisabled();
    expect(screen.getByRole("button", { name: "Save retention period" })).toBeDisabled();
    expect(
      screen.getByText("Only the firm owner can change the matter retention period.")
    ).toBeInTheDocument();
  });

  it("owner: no owner-only note", () => {
    renderForm();
    expect(
      screen.queryByText("Only the firm owner can change the matter retention period.")
    ).not.toBeInTheDocument();
  });
});

describe("matterRetentionYearsSchema", () => {
  it.each([1, 5, 7, 30])("accepts %d", (years) => {
    expect(matterRetentionYearsSchema.safeParse(years).success).toBe(true);
  });

  it.each([0, 31, -1, 2.5, Number.NaN])("rejects %d", (years) => {
    expect(matterRetentionYearsSchema.safeParse(years).success).toBe(false);
  });
});
