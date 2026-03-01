package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProposalTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID CREATED_BY_ID = UUID.randomUUID();
  private static final UUID PORTAL_CONTACT_ID = UUID.randomUUID();

  private Proposal buildProposal() {
    return new Proposal("PROP-0001", "Test Proposal", CUSTOMER_ID, FeeModel.FIXED, CREATED_BY_ID);
  }

  @Test
  void constructor_setsDraftStatus() {
    var proposal = buildProposal();

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.DRAFT);
    assertThat(proposal.getProposalNumber()).isEqualTo("PROP-0001");
    assertThat(proposal.getTitle()).isEqualTo("Test Proposal");
    assertThat(proposal.getCustomerId()).isEqualTo(CUSTOMER_ID);
    assertThat(proposal.getFeeModel()).isEqualTo(FeeModel.FIXED);
    assertThat(proposal.getCreatedById()).isEqualTo(CREATED_BY_ID);
    assertThat(proposal.getId()).isNull();
    assertThat(proposal.getSentAt()).isNull();
    assertThat(proposal.getAcceptedAt()).isNull();
    assertThat(proposal.getDeclinedAt()).isNull();
  }

  @Test
  void markSent_fromDraft_succeeds() {
    var proposal = buildProposal();

    proposal.markSent(PORTAL_CONTACT_ID);

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SENT);
    assertThat(proposal.getPortalContactId()).isEqualTo(PORTAL_CONTACT_ID);
    assertThat(proposal.getSentAt()).isNotNull();
  }

  @Test
  void markSent_fromSent_throws() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    assertThatThrownBy(() -> proposal.markSent(PORTAL_CONTACT_ID))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void markSent_requiresPortalContactId() {
    var proposal = buildProposal();

    assertThatThrownBy(() -> proposal.markSent(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void markAccepted_fromSent_succeeds() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    proposal.markAccepted();

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    assertThat(proposal.getAcceptedAt()).isNotNull();
  }

  @Test
  void markAccepted_fromDraft_throws() {
    var proposal = buildProposal();

    assertThatThrownBy(() -> proposal.markAccepted()).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void markDeclined_fromSent_succeeds() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    proposal.markDeclined("Too expensive");

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.DECLINED);
    assertThat(proposal.getDeclineReason()).isEqualTo("Too expensive");
    assertThat(proposal.getDeclinedAt()).isNotNull();
  }

  @Test
  void markDeclined_setsReasonAndTimestamp() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    proposal.markDeclined("Budget constraints");

    assertThat(proposal.getDeclineReason()).isEqualTo("Budget constraints");
    assertThat(proposal.getDeclinedAt()).isNotNull();
  }

  @Test
  void markExpired_fromSent_succeeds() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    proposal.markExpired();

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
  }

  @Test
  void markExpired_fromAccepted_throws() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);
    proposal.markAccepted();

    assertThatThrownBy(() -> proposal.markExpired()).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void isEditable_trueForDraft_falseForOthers() {
    var draft = buildProposal();
    assertThat(draft.isEditable()).isTrue();

    var sent = buildProposal();
    sent.markSent(PORTAL_CONTACT_ID);
    assertThat(sent.isEditable()).isFalse();

    var accepted = buildProposal();
    accepted.markSent(PORTAL_CONTACT_ID);
    accepted.markAccepted();
    assertThat(accepted.isEditable()).isFalse();

    var declined = buildProposal();
    declined.markSent(PORTAL_CONTACT_ID);
    declined.markDeclined("No thanks");
    assertThat(declined.isEditable()).isFalse();

    var expired = buildProposal();
    expired.markSent(PORTAL_CONTACT_ID);
    expired.markExpired();
    assertThat(expired.isEditable()).isFalse();
  }

  @Test
  void isTerminal_trueForAcceptedDeclinedExpired() {
    var draft = buildProposal();
    assertThat(draft.isTerminal()).isFalse();

    var sent = buildProposal();
    sent.markSent(PORTAL_CONTACT_ID);
    assertThat(sent.isTerminal()).isFalse();

    var accepted = buildProposal();
    accepted.markSent(PORTAL_CONTACT_ID);
    accepted.markAccepted();
    assertThat(accepted.isTerminal()).isTrue();

    var declined = buildProposal();
    declined.markSent(PORTAL_CONTACT_ID);
    declined.markDeclined("Reason");
    assertThat(declined.isTerminal()).isTrue();

    var expired = buildProposal();
    expired.markSent(PORTAL_CONTACT_ID);
    expired.markExpired();
    assertThat(expired.isTerminal()).isTrue();
  }

  @Test
  void requireEditable_throwsForNonDraft() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    assertThatThrownBy(() -> proposal.requireEditable()).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void setTitle_onDraft_succeeds() {
    var proposal = buildProposal();

    proposal.setTitle("Updated Title");

    assertThat(proposal.getTitle()).isEqualTo("Updated Title");
  }

  @Test
  void setTitle_onSent_throws() {
    var proposal = buildProposal();
    proposal.markSent(PORTAL_CONTACT_ID);

    assertThatThrownBy(() -> proposal.setTitle("New Title"))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void setContentJson_onDraft_succeeds() {
    var proposal = buildProposal();
    var content = Map.<String, Object>of("type", "doc", "content", "hello");

    proposal.setContentJson(content);

    assertThat(proposal.getContentJson()).isEqualTo(content);
  }

  @Test
  void feeModel_displayLabels() {
    assertThat(FeeModel.FIXED.getDisplayLabel()).isEqualTo("Fixed Fee");
    assertThat(FeeModel.HOURLY.getDisplayLabel()).isEqualTo("Hourly");
    assertThat(FeeModel.RETAINER.getDisplayLabel()).isEqualTo("Retainer");
  }
}
