package io.b2mash.b2b.b2bstrawman.integration.email;

/** File attachment for an email message. */
public record EmailAttachment(String filename, String contentType, byte[] content) {}
