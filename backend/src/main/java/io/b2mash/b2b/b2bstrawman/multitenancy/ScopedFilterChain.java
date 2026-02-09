package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Bridges ScopedValue's lambda-based API with the servlet FilterChain's checked exceptions
 * (IOException, ServletException).
 */
public final class ScopedFilterChain {

  private ScopedFilterChain() {}

  /**
   * Executes a ScopedValue.Carrier.run() and properly propagates checked exceptions from doFilter()
   * back to the filter contract. Unchecked exceptions (RuntimeException, Error) propagate unchanged
   * — the ScopedValue binding is automatically removed on any exit path.
   */
  public static void runScoped(
      ScopedValue.Carrier carrier,
      FilterChain chain,
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {
    try {
      carrier.run(
          () -> {
            try {
              chain.doFilter(request, response);
            } catch (IOException e) {
              throw new WrappedIOException(e);
            } catch (ServletException e) {
              throw new WrappedServletException(e);
            }
          });
    } catch (WrappedIOException e) {
      throw e.wrapped;
    } catch (WrappedServletException e) {
      throw e.wrapped;
    }
  }

  static final class WrappedIOException extends RuntimeException {
    final IOException wrapped;

    WrappedIOException(IOException e) {
      super(e);
      this.wrapped = e;
    }
  }

  static final class WrappedServletException extends RuntimeException {
    final ServletException wrapped;

    WrappedServletException(ServletException e) {
      super(e);
      this.wrapped = e;
    }
  }
}
