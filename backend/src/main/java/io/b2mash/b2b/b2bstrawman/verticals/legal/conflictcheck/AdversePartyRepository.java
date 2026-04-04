package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdversePartyRepository extends JpaRepository<AdverseParty, UUID> {

  Optional<AdverseParty> findByIdNumber(String idNumber);

  Optional<AdverseParty> findByRegistrationNumber(String registrationNumber);

  @Query(
      value =
          "SELECT * FROM adverse_parties"
              + " WHERE lower(name) LIKE lower(:pattern)"
              + " ORDER BY name ASC"
              + " LIMIT :maxResults",
      nativeQuery = true)
  List<AdverseParty> findByNameContaining(
      @Param("pattern") String pattern, @Param("maxResults") int maxResults);

  @Query(
      value =
          "SELECT * FROM adverse_parties"
              + " WHERE public.similarity(lower(name), lower(:name)) > :threshold"
              + " ORDER BY public.similarity(lower(name), lower(:name)) DESC"
              + " LIMIT :maxResults",
      nativeQuery = true)
  List<AdverseParty> findBySimilarName(
      @Param("name") String name,
      @Param("threshold") double threshold,
      @Param("maxResults") int maxResults);

  @Query(
      value =
          "SELECT * FROM adverse_parties"
              + " WHERE aliases IS NOT NULL"
              + " AND public.similarity(lower(aliases), lower(:name)) > :threshold"
              + " ORDER BY public.similarity(lower(aliases), lower(:name)) DESC"
              + " LIMIT :maxResults",
      nativeQuery = true)
  List<AdverseParty> findByAliasContaining(
      @Param("name") String name,
      @Param("threshold") double threshold,
      @Param("maxResults") int maxResults);

  @Query(
      """
      SELECT a FROM AdverseParty a
      WHERE (:partyType IS NULL OR a.partyType = :partyType)
      ORDER BY a.name ASC
      """)
  Page<AdverseParty> findByFilters(@Param("partyType") String partyType, Pageable pageable);
}
