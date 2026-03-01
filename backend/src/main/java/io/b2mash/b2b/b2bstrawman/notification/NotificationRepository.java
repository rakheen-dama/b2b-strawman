package io.b2mash.b2b.b2bstrawman.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  @Query(
      """
      SELECT n FROM Notification n
      WHERE n.recipientMemberId = :memberId
      ORDER BY n.createdAt DESC
      """)
  Page<Notification> findByRecipientMemberId(@Param("memberId") UUID memberId, Pageable pageable);

  @Query(
      """
      SELECT n FROM Notification n
      WHERE n.recipientMemberId = :memberId
        AND n.isRead = false
      ORDER BY n.createdAt DESC
      """)
  Page<Notification> findUnreadByRecipientMemberId(
      @Param("memberId") UUID memberId, Pageable pageable);

  @Query(
      """
      SELECT COUNT(n) FROM Notification n
      WHERE n.recipientMemberId = :memberId
        AND n.isRead = false
      """)
  long countUnreadByRecipientMemberId(@Param("memberId") UUID memberId);

  @Modifying
  @Query(
      """
      UPDATE Notification n SET n.isRead = true
      WHERE n.recipientMemberId = :memberId
        AND n.isRead = false
      """)
  void markAllAsRead(@Param("memberId") UUID memberId);

  @Query(
      """
      SELECT COUNT(n) > 0 FROM Notification n
      WHERE n.type = :type
        AND n.referenceEntityId = :entityId
      """)
  boolean existsByTypeAndReferenceEntityId(
      @Param("type") String type, @Param("entityId") UUID entityId);

  @Query(
      """
      SELECT COUNT(n) > 0 FROM Notification n
      WHERE n.type = :type
        AND n.recipientMemberId = :memberId
        AND n.createdAt >= :since
      """)
  boolean existsByTypeAndRecipientMemberIdAndCreatedAtAfter(
      @Param("type") String type,
      @Param("memberId") UUID memberId,
      @Param("since") java.time.Instant since);
}
