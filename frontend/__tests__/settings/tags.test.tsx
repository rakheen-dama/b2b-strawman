import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TagsContent } from "@/app/(app)/org/[slug]/settings/tags/tags-content";
import type { TagResponse } from "@/lib/types";

const mockCreateTag = vi.fn();
const mockUpdateTag = vi.fn();
const mockDeleteTag = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/tags/actions", () => ({
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
    render(
      <TagsContent slug="acme" tags={sampleTags} canManage={false} />,
    );

    expect(screen.getByText("Urgent")).toBeInTheDocument();
    expect(screen.getByText("VIP")).toBeInTheDocument();
    expect(screen.getByText("urgent")).toBeInTheDocument();
    expect(screen.getByText("vip")).toBeInTheDocument();
  });

  it("admin sees Add Tag button", () => {
    render(
      <TagsContent slug="acme" tags={sampleTags} canManage={true} />,
    );

    expect(
      screen.getAllByRole("button", { name: /Add Tag/i }).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("member does not see Add Tag button", () => {
    render(
      <TagsContent slug="acme" tags={sampleTags} canManage={false} />,
    );

    expect(
      screen.queryByRole("button", { name: /Add Tag/i }),
    ).not.toBeInTheDocument();
  });
});
