package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

/**
 * Stub for the IntakeExtraction specialist output payload.
 *
 * <p>515A defines this as an empty record so the sealed {@link OutputPayload} interface compiles.
 * Slice 513A replaces this body with the real fields per architecture §2.4.
 */
public record IntakeExtractionPayload() implements OutputPayload {}
