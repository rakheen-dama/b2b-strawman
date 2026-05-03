package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequest;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequestRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.proposal.Proposal;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed implementation of {@link AuditService}. Delegates persistence and querying to
 * {@link AuditEventRepository}.
 *
 * <p>Transaction semantics: {@code log()} participates in the caller's transaction (no
 * REQUIRES_NEW). If the domain operation rolls back, the audit event rolls back too.
 */
@Service
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class DatabaseAuditService implements AuditService {

  private static final Logger log = LoggerFactory.getLogger(DatabaseAuditService.class);

  private final AuditEventRepository auditEventRepository;
  private final MemberRepository memberRepository;
  private final AuditEventTypeRegistry auditEventTypeRegistry;
  private final CustomerProjectRepository customerProjectRepository;
  private final InvoiceRepository invoiceRepository;
  private final DocumentRepository documentRepository;
  private final ProposalRepository proposalRepository;
  private final InformationRequestRepository informationRequestRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final AcceptanceRequestRepository acceptanceRequestRepository;

  public DatabaseAuditService(
      AuditEventRepository auditEventRepository,
      MemberRepository memberRepository,
      AuditEventTypeRegistry auditEventTypeRegistry,
      CustomerProjectRepository customerProjectRepository,
      InvoiceRepository invoiceRepository,
      DocumentRepository documentRepository,
      ProposalRepository proposalRepository,
      InformationRequestRepository informationRequestRepository,
      TrustTransactionRepository trustTransactionRepository,
      AcceptanceRequestRepository acceptanceRequestRepository) {
    this.auditEventRepository = auditEventRepository;
    this.memberRepository = memberRepository;
    this.auditEventTypeRegistry = auditEventTypeRegistry;
    this.customerProjectRepository = customerProjectRepository;
    this.invoiceRepository = invoiceRepository;
    this.documentRepository = documentRepository;
    this.proposalRepository = proposalRepository;
    this.informationRequestRepository = informationRequestRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.acceptanceRequestRepository = acceptanceRequestRepository;
  }

  @Override
  @Transactional
  public void log(AuditEventRecord record) {
    var enrichedRecord = enrichActorName(record);
    var event = new AuditEvent(enrichedRecord);
    auditEventRepository.save(event);
    log.debug(
        "Recorded audit event: type={}, entity={}/{}, actor={}",
        record.eventType(),
        record.entityType(),
        record.entityId(),
        record.actorId());
  }

  /**
   * Ensures the {@code actor_name} key is present in the record's details map. If the caller
   * already set it (e.g. PortalCommentService), the existing value is preserved. Otherwise, the
   * name is resolved from the member repository for USER actors, or defaults to "System".
   */
  private AuditEventRecord enrichActorName(AuditEventRecord record) {
    var details =
        new HashMap<String, Object>(record.details() != null ? record.details() : Map.of());
    if (!details.containsKey("actor_name")) {
      if (record.actorId() != null && "USER".equals(record.actorType())) {
        memberRepository
            .findById(record.actorId())
            .ifPresent(member -> details.put("actor_name", member.getName()));
      }
      if (!details.containsKey("actor_name")) {
        details.put("actor_name", "System");
      }
    }
    return new AuditEventRecord(
        record.eventType(),
        record.entityType(),
        record.entityId(),
        record.actorId(),
        record.actorType(),
        record.source(),
        record.ipAddress(),
        record.userAgent(),
        details);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditEvent> findEvents(AuditEventFilter filter, Pageable pageable) {
    // Per AuditService.findEvents Javadoc: ordering is fixed at occurredAt DESC across BOTH
    // branches
    // (the JPQL query has it in its ORDER BY clause; the native severity-pre-flight query bakes it
    // into its SQL). Strip caller Sort up-front so both branches receive an identical sort-free
    // Pageable -- avoids the asymmetry where one branch silently honoured Sort and the other did
    // not, and prevents the native-query 500 caused by Spring Data appending a JPA property name
    // ("e.occurredAt") onto the end of a native SQL statement.
    var sortFreePageable =
        org.springframework.data.domain.PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize());

    Set<AuditSeverity> severities = filter.severities();
    // Path 1: no severity filter -- use the existing JPQL query unchanged.
    if (severities == null || severities.isEmpty()) {
      return auditEventRepository.findByFilter(
          filter.entityType(),
          filter.entityId(),
          filter.actorId(),
          filter.eventType(),
          filter.from(),
          filter.to(),
          sortFreePageable);
    }

    // Path 2: severity pre-flight (architecture §12.3.5) — shared with streamEvents() so the two
    // paths cannot drift.
    var preflight = computeSeverityPreflight(severities);
    if (preflight == null) {
      // Step E short-circuit: nothing can match.
      return Page.empty(pageable);
    }

    return auditEventRepository.findByFilterWithEventTypes(
        filter.entityType(),
        filter.entityId(),
        filter.actorId(),
        filter.eventType(),
        filter.from(),
        filter.to(),
        preflight.exactTypes(),
        preflight.prefixPatterns(),
        preflight.excludeExact(),
        preflight.allRegisteredExacts(),
        preflight.allRegisteredPrefixes(),
        sortFreePageable);
  }

  /**
   * Computes the registry-derived severity pre-flight arrays per architecture §12.3.5. Returns
   * {@code null} when the requested severity set cannot match anything (Step E short-circuit).
   * Shared between {@link #findEvents} and {@link #streamEvents}.
   */
  private SeverityPreflight computeSeverityPreflight(Set<AuditSeverity> severities) {
    // Step A: walk the registry for entries whose severity matches the request.
    var matchingEntries = auditEventTypeRegistry.entriesMatching(severities);

    // Step B: split into (a) exact strings to include, (b) prefix patterns to include.
    Set<String> includeExact = new HashSet<>();
    Set<String> includePrefixSql = new HashSet<>(); // e.g. "matter.closure.%"
    for (var entry : matchingEntries) {
      var et = entry.eventType();
      if (et.endsWith(".*")) {
        includePrefixSql.add(et.substring(0, et.length() - 2) + ".%");
      } else {
        includeExact.add(et);
      }
    }

    // Step C: compute the EXCLUSION set. Re-run resolve() per registered exact entry per spec; if
    // the resolved severity is NOT in the requested set but the eventType would match one of our
    // prefix patterns, it must be excluded. This handles the matter.closure.override_used
    // (CRITICAL) vs matter.closure.* (NOTICE) conflict.
    Set<String> excludeExact = new HashSet<>();
    for (var entry : auditEventTypeRegistry.entries()) {
      var et = entry.eventType();
      if (et.endsWith(".*")) {
        continue; // only exact entries can be excluded
      }
      var resolved = auditEventTypeRegistry.resolve(et); // re-run per spec §12.3.5
      if (severities.contains(resolved.severity())) {
        continue; // already included by Step A
      }
      // resolved severity is NOT in requested set -- would any prefix pattern catch it?
      for (var prefixSql : includePrefixSql) {
        // prefixSql ends in ".%" -- strip the "%" to compare the literal prefix.
        var literalPrefix = prefixSql.substring(0, prefixSql.length() - 1); // "matter.closure."
        if (et.startsWith(literalPrefix)) {
          excludeExact.add(et);
          break;
        }
      }
    }

    // Step D: INFO fallback for unregistered eventTypes.
    boolean infoRequested = severities.contains(AuditSeverity.INFO);
    String[] allRegisteredExacts = null;
    String[] allRegisteredPrefixes = null;
    if (infoRequested) {
      var allExacts = new HashSet<String>();
      var allPrefixes = new HashSet<String>();
      for (var entry : auditEventTypeRegistry.entries()) {
        var et = entry.eventType();
        if (et.endsWith(".*")) {
          allPrefixes.add(et.substring(0, et.length() - 2) + ".%");
        } else {
          allExacts.add(et);
        }
      }
      allRegisteredExacts = allExacts.toArray(new String[0]);
      allRegisteredPrefixes = allPrefixes.toArray(new String[0]);
    }

    // Step E: short-circuit when nothing can match.
    if (includeExact.isEmpty() && includePrefixSql.isEmpty() && !infoRequested) {
      return null;
    }

    // Step F: convert sets to nullable arrays.
    String[] exactArr = includeExact.isEmpty() ? null : includeExact.toArray(new String[0]);
    String[] prefixArr =
        includePrefixSql.isEmpty() ? null : includePrefixSql.toArray(new String[0]);
    String[] excludeArr = excludeExact.isEmpty() ? null : excludeExact.toArray(new String[0]);

    return new SeverityPreflight(
        exactArr, prefixArr, excludeArr, allRegisteredExacts, allRegisteredPrefixes);
  }

  /** Internal carrier for the §12.3.5 severity pre-flight arrays. */
  private record SeverityPreflight(
      String[] exactTypes,
      String[] prefixPatterns,
      String[] excludeExact,
      String[] allRegisteredExacts,
      String[] allRegisteredPrefixes) {}

  @Override
  @Transactional(readOnly = true)
  public Stream<AuditEvent> streamEvents(AuditEventFilter filter) {
    Set<AuditSeverity> severities = filter.severities();
    if (severities == null || severities.isEmpty()) {
      return auditEventRepository.streamByFilter(
          filter.entityType(),
          filter.entityId(),
          filter.actorId(),
          filter.eventType(),
          filter.from(),
          filter.to());
    }
    var preflight = computeSeverityPreflight(severities);
    if (preflight == null) {
      // Nothing can match — return an empty stream rather than issuing a query that returns 0 rows.
      return Stream.empty();
    }
    return auditEventRepository.streamByFilterWithEventTypes(
        filter.entityType(),
        filter.entityId(),
        filter.actorId(),
        filter.eventType(),
        filter.from(),
        filter.to(),
        preflight.exactTypes(),
        preflight.prefixPatterns(),
        preflight.excludeExact(),
        preflight.allRegisteredExacts(),
        preflight.allRegisteredPrefixes());
  }

  @Override
  @Transactional(readOnly = true)
  public Stream<AuditEvent> findEventsForCustomer(UUID customerId) {
    // Resolve every child-entity ID owned by the customer. Each list is read in a separate query
    // (these are simple PK-fetches by indexed customerId column; no N+1 because we collect ID lists
    // up-front and pass a single TEXT[] / UUID[] pair into the streaming audit query).
    var projectIds =
        customerProjectRepository.findByCustomerId(customerId).stream()
            .map(CustomerProject::getProjectId)
            .toList();
    var invoiceIds =
        invoiceRepository.findByCustomerId(customerId).stream().map(Invoice::getId).toList();
    var documentIds =
        documentRepository.findByCustomerId(customerId).stream().map(Document::getId).toList();
    var proposalIds =
        proposalRepository.findByCustomerId(customerId, Pageable.unpaged()).stream()
            .map(Proposal::getId)
            .toList();
    var informationRequestIds =
        informationRequestRepository.findByCustomerId(customerId).stream()
            .map(InformationRequest::getId)
            .toList();
    var trustTransactionIds =
        trustTransactionRepository.findByCustomerId(customerId).stream()
            .map(TrustTransaction::getId)
            .toList();
    var acceptanceRequestIds =
        acceptanceRequestRepository.findByCustomerId(customerId).stream()
            .map(AcceptanceRequest::getId)
            .toList();

    String[] childTypes = {
      "project",
      "invoice",
      "proposal",
      "information_request",
      "document",
      "trust_transaction",
      "acceptance_request"
    };
    UUID[] childIds =
        Stream.of(
                projectIds,
                invoiceIds,
                proposalIds,
                informationRequestIds,
                documentIds,
                trustTransactionIds,
                acceptanceRequestIds)
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .distinct()
            .toArray(UUID[]::new);

    return auditEventRepository.streamForCustomer(customerId, childTypes, childIds);
  }

  @Override
  @Transactional(readOnly = true)
  public long countEvents(AuditEventFilter filter) {
    Set<AuditSeverity> severities = filter.severities();
    var oneRow = org.springframework.data.domain.PageRequest.of(0, 1);
    if (severities == null || severities.isEmpty()) {
      // Spring Data derives a COUNT query for the JPQL @Query; fetching a 1-row page lets us read
      // the total without adding a dedicated repository method.
      return auditEventRepository
          .findByFilter(
              filter.entityType(),
              filter.entityId(),
              filter.actorId(),
              filter.eventType(),
              filter.from(),
              filter.to(),
              oneRow)
          .getTotalElements();
    }
    var preflight = computeSeverityPreflight(severities);
    if (preflight == null) {
      // Step E short-circuit: nothing can match — skip the SQL roundtrip.
      return 0L;
    }
    // findByFilterWithEventTypes already declares an explicit countQuery mirroring its WHERE
    // clause, so .getTotalElements() returns a true COUNT (not a derived/incorrect one).
    return auditEventRepository
        .findByFilterWithEventTypes(
            filter.entityType(),
            filter.entityId(),
            filter.actorId(),
            filter.eventType(),
            filter.from(),
            filter.to(),
            preflight.exactTypes(),
            preflight.prefixPatterns(),
            preflight.excludeExact(),
            preflight.allRegisteredExacts(),
            preflight.allRegisteredPrefixes(),
            oneRow)
        .getTotalElements();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditEventMetadataResolver.EnrichedAuditEvent> findEventsEnriched(
      AuditEventFilter filter, Pageable pageable) {
    var rawPage = findEvents(filter, pageable);
    var rows = rawPage.getContent();
    if (rows.isEmpty()) {
      return rawPage.map(e -> enrichOne(e, Map.of()));
    }
    // Single bulk member lookup -- N events with K distinct USER actors produce one IN-clause of
    // size K, not N+1 queries.
    Set<UUID> actorIds =
        rows.stream()
            .map(AuditEvent::getActorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> nameLookup = resolveActorDisplayNames(actorIds);
    return rawPage.map(e -> enrichOne(e, nameLookup));
  }

  private AuditEventMetadataResolver.EnrichedAuditEvent enrichOne(
      AuditEvent event, Map<UUID, String> nameLookup) {
    var metadata = auditEventTypeRegistry.resolve(event.getEventType());
    var displayName = resolveDisplay(event.getActorId(), event.getActorType(), nameLookup);
    return new AuditEventMetadataResolver.EnrichedAuditEvent(event, metadata, displayName);
  }

  private static String resolveDisplay(
      UUID actorId, String actorType, Map<UUID, String> nameLookup) {
    if ("USER".equals(actorType)) {
      if (actorId == null) {
        return "System";
      }
      var name = nameLookup.get(actorId);
      return name != null ? name : "Former member (" + actorId + ")";
    }
    return staticActorLabel(actorType);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEventRepository.EventTypeCount> countEventsByType() {
    return auditEventRepository.countByEventType();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> resolveActorDisplayNames(Collection<UUID> actorIds) {
    if (actorIds == null || actorIds.isEmpty()) {
      return Map.of();
    }
    Set<UUID> distinctIds = actorIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (distinctIds.isEmpty()) {
      return Map.of();
    }
    // Single batched lookup against members — Spring Data's findAllById issues a single
    // SELECT ... WHERE id IN (?) so a batch of N actorIds produces one query, not N+1.
    // Skip blank names so the batch path stays consistent with resolveActorDisplay's single-actor
    // fallback (a blank Member.name must trigger the "Former member ({uuid})" branch, not show as
    // an empty string).
    var out = new HashMap<UUID, String>();
    for (Member member : memberRepository.findAllById(distinctIds)) {
      var name = member.getName();
      if (name != null && !name.isBlank()) {
        out.put(member.getId(), name);
      }
    }
    return Map.copyOf(out);
  }

  @Override
  @Transactional(readOnly = true)
  public String resolveActorDisplay(UUID actorId, String actorType) {
    if ("USER".equals(actorType)) {
      if (actorId == null) {
        // Defensive: a USER actor with no actorId is a logging bug; fall back to "System".
        return "System";
      }
      return memberRepository
          .findById(actorId)
          .map(Member::getName)
          .filter(name -> name != null && !name.isBlank())
          .orElseGet(() -> "Former member (" + actorId + ")");
    }
    return staticActorLabel(actorType);
  }

  @Override
  @Transactional(readOnly = true)
  public FacetSnapshot facets(Instant from, Instant to) {
    var actorRows = auditEventRepository.projectActorFacets(from, to);
    var eventTypeRows = auditEventRepository.projectEventTypeFacets(from, to);
    var entityTypeRows = auditEventRepository.projectEntityTypeFacets(from, to);

    // Actor facets — apply the §12.3.4 fallback chain. The query already LEFT-JOINed members so
    // live members carry an actorName; missing rows come back null and need the "Former member
    // ({uuid})" fallback. Non-USER actor types use the static label.
    List<ActorFacet> actors = new ArrayList<>(actorRows.size());
    for (var row : actorRows) {
      var actorId = row.getActorId();
      var actorType = row.getActorType();
      String displayName;
      if ("USER".equals(actorType)) {
        var name = row.getActorName();
        displayName = (name != null && !name.isBlank()) ? name : "Former member (" + actorId + ")";
      } else {
        displayName = staticActorLabel(actorType);
      }
      actors.add(new ActorFacet(actorId, displayName, actorType, row.getEventCount()));
    }

    // EventType facets — enrich each row via the registry resolver.
    List<EventTypeFacet> eventTypes = new ArrayList<>(eventTypeRows.size());
    for (var row : eventTypeRows) {
      var metadata = auditEventTypeRegistry.resolve(row.getEventType());
      eventTypes.add(
          new EventTypeFacet(
              row.getEventType(),
              metadata.label(),
              metadata.severity(),
              metadata.group(),
              row.getCount()));
    }

    // EntityType facets — title-case the raw entity_type for the label.
    List<EntityTypeFacet> entityTypes = new ArrayList<>(entityTypeRows.size());
    for (var row : entityTypeRows) {
      entityTypes.add(
          new EntityTypeFacet(row.getEntityType(), titleCase(row.getEntityType()), row.getCount()));
    }

    return new FacetSnapshot(
        List.copyOf(actors), List.copyOf(eventTypes), List.copyOf(entityTypes));
  }

  /**
   * Returns the static display label for non-USER actor types per architecture §12.3.4. Shared with
   * {@link AuditEventMetadataResolver} so the two resolution paths cannot drift. Unknown actor
   * types map defensively to {@code "System"}.
   */
  static String staticActorLabel(String actorType) {
    return switch (actorType == null ? "" : actorType) {
      case "PORTAL_CONTACT" -> "Portal Contact";
      case "SYSTEM" -> "System";
      case "AUTOMATION" -> "Automation";
      case "API_KEY" -> "API Key";
      default -> "System";
    };
  }

  /**
   * Title-cases a dotted/underscored token for facet display. Mirrors {@link
   * AuditEventTypeRegistry}'s private titleCase helper -- {@code "task"} ⇒ {@code "Task"}, {@code
   * "trust_account"} ⇒ {@code "Trust account"}.
   */
  private static String titleCase(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    var parts = s.split("[._]");
    var out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(parts[i].charAt(0)));
      if (parts[i].length() > 1) {
        out.append(parts[i].substring(1));
      }
    }
    return out.toString();
  }
}
