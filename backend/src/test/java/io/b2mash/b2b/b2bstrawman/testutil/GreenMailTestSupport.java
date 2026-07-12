package io.b2mash.b2b.b2bstrawman.testutil;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * JVM-wide singleton GreenMail SMTP server, bound to a dynamically chosen free port. The port scan
 * starts at 13025 (the historical fixed port and the {@code application-test.yml} fallback) and
 * walks up to 13125, using the first port that accepts a bind. The chosen port is published as the
 * {@code greenmail.smtp.port} system property, which {@code application-test.yml} consumes via
 * {@code spring.mail.port: ${greenmail.smtp.port:13025}}.
 *
 * <p>The scan deliberately stays in a low, non-ephemeral range rather than using {@code new
 * ServerSocket(0)}: some tests stop and restart this server mid-test (e.g. {@code
 * PortalEmailServiceIntegrationTest}, {@code EmailNotificationChannelIntegrationTest}), and during
 * that window an OS-assigned ephemeral port could be stolen by an outbound socket, causing a rare
 * rebind flake. Ports in the 13025–13125 range are never handed out as ephemeral ports by the OS.
 *
 * <p>{@link GreenMailLauncherSessionListener} forces this class's static initialization at JUnit
 * launcher-session start, guaranteeing the system property is set before any Spring test context
 * resolves {@code spring.mail.port} — so the single cached context always sees the right port.
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

  private static final int PORT_SCAN_START = 13025;
  private static final int PORT_SCAN_END = 13125;

  private static final GreenMail INSTANCE = startServer();

  private GreenMailTestSupport() {}

  private static GreenMail startServer() {
    int port = findFreePort();
    // Consumed by application-test.yml: spring.mail.port=${greenmail.smtp.port:13025}.
    // Must be set before ANY Spring test context resolves spring.mail.port — guaranteed
    // by GreenMailLauncherSessionListener forcing this class's init at session start.
    System.setProperty("greenmail.smtp.port", String.valueOf(port));
    GreenMail server = new GreenMail(new ServerSetup(port, null, "smtp"));
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

  /**
   * Returns the first free port in {@code [13025, 13125]}, verified by binding a throwaway {@link
   * ServerSocket}. Throws if the whole range is exhausted (100 concurrent/stale GreenMail holders —
   * something is badly wrong; run the verify preflight to sweep zombie JVMs).
   */
  private static int findFreePort() {
    for (int port = PORT_SCAN_START; port <= PORT_SCAN_END; port++) {
      try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
        socket.setReuseAddress(true);
        return port;
      } catch (IOException busy) {
        // Port taken — try the next one.
      }
    }
    throw new IllegalStateException(
        "No free port for GreenMail in range "
            + PORT_SCAN_START
            + "-"
            + PORT_SCAN_END
            + ". Sweep stale Maven/Surefire JVMs (see backend/scripts/verify-preflight.sh) and retry.");
  }

  public static GreenMail getInstance() {
    return INSTANCE;
  }

  /** Clear all stored messages. Call from {@code @BeforeEach} to isolate tests. */
  public static void reset() {
    INSTANCE.reset();
  }
}
