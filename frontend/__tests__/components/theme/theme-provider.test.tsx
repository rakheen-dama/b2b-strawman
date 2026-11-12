import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

const { captured, nav } = vi.hoisted(() => ({
  captured: [] as Array<Record<string, unknown>>,
  nav: { pathname: "/org/acme/dashboard" as string | null },
}));

vi.mock("next-themes", () => ({
  ThemeProvider: ({
    children,
    ...props
  }: { children: React.ReactNode } & Record<string, unknown>) => {
    captured.push(props);
    return <>{children}</>;
  },
}));

vi.mock("next/navigation", () => ({ usePathname: () => nav.pathname }));

import { KaziThemeProvider, isThemedRoute } from "@/components/theme/theme-provider";

const lastProps = () => captured[captured.length - 1];

describe("KaziThemeProvider", () => {
  beforeEach(() => {
    captured.length = 0;
    nav.pathname = "/org/acme/dashboard";
  });

  afterEach(() => cleanup());

  it("configures next-themes: class strategy, light default, System, kazi-theme, no transitions", () => {
    render(
      <KaziThemeProvider>
        <span>provider child</span>
      </KaziThemeProvider>
    );
    expect(screen.getByText("provider child")).toBeInTheDocument();
    expect(lastProps()).toMatchObject({
      attribute: "class",
      defaultTheme: "light",
      enableSystem: true,
      storageKey: "kazi-theme",
      disableTransitionOnChange: true,
    });
  });

  it("does not force a theme under /org/", () => {
    nav.pathname = "/org/acme/dashboard";
    render(
      <KaziThemeProvider>
        <span />
      </KaziThemeProvider>
    );
    expect(lastProps().forcedTheme).toBeUndefined();
  });

  it.each(["/", "/dashboard", "/create-org", "/platform-admin", "/mock-login"])(
    "forces light on %s",
    (path) => {
      nav.pathname = path;
      render(
        <KaziThemeProvider>
          <span />
        </KaziThemeProvider>
      );
      expect(lastProps().forcedTheme).toBe("light");
    }
  );
});

describe("isThemedRoute", () => {
  it("is true only for paths under /org/", () => {
    expect(isThemedRoute("/org/acme/matters")).toBe(true);
    expect(isThemedRoute("/org")).toBe(false);
    expect(isThemedRoute("/organisation")).toBe(false);
    expect(isThemedRoute(null)).toBe(false);
  });
});
