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
}
