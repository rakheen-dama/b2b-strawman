package io.b2mash.b2b.b2bstrawman.infrastructure.testutil;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.util.UUID;

/**
 * Where the test embedded-Postgres instances live on disk (G89 Batch F, lesson 2026-11-07).
 *
 * <p>zonky extracts the Postgres binaries once into {@code <working dir>/PG-<hash>} and reuses the
 * extraction across runs. Its default working directory is {@code $TMPDIR/embedded-pg}; on macOS
 * the nightly {@code dirhelper} cleanup deletes files there that have not been accessed for days
 * (it removed {@code share/postgresql/timezonesets} mid-verify, failing Flyway with {@code invalid
 * value for parameter "timezone_abbreviations"}). The extraction is kept marked ready, so zonky
 * never repairs it.
 *
 * <p>Surefire sets zonky's own {@code ot.epg.working-dir} property to {@code
 * ${project.build.directory}/embedded-pg} ({@code backend/target/embedded-pg} from any cwd), which
 * moves the binaries and zonky's stale-data-dir sweep there; {@code mvn clean} removes it. {@link
 * #builder()} also puts each instance's data directory under it, so a killed run leaves nothing in
 * {@code $TMPDIR}. Outside Maven (property unset), zonky's defaults apply unchanged.
 */
public final class EmbeddedPostgresDirectories {

  /** zonky's working-directory system property (read by {@code EmbeddedPostgres}). */
  public static final String WORKING_DIR_PROPERTY = "ot.epg.working-dir";

  private EmbeddedPostgresDirectories() {}

  /** The configured working directory, or {@code null} when zonky's default applies. */
  public static File workingDirectory() {
    String configured = System.getProperty(WORKING_DIR_PROPERTY);
    return configured == null || configured.isBlank()
        ? null
        : new File(configured).getAbsoluteFile();
  }

  /**
   * An {@link EmbeddedPostgres.Builder} whose binaries and data directory sit in the working dir.
   */
  public static EmbeddedPostgres.Builder builder() {
    EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
    File workingDir = workingDirectory();
    if (workingDir == null) {
      return builder;
    }
    return builder
        .setOverrideWorkingDirectory(workingDir)
        .setDataDirectory(new File(workingDir, UUID.randomUUID().toString()));
  }
}
