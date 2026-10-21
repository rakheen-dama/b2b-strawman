import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MatterFiltersSheet } from "@/components/matters/matter-filters-sheet";
import type { TagResponse } from "@/lib/types";

const mockPush = vi.fn();
const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => "/org/acme/matters",
}));

function tag(slug: string, name: string): TagResponse {
  return {
    id: `tag-${slug}`,
    name,
    slug,
    color: null,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
  };
}

const ALL_TAGS = [tag("urgent", "Urgent"), tag("vip", "VIP"), tag("pro-bono", "Pro bono")];

async function openSheet(user: ReturnType<typeof userEvent.setup>) {
  render(<MatterFiltersSheet allTags={ALL_TAGS} />);
  await user.click(screen.getByTestId("matter-filters-trigger"));
  await screen.findByText("Filter matters");
}

describe("MatterFiltersSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  afterEach(() => cleanup());

  it("shows the applied tag count on the trigger", () => {
    mockSearchParams = new URLSearchParams("tags=urgent,vip");
    render(<MatterFiltersSheet allTags={ALL_TAGS} />);
    expect(screen.getByTestId("matter-filters-trigger")).toHaveTextContent("Filters2");
  });

  it("Apply writes the selected tags to ?tags=, keeping other params", async () => {
    const user = userEvent.setup();
    mockSearchParams = new URLSearchParams("view=system%3Adormant&search=raf");
    await openSheet(user);

    await user.click(screen.getByRole("button", { name: "Add Tag" }));
    await user.click(await screen.findByRole("option", { name: "VIP" }));
    await user.click(screen.getByRole("button", { name: "Add Tag" }));
    await user.click(await screen.findByRole("option", { name: "Urgent" }));
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(mockPush).toHaveBeenCalledTimes(1);
    expect(mockPush).toHaveBeenCalledWith(
      "/org/acme/matters?view=system%3Adormant&search=raf&tags=vip%2Curgent"
    );
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("Apply starts from the applied tags (removing one rewrites the list)", async () => {
    const user = userEvent.setup();
    mockSearchParams = new URLSearchParams("tags=urgent,vip");
    await openSheet(user);

    await user.click(screen.getByRole("button", { name: "Remove VIP" }));
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters?tags=urgent");
  });

  it("Clear removes the tags param, keeping other params", async () => {
    const user = userEvent.setup();
    mockSearchParams = new URLSearchParams("tags=urgent,vip&status=CLOSED");
    await openSheet(user);

    await user.click(screen.getByRole("button", { name: "Clear" }));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters?status=CLOSED");
  });

  it("Clear with no other params writes the bare pathname", async () => {
    const user = userEvent.setup();
    mockSearchParams = new URLSearchParams("tags=urgent");
    await openSheet(user);

    await user.click(screen.getByRole("button", { name: "Clear" }));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters");
  });
});
