import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import { findMentionQuery, insertMention, filterMentionCandidates } from "@/lib/comments";
import type { MentionCandidate } from "@/lib/comments";

// 634C: @-mention autocomplete in the comment textarea. The backend resolves "@First Last" (or
// "@First") when the comment is posted; the autocomplete only has to insert that text.

const mockFetchComments = vi.fn();
const mockFetchMentionCandidates = vi.fn();
const mockCreateComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: (...args: unknown[]) => mockFetchComments(...args),
  fetchMentionCandidates: (...args: unknown[]) => mockFetchMentionCandidates(...args),
  createComment: (...args: unknown[]) => mockCreateComment(...args),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
  getDownloadUrl: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

const CANDIDATES: MentionCandidate[] = [
  { id: "m-alice", name: "Alice Owner" },
  { id: "m-bob", name: "Bob Ndlovu" },
  { id: "m-bongani", name: "Bongani Dlamini" },
  { id: "m-carol", name: "Carol Mokoena" },
];

function renderForm(candidates = CANDIDATES) {
  return render(
    <AddCommentForm
      projectId="m1"
      entityType="TASK"
      entityId="t1"
      orgSlug="acme"
      canManageVisibility={false}
      mentionCandidates={candidates}
    />
  );
}

function textarea() {
  return screen.getByLabelText("Add a comment") as HTMLTextAreaElement;
}

describe("@-mention autocomplete (634C)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchComments.mockResolvedValue([]);
    mockFetchMentionCandidates.mockResolvedValue(CANDIDATES);
  });

  afterEach(() => {
    cleanup();
  });

  it("typing @Bo and picking Bob Ndlovu inserts '@Bob Ndlovu '", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "Please review @Bo");
    await user.click(await screen.findByRole("option", { name: "Bob Ndlovu" }));

    await waitFor(() => expect(textarea().value).toBe("Please review @Bob Ndlovu "));
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("filters by the typed prefix", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");

    expect(await screen.findByRole("option", { name: "Bob Ndlovu" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Bongani Dlamini" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Alice Owner" })).not.toBeInTheDocument();
  });

  it("is keyboard accessible: ArrowDown moves, Enter inserts", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");
    await screen.findByRole("option", { name: "Bob Ndlovu" });
    await user.keyboard("{ArrowDown}{Enter}");

    await waitFor(() => expect(textarea().value).toBe("@Bongani Dlamini "));
  });

  it("Escape closes the suggestions and keeps the text", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "cc @Ca");
    await screen.findByRole("option", { name: "Carol Mokoena" });
    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("option")).not.toBeInTheDocument());
    expect(textarea().value).toBe("cc @Ca");
  });

  it("does not open inside an email address", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "mail bob@Bo");

    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("offers no suggestions without candidates", async () => {
    const user = userEvent.setup();
    renderForm([]);

    await user.type(textarea(), "@Bo");

    expect(screen.queryByRole("option")).not.toBeInTheDocument();
    expect(textarea().value).toBe("@Bo");
  });

  it("CommentSectionClient fetches the matter's candidates and leaves out the current member", async () => {
    const user = userEvent.setup();
    render(
      <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
        <CommentSectionClient
          projectId="m1"
          entityType="TASK"
          entityId="t1"
          orgSlug="acme"
          currentMemberId="m-bob"
          canManageVisibility={false}
        />
      </SWRConfig>
    );

    await user.type(await screen.findByLabelText("Add a comment"), "@Bo");

    expect(await screen.findByRole("option", { name: "Bongani Dlamini" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Bob Ndlovu" })).not.toBeInTheDocument();
    expect(mockFetchMentionCandidates).toHaveBeenCalledWith("m1");
  });
});

describe("@-mention combobox semantics and keyboard edge cases (634C review)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("is a combobox named by its label whose aria-expanded follows the list", async () => {
    const user = userEvent.setup();
    renderForm();

    const input = screen.getByRole("combobox", { name: "Add a comment" });
    expect(input).toBe(textarea());
    expect(input).toHaveAttribute("aria-expanded", "false");
    expect(input).toHaveAttribute("aria-haspopup", "listbox");
    expect(input).toHaveAttribute("aria-autocomplete", "list");

    await user.type(input, "@Bo");
    const listbox = await screen.findByRole("listbox");
    expect(input).toHaveAttribute("aria-expanded", "true");
    expect(input).toHaveAttribute("aria-controls", listbox.id);

    await user.keyboard("{Escape}");
    await waitFor(() => expect(input).toHaveAttribute("aria-expanded", "false"));
  });

  it("aria-activedescendant follows the arrow keys", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");
    const bob = await screen.findByRole("option", { name: "Bob Ndlovu" });
    const bongani = screen.getByRole("option", { name: "Bongani Dlamini" });
    expect(bob.id).toBeTruthy();
    expect(bongani.id).toBeTruthy();
    expect(textarea()).toHaveAttribute("aria-activedescendant", bob.id);
    expect(bob).toHaveAttribute("aria-selected", "true");

    await user.keyboard("{ArrowDown}");
    expect(textarea()).toHaveAttribute("aria-activedescendant", bongani.id);
    expect(bongani).toHaveAttribute("aria-selected", "true");
    expect(bob).toHaveAttribute("aria-selected", "false");

    await user.keyboard("{ArrowUp}");
    expect(textarea()).toHaveAttribute("aria-activedescendant", bob.id);
  });

  it("announces the suggestion count politely", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");
    await screen.findByRole("option", { name: "Bob Ndlovu" });
    expect(screen.getByText("2 suggestions")).toHaveAttribute("aria-live", "polite");

    await user.type(textarea(), "zz");
    expect(await screen.findByText("No matches")).toHaveAttribute("aria-live", "polite");
  });

  it("an Enter during IME composition does not select", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");
    await screen.findByRole("option", { name: "Bob Ndlovu" });
    fireEvent.keyDown(textarea(), { key: "Enter", isComposing: true });

    expect(textarea().value).toBe("@Bo");
    expect(screen.getByRole("option", { name: "Bob Ndlovu" })).toBeInTheDocument();
  });

  it("Shift+Tab is not intercepted while the list is open", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(textarea(), "@Bo");
    await screen.findByRole("option", { name: "Bob Ndlovu" });
    const notPrevented = fireEvent.keyDown(textarea(), { key: "Tab", shiftKey: true });

    expect(notPrevented).toBe(true);
    expect(textarea().value).toBe("@Bo");
  });
});

describe("mention helpers", () => {
  it("findMentionQuery reads the token before the cursor", () => {
    expect(findMentionQuery("hi @Bo", 6)).toEqual({ query: "Bo", start: 3 });
    expect(findMentionQuery("@", 1)).toEqual({ query: "", start: 0 });
    expect(findMentionQuery("(@Zoë", 5)).toEqual({ query: "Zoë", start: 1 });
    expect(findMentionQuery("a@b", 3)).toBeNull();
    expect(findMentionQuery("@Bob Nd", 7)).toBeNull();
  });

  it("insertMention replaces the token and puts the cursor after the space", () => {
    expect(insertMention("hi @Bo there", 3, 6, "Bob Ndlovu")).toEqual({
      text: "hi @Bob Ndlovu  there",
      cursor: 15,
    });
  });

  it("filterMentionCandidates matches any word of the name", () => {
    expect(filterMentionCandidates(CANDIDATES, "dl").map((c) => c.id)).toEqual(["m-bongani"]);
    expect(filterMentionCandidates(CANDIDATES, "BO").map((c) => c.id)).toEqual([
      "m-bob",
      "m-bongani",
    ]);
    expect(filterMentionCandidates(CANDIDATES, "")).toHaveLength(4);
  });
});
