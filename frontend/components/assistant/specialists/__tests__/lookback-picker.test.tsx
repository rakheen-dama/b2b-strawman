import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock the SpecialistLauncherButton to capture props
vi.mock("@/components/assistant/specialist-launcher-button", () => ({
  SpecialistLauncherButton: (props: Record<string, unknown>) => (
    <button data-testid="launcher-button" data-initial-prompt={props.initialPrompt}>
      {String(props.ctaLabel ?? "launch")}
    </button>
  ),
}));

import { LookbackPicker } from "@/components/assistant/specialists/lookback-picker";

afterEach(() => {
  cleanup();
});

describe("LookbackPicker", () => {
  const baseProps = {
    specialistId: "INBOX",
    surface: "MATTER_ACTIVITY_TAB",
    contextRef: { entityType: "project", entityId: "p-1" },
    ctaLabel: "Summarise recent activity",
  };

  it("defaults to 7 days and forwards lookback in initialPrompt", () => {
    render(<LookbackPicker {...baseProps} />);

    const select = screen.getByTestId("lookback-select") as HTMLSelectElement;
    expect(select.value).toBe("P7D");

    const launcher = screen.getByTestId("launcher-button");
    expect(launcher.getAttribute("data-initial-prompt")).toBe(
      "Summarise activity for the last 7 days (lookback=P7D)."
    );
  });

  it("updates initialPrompt when a different interval is selected", async () => {
    const user = userEvent.setup();
    render(<LookbackPicker {...baseProps} />);

    const select = screen.getByTestId("lookback-select");
    await user.selectOptions(select, "P30D");

    const launcher = screen.getByTestId("launcher-button");
    expect(launcher.getAttribute("data-initial-prompt")).toBe(
      "Summarise activity for the last 30 days (lookback=P30D)."
    );
  });

  it("forwards ctaLabel to the launcher button", () => {
    render(<LookbackPicker {...baseProps} />);

    expect(screen.getByText("Summarise recent activity")).toBeDefined();
  });
});
