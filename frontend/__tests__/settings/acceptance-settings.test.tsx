import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AcceptanceSettingsForm } from "@/components/acceptance/AcceptanceSettingsForm";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/settings/acceptance/actions", () => ({
  updateAcceptanceSettings: vi.fn().mockResolvedValue({ success: true }),
}));

describe("AcceptanceSettingsForm", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders acceptance expiry input with current value", () => {
    render(
      <AcceptanceSettingsForm slug="acme" acceptanceExpiryDays={45} />,
    );
    const input = screen.getByRole("spinbutton");
    expect(input).toHaveValue(45);
    expect(
      screen.getByText(
        /Number of days before an acceptance request expires/,
      ),
    ).toBeInTheDocument();
  });

  it("saves updated expiry days", async () => {
    const { updateAcceptanceSettings } = await import(
      "@/app/(app)/org/[slug]/settings/acceptance/actions"
    );
    const user = userEvent.setup();
    render(
      <AcceptanceSettingsForm slug="acme" acceptanceExpiryDays={30} />,
    );
    await user.click(screen.getByRole("button", { name: /Save Settings/i }));
    expect(updateAcceptanceSettings).toHaveBeenCalledWith("acme", 30);
  });

  it("validates min/max range via HTML attributes", () => {
    render(
      <AcceptanceSettingsForm slug="acme" acceptanceExpiryDays={30} />,
    );
    const input = screen.getByRole("spinbutton");
    expect(input).toHaveAttribute("min", "1");
    expect(input).toHaveAttribute("max", "365");
  });
});
