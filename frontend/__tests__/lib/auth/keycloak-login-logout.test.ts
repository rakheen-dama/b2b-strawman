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

    // Mock form submission
    const mockSubmit = vi.fn();
    const mockAppendChild = vi.fn();
    const mockCreateElement = vi.spyOn(document, "createElement");

    let capturedForm: any;
    mockCreateElement.mockImplementation((tag: string) => {
      if (tag === "form") {
        capturedForm = {
          method: "",
          action: "",
          appendChild: mockAppendChild,
          submit: mockSubmit,
        };
        return capturedForm as any;
      }
      if (tag === "input") {
        return { type: "", name: "", value: "" } as any;
      }
      return document.createElement(tag);
    });

    vi.spyOn(document.body, "appendChild").mockImplementation((node) => node);

    const { performKeycloakLogout } = await import(
      "@/components/auth/user-menu-bff"
    );

    await performKeycloakLogout();

    // Verify CSRF token was fetched from gateway
    expect(mockFetch).toHaveBeenCalledWith(
      "http://localhost:8443/bff/csrf",
      { credentials: "include" },
    );

    // Verify form was created with correct action and method
    expect(capturedForm.method).toBe("POST");
    expect(capturedForm.action).toBe("http://localhost:8443/logout");

    // Verify form was submitted
    expect(mockSubmit).toHaveBeenCalled();

    mockCreateElement.mockRestore();
  });
});
