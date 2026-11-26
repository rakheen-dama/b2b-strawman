import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import {
  TagsContent,
  getContrastColor,
} from "@/app/(app)/org/[slug]/settings/matters/_tabs/tags-content";
import type { TagResponse } from "@/lib/types";

const mockCreateTag = vi.fn();
const mockUpdateTag = vi.fn();
const mockDeleteTag = vi.fn();

// DataTable (Phase 91 665C) calls useRouter for row links.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/tags-actions", () => ({
  createTagAction: (...args: unknown[]) => mockCreateTag(...args),
  updateTagAction: (...args: unknown[]) => mockUpdateTag(...args),
  deleteTagAction: (...args: unknown[]) => mockDeleteTag(...args),
}));

const tag1: TagResponse = {
  id: "tag-1",
  name: "Urgent",
  slug: "urgent",
  color: "#FF0000",
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const tag2: TagResponse = {
  id: "tag-2",
  name: "VIP",
  slug: "vip",
  color: null,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const sampleTags: TagResponse[] = [tag1, tag2];

describe("TagsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders tags in table", () => {
    render(<TagsContent slug="acme" tags={sampleTags} canManage={false} />);

    expect(screen.getByText("Urgent")).toBeInTheDocument();
    expect(screen.getByText("VIP")).toBeInTheDocument();
    expect(screen.getByText("urgent")).toBeInTheDocument();
    expect(screen.getByText("vip")).toBeInTheDocument();
  });

  it("admin's table has no Add Tag button (it lives in the tab's PageHeader)", () => {
    render(<TagsContent slug="acme" tags={sampleTags} canManage={true} />);

    expect(screen.queryByRole("button", { name: /Add Tag/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit Urgent" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete Urgent" })).toBeInTheDocument();
  });

  it("member does not see Add Tag button", () => {
    render(<TagsContent slug="acme" tags={sampleTags} canManage={false} />);

    expect(screen.queryByRole("button", { name: /Add Tag/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit Urgent" })).not.toBeInTheDocument();
  });

  it("admin's empty state offers 'Add tag' with the full description (K-111)", () => {
    render(<TagsContent slug="acme" tags={[]} canManage={true} />);

    expect(screen.getByRole("heading", { level: 2, name: "No tags yet" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add tag" })).toBeInTheDocument();
    expect(screen.queryByText("Create tag")).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "Tags are colour-coded labels for organising matters, tasks, and clients. Create a tagging system that fits your workflow."
      )
    ).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("member's empty state has no button and no call to create (K-111)", () => {
    render(<TagsContent slug="acme" tags={[]} canManage={false} />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(
      screen.getByText("Tags are colour-coded labels for organising matters, tasks, and clients.")
    ).toBeInTheDocument();
    expect(screen.queryByText(/Create a tagging system/)).not.toBeInTheDocument();
  });

  it("the Name cell holds only the tag chip (tags.spec exact cell name)", () => {
    render(<TagsContent slug="acme" tags={sampleTags} canManage={true} />);

    expect(screen.getByRole("cell", { name: "Urgent" })).toBeInTheDocument();
  });
});

describe("getContrastColor (K-15)", () => {
  it.each([
    ["#FFFF00", "var(--slate-950)"],
    ["#FFFFFF", "var(--slate-950)"],
    // Mid-luminance colours the old YIQ formula gave white text (3.68:1 / 4.0:1)
    ["#3B82F6", "var(--slate-950)"],
    ["#FF0000", "var(--slate-950)"],
    ["#1E3A8A", "var(--color-white)"],
    // Boundary: above the pure-black 0.179 threshold, but white (4.54:1) beats slate-950 (4.41:1)
    ["#767676", "var(--color-white)"],
    ["#808080", "var(--slate-950)"],
    ["#000000", "var(--color-white)"],
    ["not-a-hex", "var(--color-white)"],
  ])("%s → %s", (hex, expected) => {
    expect(getContrastColor(hex)).toBe(expected);
  });

  it("applies the token colour to the chip, never a raw hex", () => {
    render(<TagsContent slug="acme" tags={sampleTags} canManage={false} />);

    const chip = screen.getByText("Urgent");
    expect(chip.style.color).toBe("var(--slate-950)");
  });
});
