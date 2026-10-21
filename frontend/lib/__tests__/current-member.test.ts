import { describe, it, expect } from "vitest";
import { findCurrentMemberId } from "@/lib/current-member";

// The signed-in user's org member, resolved by email — shared by My work and the Diary so the
// two can't drift (PR #1624 review: My work matched case-sensitively, the Diary didn't).

const MEMBERS = [
  { id: "m-alice", email: "Alice@Example.com" },
  { id: "m-bob", email: "bob@example.com" },
];

describe("findCurrentMemberId", () => {
  it("matches the member whose email equals the session email, ignoring case", () => {
    expect(findCurrentMemberId("alice@example.com", MEMBERS)).toBe("m-alice");
    expect(findCurrentMemberId("BOB@EXAMPLE.COM", MEMBERS)).toBe("m-bob");
  });

  it("returns null when no member has the session email", () => {
    expect(findCurrentMemberId("carol@example.com", MEMBERS)).toBeNull();
  });

  it("returns null when the session email is unknown", () => {
    expect(findCurrentMemberId(null, MEMBERS)).toBeNull();
    expect(findCurrentMemberId(undefined, MEMBERS)).toBeNull();
    expect(findCurrentMemberId("", MEMBERS)).toBeNull();
  });

  it("skips members without an email", () => {
    const members = [{ id: "m-x", email: null }, ...MEMBERS];
    expect(findCurrentMemberId("alice@example.com", members)).toBe("m-alice");
  });
});
