package io.b2mash.b2b.b2bstrawman.testutil;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Forces {@link GreenMailTestSupport}'s static initialization (free-port scan + the {@code
 * greenmail.smtp.port} system property) at JUnit launcher-session start — i.e. before any test
 * runs, and therefore before any Spring test context resolves {@code spring.mail.port} from {@code
 * application-test.yml}. Without this, a non-email test class could build the shared cached context
 * first and freeze the {@code :13025} fallback into it while GreenMail later binds a different
 * port.
 *
 * <p>Registered via {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener};
 * picked up by Surefire's JUnit Platform launcher and by IDE test runs alike.
 */
public class GreenMailLauncherSessionListener implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    GreenMailTestSupport.getInstance();
  }
}
