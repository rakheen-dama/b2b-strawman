import React from "react";
import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import SignedOutPage from "@/app/(auth)/signed-out/page";

describe("SignedOutPage", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the signed-out confirmation copy", () => {
    render(<SignedOutPage />);
    // Shadcn CardTitle renders a <div>, not a heading element, so assert by text.
    expect(screen.getByText(/you've been signed out/i)).toBeTruthy();
  });

  it("offers a 'Sign in again' control that links to /sign-in", () => {
    render(<SignedOutPage />);
    const cta = screen.getByRole("link", { name: /sign in again/i });
    expect(cta.getAttribute("href")).toBe("/sign-in");
  });
});
