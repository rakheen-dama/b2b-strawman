package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AiSpecialistInvocationRepository
    extends JpaRepository<AiSpecialistInvocation, UUID>,
        JpaSpecificationExecutor<AiSpecialistInvocation> {

  List<AiSpecialistInvocation> findByStatusAndCreatedAtBefore(
      InvocationStatus status, Instant before);

  List<AiSpecialistInvocation> findByContextEntityTypeAndContextEntityIdAndStatus(
      String contextEntityType, UUID contextEntityId, InvocationStatus status);

  Page<AiSpecialistInvocation> findByActorId(UUID actorId, Pageable pageable);

  Page<AiSpecialistInvocation> findByStatus(InvocationStatus status, Pageable pageable);

  /** Dedupe query for DIRECT-mode inbox summaries: find existing auto-applied within an hour. */
  List<AiSpecialistInvocation> findBySpecialistIdAndContextEntityIdAndStatusAndCreatedAtBetween(
      String specialistId, UUID contextEntityId, InvocationStatus status, Instant from, Instant to);
}
