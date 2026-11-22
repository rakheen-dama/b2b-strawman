package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.TrustIdempotencyService.Claim;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.TrustIdempotencyService.StoredResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/**
 * Enforces {@link IdempotentTrustWrite} (648B F-11). Runs inside the request's tenant and member
 * scopes (bound by {@code TenantFilter} / {@code MemberFilter}); binds nothing itself.
 *
 * <ol>
 *   <li>{@link #preHandle}: a missing / malformed key → 400; otherwise the key is claimed in its
 *       own committed transaction ({@link TrustIdempotencyService#claim}). A completed key replays
 *       its stored response and the handler never runs; a key of another member or request, or one
 *       still in progress, → 409.
 *   <li>the handler runs exactly as it would without a key, in its own transaction.
 *   <li>{@link #afterCompletion}: the response this request sent — a success or a 4xx refusal — is
 *       stored on the claim (COMPLETED), so a retry replays the same outcome. A 5xx, or an
 *       exception no handler resolved, leaves the claim IN_PROGRESS: the outcome is unknown, so a
 *       retry is refused rather than run again.
 * </ol>
 *
 * <p>The request body and response are buffered by {@link TrustIdempotencyFilter}.
 */
@Component
public class TrustIdempotencyInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TrustIdempotencyInterceptor.class);

  /** The key this request owns, set only once its claim committed. */
  private static final String OWNED_KEY_ATTRIBUTE =
      TrustIdempotencyInterceptor.class.getName() + ".ownedKey";

  private final TrustIdempotencyService idempotencyService;
  private final TrustRequestHasher hasher;

  public TrustIdempotencyInterceptor(
      TrustIdempotencyService idempotencyService, TrustRequestHasher hasher) {
    this.idempotencyService = idempotencyService;
    this.hasher = hasher;
  }

  static boolean isIdempotentTrustWrite(Object handler) {
    return handler instanceof HandlerMethod method
        && method.hasMethodAnnotation(IdempotentTrustWrite.class);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!isIdempotentTrustWrite(handler)) {
      return true;
    }
    UUID key = idempotencyService.parseKey(request.getHeader(TrustIdempotencyService.HEADER));
    String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String hash = hasher.hash(request, route);
    Claim claim =
        idempotencyService.claim(
            key, RequestScopes.requireMemberId(), request.getMethod(), route, hash);
    return switch (claim) {
      case Claim.Owned owned -> {
        request.setAttribute(OWNED_KEY_ATTRIBUTE, owned.key());
        yield true;
      }
      case Claim.Replay replay -> {
        replay(response, replay.response());
        yield false;
      }
    };
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!(request.getAttribute(OWNED_KEY_ATTRIBUTE) instanceof UUID key)) {
      return;
    }
    int status = response.getStatus();
    if (ex != null || status >= 500) {
      log.warn(
          "Trust write with Idempotency-Key {} ended with status {} ({}); the key stays"
              + " IN_PROGRESS and a retry is refused",
          key,
          status,
          ex == null ? "resolved" : ex.getClass().getSimpleName());
      return;
    }
    var cached = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
    if (cached == null) {
      log.error(
          "Trust write with Idempotency-Key {} has no buffered response; the key stays"
              + " IN_PROGRESS",
          key);
      return;
    }
    var headers = new LinkedHashMap<String, String>();
    if (response.getContentType() != null) {
      headers.put(HttpHeaders.CONTENT_TYPE, response.getContentType());
    }
    if (response.getHeader(HttpHeaders.LOCATION) != null) {
      headers.put(HttpHeaders.LOCATION, response.getHeader(HttpHeaders.LOCATION));
    }
    var body = new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8);
    idempotencyService.complete(key, new StoredResponse(status, body, headers));
  }

  private static void replay(HttpServletResponse response, StoredResponse stored)
      throws IOException {
    response.setStatus(stored.status());
    stored.headers().forEach(response::setHeader);
    response.setHeader(TrustIdempotencyService.REPLAYED_HEADER, "true");
    if (stored.body() != null && !stored.body().isEmpty()) {
      byte[] bytes = stored.body().getBytes(StandardCharsets.UTF_8);
      response.setContentLength(bytes.length);
      response.getOutputStream().write(bytes);
    }
  }
}
