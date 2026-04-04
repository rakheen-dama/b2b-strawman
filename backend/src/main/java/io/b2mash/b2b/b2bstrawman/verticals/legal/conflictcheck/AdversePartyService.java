package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdversePartyService {

  private static final String MODULE_ID = "conflict_check";
  private static final double FUZZY_THRESHOLD = 0.3;

  private final AdversePartyRepository adversePartyRepository;
  private final AdversePartyLinkRepository adversePartyLinkRepository;
  private final VerticalModuleGuard moduleGuard;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;

  public AdversePartyService(
      AdversePartyRepository adversePartyRepository,
      AdversePartyLinkRepository adversePartyLinkRepository,
      VerticalModuleGuard moduleGuard,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.adversePartyRepository = adversePartyRepository;
    this.adversePartyLinkRepository = adversePartyLinkRepository;
    this.moduleGuard = moduleGuard;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record CreateAdversePartyRequest(
      @NotBlank String name,
      String idNumber,
      String registrationNumber,
      @NotBlank String partyType,
      String aliases,
      String notes) {}

  public record UpdateAdversePartyRequest(
      @NotBlank String name,
      String idNumber,
      String registrationNumber,
      @NotBlank String partyType,
      String aliases,
      String notes) {}

  public record AdversePartyResponse(
      UUID id,
      String name,
      String idNumber,
      String registrationNumber,
      String partyType,
      String aliases,
      String notes,
      long linkedMatterCount,
      Instant createdAt,
      Instant updatedAt) {}

  public record LinkRequest(
      @NotNull UUID projectId,
      @NotNull UUID customerId,
      @NotBlank String relationship,
      String description) {}

  public record AdversePartyLinkResponse(
      UUID id,
      UUID adversePartyId,
      String adversePartyName,
      UUID projectId,
      String projectName,
      UUID customerId,
      String customerName,
      String relationship,
      String description,
      Instant createdAt) {}

  // --- Service Methods ---

  @Transactional
  public AdversePartyResponse create(CreateAdversePartyRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var adverseParty =
        new AdverseParty(
            request.name(),
            request.idNumber(),
            request.registrationNumber(),
            request.partyType(),
            request.aliases(),
            request.notes());

    var saved = adversePartyRepository.save(adverseParty);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("adverse_party.created")
            .entityType("adverse_party")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "name", saved.getName(),
                    "party_type", saved.getPartyType()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public AdversePartyResponse update(UUID id, UpdateAdversePartyRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var adverseParty =
        adversePartyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdverseParty", id));

    adverseParty.setName(request.name());
    adverseParty.setIdNumber(request.idNumber());
    adverseParty.setRegistrationNumber(request.registrationNumber());
    adverseParty.setPartyType(request.partyType());
    adverseParty.setAliases(request.aliases());
    adverseParty.setNotes(request.notes());
    adverseParty.setUpdatedAt(Instant.now());

    var saved = adversePartyRepository.save(adverseParty);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("adverse_party.updated")
            .entityType("adverse_party")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "name", saved.getName(),
                    "party_type", saved.getPartyType()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var adverseParty =
        adversePartyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdverseParty", id));

    long linkCount = adversePartyLinkRepository.countByAdversePartyId(id);
    if (linkCount > 0) {
      throw new ResourceConflictException(
          "Adverse party has active links",
          "Cannot delete adverse party with ID "
              + id
              + " because it is linked to "
              + linkCount
              + " matter(s)");
    }

    adversePartyRepository.delete(adverseParty);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("adverse_party.deleted")
            .entityType("adverse_party")
            .entityId(id)
            .details(
                Map.of(
                    "name", adverseParty.getName(),
                    "party_type", adverseParty.getPartyType()))
            .build());
  }

  @Transactional
  public AdversePartyLinkResponse link(UUID adversePartyId, LinkRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var adverseParty =
        adversePartyRepository
            .findById(adversePartyId)
            .orElseThrow(() -> new ResourceNotFoundException("AdverseParty", adversePartyId));

    var project =
        projectRepository
            .findById(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    // Check for duplicate link
    adversePartyLinkRepository
        .findByAdversePartyIdAndProjectId(adversePartyId, request.projectId())
        .ifPresent(
            existing -> {
              throw new ResourceConflictException(
                  "Duplicate link",
                  "Adverse party "
                      + adversePartyId
                      + " is already linked to project "
                      + request.projectId());
            });

    var link =
        new AdversePartyLink(
            adversePartyId,
            request.projectId(),
            request.customerId(),
            request.relationship(),
            request.description());

    var saved = adversePartyLinkRepository.save(link);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("adverse_party.linked")
            .entityType("adverse_party_link")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "adverse_party_id", adversePartyId.toString(),
                    "adverse_party_name", adverseParty.getName(),
                    "project_id", request.projectId().toString(),
                    "relationship", request.relationship()))
            .build());

    return toLinkResponse(saved, adverseParty.getName(), project.getName(), customer.getName());
  }

  @Transactional
  public void unlink(UUID linkId) {
    moduleGuard.requireModule(MODULE_ID);

    var link =
        adversePartyLinkRepository
            .findById(linkId)
            .orElseThrow(() -> new ResourceNotFoundException("AdversePartyLink", linkId));

    adversePartyLinkRepository.delete(link);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("adverse_party.unlinked")
            .entityType("adverse_party_link")
            .entityId(linkId)
            .details(
                Map.of(
                    "adverse_party_id", link.getAdversePartyId().toString(),
                    "project_id", link.getProjectId().toString()))
            .build());
  }

  @Transactional(readOnly = true)
  public List<AdversePartyLinkResponse> listForProject(UUID projectId) {
    moduleGuard.requireModule(MODULE_ID);

    var links = adversePartyLinkRepository.findByProjectId(projectId);

    if (links.isEmpty()) {
      return List.of();
    }

    // Batch-fetch adverse parties, projects, and customers to avoid N+1
    var adversePartyIds =
        links.stream().map(AdversePartyLink::getAdversePartyId).collect(Collectors.toSet());
    var customerIds =
        links.stream().map(AdversePartyLink::getCustomerId).collect(Collectors.toSet());

    var adversePartyMap =
        adversePartyRepository.findAllById(adversePartyIds).stream()
            .collect(Collectors.toMap(AdverseParty::getId, Function.identity()));

    var projectMap =
        projectRepository.findByIdIn(List.of(projectId)).stream()
            .collect(Collectors.toMap(Project::getId, Function.identity()));

    var customerMap =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));

    return links.stream()
        .map(
            link -> {
              var party = adversePartyMap.get(link.getAdversePartyId());
              var project = projectMap.get(link.getProjectId());
              var customer = customerMap.get(link.getCustomerId());
              return toLinkResponse(
                  link,
                  party != null ? party.getName() : null,
                  project != null ? project.getName() : null,
                  customer != null ? customer.getName() : null);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<AdversePartyResponse> list(String search, String partyType, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    if (search != null && !search.isBlank()) {
      // Limit each native query to a reasonable upper bound to avoid loading unbounded results.
      // We fetch enough to fill the page after dedup + filtering, but cap at a safe maximum.
      int maxResults = pageable.isPaged() ? pageable.getPageSize() * 3 : 200;

      // Primary: substring match (handles short tokens like "BHP", "Road")
      var substringMatches =
          adversePartyRepository.findByNameContaining("%" + search + "%", maxResults);

      // Secondary: fuzzy match (handles typos like "Roadd Accident")
      var fuzzyMatches =
          adversePartyRepository.findBySimilarName(search, FUZZY_THRESHOLD, maxResults);

      // Tertiary: alias matches
      var aliasMatches =
          adversePartyRepository.findByAliasContaining(search, FUZZY_THRESHOLD, maxResults);

      // Merge and deduplicate, preserving substring-match priority
      var seen = new LinkedHashSet<UUID>();
      var merged =
          Stream.of(substringMatches.stream(), fuzzyMatches.stream(), aliasMatches.stream())
              .flatMap(Function.identity())
              .filter(ap -> seen.add(ap.getId()))
              .filter(ap -> partyType == null || ap.getPartyType().equals(partyType))
              .toList();

      // Manual pagination (handle unpaged gracefully)
      if (pageable.isUnpaged()) {
        var counts = batchLinkCounts(merged);
        return new org.springframework.data.domain.PageImpl<>(
            merged.stream()
                .map(ap -> toResponse(ap, counts.getOrDefault(ap.getId(), 0L)))
                .toList());
      }

      int start = (int) pageable.getOffset();
      int end = Math.min(start + pageable.getPageSize(), merged.size());
      var pageContent =
          start >= merged.size() ? List.<AdverseParty>of() : merged.subList(start, end);

      var counts = batchLinkCounts(pageContent);
      return new org.springframework.data.domain.PageImpl<>(
          pageContent.stream()
              .map(ap -> toResponse(ap, counts.getOrDefault(ap.getId(), 0L)))
              .toList(),
          pageable,
          merged.size());
    }

    var page = adversePartyRepository.findByFilters(partyType, pageable);
    var counts = batchLinkCounts(page.getContent());
    return page.map(ap -> toResponse(ap, counts.getOrDefault(ap.getId(), 0L)));
  }

  @Transactional(readOnly = true)
  public AdversePartyResponse getById(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var adverseParty =
        adversePartyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdverseParty", id));

    return toResponse(adverseParty);
  }

  // --- Private helpers ---

  private AdversePartyResponse toResponse(AdverseParty entity) {
    long linkCount = adversePartyLinkRepository.countByAdversePartyId(entity.getId());
    return toResponse(entity, linkCount);
  }

  private AdversePartyResponse toResponse(AdverseParty entity, long linkCount) {
    return new AdversePartyResponse(
        entity.getId(),
        entity.getName(),
        entity.getIdNumber(),
        entity.getRegistrationNumber(),
        entity.getPartyType(),
        entity.getAliases(),
        entity.getNotes(),
        linkCount,
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  /** Batch-fetch link counts for a collection of adverse parties (single grouped query). */
  private Map<UUID, Long> batchLinkCounts(List<AdverseParty> parties) {
    if (parties.isEmpty()) {
      return Map.of();
    }
    var ids = parties.stream().map(AdverseParty::getId).toList();
    return adversePartyLinkRepository.countByAdversePartyIdIn(ids).stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
  }

  private AdversePartyLinkResponse toLinkResponse(
      AdversePartyLink link, String adversePartyName, String projectName, String customerName) {
    return new AdversePartyLinkResponse(
        link.getId(),
        link.getAdversePartyId(),
        adversePartyName,
        link.getProjectId(),
        projectName,
        link.getCustomerId(),
        customerName,
        link.getRelationship(),
        link.getDescription(),
        link.getCreatedAt());
  }
}
