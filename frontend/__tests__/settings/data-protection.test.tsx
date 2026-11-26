import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}));

// Mock server actions
const mockUpdateDataProtection = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection-actions", () => ({
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
import { InformationOfficerSection } from "@/components/settings/information-officer-section";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("JurisdictionSelectorSection", () => {
  it("renders jurisdiction selector with South Africa option", () => {
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction={null} />);
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(screen.getByText("Jurisdiction")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Jurisdiction" })).toBeInTheDocument();
  });

  it("an existing jurisdiction saves instantly on change, without the dialog", async () => {
    const user = userEvent.setup();
    mockUpdateDataProtection.mockResolvedValue({ success: true });
    render(<JurisdictionSelectorSection slug="acme" currentJurisdiction="EU" />);
    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByText("South Africa (POPIA)"));
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(mockUpdateDataProtection).toHaveBeenCalledWith("acme", {
      dataProtectionJurisdiction: "ZA",
    });
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

describe("InformationOfficerSection", () => {
  it("renders an h2 section with a footer Save, disabled until a field changes", async () => {
    const user = userEvent.setup();
    render(<InformationOfficerSection slug="acme" initialName="Jane" initialEmail={null} />);
    expect(
      screen.getByRole("heading", { level: 2, name: "Information Officer" })
    ).toBeInTheDocument();
    const save = screen.getByRole("button", { name: "Save" });
    expect(save).toBeDisabled();
    await user.type(screen.getByLabelText("Email"), "privacy@acme.co.za");
    expect(save).toBeEnabled();
  });

  it("Save calls updateDataProtectionSettings with the officer fields and shows the saved line", async () => {
    const user = userEvent.setup();
    mockUpdateDataProtection.mockResolvedValue({ success: true });
    render(<InformationOfficerSection slug="acme" initialName={null} initialEmail={null} />);
    await user.type(screen.getByLabelText("Name"), "Jane Smith");
    await user.type(screen.getByLabelText("Email"), "privacy@acme.co.za");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mockUpdateDataProtection).toHaveBeenCalledWith("acme", {
      informationOfficerName: "Jane Smith",
      informationOfficerEmail: "privacy@acme.co.za",
    });
    await waitFor(() => expect(screen.getByText("Saved successfully.")).toBeInTheDocument());
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("a failed save shows the error line and no saved line", async () => {
    const user = userEvent.setup();
    mockUpdateDataProtection.mockResolvedValue({ success: false, error: "Nope." });
    render(<InformationOfficerSection slug="acme" initialName={null} initialEmail={null} />);
    await user.type(screen.getByLabelText("Name"), "Jane Smith");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.getByText("Nope.")).toBeInTheDocument());
    expect(screen.queryByText("Saved successfully.")).toBeNull();
  });
});
