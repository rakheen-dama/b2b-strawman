package io.b2mash.b2b.b2bstrawman.testutil;

import java.util.Set;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code Idempotency-Key} helpers for MockMvc tests (648B F-11). Every trust write requires the
 * header; {@code TestcontainersConfiguration} adds a fresh key to every mutating MockMvc request
 * that has none ({@link #freshKeyIfAbsent()}), so existing tests post as before. A test of the
 * protocol sets the header itself, or strips it with {@link #withoutIdempotencyKey()}.
 */
public final class TestIdempotency {

  public static final String HEADER = "Idempotency-Key";

  private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

  private TestIdempotency() {}

  /** Adds a random key to a mutating request that carries none (the shared MockMvc default). */
  public static RequestPostProcessor freshKeyIfAbsent() {
    return request -> {
      if (MUTATING.contains(request.getMethod()) && request.getHeader(HEADER) == null) {
        request.addHeader(HEADER, UUID.randomUUID().toString());
      }
      return request;
    };
  }

  /** Removes the key (runs after the shared default), to exercise the missing-key refusal. */
  public static RequestPostProcessor withoutIdempotencyKey() {
    return request -> {
      request.removeHeader(HEADER);
      return request;
    };
  }
}
