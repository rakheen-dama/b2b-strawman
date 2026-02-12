package io.b2mash.b2b.b2bstrawman.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {

  @Query(
      """
      SELECT np FROM NotificationPreference np
      WHERE np.memberId = :memberId
        AND np.notificationType = :type
      """)
  Optional<NotificationPreference> findByMemberIdAndNotificationType(
      @Param("memberId") UUID memberId, @Param("type") String type);

  @Query(
      """
      SELECT np FROM NotificationPreference np
      WHERE np.memberId = :memberId
      """)
  List<NotificationPreference> findByMemberId(@Param("memberId") UUID memberId);
}
