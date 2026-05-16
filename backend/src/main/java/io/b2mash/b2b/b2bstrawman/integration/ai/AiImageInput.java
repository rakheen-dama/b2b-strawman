package io.b2mash.b2b.b2bstrawman.integration.ai;

/** Image data carrier for vision completion requests. */
public record AiImageInput(String mediaType, String base64Data) {}
