package io.b2mash.b2b.b2bstrawman.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityMessageFormatterTest {

  private final ActivityMessageFormatter formatter = new ActivityMessageFormatter();

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();
  private static final OrgRole MEMBER_ROLE =
      new OrgRole("Member", "member", "Default member role", true);
  private static final Member ACTOR_MEMBER =
      new Member("clerk_user_1", "alice@test.com", "Alice", null, MEMBER_ROLE);

  private Map<UUID, Member> actorMap() {
    return Map.of(ACTOR_ID, ACTOR_MEMBER);
  }

  private Map<UUID, PortalContact> emptyPortalContactMap() {
    return Map.of();
  }

  private AuditEvent createEvent(String eventType, String entityType, Map<String, Object> details) {
    var record =
        new AuditEventRecord(
            eventType, entityType, ENTITY_ID, ACTOR_ID, "USER", "API", null, null, details);
    return new AuditEvent(record);
  }

  @Test
  void taskCreatedProducesCorrectMessage() {
    var event = createEvent("task.created", "task", Map.of("title", "Fix login bug"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice created task \"Fix login bug\"");
    assertThat(item.actorName()).isEqualTo("Alice");
    assertThat(item.entityType()).isEqualTo("task");
    assertThat(item.entityName()).isEqualTo("Fix login bug");
  }

  @Test
  void taskUpdatedWithAssigneeProducesAssignmentMessage() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Old Title", "to", "Fix login bug"),
                "assignee_id", Map.of("from", "", "to", UUID.randomUUID().toString())));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice assigned task \"Fix login bug\"");
  }

  @Test
  void taskUpdatedWithStatusProducesStatusChangeMessage() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Fix login bug", "to", "Fix login bug"),
                "status", Map.of("from", "OPEN", "to", "IN_PROGRESS")));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo("Alice changed task \"Fix login bug\" status to IN_PROGRESS");
  }

  @Test
  void taskUpdatedGenericProducesUpdateMessage() {
    var event =
        createEvent("task.updated", "task", Map.of("title", Map.of("from", "Old", "to", "New")));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice updated task \"New\"");
  }

  @Test
  void taskUpdatedAssigneeOnlyWithPlainTitleShowsTaskTitle() {
    // GAP-L-05 regression: when only the assignee changes, TaskService puts a plain-string
    // "title" into details via putIfAbsent (no delta). The formatter must render the title,
    // not the literal string "unknown".
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title",
                "Draft particulars of claim",
                "assignee_id",
                Map.of("from", "", "to", UUID.randomUUID().toString())));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice assigned task \"Draft particulars of claim\"");
    assertThat(item.message()).doesNotContain("unknown");
  }

  @Test
  void taskUpdatedWithBothAssigneeAndStatusPrefersAssignment() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Task", "to", "Task"),
                "assignee_id", Map.of("from", "", "to", UUID.randomUUID().toString()),
                "status", Map.of("from", "OPEN", "to", "IN_PROGRESS")));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).contains("assigned task");
  }

  @Test
  void taskClaimedProducesCorrectMessage() {
    var event =
        createEvent(
            "task.claimed",
            "task",
            Map.of("title", "Fix login bug", "assignee_id", ACTOR_ID.toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice claimed task \"Fix login bug\"");
  }

  @Test
  void taskReleasedProducesCorrectMessage() {
    var event =
        createEvent(
            "task.released",
            "task",
            Map.of("title", "Fix login bug", "previous_assignee_id", ACTOR_ID.toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice released task \"Fix login bug\"");
  }

  @Test
  void documentUploadedProducesCorrectMessage() {
    var event = createEvent("document.uploaded", "document", Map.of("file_name", "Q4 Report.pdf"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice uploaded document \"Q4 Report.pdf\"");
    assertThat(item.entityName()).isEqualTo("Q4 Report.pdf");
  }

  @Test
  void documentDeletedProducesCorrectMessage() {
    var event = createEvent("document.deleted", "document", Map.of("file_name", "Q4 Report.pdf"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice deleted document \"Q4 Report.pdf\"");
  }

  @Test
  void commentCreatedWithEntityNameProducesCorrectMessage() {
    var event =
        createEvent(
            "comment.created",
            "comment",
            Map.of(
                "entity_type", "TASK",
                "entity_id", UUID.randomUUID().toString(),
                "entity_name", "Pre-trial conference preparation",
                "body", "Great work!"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo("Alice commented on task \"Pre-trial conference preparation\"");
  }

  @Test
  void commentCreatedOnProjectWithEntityNameProducesCorrectMessage() {
    var event =
        createEvent(
            "comment.created",
            "comment",
            Map.of(
                "entity_type", "PROJECT",
                "entity_name", "Kgosi Holdings -- FY2025/26 Year-End Pack",
                "body", "@Bob Need FS draft by day 30"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo("Alice commented on project \"Kgosi Holdings -- FY2025/26 Year-End Pack\"");
    assertThat(item.message()).doesNotContain("'project'");
  }

  @Test
  void commentCreatedWithoutEntityNameFallsBackToEntityType() {
    var event =
        createEvent(
            "comment.created",
            "comment",
            Map.of(
                "entity_type", "TASK",
                "entity_id", UUID.randomUUID().toString(),
                "body", "Great work!"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice commented on task \"task\"");
  }

  @Test
  void timeEntryCreatedFormatsDuration() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 150, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).contains("Alice logged 2h 30m on task");
  }

  @Test
  void timeEntryCreatedFormatsMinutesOnly() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 45, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).contains("logged 45m");
  }

  @Test
  void timeEntryCreatedFormatsHoursOnly() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 120, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).contains("logged 2h");
  }

  @Test
  void projectMemberAddedProducesCorrectMessage() {
    var event = createEvent("project_member.added", "project_member", Map.of("name", "Bob"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice added Bob to the project");
    assertThat(item.entityName()).isEqualTo("Bob");
  }

  @Test
  void projectMemberRemovedProducesCorrectMessage() {
    var event = createEvent("project_member.removed", "project_member", Map.of("name", "Bob"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice removed Bob from the project");
  }

  @Test
  void unknownEventTypeProducesFallbackMessage() {
    var event = createEvent("customer.updated", "customer", Map.of());
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice performed customer.updated on customer");
  }

  @Test
  void nullActorIdProducesSystemName() {
    var record =
        new AuditEventRecord(
            "task.created",
            "task",
            ENTITY_ID,
            null,
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            Map.of("title", "Auto-created task"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.actorName()).isEqualTo("System");
    assertThat(item.actorAvatarUrl()).isNull();
    assertThat(item.message()).contains("System created task");
  }

  @Test
  void unknownActorIdFallsBackToUnknown() {
    UUID unknownId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "task.created",
            "task",
            ENTITY_ID,
            unknownId,
            "USER",
            "API",
            null,
            null,
            Map.of("title", "Some task"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.actorName()).isEqualTo("Unknown");
  }

  @Test
  void portalUserActorNameResolvedFromDetails() {
    UUID portalUserId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "comment.created",
            "comment",
            ENTITY_ID,
            portalUserId,
            "PORTAL_USER",
            "PORTAL",
            null,
            null,
            Map.of("actor_name", "Jane Customer", "body", "Hello"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.actorName()).isEqualTo("Jane Customer");
  }

  @Test
  void documentGeneratedProducesCorrectMessage() {
    var event =
        createEvent(
            "document.generated",
            "generated_document",
            Map.of("file_name", "engagement-letter.pdf", "template_name", "Engagement Letter"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo(
            "Alice generated document \"engagement-letter.pdf\" from template \"Engagement Letter\"");
    assertThat(item.entityName()).isEqualTo("engagement-letter.pdf");
  }

  @Test
  void docxDocumentGeneratedProducesCorrectMessage() {
    var event =
        createEvent(
            "docx_document.generated",
            "generated_document",
            Map.of("fileName", "invoice-acme.docx", "template_name", "Invoice Template"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).contains("generated document").contains("Invoice Template");
  }

  @Test
  void informationRequestItemAcceptedInterpolatesRequestNumber() {
    // BUG-CYCLE26-10 regression: acceptItem now writes request_number into audit details
    // so the activity feed renders "for REQ-0042" instead of literal "for unknown".
    var event =
        createEvent(
            "information_request.item_accepted",
            "request_item",
            Map.of("request_number", "REQ-0042", "item_name", "ID copy"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice accepted \"ID copy\" for REQ-0042");
    assertThat(item.message()).doesNotContain("unknown");
  }

  // ---------- OBS-504: info-request sent recipient attribution ----------

  /**
   * OBS-504: when the sent-event details carry a {@code contact_name}, the activity feed names the
   * recipient contact — not the sending actor.
   */
  @Test
  void informationRequestSentWithContactNameNamesTheRecipient() {
    var event =
        createEvent(
            "information_request.sent",
            "information_request",
            Map.of("request_number", "REQ-0001", "contact_name", "Sipho Dlamini"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Information request REQ-0001 sent to Sipho Dlamini");
  }

  /**
   * OBS-504 regression: when {@code contact_name} is absent, the message must render neutral copy —
   * never the actor name (the sender). Pre-fix this rendered "sent to Alice" (the actor),
   * misattributing the recipient.
   */
  @Test
  void informationRequestSentWithoutContactNameRendersNeutralCopyNotActor() {
    var event =
        createEvent(
            "information_request.sent",
            "information_request",
            Map.of("request_number", "REQ-0001"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Information request REQ-0001 sent to the client contact");
    assertThat(item.message()).doesNotContain("Alice");
  }

  // ---------- OBS-Cycle55-PortalContactBucketedAsSystem ----------

  /**
   * PORTAL_CONTACT actor with a non-null {@code display_name} renders the display name (e.g. "Sipho
   * Dlamini downloaded …") instead of bucketing under "System" / "Unknown".
   */
  @Test
  void portalContactWithDisplayNameRendersDisplayName() {
    UUID portalContactId = UUID.randomUUID();
    PortalContact contact =
        TestIds.withId(
            new PortalContact(
                "org_test",
                UUID.randomUUID(),
                "sipho@example.com",
                "Sipho Dlamini",
                PortalContact.ContactRole.PRIMARY),
            portalContactId);

    var record =
        new AuditEventRecord(
            "document.uploaded",
            "document",
            ENTITY_ID,
            portalContactId,
            "PORTAL_CONTACT",
            "PORTAL",
            null,
            null,
            Map.of("file_name", "ID copy.pdf"));
    var event = new AuditEvent(record);

    var item = formatter.format(event, Map.of(), Map.of(portalContactId, contact));

    assertThat(item.actorName()).isEqualTo("Sipho Dlamini");
    assertThat(item.actorAvatarUrl()).isNull();
    assertThat(item.message()).isEqualTo("Sipho Dlamini uploaded document \"ID copy.pdf\"");
    assertThat(item.message()).doesNotContain("System").doesNotContain("Unknown");
  }

  /**
   * PORTAL_CONTACT actor with NULL {@code display_name} but non-null {@code email} falls back to
   * the email as the actor label.
   */
  @Test
  void portalContactWithoutDisplayNameFallsBackToEmail() {
    UUID portalContactId = UUID.randomUUID();
    PortalContact contact =
        TestIds.withId(
            new PortalContact(
                "org_test",
                UUID.randomUUID(),
                "client@example.com",
                null,
                PortalContact.ContactRole.GENERAL),
            portalContactId);

    var record =
        new AuditEventRecord(
            "document.uploaded",
            "document",
            ENTITY_ID,
            portalContactId,
            "PORTAL_CONTACT",
            "PORTAL",
            null,
            null,
            Map.of("file_name", "FICA.pdf"));
    var event = new AuditEvent(record);

    var item = formatter.format(event, Map.of(), Map.of(portalContactId, contact));

    assertThat(item.actorName()).isEqualTo("client@example.com");
    assertThat(item.actorAvatarUrl()).isNull();
    assertThat(item.message()).isEqualTo("client@example.com uploaded document \"FICA.pdf\"");
  }

  /**
   * PORTAL_CONTACT actor whose UUID is not in {@code portalContactMap} (anonymized / archived /
   * orphan) falls back to {@code details.actor_name}, then to the literal "Portal user". Covers
   * GAP-L-39 anonymized-customer edge.
   */
  @Test
  void portalContactOrphanFallsBackToDetailsThenPortalUser() {
    UUID orphanContactId = UUID.randomUUID();

    // 3a — orphan with details.actor_name → use the legacy emitter-side hint.
    var withDetailsRecord =
        new AuditEventRecord(
            "document.uploaded",
            "document",
            ENTITY_ID,
            orphanContactId,
            "PORTAL_CONTACT",
            "PORTAL",
            null,
            null,
            Map.of("file_name", "doc.pdf", "actor_name", "Anonymized Contact"));
    var withDetailsEvent = new AuditEvent(withDetailsRecord);
    var withDetailsItem = formatter.format(withDetailsEvent, Map.of(), Map.of());
    assertThat(withDetailsItem.actorName()).isEqualTo("Anonymized Contact");

    // 3b — orphan with no actor_name in details → final fallback.
    var bareRecord =
        new AuditEventRecord(
            "document.uploaded",
            "document",
            ENTITY_ID,
            orphanContactId,
            "PORTAL_CONTACT",
            "PORTAL",
            null,
            null,
            Map.of("file_name", "doc.pdf"));
    var bareEvent = new AuditEvent(bareRecord);
    var bareItem = formatter.format(bareEvent, Map.of(), Map.of());
    assertThat(bareItem.actorName()).isEqualTo("Portal user");
    assertThat(bareItem.actorAvatarUrl()).isNull();
    assertThat(bareItem.message()).doesNotContain("System").doesNotContain("Unknown");
  }

  // ---------- LZKC-019: portal/document/statement/closure events must render friendly copy ----
  // Pre-fix these all fell through to the raw default:
  // "<actor> performed <event.key> on <entity_type>".

  private AuditEvent portalContactEvent(
      String eventType, String entityType, UUID portalContactId, Map<String, Object> details) {
    var record =
        new AuditEventRecord(
            eventType,
            entityType,
            ENTITY_ID,
            portalContactId,
            "PORTAL_CONTACT",
            "PORTAL",
            null,
            null,
            details);
    return new AuditEvent(record);
  }

  private PortalContact siphoContact(UUID portalContactId) {
    return TestIds.withId(
        new PortalContact(
            "org_test",
            UUID.randomUUID(),
            "sipho@example.com",
            "Sipho Dlamini",
            PortalContact.ContactRole.PRIMARY),
        portalContactId);
  }

  @Test
  void portalDocumentDownloadedRendersFileNameNotRawKey() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.document.downloaded",
            "document",
            portalContactId,
            Map.of(
                "file_name",
                "statement-of-account-2026-03.pdf",
                "project_id",
                UUID.randomUUID().toString(),
                "scope",
                "PROJECT",
                "customer_id",
                UUID.randomUUID().toString()));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message())
        .isEqualTo("Sipho Dlamini downloaded document \"statement-of-account-2026-03.pdf\"");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void portalDocumentUploadInitiatedRendersFileName() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.document.upload_initiated",
            "document",
            portalContactId,
            Map.of("file_name", "FICA-ID-copy.pdf", "request_id", UUID.randomUUID().toString()));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message())
        .isEqualTo("Sipho Dlamini started uploading document \"FICA-ID-copy.pdf\"");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void portalRequestItemSubmittedRendersItemAndRequestNumber() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.request_item.submitted",
            "request_item",
            portalContactId,
            Map.of("item_name", "Proof of address", "request_number", "REQ-0042"));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message())
        .isEqualTo("Sipho Dlamini submitted \"Proof of address\" for REQ-0042");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void informationRequestItemSubmittedAliasStillRendersFriendlyCopy() {
    // Legacy key kept as an alias of portal.request_item.submitted (zero-risk, dead emitter).
    var event =
        createEvent(
            "information_request.item_submitted",
            "request_item",
            Map.of("item_name", "ID copy", "request_number", "REQ-0007"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice submitted \"ID copy\" for REQ-0007");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void portalInvoicePaidRendersInvoiceNumber() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.invoice.paid",
            "invoice",
            portalContactId,
            Map.of("invoice_number", "INV-0012", "amount", "1500.00"));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message()).isEqualTo("Sipho Dlamini paid fee note INV-0012");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void portalInvoicePaidWithoutInvoiceNumberRendersNeutralCopy() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.invoice.paid", "invoice", portalContactId, Map.of("invoice_number", ""));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message()).isEqualTo("Sipho Dlamini paid a fee note");
  }

  @Test
  void portalDocumentAcknowledgedRendersFileNameWhenPresent() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent(
            "portal.document.acknowledged",
            "document",
            portalContactId,
            Map.of("file_name", "engagement-letter.pdf"));
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message())
        .isEqualTo("Sipho Dlamini acknowledged document \"engagement-letter.pdf\"");
  }

  @Test
  void portalDocumentAcknowledgedWithoutFileNameRendersNeutralCopy() {
    UUID portalContactId = UUID.randomUUID();
    var event =
        portalContactEvent("portal.document.acknowledged", "document", portalContactId, Map.of());
    var item =
        formatter.format(event, Map.of(), Map.of(portalContactId, siphoContact(portalContactId)));
    assertThat(item.message()).isEqualTo("Sipho Dlamini acknowledged a document");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void statementGeneratedRendersFileName() {
    var event =
        createEvent(
            "statement.generated",
            "generated_document",
            Map.of(
                "file_name", "statement-of-account-2026-03.pdf",
                "project_id", UUID.randomUUID().toString(),
                "period_start", "2026-03-01",
                "period_end", "2026-03-31"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo("Alice generated a statement of account \"statement-of-account-2026-03.pdf\"");
    assertThat(item.message()).doesNotContain("performed");
    assertThat(item.entityName()).isEqualTo("statement-of-account-2026-03.pdf");
  }

  @Test
  void documentGeneratedWithClausesRendersTemplateName() {
    // Emitter (GeneratedDocumentService) carries template_name + clause metadata, no file_name.
    var event =
        createEvent(
            "document.generated_with_clauses",
            "generated_document",
            Map.of("template_name", "Engagement Letter", "clause_count", 3));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message())
        .isEqualTo("Alice generated a document with clauses from template \"Engagement Letter\"");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void documentCreatedRendersFileName() {
    var event =
        createEvent(
            "document.created", "document", Map.of("scope", "PROJECT", "file_name", "Mandate.pdf"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice created document \"Mandate.pdf\"");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void documentAccessedRendersFileName() {
    var event =
        createEvent(
            "document.accessed",
            "document",
            Map.of("scope", "PROJECT", "file_name", "Q4 Report.pdf"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice downloaded document \"Q4 Report.pdf\"");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void documentVisibilityChangedRendersNewVisibility() {
    // Emitter (DocumentService.toggleVisibility) carries only a visibility from/to delta.
    var event =
        createEvent(
            "document.visibility_changed",
            "document",
            Map.of("visibility", Map.of("from", "INTERNAL", "to", "SHARED")));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice changed document visibility to SHARED");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void matterClosureClosedRendersFriendlyCopy() {
    var event =
        createEvent(
            "matter_closure.closed",
            "project",
            Map.of("reason", "COMPLETED", "override_used", false));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice closed the matter");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void matterClosureReopenedRendersFriendlyCopy() {
    var event =
        createEvent(
            "matter_closure.reopened",
            "project",
            Map.of("closure_log_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice reopened the matter");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void matterClosureOverrideUsedRendersFriendlyCopy() {
    var event =
        createEvent(
            "matter.closure.override_used",
            "matter_closure",
            Map.of("justification", "Client instruction", "reason", "COMPLETED"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice used an override to close the matter");
    assertThat(item.message()).doesNotContain("performed");
  }
}
