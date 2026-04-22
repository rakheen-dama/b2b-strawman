import React from "react";
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
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { MatterClosureReport } from "@/components/legal/matter-closure-report";
import type { GateResult } from "@/lib/api/matter-closure";

afterEach(() => cleanup());

function gate(overrides: Partial<GateResult> = {}): GateResult {
  return {
    order: 1,
    code: "TRUST_BALANCE_ZERO",
    passed: true,
    message: "Matter trust balance is zero.",
    detail: null,
    ...overrides,
  };
}

describe("MatterClosureReport", () => {
  it("renders a pass row with a green check for a passing gate", () => {
    render(
      <MatterClosureReport
        gates={[gate({ code: "TRUST_BALANCE_ZERO", passed: true })]}
        slug="acme"
        projectId="p1"
      />
    );
    expect(screen.getByTestId("matter-closure-gate-row-TRUST_BALANCE_ZERO")).toBeInTheDocument();
    expect(screen.getByTestId("matter-closure-gate-pass-TRUST_BALANCE_ZERO")).toBeInTheDocument();
    expect(
      screen.queryByTestId("matter-closure-gate-fix-TRUST_BALANCE_ZERO")
    ).not.toBeInTheDocument();
  });

  it("renders a fail row with a red X and a Fix this deep link", () => {
    render(
      <MatterClosureReport
        gates={[
          gate({
            code: "ALL_DISBURSEMENTS_APPROVED",
            passed: false,
            message: "2 disbursements pending approval.",
          }),
        ]}
        slug="acme"
        projectId="p1"
      />
    );
    const row = screen.getByTestId("matter-closure-gate-row-ALL_DISBURSEMENTS_APPROVED");
    expect(row).toBeInTheDocument();
    expect(row).toHaveAttribute("data-passed", "false");
    expect(
      screen.getByTestId("matter-closure-gate-fail-ALL_DISBURSEMENTS_APPROVED")
    ).toBeInTheDocument();
    const fixLink = screen.getByTestId("matter-closure-gate-fix-ALL_DISBURSEMENTS_APPROVED");
    expect(fixLink).toHaveAttribute("href", "/org/acme/projects/p1?tab=disbursements");
  });

  it("routes known gate codes to the correct deep link", () => {
    render(
      <MatterClosureReport
        gates={[
          gate({ code: "TRUST_BALANCE_ZERO", passed: false, message: "trust" }),
          gate({
            order: 2,
            code: "FINAL_BILL_ISSUED",
            passed: false,
            message: "bill",
          }),
          gate({
            order: 3,
            code: "NO_OPEN_COURT_DATES",
            passed: false,
            message: "court",
          }),
          gate({
            order: 4,
            code: "ALL_TASKS_RESOLVED",
            passed: false,
            message: "tasks",
          }),
        ]}
        slug="acme"
        projectId="p1"
      />
    );
    expect(screen.getByTestId("matter-closure-gate-fix-TRUST_BALANCE_ZERO")).toHaveAttribute(
      "href",
      "/org/acme/projects/p1?tab=trust"
    );
    expect(screen.getByTestId("matter-closure-gate-fix-FINAL_BILL_ISSUED")).toHaveAttribute(
      "href",
      "/org/acme/invoices?projectId=p1"
    );
    expect(screen.getByTestId("matter-closure-gate-fix-NO_OPEN_COURT_DATES")).toHaveAttribute(
      "href",
      "/org/acme/court-calendar?projectId=p1"
    );
    expect(screen.getByTestId("matter-closure-gate-fix-ALL_TASKS_RESOLVED")).toHaveAttribute(
      "href",
      "/org/acme/projects/p1?tab=tasks"
    );
  });

  it("renders message text verbatim from the backend", () => {
    render(
      <MatterClosureReport
        gates={[
          gate({
            code: "TRUST_BALANCE_ZERO",
            passed: false,
            message: "Matter trust balance is R4,200.00.",
          }),
        ]}
        slug="acme"
        projectId="p1"
      />
    );
    expect(screen.getByText("Matter trust balance is R4,200.00.")).toBeInTheDocument();
  });

  it("renders an empty-state message when no gates are provided", () => {
    render(<MatterClosureReport gates={[]} slug="acme" projectId="p1" />);
    expect(screen.getByTestId("matter-closure-report-empty")).toBeInTheDocument();
  });
});
