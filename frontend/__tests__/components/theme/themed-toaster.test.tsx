import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render } from "@testing-library/react";

const { themeState, toasterProps } = vi.hoisted(() => ({
  themeState: {
    forcedTheme: undefined as string | undefined,
    resolvedTheme: "light" as string | undefined,
  },
  toasterProps: { current: undefined as Record<string, unknown> | undefined },
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({ ...themeState, setTheme: vi.fn(), themes: ["light", "dark", "system"] }),
}));

vi.mock("sonner", () => ({
  Toaster: (props: Record<string, unknown>) => {
    toasterProps.current = props;
    return null;
  },
}));

import { ThemedToaster } from "@/components/theme/themed-toaster";

describe("ThemedToaster", () => {
  beforeEach(() => {
    themeState.forcedTheme = undefined;
    themeState.resolvedTheme = "light";
    toasterProps.current = undefined;
  });

  afterEach(() => cleanup());

  it("honours forcedTheme over a stored dark preference on forced-light routes", () => {
    themeState.forcedTheme = "light";
    themeState.resolvedTheme = "dark";
    render(<ThemedToaster />);
    expect(toasterProps.current?.theme).toBe("light");
  });

  it("follows the resolved theme when no theme is forced", () => {
    themeState.resolvedTheme = "dark";
    render(<ThemedToaster />);
    expect(toasterProps.current?.theme).toBe("dark");
  });

  it("falls back to light before mount when resolvedTheme is undefined", () => {
    themeState.resolvedTheme = undefined;
    render(<ThemedToaster />);
    expect(toasterProps.current?.theme).toBe("light");
  });
});
