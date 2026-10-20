import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// DormancyCheckSection imports a server action module.
vi.mock("@/app/(app)/org/[slug]/compliance/actions", () => ({
  runDormancyCheck: vi.fn(() => Promise.resolve({ success: true, candidates: [] })),
}));

import { DormancyCheckSection } from "@/components/compliance/DormancyCheckSection";

describe("DormancyCheckSection", () => {
  afterEach(() => cleanup());

  it("renders the 'Check for Dormant Clients' button", () => {
    render(<DormancyCheckSection orgSlug="test-org" />);
    expect(screen.getByRole("button", { name: "Check for Dormant Clients" })).toBeInTheDocument();
    expect(screen.queryByText("Check for Dormant Customers")).not.toBeInTheDocument();
  });
});
