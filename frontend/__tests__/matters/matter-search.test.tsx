import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MatterSearch } from "@/components/matters/matter-search";

const mockReplace = vi.fn();
const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => "/org/acme/matters",
}));

function type(value: string) {
  fireEvent.change(screen.getByPlaceholderText("Search matters…"), { target: { value } });
}

describe("MatterSearch", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("writes ?search= only after the debounce, keeping other params", () => {
    mockSearchParams = new URLSearchParams("view=system%3Adormant&sort=name");
    render(<MatterSearch />);

    type("ra");
    act(() => vi.advanceTimersByTime(100));
    type("raf ");
    act(() => vi.advanceTimersByTime(299));
    expect(mockReplace).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(1));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith(
      "/org/acme/matters?view=system%3Adormant&sort=name&search=raf"
    );
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("removes the search param when the input is cleared", () => {
    mockSearchParams = new URLSearchParams("search=raf&status=CLOSED");
    render(<MatterSearch />);
    expect(screen.getByPlaceholderText("Search matters…")).toHaveValue("raf");

    type("");
    act(() => vi.advanceTimersByTime(300));

    expect(mockReplace).toHaveBeenCalledWith("/org/acme/matters?status=CLOSED");
  });

  it("clearing the only param writes the bare pathname", () => {
    mockSearchParams = new URLSearchParams("search=raf");
    render(<MatterSearch />);

    type("   ");
    act(() => vi.advanceTimersByTime(300));

    expect(mockReplace).toHaveBeenCalledWith("/org/acme/matters");
  });

  it("does not write when the value matches the URL", () => {
    mockSearchParams = new URLSearchParams("search=raf");
    render(<MatterSearch />);

    type("raf ");
    act(() => vi.advanceTimersByTime(1000));

    expect(mockReplace).not.toHaveBeenCalled();
  });
});
