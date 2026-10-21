import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { UnbilledWipWidget } from "@/components/dashboard/unbilled-wip-widget";
import type { UnbilledWipResponse } from "@/lib/dashboard-types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const FULL: UnbilledWipResponse = {
  asOf: "2026-10-21",
  currency: "ZAR",
  totalMinutes: 900,
  totalValue: 27500,
  unbilledDisbursementTotal: 1250.5,
  byFeeEarner: [
    {
      memberId: "m1",
      memberName: "Thandi Nkosi",
      minutes: 600,
      value: 20000,
      oldestEntryDate: "2026-09-02",
    },
    {
      memberId: "m2",
      memberName: "Pieter van Wyk",
      minutes: 300,
      value: 7500,
      oldestEntryDate: "2026-10-01",
    },
  ],
  redacted: false,
};

/** The fee earner's redacted view: own row, minutes only, every value null. */
const REDACTED: UnbilledWipResponse = {
  asOf: "2026-10-21",
  currency: "ZAR",
  totalMinutes: 300,
  totalValue: null,
  unbilledDisbursementTotal: null,
  byFeeEarner: [
    {
      memberId: "m2",
      memberName: "Pieter van Wyk",
      minutes: 300,
      value: null,
      oldestEntryDate: "2026-10-01",
    },
  ],
  redacted: true,
};

describe("UnbilledWipWidget", () => {
  afterEach(() => cleanup());

  it("full view: value headline, per-fee-earner hours and values, disbursement line, unbilled link", () => {
    render(<UnbilledWipWidget slug="acme" data={FULL} personal={false} showLink />);

    const card = screen.getByTestId("unbilled-wip-widget");
    expect(within(card).getByText("Unbilled WIP")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-wip-headline").textContent).toMatch(/27\s?500/);
    expect(within(card).getByText("Thandi Nkosi")).toBeInTheDocument();
    expect(within(card).getByText("Pieter van Wyk")).toBeInTheDocument();
    expect(within(card).getByText("15h of unbilled time")).toBeInTheDocument();
    expect(within(card).getByText("10h")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-disbursements").textContent).toContain(
      "Disbursements not yet billed"
    );
    expect(screen.getByRole("link", { name: "Open unbilled work" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/unbilled"
    );
  });

  it("hides the disbursement line when unbilledDisbursementTotal is null (module off)", () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{ ...FULL, unbilledDisbursementTotal: null }}
        personal={false}
        showLink
      />
    );
    expect(screen.queryByTestId("unbilled-disbursements")).not.toBeInTheDocument();
    expect(screen.queryByText("Disbursements not yet billed")).not.toBeInTheDocument();
  });

  it("no unbilled link without INVOICING", () => {
    render(<UnbilledWipWidget slug="acme" data={FULL} personal={false} showLink={false} />);
    expect(screen.queryByRole("link", { name: "Open unbilled work" })).not.toBeInTheDocument();
  });

  it("redacted: 'My unbilled time' shows own hours only — no values, no firm total", () => {
    render(<UnbilledWipWidget slug="acme" data={REDACTED} personal showLink />);

    const card = screen.getByTestId("unbilled-wip-widget");
    expect(card).toHaveAttribute("data-variant", "personal");
    expect(within(card).getByText("My unbilled time")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-wip-headline")).toHaveTextContent("5h");
    expect(card.textContent).not.toMatch(/R\s?\d/);
    expect(within(card).queryByText("Unbilled WIP")).not.toBeInTheDocument();
    expect(screen.queryByTestId("unbilled-disbursements")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open unbilled work" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open timesheet" })).toHaveAttribute(
      "href",
      "/org/acme/my-work/timesheet"
    );
  });

  it("redacted with no unbilled time: says what to do next", () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{ ...REDACTED, totalMinutes: 0, byFeeEarner: [] }}
        personal
        showLink={false}
      />
    );
    expect(screen.getByText(/No unbilled time/)).toBeInTheDocument();
  });

  it("error: quiet error line, titled for the caller's audience", () => {
    render(<UnbilledWipWidget slug="acme" data={null} personal showLink={false} />);
    expect(screen.getByText("My unbilled time")).toBeInTheDocument();
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
  });
});
