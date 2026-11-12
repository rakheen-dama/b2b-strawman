import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import DashboardLoading from "@/app/(app)/org/[slug]/dashboard/loading";
import { KPI_TILE_LABELS } from "@/components/dashboard/kpi-row";

afterEach(() => {
  cleanup();
});

describe("Home loading skeleton (640C)", () => {
  it("draws the KPI row as four StatCards in their loading state", () => {
    render(<DashboardLoading />);

    // Labels and order come from KpiRow's own source, so the skeleton can't drift from it.
    const tiles = screen.getAllByRole("group");
    expect(KPI_TILE_LABELS.map((name) => screen.getByRole("group", { name }))).toEqual(tiles);
    for (const tile of tiles) {
      expect(tile).toHaveAttribute("aria-busy", "true");
      expect(tile).toHaveAttribute("data-state", "loading");
    }
  });

  it("renders the real Home header once", () => {
    render(<DashboardLoading />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Home");
  });

  it("carries none of the loaded page's e2e hooks", () => {
    const { container } = render(<DashboardLoading />);

    expect(container.querySelectorAll('[data-testid^="home-"]')).toHaveLength(0);
    expect(container.querySelector("[data-audience]")).toBeNull();
    // The only testid is PageHeader's own default (the real DashboardHeader); none is added.
    expect(
      Array.from(container.querySelectorAll("[data-testid]")).map((el) =>
        el.getAttribute("data-testid")
      )
    ).toEqual(["page-header"]);
  });
});
