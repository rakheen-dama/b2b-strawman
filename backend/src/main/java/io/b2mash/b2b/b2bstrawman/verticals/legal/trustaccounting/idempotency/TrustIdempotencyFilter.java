package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * The buffering half of {@link IdempotentTrustWrite} (648B F-11): for a mutating request carrying
 * an {@code Idempotency-Key} whose handler is an {@link IdempotentTrustWrite} endpoint, reads the
 * (non-multipart) body up front so it can be hashed, and buffers the response so the interceptor
 * can store it. Every other request passes through untouched. The protocol itself is {@link
 * TrustIdempotencyInterceptor}'s; this filter decides nothing.
 */
public class TrustIdempotencyFilter extends OncePerRequestFilter {

  private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

  public TrustIdempotencyFilter(ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !MUTATING.contains(request.getMethod())
        || request.getHeader(TrustIdempotencyService.HEADER) == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!handledByIdempotentTrustWrite(request)) {
      chain.doFilter(request, response);
      return;
    }
    var contentType = request.getContentType();
    HttpServletRequest requestToUse =
        contentType != null && contentType.toLowerCase().startsWith("multipart/")
            ? request // multipart parts are hashed from the resolved MultipartHttpServletRequest
            : new BufferedBodyRequest(request);
    var responseToUse = new ContentCachingResponseWrapper(response);
    try {
      chain.doFilter(requestToUse, responseToUse);
    } finally {
      responseToUse.copyBodyToResponse();
    }
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
