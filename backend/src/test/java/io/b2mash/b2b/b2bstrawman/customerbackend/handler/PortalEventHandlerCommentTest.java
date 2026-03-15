package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for comment-related event handling in {@link PortalEventHandler}. Verifies observable
 * behavior (what data is projected and what counts are updated) rather than internal call
 * sequences.
 */
@ExtendWith(MockitoExtension.class)
class PortalEventHandlerCommentTest {

  private static final String ORG_ID = "org_test";
  private static final String TENANT_ID = "tenant_test";

  @Mock private PortalReadModelRepository readModelRepo;
  @Mock private ProjectRepository projectRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceLineRepository invoiceLineRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private MemberRepository memberRepository;

  @InjectMocks private PortalEventHandler handler;

  // ── 1. SHARED comment projected to portal_comments ──────────────────

  @Test
  void onCommentCreated_shared_upsertsPortalCommentAndIncrementsCount() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var now = Instant.now();
    var event =
        new CommentCreatedEvent(
            "COMMENT_CREATED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            "Alice Smith",
            TENANT_ID,
            ORG_ID,
            now,
            Map.of("body", "Hello from main app"),
            "PROJECT",
            projectId,
            "SHARED");

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customerId));

    handler.onCommentCreated(event);

    verify(readModelRepo)
        .upsertPortalComment(
            eq(commentId),
            eq(ORG_ID),
            eq(projectId),
            eq("Alice Smith"),
            eq("Hello from main app"),
            eq(now));
    verify(readModelRepo).incrementCommentCount(projectId, customerId);
  }

  // ── 2. INTERNAL comment is NOT projected ────────────────────────────

  @Test
  void onCommentCreated_internal_skipsProjection() {
    var event =
        new CommentCreatedEvent(
            "COMMENT_CREATED",
            "COMMENT",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bob",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            Map.of("body", "Internal note"),
            "TASK",
            UUID.randomUUID(),
            "INTERNAL");

    handler.onCommentCreated(event);

    verify(readModelRepo, never()).upsertPortalComment(any(), any(), any(), any(), any(), any());
    verify(readModelRepo, never()).incrementCommentCount(any(), any());
  }

  // ── 3. Visibility SHARED -> INTERNAL deletes projection ─────────────

  @Test
  void onCommentVisibilityChanged_fromShared_deletesAndDecrements() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var event =
        new CommentVisibilityChangedEvent(
            "COMMENT_VISIBILITY_CHANGED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            "Alice",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "SHARED",
            "INTERNAL");

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customerId));

    handler.onCommentVisibilityChanged(event);

    verify(readModelRepo).deletePortalComment(commentId, ORG_ID);
    verify(readModelRepo).decrementCommentCount(projectId, customerId);
  }

  // ── 4. Visibility INTERNAL -> SHARED creates projection ─────────────

  @Test
  void onCommentVisibilityChanged_toShared_upsertsWithOriginalAuthorName() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var authorMemberId = UUID.randomUUID();
    var createdAt = Instant.now().minusSeconds(3600);
    var event =
        new CommentVisibilityChangedEvent(
            "COMMENT_VISIBILITY_CHANGED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            "Visibility Changer",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "INTERNAL",
            "SHARED");

    var comment =
        createComment(commentId, projectId, "Shared comment body", createdAt, authorMemberId);
    when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
    var memberRole =
        new io.b2mash.b2b.b2bstrawman.orgrole.OrgRole("Member", "member", "Default role", true);
    var author = new Member("clerk_author", "author@test.com", "Original Author", null, memberRole);
    when(memberRepository.findById(authorMemberId)).thenReturn(Optional.of(author));
    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customerId));

    handler.onCommentVisibilityChanged(event);

    // Should use the original author's name, not the visibility changer's
    verify(readModelRepo)
        .upsertPortalComment(
            eq(commentId),
            eq(ORG_ID),
            eq(projectId),
            eq("Original Author"),
            eq("Shared comment body"),
            eq(createdAt));
    verify(readModelRepo).incrementCommentCount(projectId, customerId);
  }

  // ── 5. Deleted comment removed from portal ──────────────────────────

  @Test
  void onCommentDeleted_sharedComment_deletesAndDecrementsForAllCustomers() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customer1 = UUID.randomUUID();
    var customer2 = UUID.randomUUID();
    var event =
        new CommentDeletedEvent(
            "COMMENT_DELETED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            "Alice",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "PROJECT",
            projectId);

    when(readModelRepo.portalCommentExists(commentId, ORG_ID)).thenReturn(true);
    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customer1, customer2));

    handler.onCommentDeleted(event);

    verify(readModelRepo).deletePortalComment(commentId, ORG_ID);
    verify(readModelRepo, times(2)).decrementCommentCount(eq(projectId), any());
  }

  @Test
  void onCommentDeleted_internalComment_deletesButDoesNotDecrement() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event =
        new CommentDeletedEvent(
            "COMMENT_DELETED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            "Alice",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "TASK",
            UUID.randomUUID());

    // Comment was INTERNAL — never projected to portal
    when(readModelRepo.portalCommentExists(commentId, ORG_ID)).thenReturn(false);

    handler.onCommentDeleted(event);

    verify(readModelRepo).deletePortalComment(commentId, ORG_ID);
    verify(readModelRepo, never()).decrementCommentCount(any(), any());
  }

  // ── 6. Null actor name defaults to "Unknown" ───────────────────────

  @Test
  void onCommentCreated_nullActorName_defaultsToUnknown() {
    var commentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var now = Instant.now();
    var event =
        new CommentCreatedEvent(
            "COMMENT_CREATED",
            "COMMENT",
            commentId,
            projectId,
            UUID.randomUUID(),
            null,
            TENANT_ID,
            ORG_ID,
            now,
            Map.of("body", "Anonymous comment"),
            "PROJECT",
            projectId,
            "SHARED");

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customerId));

    handler.onCommentCreated(event);

    verify(readModelRepo)
        .upsertPortalComment(
            eq(commentId),
            eq(ORG_ID),
            eq(projectId),
            eq("Unknown"),
            eq("Anonymous comment"),
            eq(now));
  }

  // ── Helper methods ──────────────────────────────────────────────────

  private Comment createComment(
      UUID id, UUID projectId, String body, Instant createdAt, UUID authorMemberId) {
    var comment = new Comment("PROJECT", projectId, projectId, authorMemberId, body, "SHARED");
    TestIds.withId(comment, id);
    TestIds.withField(comment, "createdAt", createdAt);
    return comment;
  }
}
