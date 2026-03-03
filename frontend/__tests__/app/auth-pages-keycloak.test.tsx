import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRedirect = vi.fn();
vi.mock("next/navigation", () => ({
  redirect: mockRedirect,
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Mock next-auth/react
const mockSignIn = vi.fn().mockResolvedValue(undefined);
vi.mock("next-auth/react", () => ({
  signIn: mockSignIn,
  useSession: () => ({ data: null, status: "unauthenticated" }),
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock @clerk/nextjs to prevent import errors
vi.mock("@clerk/nextjs", () => ({
  SignIn: () => <div data-testid="clerk-sign-in" />,
  SignUp: () => <div data-testid="clerk-sign-up" />,
  CreateOrganization: () => <div data-testid="clerk-create-org" />,
}));

// Mock mock-context
vi.mock("@/lib/auth/client/mock-context", () => ({
  useMockAuthContext: () => {
    throw new Error("Not in mock mode");
  },
}));

describe("Auth pages — Keycloak mode", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");
  });

  afterEach(() => {
    cleanup();
  });

  it("sign-in page triggers keycloak signIn on mount", async () => {
    const { KeycloakSignIn } = await import(
      "@/components/auth/keycloak-sign-in"
    );

    render(<KeycloakSignIn />);

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith("keycloak", {
        callbackUrl: "/dashboard",
      });
    });
  });

  it("sign-up page redirects to /create-org in keycloak mode", async () => {
    // Since sign-up is a server component that calls redirect(),
    // we test the redirect call directly
    const SignUpPage = (
      await import("@/app/(auth)/sign-up/[[...sign-up]]/page")
    ).default;

    // redirect() in vitest mock doesn't throw, so we can just call the component
    SignUpPage();

    expect(mockRedirect).toHaveBeenCalledWith("/create-org");
  });

  it("create-org form renders and submits org name", async () => {
    const { KeycloakCreateOrgForm } = await import(
      "@/components/auth/keycloak-create-org-form"
    );

    const mockCreateOrg = vi.fn().mockResolvedValue({
      slug: "test-org",
      orgId: "org-123",
    });

    const user = userEvent.setup();
    render(<KeycloakCreateOrgForm createOrgAction={mockCreateOrg} />);

    const input = screen.getByLabelText(/organization name/i);
    await user.type(input, "Test Org");

    const submitBtn = screen.getByRole("button", {
      name: /create organization/i,
    });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(mockCreateOrg).toHaveBeenCalledWith("Test Org");
    });

    // After successful creation, should trigger re-auth
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith("keycloak", {
        callbackUrl: "/org/test-org/dashboard",
      });
    });
  });
});
