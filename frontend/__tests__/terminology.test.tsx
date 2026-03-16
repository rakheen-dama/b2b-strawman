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
    expect(t("Rate Card")).toBe("Fee Schedule");
    expect(t("Rate Cards")).toBe("Fee Schedules");
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
      </TerminologyProvider>,
    );

    expect(screen.getByTestId("result").textContent).toBe("Engagement");
  });
});
