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

const INVESTED_LABEL = "Invested on your instruction (section 86(4))";
const GENERAL_LABEL = "In the firm's trust account";

describe("BalanceCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders balance in ZAR, as-of date, and the matter name", () => {
    render(
      <BalanceCard
        matterName="Estate Late J Mokoena"
        currentBalance={1250}
        investedBalance={0}
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
        investedBalance={0}
        lastTransactionAt="2026-04-15T08:30:00Z"
      />,
    );
    expect(screen.getByText("Matter")).toBeInTheDocument();
  });

  it("is titled 'Held in trust' and shows no invested line when nothing is invested (648D)", () => {
    render(
      <BalanceCard
        matterName="Website Redesign"
        currentBalance={2500}
        investedBalance={0}
        lastTransactionAt="2026-11-08T08:30:00Z"
      />,
    );

    expect(screen.getByText("Held in trust")).toBeInTheDocument();
    expect(screen.queryByText("Trust balance")).toBeNull();
    expect(screen.getByLabelText("Current balance").textContent).toMatch(
      /2[ ,\u00A0\u202F]?500[.,]00/,
    );
    expect(screen.queryByText(INVESTED_LABEL)).toBeNull();
    expect(screen.queryByText(GENERAL_LABEL)).toBeNull();
  });

  it("shows general + invested as the headline, and both parts, when money is invested (648D OQ-2)", () => {
    render(
      <BalanceCard
        matterName="Website Redesign"
        currentBalance={2500}
        investedBalance={7595}
        lastTransactionAt="2026-11-08T08:30:00Z"
      />,
    );

    expect(screen.getByText("Held in trust")).toBeInTheDocument();
    // The client's total held in trust — unchanged by a placement (ADR-379)
    expect(screen.getByLabelText("Current balance").textContent).toMatch(
      /10[ ,\u00A0\u202F]?095[.,]00/,
    );
    const general = screen.getByText(GENERAL_LABEL);
    expect(general.parentElement?.textContent).toMatch(
      /2[ ,\u00A0\u202F]?500[.,]00/,
    );
    const invested = screen.getByText(INVESTED_LABEL);
    expect(invested.parentElement?.textContent).toMatch(
      /7[ ,\u00A0\u202F]?595[.,]00/,
    );
  });

  it("shows the general balance and no invested line when a response has no investedBalance (PR #1802 review)", () => {
    // A pre-648D backend omits the field: the page passes it through as undefined
    render(
      <BalanceCard
        matterName="Website Redesign"
        currentBalance={2500}
        investedBalance={undefined}
        lastTransactionAt="2026-11-08T08:30:00Z"
      />,
    );

    const headline = screen.getByLabelText("Current balance").textContent;
    expect(headline).toMatch(/2[ ,\u00A0\u202F]?500[.,]00/);
    expect(headline).not.toMatch(/NaN/);
    expect(screen.queryByText(INVESTED_LABEL)).toBeNull();
  });
});
