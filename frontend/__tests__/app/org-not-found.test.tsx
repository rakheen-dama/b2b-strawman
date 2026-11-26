import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useParams: () => ({ slug: "acme" }),
}));

import OrgNotFound from "@/app/(app)/org/[slug]/not-found";

// K-63 (663C): notFound() anywhere under /org/[slug] (e.g. a member's deep link to a Fees or
// Compliance page, or a handleApiError 404) renders this in-shell page — one h1 — instead of the
// org-layout ErrorBoundary's "Something went wrong" panel.

afterEach(() => cleanup());

describe("org/[slug]/not-found", () => {
  it("renders one h1, the approved copy and no crash panel", () => {
    render(<OrgNotFound />);

    expect(screen.getByTestId("org-not-found")).toBeInTheDocument();
    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Page not found");
    expect(
      screen.getByText("This page doesn't exist, or you don't have access to it.")
    ).toBeInTheDocument();
    expect(screen.queryByText("Something went wrong")).not.toBeInTheDocument();
  });

  it("links back to the org's Home", () => {
    render(<OrgNotFound />);

    expect(screen.getByRole("link", { name: "Back to Home" })).toHaveAttribute(
      "href",
      "/org/acme/dashboard"
    );
  });
});
