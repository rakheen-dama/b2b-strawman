package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalReadModelRepositoryIntegrationTest {

  @Autowired private PortalReadModelRepository repository;

  private static final String ORG_ID = "org_test_readmodel";
  private static final String OTHER_ORG_ID = "org_test_readmodel_other";
  private final UUID customerId = UUID.randomUUID();
  private final UUID otherCustomerId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private final UUID otherProjectId = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  @BeforeAll
  void seedData() {
    // Seed a project for the primary customer
    repository.upsertPortalProject(
        projectId, customerId, ORG_ID, "Alpha Project", "ACTIVE", "Alpha description", now);

    // Seed another project for a different customer
    repository.upsertPortalProject(
        otherProjectId, otherCustomerId, ORG_ID, "Beta Project", "DRAFT", "Beta description", now);
  }

  @Test
  void upsertProjectInsertsAndUpdates() {
    UUID pid = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalProject(
        pid, cid, ORG_ID, "Original Name", "DRAFT", "Original desc", created);

    var before = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(before).isPresent();
    assertThat(before.get().name()).isEqualTo("Original Name");
    assertThat(before.get().status()).isEqualTo("DRAFT");

    // Update same project
    repository.upsertPortalProject(
        pid, cid, ORG_ID, "Updated Name", "ACTIVE", "Updated desc", created);

    var after = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(after).isPresent();
    assertThat(after.get().name()).isEqualTo("Updated Name");
    assertThat(after.get().status()).isEqualTo("ACTIVE");
    assertThat(after.get().description()).isEqualTo("Updated desc");
    // updated_at should be >= the original (both use now())
    assertThat(after.get().updatedAt()).isAfterOrEqualTo(before.get().updatedAt());
  }

  @Test
  void upsertDocumentInsertsAndUpdates() {
    UUID docId = UUID.randomUUID();
    Instant uploaded = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        customerId,
        projectId,
        "Original Doc",
        "application/pdf",
        1024L,
        "CUSTOMER",
        "s3://bucket/original.pdf",
        uploaded);

    var docs = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(docs).anyMatch(d -> d.id().equals(docId) && d.title().equals("Original Doc"));

    // Update same document
    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        customerId,
        projectId,
        "Updated Doc",
        "application/pdf",
        2048L,
        "INTERNAL",
        "s3://bucket/updated.pdf",
        uploaded);

    var updatedDocs = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(updatedDocs)
        .anyMatch(
            d -> d.id().equals(docId) && d.title().equals("Updated Doc") && d.size().equals(2048L));
  }

  @Test
  void upsertCommentInsertsAndUpdates() {
    UUID commentId = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalComment(
        commentId, ORG_ID, projectId, "Alice", "Original comment", created);

    var comments = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(comments)
        .anyMatch(c -> c.id().equals(commentId) && c.content().equals("Original comment"));

    // Update same comment
    repository.upsertPortalComment(
        commentId, ORG_ID, projectId, "Alice Updated", "Updated comment", created);

    var updatedComments = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(updatedComments)
        .anyMatch(
            c ->
                c.id().equals(commentId)
                    && c.authorName().equals("Alice Updated")
                    && c.content().equals("Updated comment"));
  }

  @Test
  void upsertProjectSummaryInsertsAndUpdates() {
    UUID pid = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant activity = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalProjectSummary(
        pid, cid, ORG_ID, new BigDecimal("10.50"), new BigDecimal("8.00"), activity);

    var summary = repository.findProjectSummary(pid, cid, ORG_ID);
    assertThat(summary).isPresent();
    assertThat(summary.get().totalHours()).isEqualByComparingTo(new BigDecimal("10.50"));
    assertThat(summary.get().billableHours()).isEqualByComparingTo(new BigDecimal("8.00"));

    // Update with new hours
    repository.upsertPortalProjectSummary(
        pid, cid, ORG_ID, new BigDecimal("20.00"), new BigDecimal("15.50"), activity);

    var updated = repository.findProjectSummary(pid, cid, ORG_ID);
    assertThat(updated).isPresent();
    assertThat(updated.get().totalHours()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(updated.get().billableHours()).isEqualByComparingTo(new BigDecimal("15.50"));
  }

  @Test
  void findProjectsByCustomerReturnsOnlyMatchingOrgAndCustomer() {
    var projects = repository.findProjectsByCustomer(ORG_ID, customerId);
    assertThat(projects).isNotEmpty();
    assertThat(projects)
        .allMatch(p -> p.orgId().equals(ORG_ID) && p.customerId().equals(customerId));

    // Other customer's projects should not appear
    assertThat(projects).noneMatch(p -> p.customerId().equals(otherCustomerId));
  }

  @Test
  void findDocumentsByProject() {
    UUID docId = UUID.randomUUID();
    Instant uploaded = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        customerId,
        projectId,
        "Project Doc",
        "text/plain",
        512L,
        "CUSTOMER",
        "s3://bucket/doc.txt",
        uploaded);

    var docs = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(docs).anyMatch(d -> d.id().equals(docId) && d.portalProjectId().equals(projectId));
  }

  @Test
  void findCommentsByProject() {
    UUID commentId = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalComment(commentId, ORG_ID, projectId, "Bob", "Test comment", created);

    var comments = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(comments).anyMatch(c -> c.id().equals(commentId) && c.authorName().equals("Bob"));
  }

  @Test
  void incrementAndDecrementDocumentCount() {
    UUID pid = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalProject(pid, cid, ORG_ID, "Count Test", "ACTIVE", null, created);

    // Initial count should be 0
    var initial = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(initial).isPresent();
    assertThat(initial.get().documentCount()).isEqualTo(0);

    // Increment twice
    repository.incrementDocumentCount(pid, cid);
    repository.incrementDocumentCount(pid, cid);
    var afterIncrement = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(afterIncrement.get().documentCount()).isEqualTo(2);

    // Decrement once
    repository.decrementDocumentCount(pid, cid);
    var afterDecrement = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(afterDecrement.get().documentCount()).isEqualTo(1);

    // Decrement below zero should floor at 0
    repository.decrementDocumentCount(pid, cid);
    repository.decrementDocumentCount(pid, cid);
    var afterFloor = repository.findProjectDetail(pid, cid, ORG_ID);
    assertThat(afterFloor.get().documentCount()).isEqualTo(0);
  }

  @Test
  void deleteProject() {
    UUID pid = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalProject(pid, cid, ORG_ID, "To Delete", "ACTIVE", null, created);
    assertThat(repository.findProjectDetail(pid, cid, ORG_ID)).isPresent();

    repository.deletePortalProject(pid, cid);
    assertThat(repository.findProjectDetail(pid, cid, ORG_ID)).isEmpty();
  }

  @Test
  void findCustomerIdsByProjectId() {
    UUID pid = UUID.randomUUID();
    UUID cid1 = UUID.randomUUID();
    UUID cid2 = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Same project projected for two customers
    repository.upsertPortalProject(pid, cid1, ORG_ID, "Shared Project", "ACTIVE", null, created);
    repository.upsertPortalProject(pid, cid2, ORG_ID, "Shared Project", "ACTIVE", null, created);

    var customerIds = repository.findCustomerIdsByProjectId(pid, ORG_ID);
    assertThat(customerIds).containsExactlyInAnyOrder(cid1, cid2);
  }

  @Test
  void deletePortalProjectsByOrgRemovesAllForOrg() {
    UUID pid1 = UUID.randomUUID();
    UUID pid2 = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    String resyncOrg = "org_resync_test";
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalProject(pid1, cid, resyncOrg, "Resync A", "ACTIVE", null, created);
    repository.upsertPortalProject(pid2, cid, resyncOrg, "Resync B", "DRAFT", null, created);

    assertThat(repository.findProjectsByCustomer(resyncOrg, cid)).hasSize(2);

    repository.deletePortalProjectsByOrg(resyncOrg);

    assertThat(repository.findProjectsByCustomer(resyncOrg, cid)).isEmpty();
  }

  @Test
  void findDocumentsByCustomer() {
    UUID docId = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant uploaded = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        cid,
        projectId,
        "Customer Doc",
        "image/png",
        4096L,
        "CUSTOMER",
        "s3://bucket/customer.png",
        uploaded);

    var docs = repository.findDocumentsByCustomer(ORG_ID, cid);
    assertThat(docs).anyMatch(d -> d.id().equals(docId) && d.customerId().equals(cid));

    // Should not return docs for other customers
    var otherDocs = repository.findDocumentsByCustomer(ORG_ID, UUID.randomUUID());
    assertThat(otherDocs).noneMatch(d -> d.id().equals(docId));
  }

  @Test
  void deletePortalDocumentRemovesSameOrgDocument() {
    UUID docId = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant uploaded = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        cid,
        projectId,
        "Doc to Delete",
        "application/pdf",
        1024L,
        "CUSTOMER",
        "s3://bucket/todelete.pdf",
        uploaded);

    var beforeDelete = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(beforeDelete).anyMatch(d -> d.id().equals(docId));

    repository.deletePortalDocument(docId, ORG_ID);

    var afterDelete = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(afterDelete).noneMatch(d -> d.id().equals(docId));
  }

  @Test
  void deletePortalDocumentDoesNotDeleteCrossOrg() {
    UUID docId = UUID.randomUUID();
    UUID cid = UUID.randomUUID();
    Instant uploaded = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Insert document for ORG_ID
    repository.upsertPortalDocument(
        docId,
        ORG_ID,
        cid,
        projectId,
        "Cross-Org Doc",
        "application/pdf",
        2048L,
        "CUSTOMER",
        "s3://bucket/crossorg.pdf",
        uploaded);

    var beforeDelete = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(beforeDelete).anyMatch(d -> d.id().equals(docId));

    // Attempt to delete with OTHER_ORG_ID (wrong org)
    repository.deletePortalDocument(docId, OTHER_ORG_ID);

    // Document should still exist
    var afterDelete = repository.findDocumentsByProject(projectId, ORG_ID);
    assertThat(afterDelete).anyMatch(d -> d.id().equals(docId));
  }

  @Test
  void deletePortalCommentRemovesSameOrgComment() {
    UUID commentId = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    repository.upsertPortalComment(
        commentId, ORG_ID, projectId, "Charlie", "Comment to delete", created);

    var beforeDelete = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(beforeDelete).anyMatch(c -> c.id().equals(commentId));

    repository.deletePortalComment(commentId, ORG_ID);

    var afterDelete = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(afterDelete).noneMatch(c -> c.id().equals(commentId));
  }

  @Test
  void deletePortalCommentDoesNotDeleteCrossOrg() {
    UUID commentId = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Insert comment for ORG_ID
    repository.upsertPortalComment(
        commentId, ORG_ID, projectId, "Dave", "Cross-org comment", created);

    var beforeDelete = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(beforeDelete).anyMatch(c -> c.id().equals(commentId));

    // Attempt to delete with OTHER_ORG_ID (wrong org)
    repository.deletePortalComment(commentId, OTHER_ORG_ID);

    // Comment should still exist
    var afterDelete = repository.findCommentsByProject(projectId, ORG_ID);
    assertThat(afterDelete).anyMatch(c -> c.id().equals(commentId));
  }

  @Test
  void deletePortalProjectDoesNotDeleteCrossCustomer() {
    UUID pid = UUID.randomUUID();
    UUID cid1 = UUID.randomUUID();
    UUID cid2 = UUID.randomUUID();
    Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Same project projected for two customers
    repository.upsertPortalProject(pid, cid1, ORG_ID, "Shared Project", "ACTIVE", null, created);
    repository.upsertPortalProject(pid, cid2, ORG_ID, "Shared Project", "ACTIVE", null, created);

    assertThat(repository.findProjectDetail(pid, cid1, ORG_ID)).isPresent();
    assertThat(repository.findProjectDetail(pid, cid2, ORG_ID)).isPresent();

    // Delete for cid1 only
    repository.deletePortalProject(pid, cid1);

    // Should be deleted for cid1, but still exist for cid2
    assertThat(repository.findProjectDetail(pid, cid1, ORG_ID)).isEmpty();
    assertThat(repository.findProjectDetail(pid, cid2, ORG_ID)).isPresent();
  }
}
