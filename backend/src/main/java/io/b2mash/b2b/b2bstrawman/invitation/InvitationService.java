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
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    // the invitee is granted this role on first sign-in: the assignment rules apply (Task C) —
    // and, defence in depth (review round 1), the owner-only rule for a custom role
    OrgRoleService.requireSystemRoleGrantable(orgRole);
    OrgRoleService.requireOwnerToGrant(
        orgRole.getCapabilities().stream().map(Capability::name).toList(),
        "invite into a role holding an owner-only capability");

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
    String emailNotSentReason = null;
    if (keycloakAdminClient != null) {
      try {
        String orgId = RequestScopes.getOrgIdOrNull();
        if (orgId != null) {
          String kcOrgId = keycloakAdminClient.resolveOrgId(orgId);
          var result = keycloakAdminClient.inviteMember(kcOrgId, email, orgRole.getSlug(), null);
          if (result == KeycloakAdminClient.InviteResult.ALREADY_MEMBER) {
            // Fix 6: e.g. a revoked invitee (still in the Keycloak org, "revoke always wins") —
            // no email; this invitation admits them on their next Kazi request
            emailNotSentReason = PendingInvitationResponse.ALREADY_HAS_LOGIN;
            log.info(
                "No Keycloak invitation email for email={}: already has a login in the org", email);
          } else {
            log.info("Triggered Keycloak invitation for email={}", email);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to send Keycloak invitation for email={}: {}", email, e.getMessage());
      }
    }

    log.info("Created invitation for email={} with role={}", email, orgRole.getSlug());
    return PendingInvitationResponse.from(invitation, emailNotSentReason);
  }

  @Transactional(readOnly = true)
  public InvitationListResponse listInvitations(InvitationStatus statusFilter) {
    var invitations =
        statusFilter != null
            ? invitationRepository.findAllByStatusOrderByCreatedAtDesc(statusFilter)
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

    // FR-8: the Keycloak invitation (its email link) goes too. Runs inside this transaction, so a
    // refusal rolls the Kazi revoke back — never a revoked invitation whose link still works.
    revokeInKeycloak(invitation.getEmail());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invitation.revoked")
            .entityType("pending_invitation")
            .entityId(invitation.getId())
            .details(Map.of("email", invitation.getEmail()))
            .build());

    log.info("Revoked invitation id={} for email={}", invitationId, invitation.getEmail());
  }

  /**
   * FR-8: cancels the Keycloak side of an invitation. Keycloak 26.5 persists an organization
   * invitation at {@code invite-user} and, when the emailed link is used (registration or existing
   * user), refuses it once that invitation no longer exists; org membership is added only on
   * acceptance, and Keycloak deletes its invitation then. So every Keycloak invitation for this
   * exact email (all pages) is deleted. None left — the link was already used, or invite-user
   * failed at creation — is fine: "revoke always wins" (founder ruling, PR #1859), and {@code
   * MemberFilter} refuses anyone whose latest invitation is revoked at first sign-in, so Kazi is
   * the authority. Kazi does not remove the person from the Keycloak org: for a member who
   * registered through the link Keycloak would delete the whole account. Any Keycloak failure
   * refuses the revoke (the caller's transaction rolls back), so a live link is never left behind a
   * "revoked" invitation. Mock/e2e mode (no Keycloak client) and calls without an org context are
   * unaffected.
   */
  private void revokeInKeycloak(String email) {
    String orgId = RequestScopes.getOrgIdOrNull();
    if (keycloakAdminClient == null || orgId == null) {
      return;
    }

    String kcOrgId;
    List<String> kcInvitationIds;
    try {
      kcOrgId = keycloakAdminClient.resolveOrgId(orgId);
      // exhaustive (every page, exact email); an error is never read as "no invitation"
      kcInvitationIds = keycloakAdminClient.findInvitationIdsByEmail(kcOrgId, email);
    } catch (Exception e) {
      throw keycloakRevokeFailed(email, e);
    }

    for (String kcInvitationId : kcInvitationIds) {
      try {
        keycloakAdminClient.revokeInvitation(kcOrgId, kcInvitationId);
      } catch (Exception e) {
        throw keycloakRevokeFailed(email, e);
      }
    }
    if (kcInvitationIds.isEmpty()) {
      log.info(
          "No live Keycloak invitation for email={} (link used or never sent) — revoked in Kazi;"
              + " sign-in is refused by MemberFilter",
          email);
    } else {
      log.info("Revoked {} Keycloak invitation(s) for email={}", kcInvitationIds.size(), email);
    }
  }

  private static InvalidStateException keycloakRevokeFailed(String email, Exception cause) {
    log.warn(
        "Failed to revoke the Keycloak invitation for email={}: {}", email, cause.getMessage());
    return new InvalidStateException(
        "Invitation not revoked",
        "The invitation email for '"
            + email
            + "' could not be cancelled in Keycloak, so its link may still work. Nothing was"
            + " changed. Please try again.");
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

  /**
   * "Revoke always wins" (PR #1859): true when the most recent invitation for {@code email} in this
   * tenant is REVOKED — {@code MemberFilter} then refuses to create a member at first sign-in. The
   * email is normalised exactly as {@link #findPendingByEmail} does.
   */
  @Transactional(readOnly = true)
  public boolean isLatestInvitationRevoked(String email) {
    return invitationRepository
        .findFirstByEmailOrderByCreatedAtDesc(email.toLowerCase().trim())
        .map(invitation -> invitation.getStatus() == InvitationStatus.REVOKED)
        .orElse(false);
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
}
