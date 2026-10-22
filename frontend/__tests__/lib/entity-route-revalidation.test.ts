import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 610E (D-2): tag and custom-field edits revalidate the edited entity's pages. The route
// segment was derived as `entityType.toLowerCase() + "s"`, which gives the dead /projects and
// /customers routes since 610D. PROJECT → /matters, CUSTOMER → /clients; other types unchanged.

vi.mock("server-only", () => ({}));

const { mockRevalidatePath, apiMock } = vi.hoisted(() => ({
  mockRevalidatePath: vi.fn(),
  apiMock: {
    get: vi.fn().mockResolvedValue({}),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("next/cache", () => ({ revalidatePath: mockRevalidatePath }));
vi.mock("@/lib/api", () => ({
  api: apiMock,
  ApiError: class ApiError extends Error {},
  entityApiPrefix: (entityType: string) => `wire-${entityType.toLowerCase()}`,
  setEntityFieldGroups: vi.fn().mockResolvedValue(undefined),
}));

import { setEntityTagsAction } from "@/app/(app)/org/[slug]/settings/matters/_tabs/tags-actions";
import {
  updateEntityCustomFieldsAction,
  setEntityFieldGroupsAction,
} from "@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields-actions";
import type { EntityType } from "@/lib/types";

const CASES: [EntityType, string][] = [
  ["PROJECT", "matters"],
  ["CUSTOMER", "clients"],
  ["TASK", "tasks"],
  ["INVOICE", "invoices"],
];

describe.each(CASES)("%s edits revalidate /org/acme/%s", (entityType, segment) => {
  beforeEach(() => mockRevalidatePath.mockClear());

  const expectRevalidated = () => {
    expect(mockRevalidatePath).toHaveBeenCalledWith(`/org/acme/${segment}/e-1`);
    expect(mockRevalidatePath).toHaveBeenCalledWith(`/org/acme/${segment}`);
    expect(mockRevalidatePath).toHaveBeenCalledTimes(2);
  };

  it("setEntityTagsAction", async () => {
    await expect(setEntityTagsAction("acme", entityType, "e-1", ["t-1"])).resolves.toEqual({
      success: true,
    });
    expectRevalidated();
  });

  it("updateEntityCustomFieldsAction", async () => {
    await expect(
      updateEntityCustomFieldsAction("acme", entityType, "e-1", { field: "v" })
    ).resolves.toEqual({ success: true });
    expectRevalidated();
  });

  it("setEntityFieldGroupsAction", async () => {
    await expect(setEntityFieldGroupsAction("acme", entityType, "e-1", ["g-1"])).resolves.toEqual({
      success: true,
    });
    expectRevalidated();
  });
});
