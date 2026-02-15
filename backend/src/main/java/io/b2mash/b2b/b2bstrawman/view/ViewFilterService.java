package io.b2mash.b2b.b2bstrawman.view;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Translates filter JSONB from SavedView.filters into SQL WHERE clause predicates. Orchestrates
 * filter handlers (status, tags, custom fields, date range, search) and constructs parameterized
 * SQL fragments. Returns WHERE clause string and populates a params map with named parameter
 * bindings.
 */
@Service
public class ViewFilterService {

  /** Allowlisted table names to prevent SQL injection via the tableName parameter. */
  private static final Set<String> ALLOWED_TABLES = Set.of("projects", "tasks", "customers");

  private final StatusFilterHandler statusFilterHandler;
  private final TagFilterHandler tagFilterHandler;
  private final CustomFieldFilterHandler customFieldFilterHandler;
  private final DateRangeFilterHandler dateRangeFilterHandler;
  private final SearchFilterHandler searchFilterHandler;

  @PersistenceContext private EntityManager entityManager;

  public ViewFilterService(
      StatusFilterHandler statusFilterHandler,
      TagFilterHandler tagFilterHandler,
      CustomFieldFilterHandler customFieldFilterHandler,
      DateRangeFilterHandler dateRangeFilterHandler,
      SearchFilterHandler searchFilterHandler) {
    this.statusFilterHandler = statusFilterHandler;
    this.tagFilterHandler = tagFilterHandler;
    this.customFieldFilterHandler = customFieldFilterHandler;
    this.dateRangeFilterHandler = dateRangeFilterHandler;
    this.searchFilterHandler = searchFilterHandler;
  }

  /**
   * Builds a SQL WHERE clause from the given filter map.
   *
   * @param filters the filter map from SavedView.filters (JSONB)
   * @param params output map to populate with named parameter bindings
   * @param entityType the entity type (PROJECT, TASK, CUSTOMER) — used by tag handler
   * @return WHERE clause string (no leading WHERE keyword), or empty string if no filters
   */
  public String buildWhereClause(
      Map<String, Object> filters, Map<String, Object> params, String entityType) {
    if (filters == null || filters.isEmpty()) {
      return "";
    }

    List<String> clauses = new ArrayList<>();

    if (filters.containsKey("status")) {
      String predicate =
          statusFilterHandler.buildPredicate(filters.get("status"), params, entityType);
      if (!predicate.isEmpty()) {
        clauses.add(predicate);
      }
    }

    if (filters.containsKey("tags")) {
      String predicate = tagFilterHandler.buildPredicate(filters.get("tags"), params, entityType);
      if (!predicate.isEmpty()) {
        clauses.add(predicate);
      }
    }

    if (filters.containsKey("customFields")) {
      String predicate =
          customFieldFilterHandler.buildPredicate(filters.get("customFields"), params, entityType);
      if (!predicate.isEmpty()) {
        clauses.add(predicate);
      }
    }

    if (filters.containsKey("dateRange")) {
      String predicate =
          dateRangeFilterHandler.buildPredicate(filters.get("dateRange"), params, entityType);
      if (!predicate.isEmpty()) {
        clauses.add(predicate);
      }
    }

    if (filters.containsKey("search")) {
      String predicate =
          searchFilterHandler.buildPredicate(filters.get("search"), params, entityType);
      if (!predicate.isEmpty()) {
        clauses.add(predicate);
      }
    }

    return String.join(" AND ", clauses);
  }

  /**
   * Executes a filtered query against the given table using native SQL. Runs inside a read-only
   * transaction and adds tenant_id filtering for multi-tenant isolation (since native queries
   * bypass Hibernate @Filter).
   *
   * @param tableName the SQL table name (projects, tasks, customers)
   * @param entityClass the JPA entity class for result mapping
   * @param filters the filter map from SavedView.filters
   * @param entityType the entity type string (PROJECT, TASK, CUSTOMER)
   * @param extraWhere optional extra WHERE clause (e.g., "project_id = :projectId")
   * @param extraParams optional extra parameters for the extra WHERE clause
   * @return filtered entity list, or null if filters produce an empty WHERE clause
   */
  @Transactional(readOnly = true)
  public <T> List<T> executeFilterQuery(
      String tableName,
      Class<T> entityClass,
      Map<String, Object> filters,
      String entityType,
      String extraWhere,
      Map<String, Object> extraParams) {

    if (!ALLOWED_TABLES.contains(tableName)) {
      throw new IllegalArgumentException("Invalid table name: " + tableName);
    }

    Map<String, Object> params = new HashMap<>();
    if (extraParams != null) {
      params.putAll(extraParams);
    }

    String whereClause = buildWhereClause(filters, params, entityType);

    if (whereClause.isEmpty() && (extraWhere == null || extraWhere.isEmpty())) {
      return null; // Signal to caller to use fallback logic
    }

    var sqlBuilder = new StringBuilder("SELECT e.* FROM ").append(tableName).append(" e WHERE ");

    // Always add tenant_id filter for multi-tenant isolation. Native queries bypass
    // Hibernate @Filter, so this explicit filter provides tenant isolation in shared schemas
    // and defense-in-depth for dedicated schemas.
    if (!RequestScopes.ORG_ID.isBound()) {
      throw new IllegalStateException("ORG_ID must be bound for view filter execution");
    }
    var conditions = new ArrayList<String>();
    conditions.add("e.tenant_id = :tenantOrgId");
    params.put("tenantOrgId", RequestScopes.ORG_ID.get());
    if (extraWhere != null && !extraWhere.isEmpty()) {
      conditions.add(extraWhere);
    }
    if (!whereClause.isEmpty()) {
      conditions.add(whereClause);
    }
    sqlBuilder.append(String.join(" AND ", conditions));
    sqlBuilder.append(" ORDER BY e.updated_at DESC");

    var query = entityManager.createNativeQuery(sqlBuilder.toString(), entityClass);
    params.forEach(query::setParameter);

    @SuppressWarnings("unchecked")
    List<T> results = query.getResultList();
    return results;
  }

  /**
   * Convenience method for entity tables without extra WHERE conditions.
   *
   * @see #executeFilterQuery(String, Class, Map, String, String, Map)
   */
  @Transactional(readOnly = true)
  public <T> List<T> executeFilterQuery(
      String tableName, Class<T> entityClass, Map<String, Object> filters, String entityType) {
    return executeFilterQuery(tableName, entityClass, filters, entityType, null, null);
  }

  /**
   * Convenience method for task filtering scoped to a project.
   *
   * @see #executeFilterQuery(String, Class, Map, String, String, Map)
   */
  @Transactional(readOnly = true)
  public <T> List<T> executeFilterQueryForProject(
      String tableName,
      Class<T> entityClass,
      Map<String, Object> filters,
      String entityType,
      UUID projectId) {
    return executeFilterQuery(
        tableName,
        entityClass,
        filters,
        entityType,
        "e.project_id = :projectId",
        Map.of("projectId", projectId));
  }
}
