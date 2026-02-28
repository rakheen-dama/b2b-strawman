package io.b2mash.b2b.b2bstrawman.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

  Page<Expense> findByProjectId(UUID projectId, Pageable pageable);

  List<Expense> findByProjectIdAndBillableTrueAndInvoiceIdIsNull(UUID projectId);

  Page<Expense> findByMemberId(UUID memberId, Pageable pageable);

  @Query(
      """
      SELECT e FROM Expense e
      WHERE (:projectId IS NULL OR e.projectId = :projectId)
        AND (:category IS NULL OR e.category = :category)
        AND (CAST(:fromDate AS date) IS NULL OR e.date >= :fromDate)
        AND (CAST(:toDate AS date) IS NULL OR e.date <= :toDate)
        AND (:memberId IS NULL OR e.memberId = :memberId)
      ORDER BY e.date DESC, e.createdAt DESC
      """)
  Page<Expense> findFiltered(
      @Param("projectId") UUID projectId,
      @Param("category") ExpenseCategory category,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      @Param("memberId") UUID memberId,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          """
      SELECT e.* FROM expenses e
        JOIN projects p ON e.project_id = p.id
        JOIN customer_projects cp ON cp.project_id = p.id
      WHERE cp.customer_id = :customerId
        AND e.billable = true
        AND e.invoice_id IS NULL
      ORDER BY e.date, e.created_at
      """)
  List<Expense> findUnbilledBillableByCustomerId(@Param("customerId") UUID customerId);
}
