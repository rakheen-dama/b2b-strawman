package io.b2mash.b2b.b2bstrawman.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

  @Query(
      "SELECT p FROM PendingInvitation p JOIN FETCH p.orgRole JOIN FETCH p.invitedBy"
          + " WHERE p.email = :email AND p.status = :status")
  Optional<PendingInvitation> findByEmailAndStatus(
      @Param("email") String email, @Param("status") InvitationStatus status);

  @Query(
      "SELECT p FROM PendingInvitation p JOIN FETCH p.orgRole JOIN FETCH p.invitedBy"
          + " WHERE p.status = :status ORDER BY p.createdAt DESC")
  List<PendingInvitation> findAllByStatusOrderByCreatedAtDesc(
      @Param("status") InvitationStatus status);

  @Query(
      "SELECT p FROM PendingInvitation p JOIN FETCH p.orgRole JOIN FETCH p.invitedBy"
          + " ORDER BY p.createdAt DESC")
  List<PendingInvitation> findAllByOrderByCreatedAtDesc();
}
