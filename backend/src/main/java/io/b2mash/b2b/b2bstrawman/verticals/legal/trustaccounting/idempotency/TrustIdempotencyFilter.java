package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * The buffering half of {@link IdempotentTrustWrite} (648B F-11): for a mutating request carrying
 * an {@code Idempotency-Key}, from a resolved member, whose handler is an {@link
 * IdempotentTrustWrite} endpoint, reads the (non-multipart) body up front so it can be hashed, and
 * buffers the response so the interceptor can store it. Every other request passes through
 * untouched. The protocol itself is {@link TrustIdempotencyInterceptor}'s; this filter decides
 * nothing.
 *
 * <p>Bounded (security review MEDIUM on the first push): a JSON body is read only up to {@link
 * #MAX_JSON_BODY_BYTES}. A declared {@code Content-Length} above it is not read at all; a body
 * without one (or with a false one) is read at most one byte past the limit. Either way the request
 * is marked {@link #BODY_TOO_LARGE_ATTRIBUTE} and the interceptor refuses it with 413 before the
 * key is claimed or the handler runs. A multipart body (the statement import) is never buffered
 * here: the container's multipart limits ({@code spring.servlet.multipart.max-file-size} / {@code
 * max-request-size}) apply exactly as before, and the hasher digests each part through a stream.
 *
 * <p>Registered after the Spring Security chain, so it runs only for authenticated requests inside
 * the tenant and member scopes; it also checks the member scope itself before reading anything.
 */
public class TrustIdempotencyFilter extends OncePerRequestFilter {

  /**
   * The largest JSON body a trust write accepts: 256 KiB. The largest legitimate trust JSON body (a
   * transfer or an interest entry with a 500-character note or reason) is under 2 KiB.
   */
  public static final int MAX_JSON_BODY_BYTES = 256 * 1024;

  /** Set when a JSON body exceeded {@link #MAX_JSON_BODY_BYTES} and was not buffered. */
  static final String BODY_TOO_LARGE_ATTRIBUTE =
      TrustIdempotencyFilter.class.getName() + ".bodyTooLarge";

  private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

  public TrustIdempotencyFilter(ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !MUTATING.contains(request.getMethod())
        || request.getHeader(TrustIdempotencyService.HEADER) == null
        || !RequestScopes.MEMBER_ID.isBound();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!handledByIdempotentTrustWrite(request)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest requestToUse = isMultipart(request) ? request : buffered(request);
    var responseToUse = new ContentCachingResponseWrapper(response);
    try {
      chain.doFilter(requestToUse, responseToUse);
    } finally {
      responseToUse.copyBodyToResponse();
    }
  }

  /** The request with its body buffered, or the request marked too large (nothing kept). */
  private static HttpServletRequest buffered(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > MAX_JSON_BODY_BYTES) {
      request.setAttribute(BODY_TOO_LARGE_ATTRIBUTE, Boolean.TRUE);
      return request;
    }
    byte[] body = request.getInputStream().readNBytes(MAX_JSON_BODY_BYTES + 1);
    if (body.length > MAX_JSON_BODY_BYTES) {
      request.setAttribute(BODY_TOO_LARGE_ATTRIBUTE, Boolean.TRUE);
      return request;
    }
    return new BufferedBodyRequest(request, body);
  }

  private static boolean isMultipart(HttpServletRequest request) {
    var contentType = request.getContentType();
    return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
  }

  /**
   * Looks the handler up the way the DispatcherServlet will, then removes every request attribute
   * the lookup added, so dispatch starts from the request as it arrived.
   */
  private boolean handledByIdempotentTrustWrite(HttpServletRequest request) {
    Set<String> before = new HashSet<>(Collections.list(request.getAttributeNames()));
    try {
      HandlerExecutionChain chain = handlerMapping.getObject().getHandler(request);
      return chain != null
          && TrustIdempotencyInterceptor.isIdempotentTrustWrite(chain.getHandler());
    } catch (Exception e) {
      return false; // no handler (404 / 405 / 415): dispatch reports it; nothing to buffer
    } finally {
      for (String name : Collections.list(request.getAttributeNames())) {
        if (!before.contains(name)) {
          request.removeAttribute(name);
        }
      }
    }
  }
}
