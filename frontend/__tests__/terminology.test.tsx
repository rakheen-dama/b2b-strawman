import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TERMINOLOGY } from "@/lib/terminology-map";
import { TerminologyProvider, useTerminology } from "@/lib/terminology";

// Helper: build a t() function for a given profile without rendering
function makeTFn(profile: string | null): (term: string) => string {
  const map = profile ? (TERMINOLOGY[profile] ?? {}) : {};
  return (term: string) => map[term] ?? term;
}

describe("terminology", () => {
  afterEach(() => {
    cleanup();
  });

  // 364.4 Test 1
  it("t('Projects') returns 'Engagements' for accounting-za", () => {
    const t = makeTFn("accounting-za");
    expect(t("Projects")).toBe("Engagements");
  });

  // 364.4 Test 2
  it("t('Projects') returns 'Projects' for null profile", () => {
    const t = makeTFn(null);
    expect(t("Projects")).toBe("Projects");
  });

  // 364.4 Test 3
  it("t('Unknown') returns 'Unknown' as passthrough for unmapped term", () => {
    const t = makeTFn("accounting-za");
    expect(t("Unknown")).toBe("Unknown");
  });

  // 364.4 Test 4 — all case variants
  it("all case variants map correctly for accounting-za", () => {
    const t = makeTFn("accounting-za");
    expect(t("Project")).toBe("Engagement");
    expect(t("Projects")).toBe("Engagements");
    expect(t("project")).toBe("engagement");
    expect(t("projects")).toBe("engagements");
    expect(t("Customer")).toBe("Client");
    expect(t("Customers")).toBe("Clients");
    expect(t("customer")).toBe("client");
    expect(t("customers")).toBe("clients");
    expect(t("Proposal")).toBe("Engagement Letter");
    expect(t("Proposals")).toBe("Engagement Letters");
    expect(t("proposal")).toBe("engagement letter");
    expect(t("proposals")).toBe("engagement letters");
    expect(t("Rate Card")).toBe("Fee Schedule");
    expect(t("Rate Cards")).toBe("Fee Schedules");
  });

  // 465.2 — legal-za unit tests
  it("t('Invoice') returns 'Fee Note' for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Invoice")).toBe("Fee Note");
  });

  it("t('Expense') returns 'Disbursement' for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Expense")).toBe("Disbursement");
  });

  it("t('Retainer') returns 'Mandate' for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Retainer")).toBe("Mandate");
  });

  it("t('Budget') returns 'Fee Estimate' for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Budget")).toBe("Fee Estimate");
  });

  it("t('Task') returns 'Action Item' for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Task")).toBe("Action Item");
  });

  // 465.2 — all case variants for legal-za
  it("all case variants map correctly for legal-za", () => {
    const t = makeTFn("legal-za");
    expect(t("Project")).toBe("Matter");
    expect(t("Projects")).toBe("Matters");
    expect(t("project")).toBe("matter");
    expect(t("projects")).toBe("matters");
    expect(t("Task")).toBe("Action Item");
    expect(t("Tasks")).toBe("Action Items");
    expect(t("task")).toBe("action item");
    expect(t("tasks")).toBe("action items");
    expect(t("Customer")).toBe("Client");
    expect(t("Customers")).toBe("Clients");
    expect(t("customer")).toBe("client");
    expect(t("customers")).toBe("clients");
    expect(t("Invoice")).toBe("Fee Note");
    expect(t("Invoices")).toBe("Fee Notes");
    expect(t("invoice")).toBe("fee note");
    expect(t("invoices")).toBe("fee notes");
    expect(t("Expense")).toBe("Disbursement");
    expect(t("Expenses")).toBe("Disbursements");
    expect(t("expense")).toBe("disbursement");
    expect(t("expenses")).toBe("disbursements");
    expect(t("Retainer")).toBe("Mandate");
    expect(t("Retainers")).toBe("Mandates");
    expect(t("retainer")).toBe("mandate");
    expect(t("retainers")).toBe("mandates");
    expect(t("Budget")).toBe("Fee Estimate");
    expect(t("Budgets")).toBe("Fee Estimates");
    expect(t("budget")).toBe("fee estimate");
    expect(t("budgets")).toBe("fee estimates");
    expect(t("Time Entry")).toBe("Time Recording");
    expect(t("Time Entries")).toBe("Time Recordings");
    expect(t("time entry")).toBe("time recording");
    expect(t("time entries")).toBe("time recordings");
    expect(t("Rate Card")).toBe("Tariff Schedule");
    expect(t("Rate Cards")).toBe("Tariff Schedules");
  });

  // 364.5 — Integration test: provider in component tree
  it("useTerminology hook returns correct t() when consumed inside TerminologyProvider", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Project")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Engagement");
  });
});
