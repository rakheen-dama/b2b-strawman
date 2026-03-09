import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HelpTip } from "@/components/help-tip";

describe("HelpTip integration — new help points #12-#22", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows templates.variables help text", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="templates.variables" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    expect(screen.getByText("Template variables")).toBeInTheDocument();
    expect(
      screen.getByText(/Use \{\{variable\}\} syntax/),
    ).toBeInTheDocument();
  });

  it("shows fields.types help text", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="fields.types" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    expect(screen.getByText("Field types")).toBeInTheDocument();
    expect(
      screen.getByText(/Choose from text, number, date/),
    ).toBeInTheDocument();
  });

  it("shows tags.overview help text", async () => {
    const user = userEvent.setup();
    render(<HelpTip code="tags.overview" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    expect(screen.getByText("Tags")).toBeInTheDocument();
    expect(
      screen.getByText(/colour-coded labels/),
    ).toBeInTheDocument();
  });
});
