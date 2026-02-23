package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalCommentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDocumentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceLineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PortalReadModelRepository {

  private final JdbcClient jdbc;

  public PortalReadModelRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant != null ? Timestamp.from(instant) : null;
  }

  // ── Upsert methods ──────────────────────────────────────────────────

  public void upsertPortalProject(
      UUID projectId,
      UUID customerId,
      String orgId,
      String name,
      String status,
      String description,
      Instant createdAt) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_projects
                (id, customer_id, org_id, name, status, description, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id, customer_id)
            DO UPDATE SET name = EXCLUDED.name,
                          status = EXCLUDED.status,
                          description = EXCLUDED.description,
                          updated_at = now()
            """)
        .params(projectId, customerId, orgId, name, status, description, toTimestamp(createdAt))
        .update();
  }

  public void upsertPortalDocument(
      UUID documentId,
      String orgId,
      UUID customerId,
      UUID portalProjectId,
      String title,
      String contentType,
      Long size,
      String scope,
      String s3Key,
      Instant uploadedAt) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_documents
                (id, org_id, customer_id, portal_project_id, title, content_type,
                 size, scope, s3_key, uploaded_at, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id)
            DO UPDATE SET title = EXCLUDED.title,
                          content_type = EXCLUDED.content_type,
                          size = EXCLUDED.size,
                          scope = EXCLUDED.scope,
                          s3_key = EXCLUDED.s3_key,
                          synced_at = now()
            """)
        .params(
            documentId,
            orgId,
            customerId,
            portalProjectId,
            title,
            contentType,
            size,
            scope,
            s3Key,
            toTimestamp(uploadedAt))
        .update();
  }

  public void upsertPortalComment(
      UUID commentId,
      String orgId,
      UUID portalProjectId,
      String authorName,
      String content,
      Instant createdAt) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_comments
                (id, org_id, portal_project_id, author_name, content, created_at, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id)
            DO UPDATE SET author_name = EXCLUDED.author_name,
                          content = EXCLUDED.content,
                          synced_at = now()
            """)
        .params(commentId, orgId, portalProjectId, authorName, content, toTimestamp(createdAt))
        .update();
  }

  public void upsertPortalProjectSummary(
      UUID projectId,
      UUID customerId,
      String orgId,
      BigDecimal totalHours,
      BigDecimal billableHours,
      Instant lastActivityAt) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_project_summaries
                (id, customer_id, org_id, total_hours, billable_hours, last_activity_at, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id, customer_id)
            DO UPDATE SET total_hours = EXCLUDED.total_hours,
                          billable_hours = EXCLUDED.billable_hours,
                          last_activity_at = EXCLUDED.last_activity_at,
                          synced_at = now()
            """)
        .params(
            projectId, customerId, orgId, totalHours, billableHours, toTimestamp(lastActivityAt))
        .update();
  }

  /**
   * Updates name, status, and description for an existing portal_project row. Unlike {@link
   * #upsertPortalProject}, this does not attempt an INSERT, avoiding NOT NULL constraint violations
   * when the caller does not have createdAt available.
   */
  public void updatePortalProjectDetails(
      UUID projectId, UUID customerId, String name, String status, String description) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET name = ?, status = ?, description = ?, updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(name, status, description, projectId, customerId)
        .update();
  }

  // ── Delete methods ──────────────────────────────────────────────────

  public void deletePortalProject(UUID projectId, UUID customerId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_projects
            WHERE id = ? AND customer_id = ?
            """)
        .params(projectId, customerId)
        .update();
  }

  public void deletePortalDocument(UUID documentId, String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_documents
            WHERE id = ? AND org_id = ?
            """)
        .params(documentId, orgId)
        .update();
  }

  public void deletePortalComment(UUID commentId, String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_comments
            WHERE id = ? AND org_id = ?
            """)
        .params(commentId, orgId)
        .update();
  }

  public void deletePortalProjectsByOrg(String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_projects
            WHERE org_id = ?
            """)
        .params(orgId)
        .update();
  }

  public void deletePortalDocumentsByOrg(String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_documents
            WHERE org_id = ?
            """)
        .params(orgId)
        .update();
  }

  public void deletePortalCommentsByOrg(String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_comments
            WHERE org_id = ?
            """)
        .params(orgId)
        .update();
  }

  public void deletePortalProjectSummariesByOrg(String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_project_summaries
            WHERE org_id = ?
            """)
        .params(orgId)
        .update();
  }

  // ── Count update methods ────────────────────────────────────────────

  public void setDocumentCount(UUID projectId, UUID customerId, int count) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET document_count = ?,
                updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(count, projectId, customerId)
        .update();
  }

  public void incrementDocumentCount(UUID projectId, UUID customerId) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET document_count = document_count + 1,
                updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(projectId, customerId)
        .update();
  }

  public void decrementDocumentCount(UUID projectId, UUID customerId) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET document_count = GREATEST(document_count - 1, 0),
                updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(projectId, customerId)
        .update();
  }

  public void incrementCommentCount(UUID projectId, UUID customerId) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET comment_count = comment_count + 1,
                updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(projectId, customerId)
        .update();
  }

  public void decrementCommentCount(UUID projectId, UUID customerId) {
    jdbc.sql(
            """
            UPDATE portal.portal_projects
            SET comment_count = GREATEST(comment_count - 1, 0),
                updated_at = now()
            WHERE id = ? AND customer_id = ?
            """)
        .params(projectId, customerId)
        .update();
  }

  // ── Lookup methods ──────────────────────────────────────────────────

  public Optional<PortalDocumentView> findPortalDocumentById(UUID documentId, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, portal_project_id, title, content_type,
                   size, scope, s3_key, uploaded_at, synced_at
            FROM portal.portal_documents
            WHERE id = ? AND org_id = ?
            """)
        .params(documentId, orgId)
        .query(PortalDocumentView.class)
        .optional();
  }

  public List<UUID> findCustomerIdsByProjectId(UUID projectId, String orgId) {
    return jdbc.sql(
            """
            SELECT customer_id FROM portal.portal_projects
            WHERE id = ? AND org_id = ?
            """)
        .params(projectId, orgId)
        .query((rs, rowNum) -> rs.getObject("customer_id", UUID.class))
        .list();
  }

  // ── Query methods ───────────────────────────────────────────────────

  public List<PortalProjectView> findProjectsByCustomer(String orgId, UUID customerId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, name, status, description,
                   document_count, comment_count, created_at, updated_at
            FROM portal.portal_projects
            WHERE org_id = ? AND customer_id = ?
            ORDER BY created_at DESC
            """)
        .params(orgId, customerId)
        .query(PortalProjectView.class)
        .list();
  }

  public Optional<PortalProjectView> findProjectDetail(
      UUID projectId, UUID customerId, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, name, status, description,
                   document_count, comment_count, created_at, updated_at
            FROM portal.portal_projects
            WHERE id = ? AND customer_id = ? AND org_id = ?
            """)
        .params(projectId, customerId, orgId)
        .query(PortalProjectView.class)
        .optional();
  }

  public List<PortalDocumentView> findDocumentsByProject(UUID portalProjectId, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, portal_project_id, title, content_type,
                   size, scope, s3_key, uploaded_at, synced_at
            FROM portal.portal_documents
            WHERE portal_project_id = ? AND org_id = ?
            ORDER BY uploaded_at DESC
            """)
        .params(portalProjectId, orgId)
        .query(PortalDocumentView.class)
        .list();
  }

  public List<PortalDocumentView> findDocumentsByCustomer(String orgId, UUID customerId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, portal_project_id, title, content_type,
                   size, scope, s3_key, uploaded_at, synced_at
            FROM portal.portal_documents
            WHERE org_id = ? AND customer_id = ?
            ORDER BY uploaded_at DESC
            """)
        .params(orgId, customerId)
        .query(PortalDocumentView.class)
        .list();
  }

  public List<PortalCommentView> findCommentsByProject(UUID portalProjectId, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, portal_project_id, author_name, content, created_at, synced_at
            FROM portal.portal_comments
            WHERE portal_project_id = ? AND org_id = ?
            ORDER BY created_at DESC
            """)
        .params(portalProjectId, orgId)
        .query(PortalCommentView.class)
        .list();
  }

  public Optional<PortalProjectSummaryView> findProjectSummary(
      UUID projectId, UUID customerId, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, total_hours, billable_hours,
                   last_activity_at, synced_at
            FROM portal.portal_project_summaries
            WHERE id = ? AND customer_id = ? AND org_id = ?
            """)
        .params(projectId, customerId, orgId)
        .query(PortalProjectSummaryView.class)
        .optional();
  }

  // ── Invoice methods ──────────────────────────────────────────────────

  public void upsertPortalInvoice(
      UUID id,
      String orgId,
      UUID customerId,
      String invoiceNumber,
      String status,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal total,
      String currency,
      String notes) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_invoices
                (id, org_id, customer_id, invoice_number, status, issue_date, due_date,
                 subtotal, tax_amount, total, currency, notes, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id)
            DO UPDATE SET status = EXCLUDED.status,
                          invoice_number = EXCLUDED.invoice_number,
                          issue_date = EXCLUDED.issue_date,
                          due_date = EXCLUDED.due_date,
                          subtotal = EXCLUDED.subtotal,
                          tax_amount = EXCLUDED.tax_amount,
                          total = EXCLUDED.total,
                          notes = EXCLUDED.notes,
                          synced_at = now()
            """)
        .params(
            id,
            orgId,
            customerId,
            invoiceNumber,
            status,
            issueDate,
            dueDate,
            subtotal,
            taxAmount,
            total,
            currency,
            notes)
        .update();
  }

  public void upsertPortalInvoiceLine(
      UUID id,
      UUID portalInvoiceId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal amount,
      int sortOrder) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_invoice_lines
                (id, portal_invoice_id, description, quantity, unit_price, amount, sort_order, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id)
            DO UPDATE SET description = EXCLUDED.description,
                          quantity = EXCLUDED.quantity,
                          unit_price = EXCLUDED.unit_price,
                          amount = EXCLUDED.amount,
                          sort_order = EXCLUDED.sort_order,
                          synced_at = now()
            """)
        .params(id, portalInvoiceId, description, quantity, unitPrice, amount, sortOrder)
        .update();
  }

  public void updatePortalInvoiceStatus(UUID id, String orgId, String status) {
    jdbc.sql(
            """
            UPDATE portal.portal_invoices
            SET status = ?, synced_at = now()
            WHERE id = ? AND org_id = ?
            """)
        .params(status, id, orgId)
        .update();
  }

  public void deletePortalInvoice(UUID id, String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_invoices
            WHERE id = ? AND org_id = ?
            """)
        .params(id, orgId)
        .update();
  }

  public void deletePortalInvoiceLinesByInvoice(UUID portalInvoiceId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_invoice_lines
            WHERE portal_invoice_id = ?
            """)
        .params(portalInvoiceId)
        .update();
  }

  public void deletePortalInvoicesByOrg(String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_invoices
            WHERE org_id = ?
            """)
        .params(orgId)
        .update();
  }

  public List<PortalInvoiceView> findInvoicesByCustomer(String orgId, UUID customerId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, invoice_number, status, issue_date, due_date,
                   subtotal, tax_amount, total, currency, notes, synced_at
            FROM portal.portal_invoices
            WHERE org_id = ? AND customer_id = ?
            ORDER BY issue_date DESC
            """)
        .params(orgId, customerId)
        .query(PortalInvoiceView.class)
        .list();
  }

  public Optional<PortalInvoiceView> findInvoiceById(UUID id, String orgId) {
    return jdbc.sql(
            """
            SELECT id, org_id, customer_id, invoice_number, status, issue_date, due_date,
                   subtotal, tax_amount, total, currency, notes, synced_at
            FROM portal.portal_invoices
            WHERE id = ? AND org_id = ?
            """)
        .params(id, orgId)
        .query(PortalInvoiceView.class)
        .optional();
  }

  public List<PortalInvoiceLineView> findInvoiceLinesByInvoice(UUID portalInvoiceId) {
    return jdbc.sql(
            """
            SELECT id, portal_invoice_id, description, quantity, unit_price, amount,
                   sort_order, synced_at
            FROM portal.portal_invoice_lines
            WHERE portal_invoice_id = ?
            ORDER BY sort_order
            """)
        .params(portalInvoiceId)
        .query(PortalInvoiceLineView.class)
        .list();
  }
}
