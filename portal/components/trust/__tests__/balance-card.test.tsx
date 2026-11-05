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

  it("renders balance in ZAR, as-of date, and the matter name", () => {
    render(
      <BalanceCard
        matterName="Estate Late J Mokoena"
        currentBalance={1250}
        lastTransactionAt="2026-04-15T08:30:00Z"
      />,
    );

    // Currency — Intl en-ZA varies by ICU version: "R 1,250.00" on some
    // runtimes, "R 1 250,00" on others (comma decimal, thin/non-breaking
    // space as thousands separator). Accept both.
    const balance = screen.getByLabelText("Current balance");
    expect(balance.textContent).toMatch(/1[ ,\u00A0\u202F]?250[.,]00/);
    expect(balance.textContent).toMatch(/R/);

    // As-of date formatted as "15 Apr 2026"
    expect(screen.getByText(/15 Apr 2026/)).toBeInTheDocument();

    // Matter name from the trust summary (G88-F31) — never a short-id "Matter ab12cd34".
    expect(screen.getByText("Estate Late J Mokoena")).toBeInTheDocument();
    expect(screen.queryByText(/^Matter [0-9a-f]{8}$/)).toBeNull();
  });

  it("falls back to 'Matter' when the summary has no matter name", () => {
    render(
      <BalanceCard
        matterName={null}
        currentBalance={0}
        lastTransactionAt="2026-04-15T08:30:00Z"
      />,
    );
    expect(screen.getByText("Matter")).toBeInTheDocument();
  });
});
