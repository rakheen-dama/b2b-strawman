package io.b2mash.b2b.b2bstrawman.integration.email;

/** Output of template rendering, ready to be passed to an EmailProvider. */
public record RenderedEmail(String subject, String htmlBody, String plainTextBody) {}
