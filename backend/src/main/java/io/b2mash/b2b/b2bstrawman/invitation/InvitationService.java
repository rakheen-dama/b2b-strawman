package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

  private static final Duration INVITATION_TTL = Duration.ofDays(7);

  private final PendingInvitationRepository pendingInvitationRepository;
  private final OrgRoleRepository orgRoleRepository;
  private final MemberRepository memberRepository;

  public InvitationService(
      PendingInvitationRepository pendingInvitationRepository,
      OrgRoleRepository orgRoleRepository,
      MemberRepository memberRepository) {
    this.pendingInvitationRepository = pendingInvitationRepository;
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
  }

  @Transactional
  public InvitationResponse invite(String email, UUID orgRoleId) {
    var existing = pendingInvitationRepository.findByEmailIgnoreCaseAndStatus(email, "PENDING");
    if (!existing.isEmpty()) {
      throw new ResourceConflictException(
          "Duplicate invitation", "A pending invitation already exists for " + email);
    }

    var role =
        orgRoleRepository
            .findById(orgRoleId)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", orgRoleId));

    UUID inviterId = RequestScopes.requireMemberId();
    var inviter =
        memberRepository
            .findById(inviterId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", inviterId));

    var invitation =
        new PendingInvitation(email, orgRoleId, inviterId, Instant.now().plus(INVITATION_TTL));
    invitation = pendingInvitationRepository.save(invitation);

    return InvitationResponse.from(invitation, role.getName(), inviter.getName());
  }

  @Transactional(readOnly = true)
  public List<PendingInvitation> findPendingByEmail(String email) {
    return pendingInvitationRepository.findByEmailIgnoreCaseAndStatus(email, "PENDING");
  }

  @Transactional
  public void markAccepted(UUID invitationId) {
    var invitation =
        pendingInvitationRepository
            .findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
    invitation.setStatus("ACCEPTED");
    pendingInvitationRepository.save(invitation);
  }

  @Transactional
  public void revoke(UUID invitationId) {
    var invitation =
        pendingInvitationRepository
            .findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));

    if (!"PENDING".equals(invitation.getStatus())) {
      throw new InvalidStateException(
          "Cannot revoke invitation",
          "Only PENDING invitations can be revoked. Current status: " + invitation.getStatus());
    }

    invitation.setStatus("REVOKED");
    pendingInvitationRepository.save(invitation);
  }

  @Transactional(readOnly = true)
  public List<InvitationResponse> listAll() {
    return pendingInvitationRepository.findAll().stream()
        .map(
            inv -> {
              String roleName =
                  orgRoleRepository
                      .findById(inv.getOrgRoleId())
                      .map(r -> r.getName())
                      .orElse("Unknown");
              String inviterName =
                  memberRepository
                      .findById(inv.getInvitedBy())
                      .map(m -> m.getName())
                      .orElse("Unknown");
              return InvitationResponse.from(inv, roleName, inviterName);
            })
        .toList();
  }
}
