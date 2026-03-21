export interface SseEvent {
  type: string;
  data: unknown;
}

/**
 * Parse a buffered SSE text stream into discrete events.
 * Returns fully-parsed events and any incomplete remainder
 * (which should be prepended to the next chunk).
 */
export function parseSseEvents(buffer: string): {
  parsed: SseEvent[];
  remainder: string;
} {
  const parsed: SseEvent[] = [];

  // SSE events are separated by double newlines
  const parts = buffer.split("\n\n");

  // The last part may be incomplete (no trailing \n\n)
  const remainder = parts.pop() ?? "";

  for (const part of parts) {
    const trimmed = part.trim();
    if (!trimmed) continue;

    let type = "message";
    let dataLine = "";

    for (const line of trimmed.split("\n")) {
      if (line.startsWith("event:")) {
        type = line.slice("event:".length).trim();
      } else if (line.startsWith("data:")) {
        dataLine = line.slice("data:".length).trim();
      }
    }

    if (!dataLine) continue;

    let data: unknown;
    try {
      data = JSON.parse(dataLine);
    } catch {
      data = dataLine;
    }

    parsed.push({ type, data });
  }

  return { parsed, remainder };
}
