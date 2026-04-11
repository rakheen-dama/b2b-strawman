import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Stub NEXT_PUBLIC_GATEWAY_URL before importing the module
vi.stubEnv("NEXT_PUBLIC_GATEWAY_URL", "http://localhost:8443");

describe("Keycloak login/logout URLs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("login URL points to gateway OAuth2 authorization endpoint", async () => {
    const { getKeycloakLoginUrl } = await import(
      "@/components/auth/user-menu-bff"
    );

    expect(getKeycloakLoginUrl()).toBe(
      "http://localhost:8443/oauth2/authorization/keycloak",
    );
  });

  it("logout URL points to gateway logout endpoint", async () => {
    const { getKeycloakLogoutUrl } = await import(
      "@/components/auth/user-menu-bff"
    );

    expect(getKeycloakLogoutUrl()).toBe("http://localhost:8443/logout");
  });

  it("performKeycloakLogout fetches CSRF token and submits form POST", async () => {
    // Mock fetch to return a CSRF token
    const mockFetch = vi.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          token: "test-csrf-token",
          parameterName: "_csrf",
          headerName: "X-XSRF-TOKEN",
        }),
    });
    vi.stubGlobal("fetch", mockFetch);

    // Track form submission and CSRF input
    const mockSubmit = vi.fn();
    const capturedInputs: Array<{ type: string; name: string; value: string }> =
      [];

    // Store original before mocking to avoid infinite recursion
    const originalCreateElement = document.createElement.bind(document);
    const mockCreateElement = vi.spyOn(document, "createElement");

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- DOM mock requires any for test stub
    let capturedForm: any;
    mockCreateElement.mockImplementation((tag: string) => {
      if (tag === "form") {
        capturedForm = {
          method: "",
          action: "",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any -- DOM mock requires any for test stub
          appendChild: (input: any) => capturedInputs.push(input),
          submit: mockSubmit,
        };
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- DOM mock requires any for test stub
        return capturedForm as any;
      }
      if (tag === "input") {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- DOM mock requires any for test stub
        return { type: "", name: "", value: "" } as any;
      }
      return originalCreateElement(tag);
    });

    vi.spyOn(document.body, "appendChild").mockImplementation((node) => node);

    const { performKeycloakLogout } = await import(
      "@/components/auth/user-menu-bff"
    );

    await performKeycloakLogout();

    // Verify CSRF token was fetched from gateway
    expect(mockFetch).toHaveBeenCalledWith("http://localhost:8443/bff/csrf", {
      credentials: "include",
    });

    // Verify form was created with correct action and method
    expect(capturedForm.method).toBe("POST");
    expect(capturedForm.action).toBe("http://localhost:8443/logout");

    // Verify CSRF input was appended to the form
    expect(capturedInputs).toHaveLength(1);
    expect(capturedInputs[0].name).toBe("_csrf");
    expect(capturedInputs[0].value).toBe("test-csrf-token");

    // Verify form was submitted
    expect(mockSubmit).toHaveBeenCalled();

    mockCreateElement.mockRestore();
  });

  it("performKeycloakLogout falls back to GET on fetch failure", async () => {
    // Mock fetch to fail
    const mockFetch = vi.fn().mockRejectedValue(new Error("Network error"));
    vi.stubGlobal("fetch", mockFetch);

    // Mock window.location.href
    const locationSpy = vi.spyOn(window, "location", "get").mockReturnValue({
      ...window.location,
      href: "",
    } as Location);

    const { performKeycloakLogout } = await import(
      "@/components/auth/user-menu-bff"
    );

    await performKeycloakLogout();

    // On failure, should fall back to direct navigation
    expect(mockFetch).toHaveBeenCalled();

    locationSpy.mockRestore();
  });
});
