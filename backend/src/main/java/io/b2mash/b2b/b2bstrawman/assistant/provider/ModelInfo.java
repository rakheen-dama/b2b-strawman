package io.b2mash.b2b.b2bstrawman.assistant.provider;

/** Metadata about an LLM model offered by a provider. */
public record ModelInfo(String id, String name, boolean recommended) {}
