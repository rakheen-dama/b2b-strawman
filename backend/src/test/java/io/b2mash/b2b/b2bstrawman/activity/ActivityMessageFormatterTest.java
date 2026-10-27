package io.b2mash.b2b.b2bstrawman.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        .isEqualTo("Alice commented on matter \"Kgosi Holdings -- FY2025/26 Year-End Pack\"");
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
    assertThat(item.message()).isEqualTo("Alice added Bob to the matter team");
    assertThat(item.entityName()).isEqualTo("Bob");
  }

  @Test
  void projectMemberRemovedProducesCorrectMessage() {
    var event = createEvent("project_member.removed", "project_member", Map.of("name", "Bob"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice removed Bob from the matter team");
  }

  @Test
  void unknownEventTypeProducesFallbackMessage() {
    // 625B (ADR-349): the default is the legal generic (verb map + noun map), never the raw
    // "<actor> performed <key> on <entity_type>".
    var event = createEvent("customer.updated", "customer", Map.of());
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice updated the client");
    assertThat(item.message()).doesNotContain("performed").doesNotContain("customer.updated");
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

  // ---------- COLLECTIONS — Phase 83 (Epic 588B). Dunning lifecycle + policy changes. ----------

  @Test
  void collectionsPolicyUpdatedRendersFriendlyCopy() {
    var event =
        createEvent(
            "collections.policy.updated",
            "org_settings",
            Map.of("stage1_days", Map.of("from", 7, "to", 5)));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice updated the collections policy");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void collectionsReminderSentRendersInvoiceNumberAndEntityName() {
    var event =
        createEvent(
            "collections.reminder.sent",
            "collection_activity",
            Map.of("invoice_number", "INV-0042", "stage", "STAGE_1"));
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("Alice sent a collection reminder for INV-0042");
    assertThat(item.message()).doesNotContain("performed");
    assertThat(item.entityName()).isEqualTo("INV-0042");
  }

  @Test
  void collectionsReminderCancelledRendersNeutralSystemCopy() {
    var record =
        new AuditEventRecord(
            "collections.reminder.cancelled",
            "collection_activity",
            ENTITY_ID,
            null,
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            Map.of("invoice_number", "INV-0042"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("A collection reminder was cancelled for INV-0042");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void collectionsEscalationFlaggedRendersSystemCopy() {
    var record =
        new AuditEventRecord(
            "collections.escalation.flagged",
            "collection_activity",
            ENTITY_ID,
            null,
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            Map.of("invoice_number", "INV-0042"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("INV-0042 was flagged for collections escalation");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void collectionsDigestSentRendersSystemCopy() {
    var record =
        new AuditEventRecord(
            "collections.digest.sent",
            "org_settings",
            ENTITY_ID,
            null,
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            Map.of());
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of(), emptyPortalContactMap());
    assertThat(item.message()).isEqualTo("The weekly cash digest was sent");
    assertThat(item.message()).doesNotContain("performed");
  }

  @Test
  void collectionActivityEntityNameFallsBackWhenNoInvoiceNumber() {
    var event = createEvent("collections.reminder.sent", "collection_activity", Map.of());
    var item = formatter.format(event, actorMap(), emptyPortalContactMap());
    assertThat(item.entityName()).isEqualTo("collection activity");
  }

  // ---------- 625B (U5, ADR-349): legal templates for every feed-reachable key ----------
  // Money renders through VariableFormatter.formatCurrencyText (JDK en-ZA: "R50 000,00" with a
  // U+00A0 grouping separator). Missing details drop their fragment — never "unknown", "()",
  // "R0,00" or a raw dotted key.

  private static final String NB = " ";

  private static Map<String, Object> d(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private String message(String eventType, String entityType, Map<String, Object> details) {
    return formatter
        .format(createEvent(eventType, entityType, details), actorMap(), Map.of())
        .message();
  }

  private String systemMessage(String eventType, String entityType, Map<String, Object> details) {
    var record =
        new AuditEventRecord(
            eventType, entityType, ENTITY_ID, null, "SYSTEM", "INTERNAL", null, null, details);
    return formatter.format(new AuditEvent(record), Map.of(), Map.of()).message();
  }

  private String portalMessage(String eventType, String entityType, Map<String, Object> details) {
    UUID portalContactId = UUID.randomUUID();
    return formatter
        .format(
            portalContactEvent(eventType, entityType, portalContactId, details),
            Map.of(),
            Map.of(portalContactId, siphoContact(portalContactId)))
        .message();
  }

  static Stream<Arguments> legalTemplates() {
    return Stream.of(
        // --- matter ---
        Arguments.of(
            "project.created",
            "project",
            d("name", "Estate Late Dlamini"),
            "Alice opened the matter \"Estate Late Dlamini\""),
        Arguments.of("project.created", "project", d(), "Alice opened the matter"),
        Arguments.of(
            "project.created_from_template",
            "project",
            d(
                "template_name",
                "Deceased Estate Administration",
                "project_name",
                "Estate Late Dlamini",
                "customer_name",
                "Sipho Dlamini"),
            "Alice opened the matter from template \"Deceased Estate Administration\""),
        Arguments.of("project.created_from_template", "project", d(), "Alice opened the matter"),
        Arguments.of(
            "project.updated",
            "project",
            d("name", Map.of("from", "Old", "to", "New")),
            "Alice updated the matter"),
        Arguments.of("project.completed", "project", d(), "Alice completed the matter"),
        Arguments.of("project.archived", "project", d("name", "X"), "Alice archived the matter"),
        Arguments.of("project.reopened", "project", d("name", "X"), "Alice reopened the matter"),
        Arguments.of(
            "project.deleted",
            "project",
            d("name", "Estate Late Dlamini"),
            "Alice deleted the matter \"Estate Late Dlamini\""),
        Arguments.of("project.deleted", "project", d(), "Alice deleted the matter"),
        Arguments.of(
            "project_member.added",
            "project_member",
            d("role", "member"),
            "Alice added a member to the matter team"),
        Arguments.of(
            "project_member.role_changed",
            "project_member",
            d("role", Map.of("from", "member", "to", "lead")),
            "Alice made a team member the matter lead"),
        Arguments.of(
            "project_member.role_changed",
            "project_member",
            d("role", Map.of("from", "lead", "to", "member")),
            "Alice changed a matter team member's role to member"),
        Arguments.of(
            "project_member.role_changed",
            "project_member",
            d(),
            "Alice changed a matter team member's role"),
        Arguments.of(
            "budget.created",
            "project_budget",
            d("budget_amount", "50000", "budget_currency", "ZAR", "budget_hours", "40"),
            "Alice set the matter budget of R50" + NB + "000,00"),
        Arguments.of(
            "budget.created",
            "project_budget",
            d("budget_hours", "40"),
            "Alice set the matter budget"),
        Arguments.of(
            "budget.updated",
            "project_budget",
            d("budget_amount", "75000.00", "budget_currency", "ZAR"),
            "Alice updated the matter budget to R75" + NB + "000,00"),
        Arguments.of("budget.updated", "project_budget", d(), "Alice updated the matter budget"),
        Arguments.of("budget.deleted", "project_budget", d(), "Alice removed the matter budget"),
        Arguments.of("customer.linked", "customer", d(), "Alice linked a client to the matter"),
        Arguments.of(
            "customer.unlinked", "customer", d(), "Alice unlinked a client from the matter"),
        // --- disbursements ---
        Arguments.of(
            "disbursement.created",
            "legal_disbursement",
            d("amount", "3500", "category", "COUNSEL_FEES", "vat_treatment", "STANDARD_15"),
            "Alice recorded a disbursement of R3" + NB + "500,00 (Counsel fees)"),
        Arguments.of(
            "disbursement.created", "legal_disbursement", d(), "Alice recorded a disbursement"),
        Arguments.of(
            "disbursement.updated", "legal_disbursement", d(), "Alice updated a disbursement"),
        Arguments.of(
            "disbursement.submitted",
            "legal_disbursement",
            d(),
            "Alice submitted a disbursement for approval"),
        Arguments.of(
            "disbursement.approved", "legal_disbursement", d(), "Alice approved a disbursement"),
        Arguments.of(
            "disbursement.rejected", "legal_disbursement", d(), "Alice rejected a disbursement"),
        Arguments.of(
            "disbursement.written_off",
            "legal_disbursement",
            d("reason", "Client indigent"),
            "Alice wrote off a disbursement"),
        Arguments.of(
            "disbursement.restored",
            "legal_disbursement",
            d(),
            "Alice restored a written-off disbursement"),
        Arguments.of(
            "disbursement.billed",
            "legal_disbursement",
            d("invoice_line_id", UUID.randomUUID().toString()),
            "Alice billed a disbursement on a fee note"),
        Arguments.of(
            "disbursement.unmarked_billed",
            "legal_disbursement",
            d(),
            "Alice removed a disbursement from a fee note"),
        Arguments.of(
            "disbursement.receipt_attached",
            "legal_disbursement",
            d("file_name", "sheriff-receipt.pdf"),
            "Alice attached receipt \"sheriff-receipt.pdf\" to a disbursement"),
        Arguments.of(
            "disbursement.receipt_attached",
            "legal_disbursement",
            d(),
            "Alice attached a receipt to a disbursement"),
        // --- fee notes ---
        Arguments.of(
            "invoice.created",
            "invoice",
            d("currency", "ZAR", "subtotal", "5000.00", "customer_name", "Sipho Dlamini"),
            "Alice created a draft fee note"),
        Arguments.of(
            "invoice.approved",
            "invoice",
            d("invoice_number", "INV-0003", "total", "5750.00"),
            "Alice approved fee note INV-0003"),
        Arguments.of("invoice.approved", "invoice", d(), "Alice approved a fee note"),
        Arguments.of(
            "invoice.sent",
            "invoice",
            d("invoice_number", "INV-0003"),
            "Alice sent fee note INV-0003"),
        Arguments.of("invoice.sent", "invoice", d("invoice_number", ""), "Alice sent a fee note"),
        Arguments.of(
            "invoice.partially_paid",
            "invoice",
            d("invoice_number", "INV-0004", "amount", "5000", "balance_due", "750.00"),
            "Alice recorded a part payment of R5" + NB + "000,00 on fee note INV-0004"),
        Arguments.of(
            "invoice.partially_paid",
            "invoice",
            d(),
            "Alice recorded a part payment on a fee note"),
        Arguments.of(
            "invoice.paid",
            "invoice",
            d("invoice_number", "INV-0004", "amount", "750.00"),
            "Alice recorded a payment of R750,00 on fee note INV-0004 — paid in full"),
        Arguments.of(
            "invoice.payment_excess",
            "invoice",
            d("invoice_number", "INV-0004", "amount", "1000", "excess", "250.00"),
            "Alice recorded an overpayment of R250,00 on fee note INV-0004"),
        Arguments.of(
            "invoice.payment_reversed",
            "invoice",
            d("invoice_number", "INV-0004", "amount", "750.00", "partial", "false"),
            "Alice reversed a payment of R750,00 on fee note INV-0004"),
        Arguments.of(
            "invoice.payment_partially_reversed",
            "invoice",
            d("invoice_number", "INV-0004", "amount", "100", "partial", "true"),
            "Alice reversed a payment of R100,00 on fee note INV-0004"),
        Arguments.of(
            "invoice.voided",
            "invoice",
            d("invoice_number", "INV-0004", "total", "5750.00"),
            "Alice voided fee note INV-0004"),
        Arguments.of(
            "invoice.updated", "invoice", d("action", "line_added"), "Alice updated a fee note"),
        Arguments.of("invoice.deleted", "invoice", d(), "Alice deleted a fee note"),
        Arguments.of(
            "payment.failed",
            "invoice",
            d("provider", "payfast", "invoice_number", "INV-0004"),
            "Online payment failed for fee note INV-0004"),
        Arguments.of(
            "payment.session.cancelled",
            "invoice",
            d("action", "cancelled"),
            "An online payment link was cancelled"),
        Arguments.of(
            "payment.session.expired",
            "invoice",
            d("invoice_number", "INV-0004"),
            "The online payment link for fee note INV-0004 expired"),
        Arguments.of("payment.session.expired", "invoice", d(), "An online payment link expired"),
        // --- acceptance (member actor) ---
        Arguments.of(
            "acceptance.created",
            "acceptance_request",
            d("document_file_name", "engagement-letter.pdf", "contact_name", "Sipho Dlamini"),
            "Alice created an acceptance request for \"engagement-letter.pdf\""),
        Arguments.of(
            "acceptance.created",
            "acceptance_request",
            d(),
            "Alice created an acceptance request for a document"),
        Arguments.of(
            "acceptance.created",
            "acceptance_request",
            d("template_name", "Engagement Letter", "document_file_name", "engagement-letter.pdf"),
            "Alice created an acceptance request for \"Engagement Letter\""),
        Arguments.of(
            "acceptance.sent",
            "acceptance_request",
            d(
                "document_file_name",
                "engagement-letter.pdf",
                "contact_name",
                "Sipho Dlamini",
                "contact_email",
                "sipho@example.com"),
            "Alice sent \"engagement-letter.pdf\" to Sipho Dlamini for acceptance"),
        Arguments.of(
            "acceptance.sent",
            "acceptance_request",
            d("document_name", "Offer to Purchase"),
            "Alice sent \"Offer to Purchase\" for acceptance"),
        Arguments.of(
            "acceptance.reminded",
            "acceptance_request",
            d("reminder_count", 2),
            "Alice sent an acceptance reminder"),
        Arguments.of(
            "acceptance.revoked",
            "acceptance_request",
            d("revoked_by", UUID.randomUUID().toString()),
            "Alice revoked an acceptance request"),
        Arguments.of(
            "acceptance.revoked",
            "acceptance_request",
            d("reason", "auto_revoked"),
            "An earlier acceptance request was withdrawn"),
        // --- trust (recorded) ---
        Arguments.of(
            "trust_deposit.recorded",
            "trust_transaction",
            d("amount", "50000", "reference", "Deposit — Dlamini", "customer_name", "Sipho"),
            "Alice recorded a trust deposit of R50" + NB + "000,00 (Deposit — Dlamini)"),
        Arguments.of(
            "trust_deposit.recorded",
            "trust_transaction",
            d("amount", "100", "reference", " "),
            "Alice recorded a trust deposit of R100,00"),
        Arguments.of(
            "trust_deposit.recorded",
            "trust_transaction",
            d("amount", "not-a-number", "reference", "DEP-1"),
            "Alice recorded a trust deposit (DEP-1)"),
        Arguments.of(
            "trust_payment.recorded",
            "trust_transaction",
            d("amount", "12000", "reference", "PAY-1"),
            "Alice recorded a trust payment of R12" + NB + "000,00 (PAY-1)"),
        Arguments.of(
            "trust_transfer.recorded",
            "trust_transaction",
            d("amount", "2000.50", "reference", "TRF-1"),
            "Alice recorded a trust transfer of R2" + NB + "000,50 (TRF-1)"),
        Arguments.of(
            "trust_fee_transfer.recorded",
            "trust_transaction",
            d("amount", "5750.00", "reference", "FEE-INV-0003"),
            "Alice recorded a fee transfer of R5" + NB + "750,00 from trust (FEE-INV-0003)"),
        Arguments.of(
            "trust_refund.recorded",
            "trust_transaction",
            d("amount", "300", "reference", "REF-1"),
            "Alice recorded a trust refund of R300,00 (REF-1)"),
        Arguments.of(
            "trust_transaction.reversed",
            "trust_transaction",
            d("original_type", "DEPOSIT", "amount", "1000", "reason", "Captured twice"),
            "Alice reversed a trust deposit of R1" + NB + "000,00"),
        Arguments.of(
            "trust_transaction.reversed",
            "trust_transaction",
            d(),
            "Alice reversed a trust transaction"),
        // --- expenses ---
        Arguments.of(
            "expense.created",
            "expense",
            d("amount", "450.00", "currency", "ZAR", "category", "COURIER"),
            "Alice recorded an expense of R450,00 (Courier)"),
        Arguments.of(
            "expense.created",
            "expense",
            d("amount", "1500", "currency", "USD", "category", "TRAVEL"),
            "Alice recorded an expense of $1,500.00 (Travel)"),
        Arguments.of("expense.updated", "expense", d(), "Alice updated an expense"),
        Arguments.of("expense.deleted", "expense", d("amount", "10"), "Alice deleted an expense"),
        Arguments.of("expense.restored", "expense", d(), "Alice restored an expense"),
        Arguments.of("expense.written_off", "expense", d(), "Alice wrote off an expense"),
        // --- court calendar / prescription ---
        Arguments.of(
            "court_date.created",
            "court_date",
            d("date_type", "HEARING", "scheduled_date", "2026-11-05", "court_name", "Gauteng"),
            "Alice diarised a court date for 5 November 2026"),
        Arguments.of(
            "court_date.created",
            "court_date",
            d("scheduled_date", "garbage"),
            "Alice diarised a court date"),
        Arguments.of("court_date.updated", "court_date", d(), "Alice updated a court date"),
        Arguments.of(
            "court_date.postponed",
            "court_date",
            d("new_date", "2027-01-15", "reason", "Counsel unavailable"),
            "Alice postponed a court date to 15 January 2027"),
        Arguments.of("court_date.postponed", "court_date", d(), "Alice postponed a court date"),
        Arguments.of("court_date.cancelled", "court_date", d(), "Alice cancelled a court date"),
        Arguments.of(
            "court_date.outcome_recorded",
            "court_date",
            d("outcome", "Granted"),
            "Alice recorded the outcome of a court date"),
        Arguments.of(
            "prescription_tracker.created",
            "prescription_tracker",
            d("prescription_type", "GENERAL_3Y", "prescription_date", "2029-03-01"),
            "Alice started tracking prescription (prescribes 1 March 2029)"),
        Arguments.of(
            "prescription_tracker.created",
            "prescription_tracker",
            d(),
            "Alice started tracking prescription"),
        Arguments.of(
            "prescription_tracker.updated",
            "prescription_tracker",
            d(),
            "Alice updated a prescription tracker"),
        Arguments.of(
            "prescription_tracker.interrupted",
            "prescription_tracker",
            d("interruption_date", "2026-10-01"),
            "Alice recorded an interruption of prescription"),
        // --- adverse parties ---
        Arguments.of(
            "adverse_party.linked",
            "adverse_party_link",
            d("adverse_party_name", "Road Accident Fund", "relationship", "OPPOSING_PARTY"),
            "Alice linked adverse party \"Road Accident Fund\" to the matter"),
        Arguments.of(
            "adverse_party.linked",
            "adverse_party_link",
            d(),
            "Alice linked an adverse party to the matter"),
        Arguments.of(
            "adverse_party.unlinked",
            "adverse_party_link",
            d(),
            "Alice unlinked an adverse party from the matter"),
        Arguments.of(
            "adverse_party.created",
            "adverse_party",
            d("name", "Road Accident Fund"),
            "Alice added adverse party \"Road Accident Fund\""),
        Arguments.of(
            "adverse_party.updated",
            "adverse_party",
            d("name", "Road Accident Fund"),
            "Alice updated adverse party \"Road Accident Fund\""),
        Arguments.of(
            "adverse_party.deleted", "adverse_party", d(), "Alice removed an adverse party"),
        // --- tasks ---
        Arguments.of(
            "task.completed",
            "task",
            d("title", "Lodge estate with Master"),
            "Alice completed task \"Lodge estate with Master\""),
        Arguments.of("task.completed", "task", d(), "Alice completed a task"),
        Arguments.of(
            "task.cancelled",
            "task",
            d("title", "Draft will"),
            "Alice cancelled task \"Draft will\""),
        Arguments.of(
            "task.reopened",
            "task",
            d("title", "Draft will"),
            "Alice reopened task \"Draft will\""),
        Arguments.of(
            "task.deleted", "task", d("title", "Draft will"), "Alice deleted task \"Draft will\""),
        Arguments.of(
            "task.recurrence_created",
            "task",
            d("title", "Monthly trust check"),
            "Alice created the next occurrence of task \"Monthly trust check\""),
        Arguments.of(
            "task_item.created",
            "task_item",
            d("title", "Certified ID"),
            "Alice added item \"Certified ID\" to a task"),
        Arguments.of("task_item.created", "task_item", d(), "Alice added an item to a task"),
        Arguments.of(
            "task_item.deleted",
            "task_item",
            d("title", "Certified ID"),
            "Alice removed item \"Certified ID\" from a task"),
        Arguments.of(
            "task_item.toggled",
            "task_item",
            d("completed", Map.of("from", false, "to", true)),
            "Alice ticked off a task item"),
        Arguments.of(
            "task_item.toggled",
            "task_item",
            d("completed", Map.of("from", true, "to", false)),
            "Alice unticked a task item"),
        Arguments.of("task_item.updated", "task_item", d(), "Alice updated a task item"),
        Arguments.of("task_item.reordered", "task", d(), "Alice reordered a task's items"),
        Arguments.of(
            "time_entry.rate_re_snapshot",
            "time_entry",
            d("entries_updated", 3, "entries_processed", 4),
            "Alice refreshed billing rates on 3 time entries"),
        Arguments.of(
            "time_entry.rate_re_snapshot",
            "time_entry",
            d("entries_updated", 1),
            "Alice refreshed billing rates on 1 time entry"),
        Arguments.of(
            "time_entry.rate_re_snapshot",
            "time_entry",
            d(),
            "Alice refreshed billing rates on time entries"),
        // --- client-tab keys (checklists / lifecycle) ---
        Arguments.of(
            "checklist.item.completed",
            "checklist_instance_item",
            d("itemName", "Certified ID copy", "instanceId", UUID.randomUUID().toString()),
            "Alice completed checklist item \"Certified ID copy\""),
        Arguments.of(
            "checklist.item.skipped",
            "checklist_instance_item",
            d("itemName", "Proof of address", "reason", "N/A"),
            "Alice skipped checklist item \"Proof of address\""),
        Arguments.of(
            "checklist.item.reopened",
            "checklist_instance_item",
            d("itemName", "Proof of address"),
            "Alice reopened checklist item \"Proof of address\""),
        Arguments.of(
            "checklist.item.completed",
            "checklist_instance_item",
            d(),
            "Alice completed a checklist item"),
        Arguments.of(
            "checklist.instance.created",
            "checklist_instance",
            d("templateName", "FICA — Individual"),
            "Alice started checklist \"FICA — Individual\""),
        Arguments.of(
            "checklist.instance.completed",
            "checklist_instance",
            d("customerId", UUID.randomUUID().toString()),
            "Alice completed a checklist"),
        Arguments.of(
            "customer.lifecycle.transitioned",
            "customer",
            d("oldStatus", "ONBOARDING", "newStatus", "ACTIVE"),
            "Alice moved the client from Onboarding to Active"),
        Arguments.of(
            "customer.lifecycle.transitioned",
            "customer",
            d("newStatus", "DORMANT"),
            "Alice moved the client to Dormant"),
        Arguments.of(
            "customer.lifecycle.transitioned",
            "customer",
            d(),
            "Alice changed the client's status"),
        // --- legal generic default (no template) ---
        Arguments.of("customer.created", "customer", d("name", "X"), "Alice created the client"),
        Arguments.of("customer.archived", "customer", d(), "Alice archived the client"),
        Arguments.of("customer.unarchived", "customer", d(), "Alice restored the client"),
        Arguments.of("invoice.custom_fields_updated", "invoice", d(), "Alice updated a fee note"),
        Arguments.of(
            "trust_account.created", "trust_account", d(), "Alice created a trust account"),
        Arguments.of(
            "information_request.frobnicated",
            "information_request",
            d(),
            "Alice updated an information request"),
        Arguments.of("tariff_item.created", "tariff_item", d(), "Alice created a tariff item"),
        Arguments.of("org_unit.deleted", "org_unit", d(), "Alice deleted an org unit"),
        Arguments.of(
            "conflict_check.performed",
            "conflict_check",
            d("checked_name", "Road Accident Fund", "result", "NO_CONFLICT"),
            "Alice ran a conflict check"),
        // --- R-8 legal wording of pre-existing templates ---
        Arguments.of(
            "comment.created",
            "comment",
            d("entity_type", "CUSTOMER", "entity_name", "Sipho Dlamini"),
            "Alice commented on client \"Sipho Dlamini\""),
        Arguments.of(
            "comment.updated",
            "comment",
            d("entity_type", "INVOICE", "entity_name", "INV-0003"),
            "Alice edited a comment on fee note \"INV-0003\""),
        Arguments.of(
            "comment.deleted",
            "comment",
            d("entity_type", "PROJECT"),
            "Alice deleted a comment on matter \"matter\""),
        Arguments.of(
            "collections.reminder.sent",
            "collection_activity",
            d(),
            "Alice sent a collection reminder for an overdue fee note"));
  }

  @ParameterizedTest(name = "{0} {2} -> {3}")
  @MethodSource("legalTemplates")
  void legalTemplateRendersSentence(
      String eventType, String entityType, Map<String, Object> details, String expected) {
    assertThat(message(eventType, entityType, details)).isEqualTo(expected);
  }

  @Test
  void portalContactAcceptanceEventsNameTheContactAndFallBackToADocument() {
    var noName = d("ip_address", "10.0.0.1", "project_id", UUID.randomUUID().toString());
    assertThat(portalMessage("acceptance.viewed", "acceptance_request", noName))
        .isEqualTo("Sipho Dlamini viewed a document sent for acceptance");
    assertThat(
            portalMessage(
                "acceptance.accepted",
                "acceptance_request",
                d("acceptor_name", "Sipho Dlamini", "ip_address", "10.0.0.1")))
        .isEqualTo("Sipho Dlamini accepted a document sent for acceptance");
    assertThat(
            portalMessage(
                "acceptance.declined", "acceptance_request", d("decline_reason", "Fees too high")))
        .isEqualTo("Sipho Dlamini declined a document sent for acceptance");
    // R-4: the most human name present wins once a publisher carries one.
    assertThat(
            portalMessage(
                "acceptance.declined",
                "acceptance_request",
                d("document_name", "Offer to Purchase", "document_file_name", "otp.pdf")))
        .isEqualTo("Sipho Dlamini declined \"Offer to Purchase\"");
    assertThat(
            portalMessage(
                "acceptance.accepted", "acceptance_request", d("document_file_name", "letter.pdf")))
        .isEqualTo("Sipho Dlamini accepted \"letter.pdf\"");
  }

  @Test
  void systemEmittedEventsRenderWithoutAnActor() {
    assertThat(
            systemMessage(
                "acceptance.certificate_generated",
                "acceptance_request",
                d("certificate_file_name", "Certificate.pdf")))
        .isEqualTo("Certificate of acceptance generated");
    assertThat(
            systemMessage(
                "acceptance.certificate_generated",
                "acceptance_request",
                d("document_file_name", "letter.pdf")))
        .isEqualTo("Certificate of acceptance generated for \"letter.pdf\"");
    assertThat(systemMessage("acceptance.expired", "acceptance_request", d("expired_at", "x")))
        .isEqualTo("An acceptance request expired");
    assertThat(
            systemMessage(
                "payment.completed",
                "invoice",
                d("provider", "payfast", "amount", "5750.00", "session_id", "s1")))
        .isEqualTo("Online payment of R5" + NB + "750,00 received");
    assertThat(systemMessage("payment.completed", "invoice", d()))
        .isEqualTo("Online payment received");
    assertThat(
            systemMessage(
                "payment.session.created",
                "invoice",
                d("provider", "payfast", "amount", "5750.00")))
        .isEqualTo("An online payment link was created for R5" + NB + "750,00");
    assertThat(systemMessage("payment.session.created", "invoice", d()))
        .isEqualTo("An online payment link was created");
    assertThat(
            systemMessage(
                "information_request.reminder_sent",
                "information_request",
                d("request_number", "REQ-0007", "outstanding_items", 2)))
        .isEqualTo("Reminder sent for information request REQ-0007");
    assertThat(systemMessage("information_request.reminder_sent", "information_request", d()))
        .isEqualTo("Reminder sent for an information request");
    // A null actor on a webhook-posted card payment reads "System …", never "performed".
    assertThat(
            systemMessage(
                "invoice.paid", "invoice", d("invoice_number", "INV-0009", "amount", "100")))
        .isEqualTo("System recorded a payment of R100,00 on fee note INV-0009 — paid in full");
  }

  static Stream<Arguments> computedTrustApprovalKeys() {
    return Stream.of(
        Arguments.of(
            "trust_payment.approved",
            "PAYMENT",
            "Alice approved the trust payment of R2" + NB + "500,00"),
        Arguments.of(
            "trust_fee_transfer.approved",
            "FEE_TRANSFER",
            "Alice approved the fee transfer of R2" + NB + "500,00"),
        Arguments.of(
            "trust_refund.approved",
            "REFUND",
            "Alice approved the trust refund of R2" + NB + "500,00"),
        Arguments.of(
            "trust_reversal.approved",
            "REVERSAL",
            "Alice approved the trust reversal of R2" + NB + "500,00"),
        Arguments.of(
            "trust_payment.rejected",
            "PAYMENT",
            "Alice rejected the trust payment of R2" + NB + "500,00"),
        Arguments.of(
            "trust_fee_transfer.rejected",
            "FEE_TRANSFER",
            "Alice rejected the fee transfer of R2" + NB + "500,00"),
        Arguments.of(
            "trust_refund.rejected",
            "REFUND",
            "Alice rejected the trust refund of R2" + NB + "500,00"),
        Arguments.of(
            "trust_reversal.rejected",
            "REVERSAL",
            "Alice rejected the trust reversal of R2" + NB + "500,00"),
        Arguments.of(
            "trust_payment.first_approved",
            "PAYMENT",
            "Alice gave first approval to the trust payment of R2" + NB + "500,00"),
        Arguments.of(
            "trust_fee_transfer.first_approved",
            "FEE_TRANSFER",
            "Alice gave first approval to the fee transfer of R2" + NB + "500,00"),
        Arguments.of(
            "trust_refund.first_approved",
            "REFUND",
            "Alice gave first approval to the trust refund of R2" + NB + "500,00"),
        Arguments.of(
            "trust_reversal.first_approved",
            "REVERSAL",
            "Alice gave first approval to the trust reversal of R2" + NB + "500,00"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("computedTrustApprovalKeys")
  void computedTrustApprovalKeysRenderTheTransactionType(
      String eventType, String transactionType, String expected) {
    assertThat(
            message(
                eventType,
                "trust_transaction",
                d(
                    "transaction_type",
                    transactionType,
                    "amount",
                    "2500.00",
                    "approved_by",
                    UUID.randomUUID().toString())))
        .isEqualTo(expected);
  }

  @Test
  void computedTrustApprovalWithoutTransactionTypeOrAmountUsesTheKey() {
    assertThat(message("trust_payment.approved", "trust_transaction", d()))
        .isEqualTo("Alice approved the trust payment");
    assertThat(message("trust_interest_credit.approved", "trust_transaction", d()))
        .isEqualTo("Alice approved the interest credit");
  }

  /** Every key the publishers emit today that can reach the matter feed / Home, plus neighbours. */
  private static final List<String[]> INVENTORY_KEYS =
      List.of(
          new String[] {"project.created", "project"},
          new String[] {"project.created_from_template", "project"},
          new String[] {"project.updated", "project"},
          new String[] {"project.completed", "project"},
          new String[] {"project.archived", "project"},
          new String[] {"project.reopened", "project"},
          new String[] {"project.deleted", "project"},
          new String[] {"project_member.role_changed", "project_member"},
          new String[] {"budget.created", "project_budget"},
          new String[] {"budget.updated", "project_budget"},
          new String[] {"budget.deleted", "project_budget"},
          new String[] {"customer.linked", "customer"},
          new String[] {"customer.unlinked", "customer"},
          new String[] {"disbursement.created", "legal_disbursement"},
          new String[] {"disbursement.updated", "legal_disbursement"},
          new String[] {"disbursement.submitted", "legal_disbursement"},
          new String[] {"disbursement.approved", "legal_disbursement"},
          new String[] {"disbursement.rejected", "legal_disbursement"},
          new String[] {"disbursement.written_off", "legal_disbursement"},
          new String[] {"disbursement.restored", "legal_disbursement"},
          new String[] {"disbursement.billed", "legal_disbursement"},
          new String[] {"disbursement.unmarked_billed", "legal_disbursement"},
          new String[] {"disbursement.receipt_attached", "legal_disbursement"},
          new String[] {"invoice.created", "invoice"},
          new String[] {"invoice.approved", "invoice"},
          new String[] {"invoice.sent", "invoice"},
          new String[] {"invoice.partially_paid", "invoice"},
          new String[] {"invoice.paid", "invoice"},
          new String[] {"invoice.payment_excess", "invoice"},
          new String[] {"invoice.payment_reversed", "invoice"},
          new String[] {"invoice.payment_partially_reversed", "invoice"},
          new String[] {"invoice.voided", "invoice"},
          new String[] {"invoice.updated", "invoice"},
          new String[] {"invoice.deleted", "invoice"},
          new String[] {"invoice.custom_fields_updated", "invoice"},
          new String[] {"invoice.field_groups_updated", "invoice"},
          new String[] {"payment.completed", "invoice"},
          new String[] {"payment.failed", "invoice"},
          new String[] {"payment.session.created", "invoice"},
          new String[] {"payment.session.cancelled", "invoice"},
          new String[] {"payment.session.expired", "invoice"},
          new String[] {"acceptance.created", "acceptance_request"},
          new String[] {"acceptance.sent", "acceptance_request"},
          new String[] {"acceptance.viewed", "acceptance_request"},
          new String[] {"acceptance.accepted", "acceptance_request"},
          new String[] {"acceptance.declined", "acceptance_request"},
          new String[] {"acceptance.certificate_generated", "acceptance_request"},
          new String[] {"acceptance.reminded", "acceptance_request"},
          new String[] {"acceptance.revoked", "acceptance_request"},
          new String[] {"acceptance.expired", "acceptance_request"},
          new String[] {"trust_deposit.recorded", "trust_transaction"},
          new String[] {"trust_payment.recorded", "trust_transaction"},
          new String[] {"trust_transfer.recorded", "trust_transaction"},
          new String[] {"trust_fee_transfer.recorded", "trust_transaction"},
          new String[] {"trust_refund.recorded", "trust_transaction"},
          new String[] {"trust_payment.approved", "trust_transaction"},
          new String[] {"trust_fee_transfer.approved", "trust_transaction"},
          new String[] {"trust_refund.approved", "trust_transaction"},
          new String[] {"trust_reversal.approved", "trust_transaction"},
          new String[] {"trust_payment.rejected", "trust_transaction"},
          new String[] {"trust_payment.first_approved", "trust_transaction"},
          new String[] {"trust_transaction.reversed", "trust_transaction"},
          new String[] {"trust_transaction.awaiting_approval", "trust_transaction"},
          new String[] {"trust_account.created", "trust_account"},
          new String[] {"trust_investment.placed", "trust_investment"},
          new String[] {"trust_reconciliation.completed", "trust_reconciliation"},
          new String[] {"expense.created", "expense"},
          new String[] {"expense.updated", "expense"},
          new String[] {"expense.deleted", "expense"},
          new String[] {"expense.restored", "expense"},
          new String[] {"expense.written_off", "expense"},
          new String[] {"court_date.created", "court_date"},
          new String[] {"court_date.updated", "court_date"},
          new String[] {"court_date.postponed", "court_date"},
          new String[] {"court_date.cancelled", "court_date"},
          new String[] {"court_date.outcome_recorded", "court_date"},
          new String[] {"prescription_tracker.created", "prescription_tracker"},
          new String[] {"prescription_tracker.updated", "prescription_tracker"},
          new String[] {"prescription_tracker.interrupted", "prescription_tracker"},
          new String[] {"adverse_party.linked", "adverse_party_link"},
          new String[] {"adverse_party.unlinked", "adverse_party_link"},
          new String[] {"adverse_party.created", "adverse_party"},
          new String[] {"adverse_party.updated", "adverse_party"},
          new String[] {"adverse_party.deleted", "adverse_party"},
          new String[] {"task.completed", "task"},
          new String[] {"task.cancelled", "task"},
          new String[] {"task.reopened", "task"},
          new String[] {"task.deleted", "task"},
          new String[] {"task.recurrence_created", "task"},
          new String[] {"task_item.created", "task_item"},
          new String[] {"task_item.toggled", "task_item"},
          new String[] {"task_item.updated", "task_item"},
          new String[] {"task_item.deleted", "task_item"},
          new String[] {"task_item.reordered", "task"},
          new String[] {"time_entry.rate_re_snapshot", "time_entry"},
          new String[] {"information_request.reminder_sent", "information_request"},
          new String[] {"checklist.item.completed", "checklist_instance_item"},
          new String[] {"checklist.item.skipped", "checklist_instance_item"},
          new String[] {"checklist.item.reopened", "checklist_instance_item"},
          new String[] {"checklist.instance.created", "checklist_instance"},
          new String[] {"checklist.instance.completed", "checklist_instance"},
          new String[] {"customer.created", "customer"},
          new String[] {"customer.updated", "customer"},
          new String[] {"customer.archived", "customer"},
          new String[] {"customer.unarchived", "customer"},
          new String[] {"customer.lifecycle.transitioned", "customer"},
          new String[] {"customer.lifecycle.auto_dormancy", "customer"},
          new String[] {"matter_closure.closed", "project"},
          new String[] {"matter_closure.reopened", "project"},
          new String[] {"matter.closure.override_used", "matter_closure"},
          new String[] {"portal.invoice.paid", "invoice"},
          new String[] {"conflict_check.performed", "conflict_check"});

  /** Stored entity literals that are not the legal word (ADR-342 keys, never shown). */
  private static final List<String> RAW_ENTITY_WORDS = List.of("project", "customer", "invoice");

  @Test
  void noInventoryKeyPrintsPerformedOrARawKeyOrAMissingDetailPlaceholder() {
    for (String[] key : INVENTORY_KEYS) {
      for (Map<String, Object> details : List.of(Map.<String, Object>of(), d("amount", ""))) {
        String member = message(key[0], key[1], details);
        String system = systemMessage(key[0], key[1], details);
        for (String sentence : List.of(member, system)) {
          if (key[1].contains("_") || RAW_ENTITY_WORDS.contains(key[1])) {
            assertThat(sentence).as("raw entity literal in %s", key[0]).doesNotContain(key[1]);
          }
          assertThat(sentence)
              .as("%s on %s with %s", key[0], key[1], details)
              .doesNotContain("performed")
              .doesNotContain(key[0])
              .doesNotContain("unknown")
              .doesNotContain("null")
              .doesNotContain("()")
              .doesNotContain("\"\"")
              .doesNotContain("R0,00")
              .doesNotContain("  ")
              .isNotBlank();
        }
      }
    }
  }

  // ---------- 625B: amount redaction (FINANCIAL_VISIBILITY / VIEW_TRUST; owner/admin = ALL) ----

  private String messageAs(
      ActivityMessageFormatter.AmountVisibility visibility,
      String eventType,
      String entityType,
      Map<String, Object> details) {
    return formatter
        .format(createEvent(eventType, entityType, details), actorMap(), Map.of(), visibility)
        .message();
  }

  @Test
  void withoutFinancialVisibilityFeeNoteAndOtherAmountsAreDropped() {
    var memberView = new ActivityMessageFormatter.AmountVisibility(false, true);
    assertThat(
            messageAs(
                memberView,
                "invoice.partially_paid",
                "invoice",
                d("invoice_number", "INV-0004", "amount", "5000")))
        .isEqualTo("Alice recorded a part payment on fee note INV-0004");
    assertThat(
            messageAs(
                memberView,
                "invoice.payment_excess",
                "invoice",
                d("invoice_number", "INV-0004", "excess", "250")))
        .isEqualTo("Alice recorded an overpayment on fee note INV-0004");
    assertThat(
            messageAs(
                memberView,
                "budget.updated",
                "project_budget",
                d("budget_amount", "75000", "budget_currency", "ZAR")))
        .isEqualTo("Alice updated the matter budget");
    assertThat(
            messageAs(
                memberView,
                "disbursement.created",
                "legal_disbursement",
                d("amount", "3500", "category", "COUNSEL_FEES")))
        .isEqualTo("Alice recorded a disbursement (Counsel fees)");
    // VIEW_TRUST alone still reads trust amounts and references.
    assertThat(
            messageAs(
                memberView,
                "trust_deposit.recorded",
                "trust_transaction",
                d("amount", "50000", "reference", "DEP-1")))
        .isEqualTo("Alice recorded a trust deposit of R50" + NB + "000,00 (DEP-1)");
  }

  @Test
  void withoutViewTrustTrustAmountsAndReferencesAreDropped() {
    var noTrust = new ActivityMessageFormatter.AmountVisibility(true, false);
    assertThat(
            messageAs(
                noTrust,
                "trust_deposit.recorded",
                "trust_transaction",
                d("amount", "50000", "reference", "DEP-1")))
        .isEqualTo("Alice recorded a trust deposit");
    assertThat(
            messageAs(
                noTrust,
                "trust_payment.approved",
                "trust_transaction",
                d("transaction_type", "PAYMENT", "amount", "2500")))
        .isEqualTo("Alice approved the trust payment");
    assertThat(
            messageAs(
                noTrust, "invoice.sent", "invoice", d("invoice_number", "INV-0004", "amount", "1")))
        .isEqualTo("Alice sent fee note INV-0004");
    // ALL (owner/admin) keeps every amount; the details map is never mutated.
    var details = d("invoice_number", "INV-0004", "amount", "5000");
    assertThat(
            messageAs(
                new ActivityMessageFormatter.AmountVisibility(false, false),
                "invoice.paid",
                "invoice",
                details))
        .isEqualTo("Alice recorded a payment on fee note INV-0004 — paid in full");
    assertThat(details).containsKey("amount");
    assertThat(
            messageAs(
                ActivityMessageFormatter.AmountVisibility.ALL, "invoice.paid", "invoice", details))
        .isEqualTo(
            "Alice recorded a payment of R5" + NB + "000,00 on fee note INV-0004 — paid in full");
  }

  @Test
  void unknownKeysAndEntityTypesFallBackToTheLegalGeneric() {
    for (int i = 0; i < 25; i++) {
      String noise = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
      String sentence = message("zz" + noise + ".x" + noise, "zz_" + noise, d());
      assertThat(sentence)
          .isEqualTo("Alice updated a zz " + noise)
          .doesNotContain("performed")
          .doesNotContain(".");
    }
    assertThat(message("weird", "invoice", d())).isEqualTo("Alice updated a fee note");
    assertThat(message("legal_disbursement.frobbed", "legal_disbursement", d()))
        .isEqualTo("Alice updated a disbursement");
    assertThat(message("trust_transaction.awaiting_approval", "trust_transaction", d()))
        .isEqualTo("Alice updated a trust transaction");
    assertThat(message("project.frobbed", "project", d())).isEqualTo("Alice updated the matter");
    assertThat(message("something.archived", "acceptance_request", d()))
        .isEqualTo("Alice archived an acceptance request");
  }
}
