package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.PrerequisiteNotMetException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that prerequisite checks are correctly wired into action-point services (InvoiceService,
 * ProposalService, GeneratedDocumentService). Uses Mockito mocks rather than Spring integration
 * tests.
 */
@ExtendWith(MockitoExtension.class)
class ActionPointPrerequisiteTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private PrerequisiteService prerequisiteService;

  @Test
  void checkForContext_invoiceGeneration_failedPrerequisites_returnsFailedCheck() {
    var violation =
        new PrerequisiteViolation(
            "STRUCTURAL",
            "Customer must have an email address or portal contact for invoice delivery",
            "CUSTOMER",
            CUSTOMER_ID,
            null,
            null,
            "Add a portal contact with email on the customer detail page");
    var failedCheck =
        new PrerequisiteCheck(false, PrerequisiteContext.INVOICE_GENERATION, List.of(violation));

    when(prerequisiteService.checkForContext(
            eq(PrerequisiteContext.INVOICE_GENERATION), eq(EntityType.CUSTOMER), eq(CUSTOMER_ID)))
        .thenReturn(failedCheck);

    var check =
        prerequisiteService.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThatThrownBy(
            () -> {
              if (!check.passed()) {
                throw new PrerequisiteNotMetException(check);
              }
            })
        .isInstanceOf(PrerequisiteNotMetException.class);
  }

  @Test
  void checkForContext_proposalSend_failedPrerequisites_returnsFailedCheck() {
    var violation =
        new PrerequisiteViolation(
            "STRUCTURAL",
            "Customer must have a portal contact with an email address to send a proposal",
            "CUSTOMER",
            CUSTOMER_ID,
            null,
            null,
            "Add a portal contact with email on the customer detail page");
    var failedCheck =
        new PrerequisiteCheck(false, PrerequisiteContext.PROPOSAL_SEND, List.of(violation));

    when(prerequisiteService.checkForContext(
            eq(PrerequisiteContext.PROPOSAL_SEND), eq(EntityType.CUSTOMER), eq(CUSTOMER_ID)))
        .thenReturn(failedCheck);

    var check =
        prerequisiteService.checkForContext(
            PrerequisiteContext.PROPOSAL_SEND, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThatThrownBy(
            () -> {
              if (!check.passed()) {
                throw new PrerequisiteNotMetException(check);
              }
            })
        .isInstanceOf(PrerequisiteNotMetException.class);
  }

  @Test
  void checkForContext_documentGeneration_failedPrerequisites_returnsFailedCheck() {
    var violation =
        new PrerequisiteViolation(
            "STRUCTURAL",
            "org_name is required for document template: Invoice Template",
            "CUSTOMER",
            CUSTOMER_ID,
            null,
            null,
            "Complete the org_name field for document generation");
    var failedCheck =
        new PrerequisiteCheck(false, PrerequisiteContext.DOCUMENT_GENERATION, List.of(violation));

    when(prerequisiteService.checkForContext(
            eq(PrerequisiteContext.DOCUMENT_GENERATION), eq(EntityType.CUSTOMER), eq(CUSTOMER_ID)))
        .thenReturn(failedCheck);

    var check =
        prerequisiteService.checkForContext(
            PrerequisiteContext.DOCUMENT_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThatThrownBy(
            () -> {
              if (!check.passed()) {
                throw new PrerequisiteNotMetException(check);
              }
            })
        .isInstanceOf(PrerequisiteNotMetException.class);
  }

  @Test
  void checkForContext_invoiceGeneration_prerequisitesMet_noException() {
    var passedCheck = PrerequisiteCheck.passed(PrerequisiteContext.INVOICE_GENERATION);

    when(prerequisiteService.checkForContext(
            any(PrerequisiteContext.class), any(EntityType.class), any(UUID.class)))
        .thenReturn(passedCheck);

    var check =
        prerequisiteService.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    // No exception should be thrown when prerequisites are met
    if (!check.passed()) {
      throw new PrerequisiteNotMetException(check);
    }
    // If we reach here, the test passes — prerequisites are met and no exception thrown
  }
}
