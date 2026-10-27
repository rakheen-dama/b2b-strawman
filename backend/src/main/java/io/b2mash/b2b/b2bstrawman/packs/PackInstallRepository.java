package io.b2mash.b2b.b2bstrawman.packs;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PackInstallRepository extends JpaRepository<PackInstall, UUID> {
  Optional<PackInstall> findByPackId(String packId);

  /**
   * Atomically advances the recorded pack version (compare-and-set on the current version). Returns
   * the number of rows updated: 1 if this transaction claimed the version advance, 0 if another
   * transaction already advanced it. Under concurrent reconciliation the loser blocks on the
   * pack_install row lock until the winner commits, then matches zero rows — serialising reconcile
   * without needing a unique constraint on document_templates.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE PackInstall p SET p.packVersion = :newVersion, p.itemCount = :itemCount"
          + " WHERE p.packId = :packId AND p.packVersion = :expectedVersion")
  int advancePackVersion(
      @Param("packId") String packId,
      @Param("expectedVersion") String expectedVersion,
      @Param("newVersion") String newVersion,
      @Param("itemCount") int itemCount);

  /**
   * ADR-356 ledger write for seeder-managed pack types (CLAUSE / FIELD / REPORT / REQUEST).
   * Idempotent and race-safe: {@code ON CONFLICT (pack_id) DO NOTHING} never raises, so the
   * caller's transaction is never aborted by a concurrent insert. Returns 1 if inserted, else 0.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO pack_install (id, pack_id, pack_type, pack_version, pack_name, installed_at,"
              + " item_count) VALUES (gen_random_uuid(), :packId, :packType, :packVersion,"
              + " :packName, now(), 0) ON CONFLICT (pack_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("packId") String packId,
      @Param("packType") String packType,
      @Param("packVersion") String packVersion,
      @Param("packName") String packName);

  /**
   * Compare-and-set of the recorded version only (ADR-356 version gate). Unlike {@link
   * #advancePackVersion} it does NOT clear the persistence context, so callers holding managed
   * entities (e.g. the {@code OrgSettings} that {@code AbstractPackSeeder} mutates and saves) keep
   * them attached. Returns 1 if this transaction claimed the advance, 0 otherwise.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE PackInstall p SET p.packVersion = :newVersion"
          + " WHERE p.packId = :packId AND p.packVersion = :expectedVersion")
  int compareAndSetVersion(
      @Param("packId") String packId,
      @Param("expectedVersion") String expectedVersion,
      @Param("newVersion") String newVersion);
}
