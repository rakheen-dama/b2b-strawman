import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";
import { TrustEquationIndicator } from "@/components/take-on/trust-equation-indicator";
import { TRUST_EQUATION_17313, trustSummaryWith } from "./fixtures";

afterEach(() => cleanup());

const ROWS = ["eq1", "items", "eq2", "investments", "deficit"] as const;

/** The X3 indicator labels (§17.10), typed here so the test pins them independently. */
const X3_LABELS = [
  "Bank balance + outstanding deposits − outstanding payments = cashbook",
  "Listed outstanding items agree with the totals",
  "Cashbook = trust creditors",
  "Investments agree with the confirmations",
  "No trust deficit",
];

describe("TrustEquationIndicator (657B, §17.10 X3)", () => {
  it("shows the five X3 rows of the §17.3.13 position, each agreeing with Difference R0,00", () => {
    render(<TrustEquationIndicator equation={TRUST_EQUATION_17313} />);
    ROWS.forEach((id, i) => {
      const row = screen.getByTestId(`take-on-equation-${id}`);
      expect(row).toHaveTextContent(X3_LABELS[i]);
      expect(row).toHaveAttribute("data-holds", "true");
      expect(within(row).getByText("Agrees")).toBeInTheDocument();
      expect(screen.getByTestId(`take-on-equation-${id}-difference`).textContent).toBe(
        `Difference ${formatCurrency(0, "ZAR")}`
      );
    });
    expect(screen.queryByText("Does not agree")).not.toBeInTheDocument();
  });

  it("marks EQ-1 and EQ-2 as not agreeing when the cashbook is R10,00 off, the others agreeing", () => {
    const { equation } = trustSummaryWith({
      equation: {
        eq1: {
          ...TRUST_EQUATION_17313.eq1,
          cashbook: "403510.00",
          difference: "-10.00",
          holds: false,
        },
        eq2: {
          ...TRUST_EQUATION_17313.eq2,
          cashbook: "403510.00",
          difference: "-10.00",
          holds: false,
        },
      },
    });
    render(<TrustEquationIndicator equation={equation} />);
    for (const id of ["eq1", "eq2"]) {
      expect(screen.getByTestId(`take-on-equation-${id}`)).toHaveAttribute("data-holds", "false");
      const difference = screen.getByTestId(`take-on-equation-${id}-difference`);
      // A difference is shown as its size (D-185 OP-9); red, not green.
      expect(difference.textContent).toBe(`Difference ${formatCurrency(10, "ZAR")}`);
      expect(difference.className).toContain("text-red-600");
    }
    for (const id of ["items", "investments", "deficit"]) {
      expect(screen.getByTestId(`take-on-equation-${id}`)).toHaveAttribute("data-holds", "true");
    }
    expect(screen.getAllByText("Does not agree")).toHaveLength(2);
    // The figures behind the row, formatted with the house helper.
    expect(
      within(screen.getByTestId("take-on-equation-eq1")).getByText("Cashbook balance").nextSibling
        ?.textContent
    ).toBe(formatCurrency(403510, "ZAR"));
  });

  it("marks No trust deficit as not agreeing when the trust creditors exceed the money held", () => {
    const { equation } = trustSummaryWith({
      equation: {
        deficit: {
          trustCreditors: "713500.00",
          moneyHeld: "703500.00",
          difference: "10000.00",
          inDeficit: true,
        },
      },
    });
    render(<TrustEquationIndicator equation={equation} />);
    const row = screen.getByTestId("take-on-equation-deficit");
    expect(row).toHaveAttribute("data-holds", "false");
    expect(within(row).getByText("Does not agree")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-equation-deficit-difference").textContent).toBe(
      `Difference ${formatCurrency(10000, "ZAR")}`
    );
  });

  it("renders nothing when there is no equation (an account check failed)", () => {
    const { container } = render(<TrustEquationIndicator equation={null} />);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId("take-on-equation")).not.toBeInTheDocument();
  });

  it("writes the EQ-1 label with the U+2212 minus sign, exactly as X3", () => {
    render(<TrustEquationIndicator equation={TRUST_EQUATION_17313} />);
    const label = screen.getByText(/^Bank balance \+ outstanding deposits/);
    expect(label.textContent).toBe(
      "Bank balance + outstanding deposits − outstanding payments = cashbook"
    );
    expect(label.textContent).not.toContain("-");
  });
});
