package io.b2mash.b2b.b2bstrawman.packs;

import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADR-356 reconcile version gate over {@code pack_install}. An already-applied pack is reconciled
 * only when its recorded version (parsed as an integer) is lower than the classpath version; the
 * gate then claims the advance with a compare-and-set in the caller's transaction, so a failing
 * reconcile rolls the version back. Non-numeric versions fail closed with a WARN.
 *
 * <p>Depends only on {@link PackInstallRepository} so pack seeders can use it without a bean cycle
 * ({@code PackInstallService → PackCatalogService → PackInstaller → seeder}). Must be called inside
 * the caller's tenant transaction.
 */
@Component
public class PackVersionGate {

  private static final Logger log = LoggerFactory.getLogger(PackVersionGate.class);

  private final PackInstallRepository packInstallRepository;

  public PackVersionGate(PackInstallRepository packInstallRepository) {
    this.packInstallRepository = packInstallRepository;
  }

  /** Integer pack version, or empty for null / non-numeric (callers close the gate + WARN). */
  public static OptionalInt parseVersion(String version) {
    if (version == null) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(version.trim()));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  /**
   * Writes the ledger row of a seeder-managed pack unless a row with this packId already exists.
   * Race-safe and exception-free ({@code ON CONFLICT (pack_id) DO NOTHING}).
   *
   * @return true if a row was inserted
   * @throws IllegalArgumentException for installer-managed types (their {@link PackInstaller} owns
   *     the row)
   */
  public boolean recordIfAbsent(PackType type, String packId, String version, String packName) {
    if (type.isInstallerManaged()) {
      throw new IllegalArgumentException(
          type + " pack_install rows are owned by its PackInstaller, not the seeder ledger");
    }
    return packInstallRepository.insertIfAbsent(packId, type.name(), version, packName) == 1;
  }

  /**
   * Opens the gate when the recorded version is numerically lower than {@code classpathVersion} and
   * this transaction claimed the advance (compare-and-set to the classpath version). The caller
   * then runs the reconcile hook in the same transaction.
   *
   * @return true if the caller must reconcile now; false when equal/higher, non-numeric (WARN), no
   *     row (WARN), a row of another type (WARN), or the advance was claimed by another transaction
   */
  public boolean tryOpen(PackType type, String packId, String classpathVersion, String tenantId) {
    var row = packInstallRepository.findByPackId(packId);
    if (row.isEmpty()) {
      log.warn(
          "Reconcile gate closed: no pack_install row for {} pack {} (tenant {})",
          type,
          packId,
          tenantId);
      return false;
    }
    PackInstall install = row.get();
    if (install.getPackType() != type) {
      log.warn(
          "Reconcile gate closed: pack_install row {} is {} not {} (tenant {})",
          packId,
          install.getPackType(),
          type,
          tenantId);
      return false;
    }
    OptionalInt recorded = parseVersion(install.getPackVersion());
    OptionalInt target = parseVersion(classpathVersion);
    if (recorded.isEmpty() || target.isEmpty()) {
      log.warn(
          "Reconcile gate closed: non-numeric version for {} pack {} (recorded '{}', classpath"
              + " '{}', tenant {})",
          type,
          packId,
          install.getPackVersion(),
          classpathVersion,
          tenantId);
      return false;
    }
    if (recorded.getAsInt() >= target.getAsInt()) {
      return false;
    }
    String newVersion = String.valueOf(target.getAsInt());
    if (packInstallRepository.compareAndSetVersion(packId, install.getPackVersion(), newVersion)
        == 0) {
      log.info(
          "Reconcile of {} pack {} for tenant {} skipped — version advance already claimed",
          type,
          packId,
          tenantId);
      return false;
    }
    log.info(
        "Reconciling {} pack {} v{} -> v{} for tenant {}",
        type,
        packId,
        recorded.getAsInt(),
        newVersion,
        tenantId);
    return true;
  }
}
