import { describe, it, expect, vi, afterEach, afterAll } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

// AUTH_MODE is read at module load, so the env must be set before the static import below runs.
const { originalAuthMode } = vi.hoisted(() => {
  const original = process.env.NEXT_PUBLIC_AUTH_MODE;
  process.env.NEXT_PUBLIC_AUTH_MODE = "mock";
  return { originalAuthMode: original };
});

vi.mock("@/lib/auth/client/mock-context", () => ({
  useMockAuthContext: () => ({
    authUser: {
      firstName: "Alice",
      lastName: "Owner",
      email: "alice@example.com",
      imageUrl: null,
    },
    isLoaded: true,
    orgSlug: "e2e-test-org",
    token: null,
  }),
}));

vi.mock("@/lib/auth/client", () => ({ useSignOut: () => ({ signOut: vi.fn() }) }));

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "light", resolvedTheme: "light", setTheme: vi.fn(), themes: [] }),
}));

import { AuthHeaderControls } from "@/components/auth-header-controls";

afterEach(() => cleanup());

afterAll(() => {
  if (originalAuthMode === undefined) delete process.env.NEXT_PUBLIC_AUTH_MODE;
  else process.env.NEXT_PUBLIC_AUTH_MODE = originalAuthMode;
});

describe("AuthHeaderControls (mock auth) — MockUserButton menu", () => {
  it("shows a Theme section with the toggle directly above Sign out", () => {
    render(<AuthHeaderControls />);
    fireEvent.click(screen.getByText("AO"));

    expect(screen.getByText("Theme")).toBeInTheDocument();
    const group = screen.getByRole("group", { name: "Theme" });
    expect(screen.getAllByRole("radio").map((r) => r.textContent)).toEqual([
      "Light",
      "Dark",
      "System",
    ]);
    const signOut = screen.getByRole("button", { name: "Sign out" });
    expect(group.compareDocumentPosition(signOut) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("keeps the existing name, email and Sign out entries", () => {
    render(<AuthHeaderControls />);
    fireEvent.click(screen.getByText("AO"));

    expect(screen.getByText("Alice Owner")).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign out" })).toBeInTheDocument();
  });
});
