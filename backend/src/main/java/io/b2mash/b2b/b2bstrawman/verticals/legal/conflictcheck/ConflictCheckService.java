package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConflictCheckService {

  private static final String MODULE_ID = "conflict_check";
  private static final double FUZZY_THRESHOLD = 0.3;
  private static final double CONFLICT_THRESHOLD = 0.6;
  private static final int MAX_SEARCH_RESULTS = 50;

  private final ConflictCheckRepository conflictCheckRepository;
  private final AdversePartyRepository adversePartyRepository;
  private final AdversePartyLinkRepository adversePartyLinkRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ConflictCheckService(
      ConflictCheckRepository conflictCheckRepository,
      AdversePartyRepository adversePartyRepository,
      AdversePartyLinkRepository adversePartyLinkRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.conflictCheckRepository = conflictCheckRepository;
    this.adversePartyRepository = adversePartyRepository;
    this.adversePartyLinkRepository = adversePartyLinkRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  // --- DTO Records ---

  public record PerformConflictCheckRequest(
      @NotBlank String checkedName,
      String checkedIdNumber,
      String checkedRegistrationNumber,
      @NotBlank
          @Pattern(
              regexp = "NEW_CLIENT|NEW_MATTER|PERIODIC_REVIEW|LATERAL_HIRE|RELATED_PARTY",
              message =
                  "checkType must be one of: NEW_CLIENT, NEW_MATTER, PERIODIC_REVIEW, LATERAL_HIRE, RELATED_PARTY")
          String checkType,
      UUID customerId,
      UUID projectId) {}

  public record ResolveRequest(String resolution, String resolutionNotes, UUID waiverDocumentId) {}

  public record ConflictCheckResponse(
      UUID id,
      String checkedName,
      String checkedIdNumber,
      String checkedRegistrationNumber,
      String checkType,
      String result,
      List<ConflictDetail> conflictsFound,
      String resolution,
      String resolutionNotes,
      UUID waiverDocumentId,
      UUID checkedBy,
      UUID resolvedBy,
      Instant checkedAt,
      Instant resolvedAt,
      UUID customerId,
      UUID projectId) {}

  public record ConflictDetail(
      UUID adversePartyId,
      String adversePartyName,
      UUID customerId,
      String customerName,
      UUID projectId,
      String projectName,
      String relationship,
      String matchType,
      double similarityScore,
      String explanation) {}

  // --- Service Methods ---

  @Transactional
  public ConflictCheckResponse performCheck(PerformConflictCheckRequest request, UUID memberId) {
    moduleGuard.requireModule(MODULE_ID);

    var conflicts = new ArrayList<ConflictDetail>();
    boolean hasExactIdMatch = false;
    double highestScore = 0.0;
    boolean hasAliasMatch = false;

    // Step 1: Exact ID matching
    if (request.checkedIdNumber() != null && !request.checkedIdNumber().isBlank()) {
      // Search adverse parties by ID number
      var apMatch = adversePartyRepository.findByIdNumber(request.checkedIdNumber());
      if (apMatch.isPresent()) {
        var ap = apMatch.get();
        var links = adversePartyLinkRepository.findByAdversePartyId(ap.getId());
        if (links.isEmpty()) {
          conflicts.add(
              buildAdversePartyConflict(
                  ap, null, "ID_NUMBER_EXACT", 1.0, request.checkedIdNumber()));
        } else {
          for (var link : links) {
            conflicts.add(
                buildAdversePartyConflict(
                    ap, link, "ID_NUMBER_EXACT", 1.0, request.checkedIdNumber()));
          }
        }
        hasExactIdMatch = true;
      }

      // Search customers by ID number
      var custMatch = customerRepository.findByIdNumberExact(request.checkedIdNumber());
      if (custMatch.isPresent()) {
        conflicts.add(
            buildCustomerConflict(
                custMatch.get(), "ID_NUMBER_EXACT", 1.0, request.checkedIdNumber()));
        hasExactIdMatch = true;
      }
    }

    if (request.checkedRegistrationNumber() != null
        && !request.checkedRegistrationNumber().isBlank()) {
      // Search adverse parties by registration number
      var apMatch =
          adversePartyRepository.findByRegistrationNumber(request.checkedRegistrationNumber());
      boolean regMatched = apMatch.isPresent();
      if (regMatched) {
        var ap = apMatch.get();
        var links = adversePartyLinkRepository.findByAdversePartyId(ap.getId());
        if (links.isEmpty()) {
          conflicts.add(
              buildAdversePartyConflict(
                  ap, null, "REGISTRATION_NUMBER_EXACT", 1.0, request.checkedRegistrationNumber()));
        } else {
          for (var link : links) {
            conflicts.add(
                buildAdversePartyConflict(
                    ap,
                    link,
                    "REGISTRATION_NUMBER_EXACT",
                    1.0,
                    request.checkedRegistrationNumber()));
          }
        }
      }

      // Search customers by registration number (entity column). Use List (not Optional) because
      // import / migration data can legitimately produce duplicate registration numbers — we must
      // iterate every match rather than let Spring Data throw
      // IncorrectResultSizeDataAccessException.
      var custRegMatches =
          customerRepository.findByRegistrationNumber(request.checkedRegistrationNumber());
      for (var custRegMatch : custRegMatches) {
        conflicts.add(
            buildCustomerConflict(
                custRegMatch,
                "REGISTRATION_NUMBER_EXACT",
                1.0,
                request.checkedRegistrationNumber()));
        regMatched = true;
      }

      hasExactIdMatch |= regMatched;
    }

    // Step 2: Fuzzy name matching — batch-fetch all adverse party links to avoid N+1
    var adverseNameMatches =
        adversePartyRepository.findBySimilarName(
            request.checkedName(), FUZZY_THRESHOLD, MAX_SEARCH_RESULTS);

    // Step 2b: Alias matches
    var aliasMatches =
        adversePartyRepository.findByAliasContaining(
            request.checkedName(), FUZZY_THRESHOLD, MAX_SEARCH_RESULTS);

    // Collect all adverse party IDs from fuzzy + alias matches for batch link fetch
    var fuzzyPartyIds =
        adverseNameMatches.stream()
            .filter(ap -> !isAdversePartyAlreadyFound(conflicts, ap.getId()))
            .map(AdverseParty::getId)
            .collect(Collectors.toSet());
    var aliasPartyIds =
        aliasMatches.stream()
            .filter(ap -> !isAdversePartyAlreadyFound(conflicts, ap.getId()))
            .filter(ap -> !fuzzyPartyIds.contains(ap.getId()))
            .map(AdverseParty::getId)
            .collect(Collectors.toSet());

    var allPartyIds = new java.util.HashSet<>(fuzzyPartyIds);
    allPartyIds.addAll(aliasPartyIds);

    // Batch-fetch all links in one query
    Map<UUID, List<AdversePartyLink>> linksByPartyId = Map.of();
    if (!allPartyIds.isEmpty()) {
      linksByPartyId =
          adversePartyLinkRepository.findByAdversePartyIdIn(allPartyIds).stream()
              .collect(Collectors.groupingBy(AdversePartyLink::getAdversePartyId));
    }

    for (var ap : adverseNameMatches) {
      if (isAdversePartyAlreadyFound(conflicts, ap.getId())) {
        continue;
      }
      double score = estimateSimilarity(ap.getName(), request.checkedName());
      if (score > highestScore) {
        highestScore = score;
      }
      var links = linksByPartyId.getOrDefault(ap.getId(), List.of());
      if (links.isEmpty()) {
        conflicts.add(
            buildAdversePartyConflict(ap, null, "NAME_SIMILARITY", score, request.checkedName()));
      } else {
        for (var link : links) {
          conflicts.add(
              buildAdversePartyConflict(ap, link, "NAME_SIMILARITY", score, request.checkedName()));
        }
      }
    }

    // Search customers by name
    var customerNameMatches =
        customerRepository.findBySimilarName(
            request.checkedName(), FUZZY_THRESHOLD, MAX_SEARCH_RESULTS);
    for (var customer : customerNameMatches) {
      double score = estimateSimilarity(customer.getName(), request.checkedName());
      if (score > highestScore) {
        highestScore = score;
      }
      conflicts.add(
          buildCustomerConflict(customer, "NAME_SIMILARITY", score, request.checkedName()));
    }

    // Search project/matter names (GAP-D14-01)
    var projectNameMatches =
        projectRepository.findBySimilarName(
            request.checkedName(), FUZZY_THRESHOLD, MAX_SEARCH_RESULTS);
    for (var project : projectNameMatches) {
      double score = estimateSimilarity(project.getName(), request.checkedName());
      // Boost score for substring matches — if the search term appears verbatim in the
      // project name, ensure the score reflects a meaningful match even when trigram
      // similarity is low due to the name being much longer than the search term.
      if (project.getName().toLowerCase().contains(request.checkedName().toLowerCase())
          && score < CONFLICT_THRESHOLD) {
        score = CONFLICT_THRESHOLD;
      }
      if (score > highestScore) {
        highestScore = score;
      }
      conflicts.add(
          new ConflictDetail(
              null,
              null,
              project.getCustomerId(),
              null,
              project.getId(),
              project.getName(),
              "MATTER_NAME",
              "NAME_SIMILARITY",
              score,
              buildExplanation(
                  "NAME_SIMILARITY", project.getName(), request.checkedName(), score)));
    }

    for (var ap : aliasMatches) {
      if (isAdversePartyAlreadyFound(conflicts, ap.getId())) {
        continue;
      }
      hasAliasMatch = true;
      double score = estimateSimilarity(ap.getAliases(), request.checkedName());
      var links = linksByPartyId.getOrDefault(ap.getId(), List.of());
      if (links.isEmpty()) {
        conflicts.add(
            buildAdversePartyConflict(ap, null, "ALIAS_MATCH", score, request.checkedName()));
      } else {
        for (var link : links) {
          conflicts.add(
              buildAdversePartyConflict(ap, link, "ALIAS_MATCH", score, request.checkedName()));
        }
      }
    }

    // Step 3: Classify result
    String result;
    if (hasExactIdMatch || highestScore > CONFLICT_THRESHOLD) {
      result = "CONFLICT_FOUND";
    } else if (!conflicts.isEmpty() || hasAliasMatch) {
      result = "POTENTIAL_CONFLICT";
    } else {
      result = "NO_CONFLICT";
    }

    // Step 4: Resolve project/customer names for conflict details
    var resolvedConflicts = resolveNames(conflicts);

    // Step 5: Persist
    String conflictsJson = resolvedConflicts.isEmpty() ? null : toJson(resolvedConflicts);

    var conflictCheck =
        new ConflictCheck(
            request.checkedName(),
            request.checkedIdNumber(),
            request.checkedRegistrationNumber(),
            request.checkType(),
            result,
            conflictsJson,
            memberId,
            request.customerId(),
            request.projectId());

    var saved = conflictCheckRepository.save(conflictCheck);

    // Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("conflict_check.performed")
            .entityType("conflict_check")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "checked_name", saved.getCheckedName(),
                    "check_type", saved.getCheckType(),
                    "result", saved.getResult()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public ConflictCheckResponse resolve(UUID id, ResolveRequest request, UUID memberId) {
    moduleGuard.requireModule(MODULE_ID);

    var conflictCheck =
        conflictCheckRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ConflictCheck", id));

    conflictCheck.setResolution(request.resolution());
    conflictCheck.setResolutionNotes(request.resolutionNotes());
    conflictCheck.setWaiverDocumentId(request.waiverDocumentId());
    conflictCheck.setResolvedBy(memberId);
    conflictCheck.setResolvedAt(Instant.now());

    var saved = conflictCheckRepository.save(conflictCheck);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("conflict_check.resolved")
            .entityType("conflict_check")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "resolution", saved.getResolution(),
                    "checked_name", saved.getCheckedName()))
            .build());

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<ConflictCheckResponse> list(
      String result,
      String checkType,
      UUID checkedBy,
      Instant dateFrom,
      Instant dateTo,
      Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    return conflictCheckRepository
        .findByFilters(result, checkType, checkedBy, dateFrom, dateTo, pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public ConflictCheckResponse getById(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var conflictCheck =
        conflictCheckRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ConflictCheck", id));

    return toResponse(conflictCheck);
  }

  // --- Private helpers ---

  private boolean isAdversePartyAlreadyFound(List<ConflictDetail> conflicts, UUID adversePartyId) {
    return conflicts.stream()
        .anyMatch(c -> c.adversePartyId() != null && c.adversePartyId().equals(adversePartyId));
  }

  private ConflictDetail buildAdversePartyConflict(
      AdverseParty ap, AdversePartyLink link, String matchType, double score, String searchTerm) {
    return new ConflictDetail(
        ap.getId(),
        ap.getName(),
        link != null ? link.getCustomerId() : null,
        null,
        link != null ? link.getProjectId() : null,
        null,
        link != null ? link.getRelationship() : null,
        matchType,
        score,
        buildExplanation(matchType, ap.getName(), searchTerm, score));
  }

  private ConflictDetail buildCustomerConflict(
      Customer customer, String matchType, double score, String searchTerm) {
    return new ConflictDetail(
        null,
        null,
        customer.getId(),
        customer.getName(),
        null,
        null,
        "EXISTING_CLIENT",
        matchType,
        score,
        buildExplanation(matchType, customer.getName(), searchTerm, score));
  }

  private String buildExplanation(
      String matchType, String matchedName, String searchTerm, double score) {
    return switch (matchType) {
      case "ID_NUMBER_EXACT" -> "Exact ID number match: " + searchTerm;
      case "REGISTRATION_NUMBER_EXACT" -> "Exact registration number match: " + searchTerm;
      case "NAME_SIMILARITY" ->
          String.format("Name match: '%s' vs '%s' (score: %.2f)", matchedName, searchTerm, score);
      case "ALIAS_MATCH" ->
          String.format("Alias match: '%s' vs '%s' (score: %.2f)", matchedName, searchTerm, score);
      default -> "Match found";
    };
  }

  /**
   * Resolve project and customer names by batch-fetching referenced entities. Returns a new list
   * with names populated.
   */
  private ArrayList<ConflictDetail> resolveNames(List<ConflictDetail> conflicts) {
    if (conflicts.isEmpty()) {
      return new ArrayList<>(conflicts);
    }

    // Collect all unique project and customer IDs
    var projectIds =
        conflicts.stream()
            .map(ConflictDetail::projectId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    var customerIds =
        conflicts.stream()
            .map(ConflictDetail::customerId)
            .filter(id -> id != null)
            .distinct()
            .toList();

    Map<UUID, String> projectNames = Map.of();
    if (!projectIds.isEmpty()) {
      projectNames =
          projectRepository.findByIdIn(projectIds).stream()
              .collect(Collectors.toMap(Project::getId, Project::getName));
    }

    Map<UUID, String> customerNames = Map.of();
    if (!customerIds.isEmpty()) {
      customerNames =
          customerRepository.findByIdIn(customerIds).stream()
              .collect(Collectors.toMap(Customer::getId, Customer::getName));
    }

    var resolved = new ArrayList<ConflictDetail>();
    for (var c : conflicts) {
      var projectName =
          c.projectId() != null ? projectNames.getOrDefault(c.projectId(), null) : c.projectName();
      var customerName =
          c.customerId() != null
              ? customerNames.getOrDefault(c.customerId(), c.customerName())
              : c.customerName();

      resolved.add(
          new ConflictDetail(
              c.adversePartyId(),
              c.adversePartyName(),
              c.customerId(),
              customerName,
              c.projectId(),
              projectName,
              c.relationship(),
              c.matchType(),
              c.similarityScore(),
              c.explanation()));
    }
    return resolved;
  }

  private String toJson(List<ConflictDetail> conflicts) {
    try {
      return objectMapper.writeValueAsString(conflicts);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize conflict details", e);
    }
  }

  private List<ConflictDetail> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          json,
          objectMapper.getTypeFactory().constructCollectionType(List.class, ConflictDetail.class));
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize conflict details", e);
    }
  }

  private ConflictCheckResponse toResponse(ConflictCheck entity) {
    return new ConflictCheckResponse(
        entity.getId(),
        entity.getCheckedName(),
        entity.getCheckedIdNumber(),
        entity.getCheckedRegistrationNumber(),
        entity.getCheckType(),
        entity.getResult(),
        fromJson(entity.getConflictsFound()),
        entity.getResolution(),
        entity.getResolutionNotes(),
        entity.getWaiverDocumentId(),
        entity.getCheckedBy(),
        entity.getResolvedBy(),
        entity.getCheckedAt(),
        entity.getResolvedAt(),
        entity.getCustomerId(),
        entity.getProjectId());
  }

  /**
   * Simple similarity estimation for score classification. The actual fuzzy matching is done by
   * pg_trgm in the database -- this is used to estimate the score for result classification when
   * the database has already filtered results above the threshold.
   */
  private double estimateSimilarity(String a, String b) {
    if (a == null || b == null) {
      return 0.0;
    }
    String la = a.toLowerCase().trim();
    String lb = b.toLowerCase().trim();
    if (la.equals(lb)) {
      return 1.0;
    }

    // Use trigram-based Jaccard similarity to approximate pg_trgm
    var trigramsA = trigrams(la);
    var trigramsB = trigrams(lb);
    if (trigramsA.isEmpty() || trigramsB.isEmpty()) {
      return 0.0;
    }
    long intersection = trigramsA.stream().filter(trigramsB::contains).count();
    long union = trigramsA.size() + trigramsB.size() - intersection;
    return union == 0 ? 0.0 : (double) intersection / union;
  }

  private List<String> trigrams(String s) {
    if (s.length() < 3) {
      return List.of(s);
    }
    var result = new ArrayList<String>();
    for (int i = 0; i <= s.length() - 3; i++) {
      result.add(s.substring(i, i + 3));
    }
    return result;
  }
}
