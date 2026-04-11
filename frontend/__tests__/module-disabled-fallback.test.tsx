import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

afterEach(() => cleanup());

describe("ModuleDisabledFallback", () => {
  it("renders the module name in the heading", () => {
    render(<ModuleDisabledFallback moduleName="Resource Planning" slug="acme" />);
    expect(
      screen.getByRole("heading", { name: /resource planning is not enabled/i })
    ).toBeInTheDocument();
  });

  it("links to /org/{slug}/settings/features", () => {
    render(<ModuleDisabledFallback moduleName="Bulk Billing Runs" slug="acme" />);
    const link = screen.getByRole("link", { name: /go to features/i });
    expect(link).toHaveAttribute("href", "/org/acme/settings/features");
  });

  it("shows the explanation message", () => {
    render(<ModuleDisabledFallback moduleName="Automation Rule Builder" slug="my-org" />);
    expect(
      screen.getByText(/this feature is not enabled for your organization/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/settings → features/i)).toBeInTheDocument();
  });
});
