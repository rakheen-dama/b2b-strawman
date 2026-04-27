import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  storeAuth,
  getAuth,
  clearAuth,
  isAuthenticated,
  getLastOrgId,
  type CustomerInfo,
} from "@/lib/auth";

// Mock jose
vi.mock("jose", () => ({
  decodeJwt: vi.fn(),
}));

import { decodeJwt } from "jose";
const mockDecodeJwt = vi.mocked(decodeJwt);

// Helper: build a mock JWT string (the actual decoding is mocked)
function buildMockJwt(): string {
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = btoa(JSON.stringify({ sub: "test" }));
  return `${header}.${body}.fake-signature`;
}

const mockCustomer: CustomerInfo = {
  id: "cust-123",
  name: "Test Customer",
  email: "test@example.com",
  orgId: "org_abc",
};

describe("auth", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("storeAuth saves JWT and customer to localStorage", () => {
    const jwt = buildMockJwt();
    storeAuth(jwt, mockCustomer);

    expect(localStorage.getItem("portal_jwt")).toBe(jwt);
    expect(localStorage.getItem("portal_customer")).toBe(
      JSON.stringify(mockCustomer),
    );
  });

  it("getAuth returns stored data when JWT is valid", () => {
    const jwt = buildMockJwt();
    // JWT expires in the future
    mockDecodeJwt.mockReturnValue({ exp: Math.floor(Date.now() / 1000) + 3600 } as ReturnType<typeof decodeJwt>);

    storeAuth(jwt, mockCustomer);
    const result = getAuth();

    expect(result).not.toBeNull();
    expect(result!.jwt).toBe(jwt);
    expect(result!.customer).toEqual(mockCustomer);
  });

  it("getAuth returns null when JWT is expired", () => {
    const jwt = buildMockJwt();
    // JWT expired in the past
    mockDecodeJwt.mockReturnValue({ exp: Math.floor(Date.now() / 1000) - 3600 } as ReturnType<typeof decodeJwt>);

    storeAuth(jwt, mockCustomer);
    const result = getAuth();

    expect(result).toBeNull();
    // Should also clear storage
    expect(localStorage.getItem("portal_jwt")).toBeNull();
  });

  it("clearAuth removes entries from localStorage", () => {
    const jwt = buildMockJwt();
    storeAuth(jwt, mockCustomer);

    clearAuth();

    expect(localStorage.getItem("portal_jwt")).toBeNull();
    expect(localStorage.getItem("portal_customer")).toBeNull();
  });

  it("isAuthenticated returns false when no JWT is stored", () => {
    expect(isAuthenticated()).toBe(false);
  });

  // GAP-L-66
  it("storeAuth persists portal_last_org_id", () => {
    const jwt = buildMockJwt();
    storeAuth(jwt, mockCustomer);

    expect(localStorage.getItem("portal_last_org_id")).toBe(mockCustomer.orgId);
  });

  // GAP-L-66
  it("clearAuth preserves portal_last_org_id", () => {
    const jwt = buildMockJwt();
    storeAuth(jwt, mockCustomer);

    clearAuth();

    expect(localStorage.getItem("portal_jwt")).toBeNull();
    expect(localStorage.getItem("portal_customer")).toBeNull();
    // last-known orgId is a routing hint, not a credential — must survive
    // clearAuth so /login can recover after session expiry.
    expect(localStorage.getItem("portal_last_org_id")).toBe(mockCustomer.orgId);
  });

  // GAP-L-66
  it("getLastOrgId returns persisted value", () => {
    const jwt = buildMockJwt();
    storeAuth(jwt, mockCustomer);

    expect(getLastOrgId()).toBe(mockCustomer.orgId);

    clearAuth();
    expect(getLastOrgId()).toBe(mockCustomer.orgId);
  });
});
