import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 610E (D-1): the saved-view server actions revalidate the list page they belong to.
// 610D moved the routes to /matters and /clients, so revalidating /projects or /customers is a
// silent no-op (no page there any more). Pin the canonical paths.

vi.mock("server-only", () => ({}));

const { mockRevalidatePath, apiMock } = vi.hoisted(() => ({
  mockRevalidatePath: vi.fn(),
  apiMock: {
    get: vi.fn(),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    patch: vi.fn(),
    delete: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("next/cache", () => ({ revalidatePath: mockRevalidatePath }));
vi.mock("@/lib/api", () => ({ api: apiMock, ApiError: class ApiError extends Error {} }));

import * as matterListViews from "@/app/(app)/org/[slug]/matters/view-actions";
import * as matterDetailViews from "@/app/(app)/org/[slug]/matters/[id]/view-actions";
import * as clientListViews from "@/app/(app)/org/[slug]/clients/view-actions";
import type { CreateSavedViewRequest, UpdateSavedViewRequest } from "@/lib/types";

const createReq = {} as CreateSavedViewRequest;
const updateReq = {} as UpdateSavedViewRequest;

describe.each([
  ["matters/view-actions", matterListViews, "/org/acme/matters"],
  ["matters/[id]/view-actions", matterDetailViews, "/org/acme/matters"],
  ["clients/view-actions", clientListViews, "/org/acme/clients"],
])("%s revalidates the renamed route", (_name, actions, expectedPath) => {
  beforeEach(() => mockRevalidatePath.mockClear());

  it("createSavedViewAction", async () => {
    await expect(actions.createSavedViewAction("acme", createReq)).resolves.toEqual({
      success: true,
    });
    expect(mockRevalidatePath).toHaveBeenCalledWith(expectedPath);
  });

  it("updateSavedViewAction", async () => {
    await actions.updateSavedViewAction("acme", "v-1", updateReq);
    expect(mockRevalidatePath).toHaveBeenCalledWith(expectedPath);
  });

  it("deleteSavedViewAction", async () => {
    await actions.deleteSavedViewAction("acme", "v-1");
    expect(mockRevalidatePath).toHaveBeenCalledWith(expectedPath);
  });
});
