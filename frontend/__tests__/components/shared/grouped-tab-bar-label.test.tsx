import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { GroupedTabBar } from "@/components/shared/grouped-tab-bar";
import { MATTER_TAB_GROUPS } from "@/lib/constants/matter-tab-groups";
import { CLIENT_TAB_GROUPS } from "@/lib/constants/client-tab-groups";
import type { TabGroup } from "@/lib/constants/tab-group-types";

// Phase 91 664A (K-20, wording approved 2026-11-26): the active group trigger reads
// "{group} · {sub-tab}", except when the sub-tab is labelled like its group — then just "{group}"
// (no "Activity · Activity"). The 642C label cases live in projects/grouped-tab-bar.test.tsx.

afterEach(() => {
  cleanup();
});

function renderBar(groups: readonly TabGroup[], activeTab: string) {
  render(<GroupedTabBar groups={[...groups]} activeTab={activeTab} onTabChange={() => {}} />);
}

describe("GroupedTabBar label — K-20 duplicate sub-tab label", () => {
  it('shows just "Activity" on the matter Activity tab', () => {
    renderBar(MATTER_TAB_GROUPS, "activity");
    const trigger = screen.getByTestId("tab-group-activity");
    expect(trigger).toHaveTextContent(/^Activity$/);
    expect(trigger).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Activity" })).toBe(trigger);
  });

  it('keeps the suffix when the labels differ ("Activity · Audit trail")', () => {
    renderBar(MATTER_TAB_GROUPS, "audit");
    expect(screen.getByTestId("tab-group-activity")).toHaveTextContent("Activity · Audit trail");
  });

  it('shows just "Details" on the matter Details tab, "Details · Team" on Team', () => {
    renderBar(MATTER_TAB_GROUPS, "details");
    expect(screen.getByTestId("tab-group-details")).toHaveTextContent(/^Details$/);
    cleanup();

    renderBar(MATTER_TAB_GROUPS, "team");
    expect(screen.getByTestId("tab-group-details")).toHaveTextContent("Details · Team");
  });

  it('leaves "Fees · Time recording" unchanged', () => {
    renderBar(MATTER_TAB_GROUPS, "time");
    expect(screen.getByTestId("tab-group-finance")).toHaveTextContent("Fees · Time recording");
  });

  it('shows just "Matters" on the client Matters tab (the generic rule)', () => {
    renderBar(CLIENT_TAB_GROUPS, "matters");
    expect(screen.getByTestId("tab-group-work")).toHaveTextContent(/^Matters$/);
    cleanup();

    renderBar(CLIENT_TAB_GROUPS, "documents");
    expect(screen.getByTestId("tab-group-work")).toHaveTextContent("Matters · Documents");
  });
});
