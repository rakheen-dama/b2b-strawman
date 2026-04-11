import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}));

// Mock server actions
const mockUpdateDataProtection = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/data-protection/actions", () => ({
  updateDataProtectionSettings: (...args: unknown[]) => mockUpdateDataProtection(...args),
}));

vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

import { JurisdictionSelectorSection } from "@/components/settings/jurisdiction-selector";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("JurisdictionSelectorSection", () => {
  it("renders jurisdiction selector with South Africa option", () => {
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction={null} />);
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(screen.getByText("Jurisdiction")).toBeInTheDocument();
  });

  it("shows confirmation dialog when selecting jurisdiction with none set", async () => {
    const user = userEvent.setup();
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction={null} />);
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("South Africa (POPIA)"));
    expect(
      screen.getByText(/Setting your jurisdiction will create default retention policies/i)
    ).toBeInTheDocument();
  });

  it("calls updateDataProtectionSettings on confirm", async () => {
    const user = userEvent.setup();
    mockUpdateDataProtection.mockResolvedValue({ success: true });
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction={null} />);
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("South Africa (POPIA)"));
    await user.click(screen.getByRole("button", { name: /Confirm/i }));
    expect(mockUpdateDataProtection).toHaveBeenCalledWith("acme", {
      dataProtectionJurisdiction: "ZA",
    });
  });

  it("clicking cancel does NOT call updateDataProtectionSettings", async () => {
    const user = userEvent.setup();
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction={null} />);
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("South Africa (POPIA)"));
    await user.click(screen.getByRole("button", { name: /Cancel/i }));
    expect(mockUpdateDataProtection).not.toHaveBeenCalled();
  });
});
