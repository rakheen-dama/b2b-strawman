package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A request whose (non-multipart) body was read up front — at most {@link
 * TrustIdempotencyFilter#MAX_JSON_BODY_BYTES} — so {@link TrustIdempotencyInterceptor} can hash it
 * before the handler reads it again (648B F-11). Used only for {@link IdempotentTrustWrite}
 * endpoints carrying an {@code Idempotency-Key}; those take small JSON bodies.
 */
final class BufferedBodyRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  BufferedBodyRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    var in = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return in.read();
      }

      @Override
      public int read(byte[] b, int off, int len) {
        return in.read(b, off, len);
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
      public void setReadListener(ReadListener listener) {
        throw new UnsupportedOperationException("Buffered request bodies are read synchronously");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    String encoding = getCharacterEncoding();
    Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }

  @Override
  public int getContentLength() {
    return body.length;
  }

  @Override
  public long getContentLengthLong() {
    return body.length;
  }
}
