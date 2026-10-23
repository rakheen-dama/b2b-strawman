import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useParams: () => ({ slug: "acme", id: "m-1" }),
}));

import MatterNotFound from "@/app/(app)/org/[slug]/matters/[id]/not-found";

// Phase 88 OQ #4 / ADR-346: a matter the viewer is not on (GET /api/matters/{id} → 404 →
// notFound()) renders this page, not the org-layout ErrorBoundary's "Something went wrong".

afterEach(() => cleanup());

describe("matters/[id]/not-found", () => {
  it("tells the viewer the matter isn't on their list", () => {
    render(<MatterNotFound />);

    expect(screen.getByTestId("matter-not-found")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "This matter isn't on your list" })
    ).toBeInTheDocument();
    expect(screen.queryByText("Something went wrong")).not.toBeInTheDocument();
  });

  it("links back to the org's matters list", () => {
    render(<MatterNotFound />);

    expect(screen.getByRole("link", { name: "Back to Matters" })).toHaveAttribute(
      "href",
      "/org/acme/matters"
    );
  });
});
