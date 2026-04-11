import { describe, it, expect } from "vitest";
import { parseSseEvents } from "@/lib/sse-parser";

describe("parseSseEvents", () => {
  it("parses complete events with event + data lines", () => {
    const buffer =
      'event: text_delta\ndata: {"text":"Hello"}\n\nevent: done\ndata: {"totalInputTokens":100,"totalOutputTokens":50}\n\n';

    const { parsed, remainder } = parseSseEvents(buffer);

    expect(parsed).toHaveLength(2);
    expect(parsed[0]).toEqual({
      type: "text_delta",
      data: { text: "Hello" },
    });
    expect(parsed[1]).toEqual({
      type: "done",
      data: { totalInputTokens: 100, totalOutputTokens: 50 },
    });
    expect(remainder).toBe("");
  });

  it("returns partial chunk as remainder when buffer ends mid-event", () => {
    const buffer = 'event: text_delta\ndata: {"text":"Hello"}\n\nevent: usage\ndata: {"input';

    const { parsed, remainder } = parseSseEvents(buffer);

    expect(parsed).toHaveLength(1);
    expect(parsed[0]).toEqual({
      type: "text_delta",
      data: { text: "Hello" },
    });
    expect(remainder).toBe('event: usage\ndata: {"input');
  });
});
