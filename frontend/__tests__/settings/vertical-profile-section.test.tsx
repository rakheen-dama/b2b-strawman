import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock swr FIRST (before component import)
vi.mock("swr", () => ({ default: vi.fn() }));

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}));

// Mock profile-actions
const mockUpdateVerticalProfile = vi.fn();
const mockFetchProfiles = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/settings/general/profile-actions",
  () => ({
    updateVerticalProfile: (...args: unknown[]) =>
      mockUpdateVerticalProfile(...args),
    fetchProfiles: (...args: unknown[]) => mockFetchProfiles(...args),
  }),
);

vi.mock("motion/react", () => ({
  motion: {
    div: ({
      children,
      ...props
    }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

import useSWR from "swr";
import { VerticalProfileSection } from "@/components/settings/vertical-profile-section";

const MOCK_PROFILES = [
  {
    id: "legal-za",
    name: "Legal (ZA)",
    description: "Law firm profile",
    modules: ["trust_accounting"],
  },
  {
    id: "consulting-generic",
    name: "Consulting (Generic)",
    description: "Generic",
    modules: [],
  },
];

function mockSWRLoaded() {
  vi.mocked(useSWR).mockReturnValue({
    data: MOCK_PROFILES,
    error: undefined,
    isLoading: false,
    mutate: vi.fn(),
  } as ReturnType<typeof useSWR>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("VerticalProfileSection — owner view", () => {
  it("renders dropdown and Apply Profile button for owner", () => {
    mockSWRLoaded();
    render(
      <VerticalProfileSection
        slug="acme"
        currentProfile={null}
        isOwner={true}
      />,
    );
    // Select trigger is present (owner sees dropdown)
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    // Apply Profile button is present
    expect(
      screen.getByRole("button", { name: /Apply Profile/i }),
    ).toBeInTheDocument();
  });

  it("shows read-only text for non-owner", () => {
    render(
      <VerticalProfileSection
        slug="acme"
        currentProfile="legal-za"
        isOwner={false}
      />,
    );
    // No combobox/dropdown
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    // No Apply Profile button
    expect(
      screen.queryByRole("button", { name: /Apply Profile/i }),
    ).not.toBeInTheDocument();
    // Profile name shown
    expect(screen.getByText("legal-za")).toBeInTheDocument();
  });
});

describe("VerticalProfileSection — confirmation dialog", () => {
  it("confirmation dialog appears after selecting profile and clicking Apply Profile", async () => {
    const user = userEvent.setup();
    mockSWRLoaded();
    render(
      <VerticalProfileSection
        slug="acme"
        currentProfile={null}
        isOwner={true}
      />,
    );

    // Select a profile from the dropdown
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("Legal (ZA)"));

    // Click Apply Profile
    await user.click(
      screen.getByRole("button", { name: /Apply Profile/i }),
    );

    // Dialog warning text should appear
    expect(
      screen.getByText(
        /Changing your vertical profile will add new field definitions/i,
      ),
    ).toBeInTheDocument();
  });

  it("dialog cancel does not call updateVerticalProfile", async () => {
    const user = userEvent.setup();
    mockSWRLoaded();
    render(
      <VerticalProfileSection
        slug="acme"
        currentProfile={null}
        isOwner={true}
      />,
    );

    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("Legal (ZA)"));

    await user.click(
      screen.getByRole("button", { name: /Apply Profile/i }),
    );

    // Click Cancel in the dialog
    await user.click(screen.getByRole("button", { name: /Cancel/i }));

    expect(mockUpdateVerticalProfile).not.toHaveBeenCalled();
  });

  it("calls updateVerticalProfile on confirm", async () => {
    const user = userEvent.setup();
    mockSWRLoaded();
    mockUpdateVerticalProfile.mockResolvedValue({ success: true });
    render(
      <VerticalProfileSection
        slug="acme"
        currentProfile={null}
        isOwner={true}
      />,
    );

    // Select a profile from the dropdown
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);
    await user.click(screen.getByText("Legal (ZA)"));

    // Click Apply Profile to open dialog
    await user.click(
      screen.getByRole("button", { name: /Apply Profile/i }),
    );

    // Click Confirm in the dialog
    await user.click(screen.getByRole("button", { name: /Confirm/i }));

    expect(mockUpdateVerticalProfile).toHaveBeenCalledWith(
      "acme",
      "legal-za",
    );
  });
});
