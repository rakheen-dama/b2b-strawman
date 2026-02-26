package io.b2mash.b2b.b2bstrawman.integration.email;

/** Thrown when webhook payload is invalid (unsupported provider, malformed JSON, etc.). */
public class WebhookPayloadException extends RuntimeException {

  public WebhookPayloadException(String message) {
    super(message);
  }

  public WebhookPayloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
