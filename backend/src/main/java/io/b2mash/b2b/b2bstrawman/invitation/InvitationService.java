package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.CreateInvitationRequest;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.InvitationListResponse;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.PendingInvitationResponse;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

  private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
  private static final Duration DEFAULT_EXPIRY = Duration.ofDays(7);

  private final PendingInvitationRepository invitationRepository;
  private final OrgRoleRepository orgRoleRepository;
  private final MemberRepository memberRepository;
  private final AuditService auditService;
  private final KeycloakAdminClient keycloakAdminClient;

  public InvitationService(
      PendingInvitationRepository invitationRepository,
      OrgRoleRepository orgRoleRepository,
      MemberRepository memberRepository,
      AuditService auditService,
      @Nullable KeycloakAdminClient keycloakAdminClient) {
    this.invitationRepository = invitationRepository;
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
    this.keycloakAdminClient = keycloakAdminClient;
  }

  @Transactional
  public PendingInvitationResponse createInvitation(
      CreateInvitationRequest request, UUID invitedByMemberId) {
    String email = request.email().toLowerCase().trim();

    if (memberRepository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Email already a member",
          "A member with the email '" + email + "' already exists in this organization");
    }

    Optional<PendingInvitation> existingPending =
        invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING);
    if (existingPending.isPresent()) {
      PendingInvitation existing = existingPending.get();
      if (existing.isExpired()) {
        existing.expire();
        invitationRepository.save(existing);
      } else {
        throw new ResourceConflictException(
            "Pending invitation exists", "A pending invitation for '" + email + "' already exists");
      }
    }

    OrgRole orgRole =
        orgRoleRepository
            .findById(request.orgRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", request.orgRoleId()));

    Member invitedBy =
        memberRepository
            .findById(invitedByMemberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", invitedByMemberId));

    Instant expiresAt = Instant.now().plus(DEFAULT_EXPIRY);
    var invitation = new PendingInvitation(email, orgRole, invitedBy, expiresAt);

    try {
      invitation = invitationRepository.save(invitation);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate invitation", "A pending invitation for '" + email + "' already exists");
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invitation.created")
            .entityType("pending_invitation")
            .entityId(invitation.getId())
            .details(
                Map.of(
                    "email", email,
                    "roleName", orgRole.getName(),
                    "invitedBy", invitedBy.getName()))
            .build());

    // Trigger Keycloak invitation email (best-effort — failure doesn't roll back DB record)
    if (keycloakAdminClient != null) {
      try {
        String orgId = RequestScopes.getOrgIdOrNull();
        if (orgId != null) {
          String kcOrgId = keycloakAdminClient.resolveOrgId(orgId);
          keycloakAdminClient.inviteMember(kcOrgId, email, orgRole.getSlug(), null);
          log.info("Triggered Keycloak invitation for email={}", email);
        }
      } catch (Exception e) {
        log.warn("Failed to send Keycloak invitation for email={}: {}", email, e.getMessage());
      }
    }

    log.info("Created invitation for email={} with role={}", email, orgRole.getSlug());
    return PendingInvitationResponse.from(invitation);
  }

  @Transactional(readOnly = true)
  public InvitationListResponse listInvitations(String statusFilter) {
    var invitations =
        (statusFilter != null && !statusFilter.isBlank())
            ? invitationRepository.findAllByStatusOrderByCreatedAtDesc(parseStatus(statusFilter))
            : invitationRepository.findAllByOrderByCreatedAtDesc();

    var responses = invitations.stream().map(PendingInvitationResponse::from).toList();
    return new InvitationListResponse(responses);
  }

  @Transactional
  public void revokeInvitation(UUID invitationId) {
    PendingInvitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("PendingInvitation", invitationId));

    if (invitation.getStatus() != InvitationStatus.PENDING) {
      throw new InvalidStateException(
          "Cannot revoke invitation",
          "Invitation is in '"
              + invitation.getStatus()
              + "' state and cannot be revoked. Only PENDING invitations can be revoked.");
    }

    invitation.revoke();
    invitationRepository.save(invitation);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invitation.revoked")
            .entityType("pending_invitation")
            .entityId(invitation.getId())
            .details(Map.of("email", invitation.getEmail()))
            .build());

    log.info("Revoked invitation id={} for email={}", invitationId, invitation.getEmail());
  }

  @Transactional
  public Optional<PendingInvitation> findPendingByEmail(String email) {
    Optional<PendingInvitation> result =
        invitationRepository.findByEmailAndStatus(
            email.toLowerCase().trim(), InvitationStatus.PENDING);

    if (result.isPresent() && result.get().isExpired()) {
      PendingInvitation expired = result.get();
      expired.expire();
      invitationRepository.save(expired);
      return Optional.empty();
    }

    return result;
  }

  @Transactional
  public void markAccepted(UUID invitationId) {
    PendingInvitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("PendingInvitation", invitationId));

    invitation.accept();
    invitationRepository.save(invitation);
    log.info("Marked invitation id={} as accepted", invitationId);
  }

  private InvitationStatus parseStatus(String statusFilter) {
    try {
      return InvitationStatus.valueOf(statusFilter.toUpperCase().trim());
    } catch (IllegalArgumentException ex) {
      throw new InvalidStateException(
          "Invalid status filter",
          "'"
              + statusFilter
              + "' is not a valid invitation status. Valid values: "
              + java.util.Arrays.toString(InvitationStatus.values()));
    }
  }
}
