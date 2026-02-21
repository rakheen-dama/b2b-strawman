import { describe, it, expect } from "vitest";
import type { AuthContext, AuthUser, OrgMemberInfo } from "@/lib/auth/types";

describe("Auth types — compile-time type assertions", () => {
  it("AuthContext has orgId, orgSlug, orgRole, userId fields", () => {
    const ctx = {
      orgId: "org_123",
      orgSlug: "acme",
      orgRole: "org:admin",
      userId: "user_456",
    } satisfies AuthContext;

    expect(ctx.orgId).toBe("org_123");
    expect(ctx.orgSlug).toBe("acme");
    expect(ctx.orgRole).toBe("org:admin");
    expect(ctx.userId).toBe("user_456");
  });

  it("AuthUser allows null for firstName, lastName, and imageUrl", () => {
    const user = {
      firstName: null,
      lastName: null,
      email: "alice@example.com",
      imageUrl: null,
    } satisfies AuthUser;

    expect(user.firstName).toBeNull();
    expect(user.lastName).toBeNull();
    expect(user.email).toBe("alice@example.com");
    expect(user.imageUrl).toBeNull();

    // Also verify non-null values compile
    const userWithName = {
      firstName: "Alice",
      lastName: "Smith",
      email: "alice@example.com",
      imageUrl: "https://img.example.com/alice.jpg",
    } satisfies AuthUser;

    expect(userWithName.firstName).toBe("Alice");
  });

  it("OrgMemberInfo has id, role, email, name fields", () => {
    const member = {
      id: "member_789",
      role: "admin",
      email: "bob@example.com",
      name: "Bob Jones",
    } satisfies OrgMemberInfo;

    expect(member.id).toBe("member_789");
    expect(member.role).toBe("admin");
    expect(member.email).toBe("bob@example.com");
    expect(member.name).toBe("Bob Jones");
  });
});
