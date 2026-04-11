import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HelpTip } from "@/components/help-tip";

describe("HelpTip", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders CircleHelp icon", () => {
    render(<HelpTip code="rates.hierarchy" />);

    const button = screen.getByRole("button", { name: /help/i });
    expect(button).toBeInTheDocument();

    const svg = button.querySelector("svg");
    expect(svg).not.toBeNull();
  });

  it("opens popover on click", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="rates.hierarchy" />);

    const button = screen.getByRole("button", { name: /help/i });
    await user.click(button);

    expect(screen.getByText("Rate card hierarchy")).toBeInTheDocument();
  });

  it("displays title and body from message catalog", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="rates.hierarchy" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    expect(screen.getByText("Rate card hierarchy")).toBeInTheDocument();
    expect(screen.getByText(/Rates follow a three-level hierarchy/)).toBeInTheDocument();
  });

  it("closes on click outside", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <HelpTip code="rates.hierarchy" />
        <span data-testid="outside">Outside</span>
      </div>
    );

    // Open popover
    await user.click(screen.getByRole("button", { name: /help/i }));
    expect(screen.getByText("Rate card hierarchy")).toBeInTheDocument();

    // Click outside
    await user.click(screen.getByTestId("outside"));

    // Popover should close (content removed from DOM)
    expect(screen.queryByText("Rate card hierarchy")).not.toBeInTheDocument();
  });

  it("handles missing code gracefully (shows fallback)", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="nonexistent.code" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    // Missing keys return the code itself as fallback
    expect(screen.getByText("nonexistent.code.title")).toBeInTheDocument();
    expect(screen.getByText("nonexistent.code.body")).toBeInTheDocument();
  });
});
