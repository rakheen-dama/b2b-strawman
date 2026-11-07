import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { EntityPicker } from "@/components/reports/entity-picker";

vi.mock("server-only", () => ({}));
vi.mock("@/app/(app)/org/[slug]/reports/[reportSlug]/actions", () => ({
  fetchEntityOptionsAction: vi.fn().mockResolvedValue({ data: [] }),
}));

// G89-F19: the Section 35 / trust report pickers said "Select a trustaccount..." — the raw
// camelCase entity type lower-cased, with three dots instead of an ellipsis.
describe("EntityPicker placeholder", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("splits a camelCase entity type into words", () => {
    render(<EntityPicker entityType="trustAccount" value="" onChange={vi.fn()} />);

    expect(screen.getByRole("combobox")).toHaveTextContent("Select a trust account…");
    expect(screen.queryByText(/trustaccount/i)).not.toBeInTheDocument();
  });

  it("keeps the legal noun for core entity types", () => {
    render(<EntityPicker entityType="project" value="" onChange={vi.fn()} />);

    expect(screen.getByRole("combobox")).toHaveTextContent("Select a matter…");
  });
});
