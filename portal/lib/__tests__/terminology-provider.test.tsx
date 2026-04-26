import { afterEach, describe, it, expect } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider, useTerminology } from "@/lib/terminology";

function Probe() {
  const { t } = useTerminology();
  return (
    <div>
      <span data-testid="lower">{t("invoice")}</span>
      <span data-testid="lower-plural">{t("invoices")}</span>
      <span data-testid="title">{t("Invoice")}</span>
      <span data-testid="title-plural">{t("Invoices")}</span>
    </div>
  );
}

describe("TerminologyProvider (portal)", () => {
  afterEach(() => {
    cleanup();
  });

  it("translates legal-za vocabulary", () => {
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <Probe />
      </TerminologyProvider>,
    );
    expect(screen.getByTestId("lower").textContent).toBe("fee note");
    expect(screen.getByTestId("lower-plural").textContent).toBe("fee notes");
    expect(screen.getByTestId("title").textContent).toBe("Fee Note");
    expect(screen.getByTestId("title-plural").textContent).toBe("Fee Notes");
  });

  it("falls back to identity when verticalProfile is null", () => {
    render(
      <TerminologyProvider verticalProfile={null}>
        <Probe />
      </TerminologyProvider>,
    );
    expect(screen.getByTestId("lower").textContent).toBe("invoice");
    expect(screen.getByTestId("lower-plural").textContent).toBe("invoices");
    expect(screen.getByTestId("title").textContent).toBe("Invoice");
    expect(screen.getByTestId("title-plural").textContent).toBe("Invoices");
  });

  it("falls back to identity for unknown verticalProfile", () => {
    render(
      <TerminologyProvider verticalProfile="retail-uk">
        <Probe />
      </TerminologyProvider>,
    );
    expect(screen.getByTestId("lower").textContent).toBe("invoice");
    expect(screen.getByTestId("title").textContent).toBe("Invoice");
  });

  it("falls back to identity when no provider is mounted", () => {
    // useTerminology() returns the DEFAULT_TERMINOLOGY when ctx is null.
    render(<Probe />);
    expect(screen.getByTestId("lower").textContent).toBe("invoice");
    expect(screen.getByTestId("title").textContent).toBe("Invoice");
  });
});
