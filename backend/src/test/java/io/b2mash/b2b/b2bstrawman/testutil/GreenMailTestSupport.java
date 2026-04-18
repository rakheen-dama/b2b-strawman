package io.b2mash.b2b.b2bstrawman.testutil;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * JVM-wide singleton GreenMail SMTP server, bound to port 13025 (the default SMTP port declared in
 * {@code application-test.yml}).
 *
 * <p>Same pattern as the embedded Postgres singleton in {@code TestcontainersConfiguration} — one
 * real server process for the entire {@code mvn verify} run. A naïve "start per test class"
 * approach on a shared port causes silent port-binding conflicts when surefire loads the next
 * class's static initializer before the previous {@code @AfterAll} has fully released the port.
 *
 * <p>Call {@link #getInstance()} to access the server. Each test that asserts on received mail
 * should call {@link #reset()} in {@code @BeforeEach} to clear the in-memory mail store.
 */
public final class GreenMailTestSupport {

  private static final GreenMail INSTANCE = startServer();

  private GreenMailTestSupport() {}

  private static GreenMail startServer() {
    GreenMail server = new GreenMail(new ServerSetup(13025, null, "smtp"));
    server.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    server.stop();
                  } catch (Exception ignored) {
                    // Best effort on JVM exit.
                  }
                },
                "greenmail-shutdown"));
    return server;
  }

  public static GreenMail getInstance() {
    return INSTANCE;
  }

  /** Clear all stored messages. Call from {@code @BeforeEach} to isolate tests. */
  public static void reset() {
    INSTANCE.reset();
  }
}
