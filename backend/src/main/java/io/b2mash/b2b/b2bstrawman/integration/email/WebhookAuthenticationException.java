package io.b2mash.b2b.b2bstrawman.integration.email;

/** Thrown when webhook signature verification fails or is not configured. */
public class WebhookAuthenticationException extends RuntimeException {

  public WebhookAuthenticationException(String message) {
    super(message);
  }

  public WebhookAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
