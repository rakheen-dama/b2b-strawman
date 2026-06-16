package io.b2mash.b2b.b2bstrawman.mcp.dto;

/**
 * {@code get_document_url} result: a short-lived presigned download URL plus its validity window.
 * <b>Never inlines document bytes</b> — the caller fetches the bytes directly from object storage
 * using {@code url} within {@code expiresInSeconds}.
 *
 * @param url presigned download URL
 * @param expiresInSeconds seconds until the URL expires
 */
public record McpDownloadUrl(String url, long expiresInSeconds) {}
