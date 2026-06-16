package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a {@link CallToolResult} flagged {@code isError:true} from a non-leaking {@link McpError}.
 *
 * <p>Why this exists: with Spring AI {@code 2.0.0-M6} the annotated-tool result converter only sets
 * {@code isError:true} when the tool method <i>throws</i> — and the thrown message is wrapped as
 * {@code "Error invoking method: <message>"}, which would leak the original exception text to the
 * LLM. Simply <i>returning</i> an {@code McpError} record serialises it as a normal (non-error)
 * text block. To get BOTH a non-leaking payload AND {@code isError:true}, a tool returns a
 * pre-built {@link CallToolResult} (the converter passes a {@code CallToolResult} through
 * verbatim). The body is the serialised {@code McpError}, so the message stays sanitised (identical
 * for not-found vs no-access).
 */
public final class McpToolErrors {

  private McpToolErrors() {}

  /** Wraps {@code error} as an {@code isError:true} tool result carrying the safe JSON message. */
  public static CallToolResult asResult(McpError error, ObjectMapper objectMapper) {
    return CallToolResult.builder()
        .isError(true)
        .addTextContent(objectMapper.writeValueAsString(error))
        .build();
  }
}
