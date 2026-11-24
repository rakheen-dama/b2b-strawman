package io.b2mash.b2b.b2bstrawman.takeon;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TakeOnGoLiveItemRepository extends JpaRepository<TakeOnGoLiveItem, GoLiveItem> {

  /**
   * Records an acknowledgement of {@code itemKey}, replacing the one it has (D-166). One statement,
   * so two concurrent first acknowledgements never collide on the key: the second waits for the
   * first and then replaces it (D-180). Never raises a key violation, so it never aborts the
   * caller's transaction.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO take_on_go_live_items (item_key, acknowledged_by, acknowledged_at, note)
          VALUES (:itemKey, :acknowledgedBy, :acknowledgedAt, :note)
          ON CONFLICT (item_key) DO UPDATE SET acknowledged_by = EXCLUDED.acknowledged_by,
                 acknowledged_at = EXCLUDED.acknowledged_at, note = EXCLUDED.note""")
  int upsertAcknowledgement(
      @Param("itemKey") String itemKey,
      @Param("acknowledgedBy") UUID acknowledgedBy,
      @Param("acknowledgedAt") Instant acknowledgedAt,
      @Param("note") String note);
}
