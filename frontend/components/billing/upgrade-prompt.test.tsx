import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { UpgradePrompt } from "./upgrade-prompt";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
  }: {
    children: React.ReactNode;
    href: string;
  }) => <a href={href}>{children}</a>,
}));

afterEach(() => cleanup());

describe("UpgradePrompt", () => {
  it("renders upgrade title", () => {
    render(<UpgradePrompt slug="acme" />);
    expect(screen.getByText("Upgrade to Pro")).toBeInTheDocument();
  });

  it("renders description text", () => {
    render(<UpgradePrompt slug="acme" />);
    expect(
      screen.getByText(/dedicated infrastructure, higher member limits/)
    ).toBeInTheDocument();
  });

  it("links to billing page with correct slug", () => {
    render(<UpgradePrompt slug="acme" />);
    const link = screen.getByRole("link", { name: "View plans" });
    expect(link).toHaveAttribute("href", "/org/acme/settings/billing");
  });
});
