package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.TrustIdempotencyService.Claim;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.TrustIdempotencyService.StoredResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 648B F-11 completion policy, without a Spring context: a success or a 4xx refusal is stored
 * (COMPLETED) so a retry replays it; a 5xx or an unresolved exception leaves the key IN_PROGRESS —
 * the outcome is unknown, so it is never re-executed.
 */
class TrustIdempotencyInterceptorTest {

  private static final UUID MEMBER = UUID.randomUUID();

  private final TrustIdempotencyService service = mock(TrustIdempotencyService.class);
  private final TrustRequestHasher hasher = mock(TrustRequestHasher.class);
  private final TrustIdempotencyInterceptor interceptor =
      new TrustIdempotencyInterceptor(service, hasher);

  static class Endpoints {
    @IdempotentTrustWrite
    public void write() {}

    public void read() {}
  }

  @Test
  void aSuccessIsStored() throws Exception {
    var key = UUID.randomUUID();
    var stored = complete(key, 201, "{\"id\":1}", null);
    assertThat(stored.status()).isEqualTo(201);
    assertThat(stored.body()).isEqualTo("{\"id\":1}");
    assertThat(stored.headers()).containsEntry("Content-Type", "application/json");
  }

  @Test
  void a4xxRefusalIsStored() throws Exception {
    var stored = complete(UUID.randomUUID(), 400, "{\"title\":\"Refused\"}", null);
    assertThat(stored.status()).isEqualTo(400);
  }

  @Test
  void a5xxLeavesTheKeyInProgress() throws Exception {
    runOwned(UUID.randomUUID(), 500, "{}", null);
    verify(service, never()).complete(any(), any());
  }

  @Test
  void anUnresolvedExceptionLeavesTheKeyInProgress() throws Exception {
    runOwned(UUID.randomUUID(), 200, "", new IllegalStateException("boom"));
    verify(service, never()).complete(any(), any());
  }

  @Test
  void anUnannotatedHandlerIsIgnored() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/x");
    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler("read")))
        .isTrue();
    Mockito.verifyNoInteractions(service, hasher);
  }

  @Test
  void aReplayWritesTheStoredResponseAndSkipsTheHandler() throws Exception {
    var key = UUID.randomUUID();
    Mockito.when(service.parseKey(key.toString())).thenReturn(key);
    Mockito.when(service.claim(eq(key), eq(MEMBER), any(), any(), any()))
        .thenReturn(
            new Claim.Replay(
                new StoredResponse(
                    201,
                    "{\"id\":\"é\"}",
                    Map.of("Content-Type", "application/json", "Location", "/api/y"))));
    var request = request(key);
    var response = new MockHttpServletResponse();

    boolean proceed =
        ScopedValue.where(RequestScopes.MEMBER_ID, MEMBER)
            .call(() -> interceptor.preHandle(request, response, handler("write")));

    assertThat(proceed).isFalse();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"é\"}");
    assertThat(response.getHeader("Location")).isEqualTo("/api/y");
    assertThat(response.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
  }

  private StoredResponse complete(UUID key, int status, String body, Exception ex)
      throws Exception {
    runOwned(key, status, body, ex);
    var captor = ArgumentCaptor.forClass(StoredResponse.class);
    verify(service).complete(eq(key), captor.capture());
    return captor.getValue();
  }

  private void runOwned(UUID key, int status, String body, Exception ex) throws Exception {
    Mockito.when(service.parseKey(key.toString())).thenReturn(key);
    Mockito.when(service.claim(eq(key), eq(MEMBER), any(), any(), any()))
        .thenReturn(new Claim.Owned(key));
    var request = request(key);
    var response = new ContentCachingResponseWrapper(new MockHttpServletResponse());
    var handler = handler("write");

    boolean proceed =
        ScopedValue.where(RequestScopes.MEMBER_ID, MEMBER)
            .call(() -> interceptor.preHandle(request, response, handler));
    assertThat(proceed).isTrue();

    response.setStatus(status);
    response.setContentType("application/json");
    response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    interceptor.afterCompletion(request, response, handler, ex);
  }

  private static MockHttpServletRequest request(UUID key) {
    var request = new MockHttpServletRequest("POST", "/api/trust-accounts/1/transactions/deposit");
    request.addHeader(TrustIdempotencyService.HEADER, key.toString());
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
        "/api/trust-accounts/{accountId}/transactions/deposit");
    return request;
  }

  private static HandlerMethod handler(String name) throws NoSuchMethodException {
    return new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
  }
}
