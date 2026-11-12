import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

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

import { DetailHero, type HeroTile } from "@/components/shared/detail-hero";
import { HeaderFactsStrip } from "@/components/shared/header-facts-strip";

afterEach(() => {
  cleanup();
});

const TILES: HeroTile[] = [
  { id: "wip", label: "Unbilled time", value: "R 12 500,00", state: "ready" },
  { id: "trust", label: "Trust balance", value: "R 40 000,00", state: "ready" },
  { id: "outstanding", label: "Outstanding", value: "R 3 000,00", state: "ready" },
  {
    id: "court",
    label: "Next court date",
    value: null,
    hint: "None scheduled",
    state: "ready",
  },
];

function tilesGrid(container: HTMLElement) {
  return container.querySelector('[data-slot="detail-hero-tiles"]');
}

describe("DetailHero", () => {
  it("renders no tile grid and no tile groups with 0 tiles", () => {
    const { container } = render(<DetailHero title="Mokoena v RAF" />);
    expect(tilesGrid(container)).toBeNull();
    expect(screen.queryAllByRole("group")).toHaveLength(0);
  });

  it("renders 2 tiles in order", () => {
    render(<DetailHero title="Mokoena v RAF" tiles={TILES.slice(0, 2)} />);
    const groups = screen.getAllByRole("group");
    expect(groups).toHaveLength(2);
    expect(groups[0]).toHaveAccessibleName("Unbilled time");
    expect(groups[1]).toHaveAccessibleName("Trust balance");
  });

  it("renders 4 tiles as compact StatCards in a 2 → 4 column container grid", () => {
    const { container } = render(<DetailHero title="Mokoena v RAF" tiles={TILES} />);
    const groups = screen.getAllByRole("group");
    expect(groups).toHaveLength(4);
    groups.forEach((group) => expect(group).toHaveAttribute("data-variant", "compact"));
    expect(tilesGrid(container)).toHaveClass("grid-cols-2", "@3xl:grid-cols-4");
    expect(container.querySelector('[data-slot="detail-hero"]')).toHaveClass("@container");
  });

  it("reflows when a gated tile is omitted: 3 groups, order kept, no placeholder", () => {
    const shown = [TILES[0], TILES[2], TILES[3]];
    const { container } = render(<DetailHero title="Mokoena v RAF" tiles={shown} />);
    const groups = screen.getAllByRole("group");
    expect(groups).toHaveLength(3);
    expect(groups[0]).toHaveAccessibleName("Unbilled time");
    expect(groups[1]).toHaveAccessibleName("Outstanding");
    expect(groups[2]).toHaveAccessibleName("Next court date");
    expect(screen.queryByText("Trust balance")).toBeNull();
    expect(tilesGrid(container)).toHaveClass("@3xl:grid-cols-3");
    // Only the real "no court date" tile shows a dash.
    expect(screen.getAllByText("—")).toHaveLength(1);
  });

  it("renders an error tile as 'Couldn't load'", () => {
    render(
      <DetailHero
        title="Mokoena v RAF"
        tiles={[{ id: "trust", label: "Trust balance", value: null, state: "error" }]}
      />
    );
    const group = screen.getByRole("group", { name: "Trust balance" });
    expect(within(group).getByRole("status")).toHaveTextContent("Couldn't load");
  });

  it("passes titleTestId to the h1 and data-testid to the root", () => {
    render(
      <DetailHero
        title="Mokoena v RAF"
        titleTestId="matter-name"
        data-testid="matter-header-card"
      />
    );
    expect(screen.getByTestId("matter-name")).toBe(
      screen.getByRole("heading", { level: 1, name: "Mokoena v RAF" })
    );
    expect(screen.getByTestId("matter-header-card")).toHaveAttribute("data-slot", "detail-hero");
  });

  it("renders a single h1 with status, actions, facts and tiles", () => {
    render(
      <DetailHero
        title="Mokoena v RAF"
        subtitle="Thandi Mokoena · A. Dlamini"
        status={<span data-testid="project-status-badge">Active</span>}
        actions={
          <div data-testid="header-lifecycle-actions">
            <button type="button">Close matter</button>
          </div>
        }
        facts={
          <HeaderFactsStrip
            fields={[{ slug: "case_number", label: "Case number", value: "1234/2026" }]}
            testId="matter-header-fields"
            itemTestIdPrefix="matter-header-field"
          />
        }
        tiles={TILES}
      />
    );

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByTestId("project-status-badge")).toHaveTextContent("Active");
    expect(screen.getByRole("button", { name: "Close matter" })).toBeInTheDocument();
    expect(screen.getByText("Thandi Mokoena · A. Dlamini")).toBeInTheDocument();
    const facts = screen.getByTestId("matter-header-fields");
    expect(within(facts).getByTestId("matter-header-field-case_number")).toBeInTheDocument();
  });

  it("renders the identifier as a mono code chip only when present", () => {
    const { container, rerender } = render(<DetailHero title="Mokoena v RAF" />);
    expect(container.querySelector("code")).toBeNull();

    rerender(<DetailHero title="Mokoena v RAF" identifier="MAT-0042" />);
    const chip = screen.getByText("MAT-0042");
    expect(chip.tagName).toBe("CODE");
    expect(chip).toHaveClass("font-mono");
  });

  it("wraps a tile with href in a link named by its label and value", () => {
    render(
      <DetailHero
        title="Mokoena v RAF"
        tiles={[{ ...TILES[1], href: "/org/acme/trust-accounting" }]}
      />
    );
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/org/acme/trust-accounting");
    expect(within(link).getByRole("group", { name: "Trust balance" })).toBeInTheDocument();
  });
});
