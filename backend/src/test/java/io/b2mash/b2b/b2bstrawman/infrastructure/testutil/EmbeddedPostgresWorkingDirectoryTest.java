package io.b2mash.b2b.b2bstrawman.infrastructure.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * G89 Batch F (lesson 2026-11-07): the zonky embedded-Postgres binaries were extracted to {@code
 * $TMPDIR/embedded-pg}, where the macOS nightly temp cleaner deleted {@code
 * share/postgresql/timezonesets} mid-verify. Surefire now pins zonky's working directory to {@code
 * target/embedded-pg} (removed by {@code mvn clean}), and the test Postgres instances keep their
 * data directories there too.
 */
class EmbeddedPostgresWorkingDirectoryTest {

  @Test
  void surefirePinsTheWorkingDirectoryUnderTheBuildDirectory() {
    String configured = System.getProperty(EmbeddedPostgresDirectories.WORKING_DIR_PROPERTY);
    assertThat(configured).as("ot.epg.working-dir set by Surefire").isNotBlank();
    Path workingDir = Path.of(configured);
    assertThat(workingDir.isAbsolute()).as("%s is absolute", workingDir).isTrue();
    assertThat(workingDir.endsWith(Path.of("target", "embedded-pg")))
        .as("%s ends with target/embedded-pg", workingDir)
        .isTrue();
    String basedir = System.getProperty("basedir");
    if (basedir != null) {
      assertThat(workingDir).isEqualTo(Path.of(basedir, "target", "embedded-pg"));
    }
  }

  @Test
  void theSharedTestPostgresRunsFromTheWorkingDirectory() throws Exception {
    // Starts (or reuses) the JVM-wide test Postgres.
    Class.forName(
        "io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration", true, getClass().getClassLoader());
    File workingDir = EmbeddedPostgresDirectories.workingDirectory();
    assertThat(workingDir).isNotNull();

    File[] binaries = workingDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("PG-"));
    assertThat(binaries).as("extracted PG binaries under %s", workingDir).isNotEmpty();
    assertThat(
            Arrays.stream(binaries)
                .anyMatch(
                    pg ->
                        new File(pg, ".exists").isFile()
                            && new File(pg, "share/postgresql/timezonesets").isDirectory()))
        .isTrue();

    File[] dataDirs =
        workingDir.listFiles(f -> f.isDirectory() && new File(f, "postgresql.conf").isFile());
    assertThat(dataDirs).as("live data directory under %s", workingDir).isNotEmpty();
  }
}
