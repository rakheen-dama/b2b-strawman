import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import SettingsError from "@/app/(app)/org/[slug]/settings/error";

describe("SettingsError boundary", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the failure message", () => {
    render(<SettingsError error={new Error("boom")} reset={vi.fn()} />);

    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(screen.getByText("Unable to load settings. Please try again.")).toBeInTheDocument();
  });

  it("invokes reset when Try again is clicked", async () => {
    const reset = vi.fn();
    const user = userEvent.setup();

    render(<SettingsError error={new Error("boom")} reset={reset} />);

    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(reset).toHaveBeenCalledTimes(1);
  });
});
