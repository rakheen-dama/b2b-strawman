package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Tolerant JSON extractor for LLM responses. Models such as Claude routinely wrap their structured
 * output in a markdown code fence (```` ```json … ``` ````) and may prepend a short prose preamble
 * ("Here is the JSON: …"). Feeding that verbatim text to {@link ObjectMapper#readValue} fails with
 * a {@link JacksonException} on the leading backtick. This helper strips the fence (and tolerates a
 * preamble) before delegating to Jackson, so every AI skill shares one parse path instead of five
 * fragile copies.
 *
 * <p>Stateless and side-effect free; registered as a {@code @Component} so skills can inject it and
 * tests can exercise it directly.
 */
@Component
public class LlmJsonParser {

  /**
   * Strip any surrounding markdown code fence / prose preamble from {@code rawContent} and bind the
   * resulting JSON to {@code type}.
   *
   * @throws InvalidStateException if the extracted content still cannot be parsed as {@code type}.
   */
  public <T> T parse(ObjectMapper mapper, String rawContent, Class<T> type) {
    String json = extractJson(rawContent);
    try {
      return mapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new InvalidStateException(
          "AI response parse failed",
          "AI response could not be parsed as valid "
              + type.getSimpleName()
              + ": "
              + e.getMessage());
    }
  }

  /**
   * Extract the JSON payload from a raw LLM response. Handles three shapes:
   *
   * <ul>
   *   <li>already-clean JSON (returned trimmed, unchanged);
   *   <li>a fenced block (```` ``` ````, ```` ```json ````, ```` ```JSON ````) — the fence markers
   *       and any leading language hint are removed;
   *   <li>a prose preamble before the JSON (e.g. "Here is the result: { … }") — falls back to the
   *       substring from the first opening brace/bracket to the matching last closing one.
   * </ul>
   *
   * <p>The raw text is returned (trimmed) when no JSON-looking content is found, so the downstream
   * parse produces a meaningful Jackson error rather than this method swallowing it.
   */
  String extractJson(String rawContent) {
    if (rawContent == null) {
      return "";
    }
    String trimmed = rawContent.strip();
    if (trimmed.isEmpty()) {
      return trimmed;
    }

    // Case 1: markdown code fence — strip the opening fence (with optional language hint) and the
    // trailing fence, then continue (the inner content may still carry a preamble in odd cases).
    if (trimmed.startsWith("```")) {
      String withoutFences = stripFences(trimmed);
      String inner = withoutFences.strip();
      // After de-fencing, still locate the JSON body in case a hint/prose slipped through.
      String body = sliceJsonBody(inner);
      return body != null ? body : inner;
    }

    // Case 2: bare JSON or prose preamble — slice from first brace/bracket to its matching close.
    String body = sliceJsonBody(trimmed);
    return body != null ? body : trimmed;
  }

  /** Remove a leading ```` ``` ```` (with optional language hint) and a trailing ```` ``` ````. */
  private String stripFences(String trimmed) {
    String result = trimmed;

    // Opening fence: ``` optionally followed by a language hint up to the end of the line.
    int firstNewline = result.indexOf('\n');
    if (firstNewline >= 0) {
      String firstLine = result.substring(0, firstNewline).strip();
      // First line is just the fence (possibly "```json") — drop it entirely.
      if (firstLine.matches("```[a-zA-Z0-9]*")) {
        result = result.substring(firstNewline + 1);
      }
    } else if (result.matches("```[a-zA-Z0-9]*")) {
      // Degenerate: only a fence marker, no body.
      return "";
    }

    // Trailing fence.
    int lastFence = result.lastIndexOf("```");
    if (lastFence >= 0) {
      result = result.substring(0, lastFence);
    }
    return result;
  }

  /**
   * Return the substring spanning the first JSON object/array opener and its matching closer, or
   * {@code null} when no balanced span exists. Uses a depth-counting scan (not {@code lastIndexOf})
   * so that a stray closing brace/bracket in a trailing prose epilogue — or one inside a JSON
   * string value — does not extend the slice past the real end of the JSON body.
   */
  private String sliceJsonBody(String text) {
    int objStart = text.indexOf('{');
    int arrStart = text.indexOf('[');
    int start = firstNonNegative(objStart, arrStart);
    if (start < 0) {
      return null;
    }
    char opener = text.charAt(start);
    char closer = opener == '{' ? '}' : ']';
    int depth = 0;
    boolean inString = false;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (c == '\\') {
          i++; // skip the escaped character
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
      } else if (c == opener) {
        depth++;
      } else if (c == closer) {
        depth--;
        if (depth == 0) {
          return text.substring(start, i + 1);
        }
      }
    }
    return null;
  }

  private int firstNonNegative(int a, int b) {
    if (a < 0) {
      return b;
    }
    if (b < 0) {
      return a;
    }
    return Math.min(a, b);
  }
}
