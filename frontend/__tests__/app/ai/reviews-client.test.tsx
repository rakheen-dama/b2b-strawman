import fs from "node:fs";
import path from "node:path";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/app/(app)/org/[slug]/ai/reviews/actions", () => ({
  approveGateAction: vi.fn(),
  rejectGateAction: vi.fn(),
}));

import { AiReviewsClient } from "@/app/(app)/org/[slug]/ai/reviews/reviews-client";

const SOURCE = path.resolve(
  __dirname,
  "../../../app/(app)/org/[slug]/ai/reviews/reviews-client.tsx"
);

describe("AiReviewsClient empty states (Phase 91 663A)", () => {
  afterEach(() => cleanup());

  it("renders the shared default EmptyState on the empty Pending tab", () => {
    const { container } = render(
      <AiReviewsClient slug="acme" pendingGates={[]} historyGates={[]} />
    );

    const empty = container.querySelector('[data-slot="empty-state"]');
    expect(empty).toHaveAttribute("data-variant", "default");
    expect(container.querySelector('[data-slot="empty-state-icon"]')).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "No pending reviews" })
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "All AI-proposed actions have been reviewed. New proposals will appear here when AI skills request approval."
      )
    ).toBeInTheDocument();
    expect(container.querySelector(".border-dashed")).toBeNull();
  });

  it("renders the shared default EmptyState on the empty History tab", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <AiReviewsClient slug="acme" pendingGates={[]} historyGates={[]} />
    );

    await user.click(screen.getByRole("tab", { name: "History" }));

    expect(
      screen.getByRole("heading", { level: 2, name: "No review history" })
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Previously reviewed AI actions will appear here once gates have been approved or rejected."
      )
    ).toBeInTheDocument();
    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "default"
    );
  });

  it("uses the shared component, not a local look-alike", () => {
    const src = fs.readFileSync(SOURCE, "utf8");
    expect(src).not.toMatch(/function EmptyState\b/);
  });
});
