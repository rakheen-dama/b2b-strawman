import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, renderHook, act } from "@testing-library/react";
import { useAssistantChat } from "@/hooks/use-assistant-chat";

// ---- Helpers ----

function createSseStream(events: string): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(events));
      controller.close();
    },
  });
}

// ---- Setup ----

const originalFetch = globalThis.fetch;

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  cleanup();
});

describe("useAssistantChat", () => {
  it("sendMessage adds user message and starts streaming", async () => {
    const ssePayload =
      'event: text_delta\ndata: {"text":"Hi there!"}\n\nevent: done\ndata: {}\n\n';

    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      body: createSseStream(ssePayload),
    });

    const { result } = renderHook(() => useAssistantChat());

    await act(async () => {
      await result.current.sendMessage("Hello");
    });

    // Should have user message + assistant message with streamed content
    const msgs = result.current.messages;
    expect(msgs.length).toBeGreaterThanOrEqual(2);
    expect(msgs[0].role).toBe("user");
    expect(msgs[0].content).toBe("Hello");
    expect(msgs[1].role).toBe("assistant");
    expect(msgs[1].content).toBe("Hi there!");
    expect(result.current.isStreaming).toBe(false);
  });

  it("stopStreaming aborts the request", async () => {
    // Spy on AbortController.prototype.abort
    const abortSpy = vi.spyOn(AbortController.prototype, "abort");

    // Make fetch hang so we can abort it
    globalThis.fetch = vi.fn().mockImplementation(
      () =>
        new Promise(() => {
          // Never resolves — simulates a pending SSE connection
        }),
    );

    const { result } = renderHook(() => useAssistantChat());

    // Start a message (will hang because fetch never resolves)
    act(() => {
      result.current.sendMessage("Hello");
    });

    // Stop streaming
    act(() => {
      result.current.stopStreaming();
    });

    expect(abortSpy).toHaveBeenCalled();
    expect(result.current.isStreaming).toBe(false);

    abortSpy.mockRestore();
  });
});
