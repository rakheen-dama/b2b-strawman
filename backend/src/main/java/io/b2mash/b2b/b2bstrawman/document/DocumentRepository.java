package io.b2mash.b2b.b2bstrawman.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  List<Document> findByProjectId(UUID projectId);

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT d FROM Document d WHERE d.id = :id")
  Optional<Document> findOneById(@Param("id") UUID id);

  /** Find PROJECT-scoped documents for a given project. */
  @Query("SELECT d FROM Document d WHERE d.projectId = :projectId AND d.scope = 'PROJECT'")
  List<Document> findProjectScopedByProjectId(@Param("projectId") UUID projectId);

  /** Find all documents with a given scope. */
  @Query("SELECT d FROM Document d WHERE d.scope = :scope")
  List<Document> findByScope(@Param("scope") String scope);

  /** Find documents for a given customer. */
  @Query("SELECT d FROM Document d WHERE d.customerId = :customerId")
  List<Document> findByCustomerId(@Param("customerId") UUID customerId);

  /** Find documents by scope and customer. */
  @Query("SELECT d FROM Document d WHERE d.scope = :scope AND d.customerId = :customerId")
  List<Document> findByScopeAndCustomerId(
      @Param("scope") String scope, @Param("customerId") UUID customerId);
}
