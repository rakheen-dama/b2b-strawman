package io.b2mash.b2b.b2bstrawman.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

  Optional<PendingInvitation> findByEmailAndStatus(String email, String status);

  List<PendingInvitation> findAllByStatusOrderByCreatedAtDesc(String status);

  List<PendingInvitation> findAllByOrderByCreatedAtDesc();
}
