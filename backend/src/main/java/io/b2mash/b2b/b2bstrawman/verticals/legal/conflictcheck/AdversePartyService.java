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

    if (adversePartyLinkRepository.existsByAdversePartyId(id)) {
      var linkCount = adversePartyLinkRepository.findByAdversePartyId(id).size();
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
      // Fuzzy search: combine name + alias results, deduplicate, then paginate in-memory
      var nameMatches = adversePartyRepository.findBySimilarName(search, FUZZY_THRESHOLD);
      var aliasMatches = adversePartyRepository.findByAliasContaining(search, FUZZY_THRESHOLD);

      // Merge and deduplicate preserving name-match order
      var seen = new LinkedHashSet<UUID>();
      var merged =
          Stream.concat(nameMatches.stream(), aliasMatches.stream())
              .filter(ap -> seen.add(ap.getId()))
              .filter(ap -> partyType == null || ap.getPartyType().equals(partyType))
              .toList();

      // Manual pagination (handle unpaged gracefully)
      if (pageable.isUnpaged()) {
        return new org.springframework.data.domain.PageImpl<>(
            merged.stream().map(this::toResponse).toList());
      }

      int start = (int) pageable.getOffset();
      int end = Math.min(start + pageable.getPageSize(), merged.size());
      var pageContent =
          start >= merged.size() ? List.<AdverseParty>of() : merged.subList(start, end);

      return new org.springframework.data.domain.PageImpl<>(
          pageContent.stream().map(this::toResponse).toList(), pageable, merged.size());
    }

    return adversePartyRepository.findByFilters(partyType, pageable).map(this::toResponse);
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
    long linkCount = adversePartyLinkRepository.findByAdversePartyId(entity.getId()).size();
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
