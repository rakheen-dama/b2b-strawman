import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderToString } from "react-dom/server";

const { mockSetTheme, themeState } = vi.hoisted(() => ({
  mockSetTheme: vi.fn(),
  themeState: {
    theme: "light" as string | undefined,
    resolvedTheme: "light" as string | undefined,
  },
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({
    ...themeState,
    setTheme: mockSetTheme,
    themes: ["light", "dark", "system"],
  }),
}));

import { ThemeToggle } from "@/components/theme/theme-toggle";

describe("ThemeToggle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    themeState.theme = "light";
    themeState.resolvedTheme = "light";
  });

  afterEach(() => cleanup());

  it("server-renders a placeholder, not the control (the stored theme is unknown before mount)", () => {
    const html = renderToString(<ThemeToggle />);
    expect(html).toContain('data-slot="theme-toggle-placeholder"');
    expect(html).toContain('aria-hidden="true"');
    expect(html).not.toContain('role="radio"');
    expect(html).not.toContain("Light");
  });

  it("renders a group named Theme with Light, Dark and System once mounted", () => {
    const { container } = render(<ThemeToggle />);
    expect(screen.getByRole("group", { name: "Theme" })).toBeInTheDocument();
    expect(screen.getAllByRole("radio").map((r) => r.textContent)).toEqual([
      "Light",
      "Dark",
      "System",
    ]);
    expect(container.querySelector('[data-slot="theme-toggle-placeholder"]')).toBeNull();
  });

  it("checks the stored choice", () => {
    themeState.theme = "dark";
    themeState.resolvedTheme = "dark";
    render(<ThemeToggle />);
    const dark = screen.getByRole("radio", { name: "Dark" });
    expect(dark).toHaveAttribute("aria-checked", "true");
    expect(dark).toHaveAttribute("data-state", "on");
    expect(screen.getByRole("radio", { name: "Light" })).toHaveAttribute("aria-checked", "false");
  });

  it("keeps System checked while it resolves to dark", () => {
    themeState.theme = "system";
    themeState.resolvedTheme = "dark";
    render(<ThemeToggle />);
    expect(screen.getByRole("radio", { name: "System" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "Dark" })).toHaveAttribute("aria-checked", "false");
  });

  it("calls setTheme('dark') when Dark is chosen", async () => {
    render(<ThemeToggle />);
    await userEvent.setup().click(screen.getByRole("radio", { name: "Dark" }));
    expect(mockSetTheme).toHaveBeenCalledTimes(1);
    expect(mockSetTheme).toHaveBeenCalledWith("dark");
  });

  it("ignores a click on the already-active option (never sets an empty theme)", async () => {
    render(<ThemeToggle />);
    await userEvent.setup().click(screen.getByRole("radio", { name: "Light" }));
    expect(mockSetTheme).not.toHaveBeenCalled();
  });
});
