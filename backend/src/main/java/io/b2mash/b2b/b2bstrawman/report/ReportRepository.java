package io.b2mash.b2b.b2bstrawman.report;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Custom repository for profitability aggregation queries. Uses EntityManager directly because
 * these queries are not bound to a single JPA entity. Native SQL is required for conditional
 * aggregation (CASE WHEN) and multi-table GROUP BY. Tenant isolation is provided by the dedicated
 * schema (search_path set on connection checkout).
 */
@Repository
public class ReportRepository {

  private final EntityManager entityManager;

  public ReportRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * Revenue aggregation for a project, grouped by billing_rate_currency. Returns billable hours,
   * non-billable hours, total hours, and billable value per currency.
   */
  public List<RevenueCurrencyProjection> getProjectRevenue(
      UUID projectId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.billing_rate_currency AS currency,
                SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS totalBillableHours,
                SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS totalNonBillableHours,
                SUM(te.duration_minutes) / 60.0
                    AS totalHours,
                SUM(CASE WHEN te.billable AND te.billing_rate_snapshot IS NOT NULL
                    THEN CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END)
                    AS billableValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            WHERE t.project_id = :projectId
              AND te.billing_rate_currency IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
            GROUP BY te.billing_rate_currency
            """,
            Tuple.class);
    query.setParameter("projectId", projectId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toRevenueProjection).toList();
  }

  /**
   * Cost aggregation for a project, grouped by cost_rate_currency. Returns cost value per currency
   * for all time entries with cost rate snapshots.
   */
  public List<CostCurrencyProjection> getProjectCost(UUID projectId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.cost_rate_currency AS currency,
                SUM(CAST(te.cost_rate_snapshot AS DECIMAL(14,2))
                    * te.duration_minutes / 60.0) AS costValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            WHERE t.project_id = :projectId
              AND te.cost_rate_snapshot IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
            GROUP BY te.cost_rate_currency
            """,
            Tuple.class);
    query.setParameter("projectId", projectId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toCostProjection).toList();
  }

  /**
   * Revenue aggregation for a customer (across all linked projects), grouped by
   * billing_rate_currency.
   */
  public List<RevenueCurrencyProjection> getCustomerRevenue(
      UUID customerId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.billing_rate_currency AS currency,
                SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS totalBillableHours,
                SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS totalNonBillableHours,
                SUM(te.duration_minutes) / 60.0 AS totalHours,
                SUM(CASE WHEN te.billable AND te.billing_rate_snapshot IS NOT NULL
                    THEN CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END)
                    AS billableValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN customer_projects cp ON t.project_id = cp.project_id
            WHERE cp.customer_id = :customerId
              AND te.billing_rate_currency IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
            GROUP BY te.billing_rate_currency
            """,
            Tuple.class);
    query.setParameter("customerId", customerId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toRevenueProjection).toList();
  }

  /**
   * Cost aggregation for a customer (across all linked projects), grouped by cost_rate_currency.
   */
  public List<CostCurrencyProjection> getCustomerCost(
      UUID customerId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.cost_rate_currency AS currency,
                SUM(CAST(te.cost_rate_snapshot AS DECIMAL(14,2))
                    * te.duration_minutes / 60.0) AS costValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN customer_projects cp ON t.project_id = cp.project_id
            WHERE cp.customer_id = :customerId
              AND te.cost_rate_snapshot IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
            GROUP BY te.cost_rate_currency
            """,
            Tuple.class);
    query.setParameter("customerId", customerId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toCostProjection).toList();
  }

  // --- Utilization queries ---

  /**
   * Member utilization aggregation: total, billable, and non-billable hours per member. Both from
   * and to are required — no nullable CAST needed.
   */
  public List<MemberUtilizationProjection> getMemberUtilization(LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.member_id AS memberId,
                m.name AS memberName,
                SUM(te.duration_minutes) / 60.0 AS totalHours,
                SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS billableHours,
                SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS nonBillableHours
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN members m ON te.member_id = m.id
            WHERE te.date >= :fromDate
              AND te.date <= :toDate
            GROUP BY te.member_id, m.name
            ORDER BY billableHours DESC
            """,
            Tuple.class);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toMemberUtilizationProjection).toList();
  }

  /**
   * Member utilization for a single member. Both from and to are required — no nullable CAST
   * needed.
   */
  public List<MemberUtilizationProjection> getMemberUtilization(
      UUID memberId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.member_id AS memberId,
                m.name AS memberName,
                SUM(te.duration_minutes) / 60.0 AS totalHours,
                SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS billableHours,
                SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS nonBillableHours
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN members m ON te.member_id = m.id
            WHERE te.member_id = :memberId
              AND te.date >= :fromDate
              AND te.date <= :toDate
            GROUP BY te.member_id, m.name
            ORDER BY billableHours DESC
            """,
            Tuple.class);
    query.setParameter("memberId", memberId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toMemberUtilizationProjection).toList();
  }

  /**
   * Per-member per-currency billable/cost value breakdown for a single member. Used to enrich
   * utilization data with currency information when querying self or a specific member.
   */
  public List<MemberValueProjection> getMemberBillableValues(
      UUID memberId, LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.member_id AS memberId,
                te.billing_rate_currency AS currency,
                SUM(CASE WHEN te.billable AND te.billing_rate_snapshot IS NOT NULL
                    THEN CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END) AS billableValue,
                SUM(CASE WHEN te.cost_rate_snapshot IS NOT NULL
                    THEN CAST(te.cost_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END) AS costValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            WHERE te.member_id = :memberId
              AND te.billing_rate_currency IS NOT NULL
              AND te.date >= :fromDate
              AND te.date <= :toDate
            GROUP BY te.member_id, te.billing_rate_currency
            """,
            Tuple.class);
    query.setParameter("memberId", memberId);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toMemberValueProjection).toList();
  }

  /**
   * Per-member per-currency billable/cost value breakdown. Used to enrich utilization data with
   * currency information.
   */
  public List<MemberValueProjection> getMemberBillableValues(LocalDate from, LocalDate to) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                te.member_id AS memberId,
                te.billing_rate_currency AS currency,
                SUM(CASE WHEN te.billable AND te.billing_rate_snapshot IS NOT NULL
                    THEN CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END) AS billableValue,
                SUM(CASE WHEN te.cost_rate_snapshot IS NOT NULL
                    THEN CAST(te.cost_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END) AS costValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            WHERE te.billing_rate_currency IS NOT NULL
              AND te.date >= :fromDate
              AND te.date <= :toDate
            GROUP BY te.member_id, te.billing_rate_currency
            """,
            Tuple.class);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toMemberValueProjection).toList();
  }

  // --- Org profitability queries ---

  /**
   * Org-level revenue aggregation grouped by project and billing_rate_currency. Optional customerId
   * filter via customer_projects join.
   */
  public List<OrgRevenueProjection> getOrgProjectRevenue(
      LocalDate from, LocalDate to, UUID customerId) {
    String customerJoin =
        customerId != null ? "JOIN customer_projects cp ON t.project_id = cp.project_id" : "";
    String customerWhere = customerId != null ? "AND cp.customer_id = :customerId" : "";

    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                t.project_id AS projectId,
                p.name AS projectName,
                te.billing_rate_currency AS currency,
                SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0
                    AS billableHours,
                SUM(CASE WHEN te.billable AND te.billing_rate_snapshot IS NOT NULL
                    THEN CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
                         * te.duration_minutes / 60.0
                    ELSE 0 END)
                    AS billableValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN projects p ON t.project_id = p.id
            %s
            WHERE te.billing_rate_currency IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
              %s
            GROUP BY t.project_id, p.name, te.billing_rate_currency
            ORDER BY billableValue DESC
            """
                .formatted(customerJoin, customerWhere),
            Tuple.class);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);
    if (customerId != null) {
      query.setParameter("customerId", customerId);
    }

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toOrgRevenueProjection).toList();
  }

  /**
   * Org-level cost aggregation grouped by project and cost_rate_currency. Optional customerId
   * filter via customer_projects join.
   */
  public List<OrgCostProjection> getOrgProjectCost(LocalDate from, LocalDate to, UUID customerId) {
    String customerJoin =
        customerId != null ? "JOIN customer_projects cp ON t.project_id = cp.project_id" : "";
    String customerWhere = customerId != null ? "AND cp.customer_id = :customerId" : "";

    var query =
        entityManager.createNativeQuery(
            """
            SELECT
                t.project_id AS projectId,
                p.name AS projectName,
                te.cost_rate_currency AS currency,
                SUM(CAST(te.cost_rate_snapshot AS DECIMAL(14,2))
                    * te.duration_minutes / 60.0) AS costValue
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN projects p ON t.project_id = p.id
            %s
            WHERE te.cost_rate_snapshot IS NOT NULL
              AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
              AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
              %s
            GROUP BY t.project_id, p.name, te.cost_rate_currency
            """
                .formatted(customerJoin, customerWhere),
            Tuple.class);
    query.setParameter("fromDate", from);
    query.setParameter("toDate", to);
    if (customerId != null) {
      query.setParameter("customerId", customerId);
    }

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();
    return results.stream().map(this::toOrgCostProjection).toList();
  }

  // --- Tuple-to-projection mappers ---

  private MemberUtilizationProjection toMemberUtilizationProjection(Tuple tuple) {
    return new MemberUtilizationProjection() {
      @Override
      public UUID getMemberId() {
        return tuple.get("memberId", UUID.class);
      }

      @Override
      public String getMemberName() {
        return tuple.get("memberName", String.class);
      }

      @Override
      public BigDecimal getTotalHours() {
        return toBigDecimal(tuple.get("totalHours"));
      }

      @Override
      public BigDecimal getBillableHours() {
        return toBigDecimal(tuple.get("billableHours"));
      }

      @Override
      public BigDecimal getNonBillableHours() {
        return toBigDecimal(tuple.get("nonBillableHours"));
      }
    };
  }

  private MemberValueProjection toMemberValueProjection(Tuple tuple) {
    return new MemberValueProjection() {
      @Override
      public UUID getMemberId() {
        return tuple.get("memberId", UUID.class);
      }

      @Override
      public String getCurrency() {
        return tuple.get("currency", String.class);
      }

      @Override
      public BigDecimal getBillableValue() {
        return toBigDecimal(tuple.get("billableValue"));
      }

      @Override
      public BigDecimal getCostValue() {
        return toBigDecimal(tuple.get("costValue"));
      }
    };
  }

  private OrgRevenueProjection toOrgRevenueProjection(Tuple tuple) {
    return new OrgRevenueProjection() {
      @Override
      public UUID getProjectId() {
        return tuple.get("projectId", UUID.class);
      }

      @Override
      public String getProjectName() {
        return tuple.get("projectName", String.class);
      }

      @Override
      public String getCurrency() {
        return tuple.get("currency", String.class);
      }

      @Override
      public BigDecimal getBillableHours() {
        return toBigDecimal(tuple.get("billableHours"));
      }

      @Override
      public BigDecimal getBillableValue() {
        return toBigDecimal(tuple.get("billableValue"));
      }
    };
  }

  private OrgCostProjection toOrgCostProjection(Tuple tuple) {
    return new OrgCostProjection() {
      @Override
      public UUID getProjectId() {
        return tuple.get("projectId", UUID.class);
      }

      @Override
      public String getProjectName() {
        return tuple.get("projectName", String.class);
      }

      @Override
      public String getCurrency() {
        return tuple.get("currency", String.class);
      }

      @Override
      public BigDecimal getCostValue() {
        return toBigDecimal(tuple.get("costValue"));
      }
    };
  }

  private RevenueCurrencyProjection toRevenueProjection(Tuple tuple) {
    return new RevenueCurrencyProjection() {
      @Override
      public String getCurrency() {
        return tuple.get("currency", String.class);
      }

      @Override
      public BigDecimal getTotalBillableHours() {
        return toBigDecimal(tuple.get("totalBillableHours"));
      }

      @Override
      public BigDecimal getTotalNonBillableHours() {
        return toBigDecimal(tuple.get("totalNonBillableHours"));
      }

      @Override
      public BigDecimal getTotalHours() {
        return toBigDecimal(tuple.get("totalHours"));
      }

      @Override
      public BigDecimal getBillableValue() {
        return toBigDecimal(tuple.get("billableValue"));
      }
    };
  }

  private CostCurrencyProjection toCostProjection(Tuple tuple) {
    return new CostCurrencyProjection() {
      @Override
      public String getCurrency() {
        return tuple.get("currency", String.class);
      }

      @Override
      public BigDecimal getCostValue() {
        return toBigDecimal(tuple.get("costValue"));
      }
    };
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    return new BigDecimal(value.toString());
  }
}
