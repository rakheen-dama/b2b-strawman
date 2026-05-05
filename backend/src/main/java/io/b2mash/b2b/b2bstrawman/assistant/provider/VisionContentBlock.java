package io.b2mash.b2b.b2bstrawman.assistant.provider;

/**
 * Represents a vision content block for native PDF document processing via the Anthropic API.
 *
 * <p>When included in a {@link ChatMessage}, the provider serializes this as a document content
 * block: {@code {type: 'document', source: {type: 'base64', media_type: ..., data: ...}}}.
 *
 * <p>Per ADR-268: no separate OCR vendor — vision fallback uses the same Anthropic API key.
 */
public record VisionContentBlock(String mediaType, String base64Data) {

  public VisionContentBlock {
    if (mediaType == null || mediaType.isBlank()) {
      throw new IllegalArgumentException("mediaType must not be blank");
    }
    if (base64Data == null || base64Data.isBlank()) {
      throw new IllegalArgumentException("base64Data must not be blank");
    }
  }
}
