import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { BalanceCard } from "@/components/trust/balance-card";

describe("BalanceCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders balance in ZAR, as-of date, and matter label", () => {
    render(
      <BalanceCard
        matterId="ab12cd34-ef56-7890-abcd-ef0123456789"
        currentBalance={1250}
        lastTransactionAt="2026-04-15T08:30:00Z"
      />,
    );

    // Currency — Intl en-ZA renders "R 1,250.00" (non-breaking space between R and digits)
    const balance = screen.getByLabelText("Current balance");
    expect(balance.textContent).toMatch(/1[ ,\u00A0]?250\.00/);
    expect(balance.textContent).toMatch(/R/);

    // As-of date formatted as "15 Apr 2026"
    expect(screen.getByText(/15 Apr 2026/)).toBeInTheDocument();

    // Matter label — short-id fallback uses first 8 hex chars after stripping hyphens
    expect(screen.getByText("Matter ab12cd34")).toBeInTheDocument();
  });
});
