import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import {
  CompliancePacks,
  formatPackName,
} from "@/components/settings/CompliancePacks";
import type { PackStatusDto } from "@/lib/types";

// Radix-based Switch component leaks portals between tests
afterEach(() => cleanup());

function makePacks(
  overrides: Partial<PackStatusDto>[] = [],
): PackStatusDto[] {
  const defaults: PackStatusDto[] = [
    {
      packId: "generic-onboarding",
      version: 1,
      appliedAt: "2026-01-15T10:00:00Z",
      active: true,
    },
    {
      packId: "sa-fica-individual",
      version: 1,
      appliedAt: "2026-01-15T10:00:00Z",
      active: true,
    },
    {
      packId: "sa-fica-company",
      version: 1,
      appliedAt: "2026-01-15T10:00:00Z",
      active: false,
    },
  ];
  return defaults.map((pack, i) => ({ ...pack, ...overrides[i] }));
}

describe("formatPackName", () => {
  it("capitalises each word separated by hyphens", () => {
    expect(formatPackName("generic-onboarding")).toBe("Generic Onboarding");
  });

  it("handles multi-word pack ids", () => {
    expect(formatPackName("sa-fica-individual")).toBe("Sa Fica Individual");
  });

  it("handles single-word pack ids", () => {
    expect(formatPackName("onboarding")).toBe("Onboarding");
  });
});

describe("CompliancePacks", () => {
  it("renders heading and description", () => {
    render(<CompliancePacks packs={[]} />);
    expect(screen.getByText("Compliance Packs")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Pre-configured checklist templates for compliance requirements",
      ),
    ).toBeInTheDocument();
  });

  it("renders pack cards with formatted names", () => {
    render(<CompliancePacks packs={makePacks()} />);
    expect(screen.getByText("Generic Onboarding")).toBeInTheDocument();
    expect(screen.getByText("Sa Fica Individual")).toBeInTheDocument();
    expect(screen.getByText("Sa Fica Company")).toBeInTheDocument();
  });

  it("shows Active badge for active packs", () => {
    render(<CompliancePacks packs={makePacks()} />);
    const activeBadges = screen.getAllByText("Active");
    expect(activeBadges).toHaveLength(2);
  });

  it("shows Inactive badge for inactive packs", () => {
    render(<CompliancePacks packs={makePacks()} />);
    expect(screen.getByText("Inactive")).toBeInTheDocument();
  });

  it("renders applied date for each pack", () => {
    render(<CompliancePacks packs={makePacks()} />);
    // formatDate("2026-01-15T10:00:00Z") → "Jan 15, 2026"
    const dateElements = screen.getAllByText(/Applied: Jan 15, 2026/);
    expect(dateElements).toHaveLength(3);
  });

  it("renders disabled switches for each pack", () => {
    render(<CompliancePacks packs={makePacks()} />);
    const switches = screen.getAllByRole("switch");
    expect(switches).toHaveLength(3);
    switches.forEach((sw) => {
      expect(sw).toBeDisabled();
    });
  });

  it("renders empty grid when no packs provided", () => {
    const { container } = render(<CompliancePacks packs={[]} />);
    const cards = container.querySelectorAll("[data-slot='card']");
    expect(cards).toHaveLength(0);
  });
});
