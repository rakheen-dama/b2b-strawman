package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/**
 * 648B F-11 security review (MEDIUM): the filter buffers a trust write's JSON body only up to
 * {@link TrustIdempotencyFilter#MAX_JSON_BODY_BYTES}, never a multipart body, and nothing at all
 * for a request without a resolved member or for any other endpoint.
 */
class TrustIdempotencyFilterTest {

  private static final int MAX = TrustIdempotencyFilter.MAX_JSON_BODY_BYTES;

  private final RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<RequestMappingHandlerMapping> provider = mock(ObjectProvider.class);

  private final TrustIdempotencyFilter filter = new TrustIdempotencyFilter(provider);

  static class Endpoints {
    @IdempotentTrustWrite
    public void write() {}

    public void other() {}
  }

  @BeforeEach
  void setUp() throws Exception {
    when(provider.getObject()).thenReturn(mapping);
    handler("write");
  }

  @Test
  void aNormalBodyIsBufferedWhole_andTheResponseIsWrapped() throws Exception {
    var request = request(new CountingBody("{\"amount\": 1}".getBytes()), 13);

    var seen = run(request);

    var buffered = WebUtils.getNativeRequest(seen.request(), BufferedBodyRequest.class);
    assertThat(buffered).isNotNull();
    assertThat(new String(buffered.body())).isEqualTo("{\"amount\": 1}");
    assertThat(seen.response()).isInstanceOf(ContentCachingResponseWrapper.class);
    assertThat(request.getAttribute(TrustIdempotencyFilter.BODY_TOO_LARGE_ATTRIBUTE)).isNull();
  }

  @Test
  void aDeclaredLengthOverTheLimit_isNotReadAtAll() throws Exception {
    var body = new CountingBody(new byte[MAX + 10]);
    var request = request(body, MAX + 10);

    var seen = run(request);

    assertThat(body.read.get()).isZero();
    assertThat(WebUtils.getNativeRequest(seen.request(), BufferedBodyRequest.class)).isNull();
    assertThat(request.getAttribute(TrustIdempotencyFilter.BODY_TOO_LARGE_ATTRIBUTE))
        .isEqualTo(Boolean.TRUE);
  }

  @Test
  void aBodyWithoutALength_orWithAFalseOne_isReadAtMostOneBytePastTheLimit() throws Exception {
    for (long declared : new long[] {-1, 10}) {
      var body = new CountingBody(new byte[4 * MAX]);
      var request = request(body, declared);

      var seen = run(request);

      assertThat(body.read.get()).as("declared %d", declared).isLessThanOrEqualTo(MAX + 1);
      assertThat(WebUtils.getNativeRequest(seen.request(), BufferedBodyRequest.class)).isNull();
      assertThat(request.getAttribute(TrustIdempotencyFilter.BODY_TOO_LARGE_ATTRIBUTE))
          .isEqualTo(Boolean.TRUE);
    }
  }

  @Test
  void aMultipartBodyIsNeverBuffered() throws Exception {
    var body = new CountingBody(new byte[1024]);
    var request = request(body, 1024);
    request.setContentType("multipart/form-data; boundary=x");

    var seen = run(request);

    assertThat(body.read.get()).isZero();
    assertThat(WebUtils.getNativeRequest(seen.request(), BufferedBodyRequest.class)).isNull();
    assertThat(seen.response()).isInstanceOf(ContentCachingResponseWrapper.class);
  }

  @Test
  void withoutAResolvedMember_nothingIsLookedUpOrRead() throws Exception {
    var body = new CountingBody(new byte[1024]);
    var request = request(body, 1024);
    var seen = new AtomicReference<ServletRequest>();

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));

    assertThat(seen.get()).isSameAs(request);
    assertThat(body.read.get()).isZero();
    verify(mapping, never()).getHandler(any());
  }

  @Test
  void anotherEndpointIsPassedThroughUntouched() throws Exception {
    handler("other");
    var body = new CountingBody(new byte[1024]);
    var request = request(body, 1024);

    var seen = run(request);

    assertThat(seen.request()).isSameAs(request);
    assertThat(body.read.get()).isZero();
  }

  // --- helpers --------------------------------------------------------------------------------

  private record Seen(ServletRequest request, ServletResponse response) {}

  private Seen run(MockHttpServletRequest request) throws Exception {
    var seen = new AtomicReference<Seen>();
    ScopedValue.where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .call(
            () -> {
              filter.doFilter(
                  request,
                  new MockHttpServletResponse(),
                  (req, res) -> seen.set(new Seen(req, res)));
              return null;
            });
    return seen.get();
  }

  private void handler(String name) throws Exception {
    var method = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
    when(mapping.getHandler(any())).thenReturn(new HandlerExecutionChain(method));
  }

  private static MockHttpServletRequest request(CountingBody body, long declaredLength) {
    var request =
        new MockHttpServletRequest("POST", "/api/trust-accounts/1/transactions/deposit") {
          @Override
          public ServletInputStream getInputStream() {
            return body;
          }

          @Override
          public long getContentLengthLong() {
            return declaredLength;
          }

          @Override
          public int getContentLength() {
            return (int) declaredLength;
          }
        };
    request.setContentType("application/json");
    request.addHeader(TrustIdempotencyService.HEADER, UUID.randomUUID().toString());
    return request;
  }

  /** A request body that counts the bytes read from it. */
  private static final class CountingBody extends ServletInputStream {
    private final ByteArrayInputStream in;
    final AtomicInteger read = new AtomicInteger();

    CountingBody(byte[] bytes) {
      this.in = new ByteArrayInputStream(bytes);
    }

    @Override
    public int read() {
      int b = in.read();
      if (b >= 0) {
        read.incrementAndGet();
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) {
      int n = in.read(b, off, len);
      if (n > 0) {
        read.addAndGet(n);
      }
      return n;
    }

    @Override
    public boolean isFinished() {
      return in.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {}
  }
}
