import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TagInput } from "@/components/tags/TagInput";
import type { TagResponse } from "@/lib/types";

const mockCreateTag = vi.fn();
const mockSetEntityTags = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/tags/actions", () => ({
  createTagAction: (...args: unknown[]) => mockCreateTag(...args),
  setEntityTagsAction: (...args: unknown[]) => mockSetEntityTags(...args),
}));

const tagUrgent: TagResponse = {
  id: "tag-1",
  name: "Urgent",
  slug: "urgent",
  color: "#FF0000",
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const tagVip: TagResponse = {
  id: "tag-2",
  name: "VIP",
  slug: "vip",
  color: "#FFD700",
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const tagInternal: TagResponse = {
  id: "tag-3",
  name: "Internal",
  slug: "internal",
  color: null,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const allTags = [tagUrgent, tagVip, tagInternal];

describe("TagInput", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders badges for existing tags", () => {
    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[tagUrgent, tagVip]}
        allTags={allTags}
        editable={false}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    expect(screen.getByText("Urgent")).toBeInTheDocument();
    expect(screen.getByText("VIP")).toBeInTheDocument();
  });

  it("shows 'No tags' in read-only mode when empty", () => {
    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[]}
        allTags={allTags}
        editable={false}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    expect(screen.getByText("No tags")).toBeInTheDocument();
  });

  it("shows Add Tag button when editable", () => {
    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[]}
        allTags={allTags}
        editable={true}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    expect(
      screen.getByRole("button", { name: /Add Tag/i }),
    ).toBeInTheDocument();
  });

  it("does not show Add Tag button in read-only mode", () => {
    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[]}
        allTags={allTags}
        editable={false}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    expect(
      screen.queryByRole("button", { name: /Add Tag/i }),
    ).not.toBeInTheDocument();
  });

  it("shows remove button on badges when editable", () => {
    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[tagUrgent]}
        allTags={allTags}
        editable={true}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    expect(
      screen.getByRole("button", { name: /Remove Urgent/i }),
    ).toBeInTheDocument();
  });

  it("calls setEntityTagsAction when removing a tag", async () => {
    const user = userEvent.setup();
    mockSetEntityTags.mockResolvedValue({ success: true });

    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[tagUrgent, tagVip]}
        allTags={allTags}
        editable={true}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    await user.click(
      screen.getByRole("button", { name: /Remove Urgent/i }),
    );

    await waitFor(() => {
      expect(mockSetEntityTags).toHaveBeenCalledWith(
        "acme",
        "PROJECT",
        "proj-1",
        ["tag-2"],
      );
    });
  });

  it("calls setEntityTagsAction when adding a tag from popover", async () => {
    const user = userEvent.setup();
    mockSetEntityTags.mockResolvedValue({ success: true });

    render(
      <TagInput
        entityType="PROJECT"
        entityId="proj-1"
        tags={[tagUrgent]}
        allTags={allTags}
        editable={true}
        canInlineCreate={false}
        slug="acme"
      />,
    );

    // Open popover
    await user.click(
      screen.getByRole("button", { name: /Add Tag/i }),
    );

    // Wait for popover content to appear and click Internal
    await waitFor(() => {
      expect(screen.getByText("Internal")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Internal"));

    await waitFor(() => {
      expect(mockSetEntityTags).toHaveBeenCalledWith(
        "acme",
        "PROJECT",
        "proj-1",
        ["tag-1", "tag-3"],
      );
    });
  });
});
