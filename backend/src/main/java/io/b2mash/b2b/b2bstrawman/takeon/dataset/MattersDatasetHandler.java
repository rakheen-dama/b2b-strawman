package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import io.b2mash.b2b.b2bstrawman.worktype.WorkTypes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The matters dataset (Phase 93 656A, §17.3.6): the matters dry run on top of the generic checks,
 * and the per-row commit through the ordinary matter-creation path ({@link
 * ProjectService#createProject}, no template).
 *
 * <p>Extension model (D-103): delegation to {@link GenericTakeOnDatasetHandler}, plus a {@link
 * ValidationContext} subclass built with the copy constructor that holds the client, matter and
 * member lookups.
 *
 * <p>Validation: a matter name and at least one client reference, resolved in order client email →
 * client previous-system ID (a client taken on by an earlier clients batch, {@link
 * TakeOnRowRepository#clientBySourceRef}) → client ID number; no match is an orphan error, a client
 * whose lifecycle may not take a matter is the {@link CustomerLifecycleGuard}'s own refusal. Dedup
 * by the matter reference, else by the name on the resolved client — against existing matters
 * ({@code duplicate_of_id}) and earlier rows of the file ({@code DUPLICATE_IN_FILE}). An unknown
 * responsible-attorney email is a warning: the committing member stays the lead.
 *
 * <p>No sign-off: {@link #requiredSignOff()} is not overridden (§17.8.3).
 */
@Component
public class MattersDatasetHandler
    implements TakeOnDatasetHandler,
        TakeOnRowCommitter<MattersDatasetHandler.MattersCommitContext> {

  /** The {@code take_on_rows.result_entity_type} of a matter (ADR-342 storage key). */
  public static final String RESULT_ENTITY_TYPE = "project";

  public static final String CLIENT_UNRESOLVED = "CLIENT_UNRESOLVED";
  public static final String CLIENT_LIFECYCLE = "CLIENT_LIFECYCLE";
  public static final String CLIENT_AMBIGUOUS = "CLIENT_AMBIGUOUS";
  public static final String LEAD_MEMBER_UNKNOWN = "LEAD_MEMBER_UNKNOWN";
  public static final String LEAD_NOT_APPLIED = "LEAD_NOT_APPLIED";
  public static final String WORK_TYPE_INVALID = "WORK_TYPE_INVALID";

  /** D-156: a matter row names no client. */
  public static final String CLIENT_REFERENCE_REQUIRED_MESSAGE =
      "Enter the client's email, previous-system ID or ID number.";

  /** D-160: the responsible attorney's email is not a member of the firm. */
  public static final String LEAD_MEMBER_UNKNOWN_MESSAGE =
      "No member of the firm has this email address. The member who commits the take-on will be"
          + " the matter's lead.";

  /** D-161: the lead could no longer be applied at commit (the matter is still created). */
  public static final String LEAD_NOT_APPLIED_MESSAGE =
      "The responsible attorney could not be made the matter's lead. The member who committed the"
          + " take-on is the lead; change it on the matter.";

  public static final String WORK_TYPE_INVALID_MESSAGE =
      "Enter a work type of at most 50 characters.";

  /**
   * D-171: the client resolved at validation is gone (deleted or anonymised) by the commit. Fixed
   * text — a row's failure reason never quotes a cell value (D-102).
   */
  public static final String CLIENT_GONE_MESSAGE =
      "The client of this matter is no longer in Kazi. Take the client on first, then take this"
          + " matter on in a new batch.";

  /** The client reference fields, in resolution order (§17.3.6, D-157). */
  static final List<String> CLIENT_REFERENCE_FIELDS =
      List.of("clientEmail", "clientRef", "clientIdNumber");

  /**
   * The derived values the dry run adds to a row's mapped values (D-159). {@code
   * TakeOnRowRepository.redactForClient} repeats this key as a SQL literal.
   */
  public static final String CLIENT_ID = "clientId";

  public static final String CLIENT_RESOLVED_BY = "clientResolvedBy";
  public static final String LEAD_MEMBER_ID = "leadMemberId";

  /** AC-3: the orphan text, quoting the reference the row gave. */
  public static String orphanMessage(String reference) {
    return "No client matches %s. Take the client on first, or correct the reference."
        .formatted(reference);
  }

  /** D-158: more than one live client holds the ID number. */
  public static String ambiguousIdNumberMessage(String idNumber) {
    return ("More than one client has the ID number %s. Use the client's email or previous-system"
            + " ID instead.")
        .formatted(idNumber);
  }

  /** The matters dry-run context: client, matter and member lookups. */
  static final class MattersValidationContext extends ValidationContext {

    /** lower(email) → client (live: not anonymised). */
    final Map<String, UUID> clientByEmail = new HashMap<>();

    /** lower(strip(ID number)) → clients. */
    final Map<String, List<UUID>> clientsByIdNumber = new HashMap<>();

    /** The live clients (the dedup candidates: anonymised clients excluded). */
    final Set<UUID> liveClients = new HashSet<>();

    /** Cache of {@link TakeOnRowRepository#clientBySourceRef}, keyed by lower(ref). */
    final Map<String, Optional<UUID>> bySourceRef = new HashMap<>();

    /** Client → the guard's refusal text, or empty when it may take a matter. */
    final Map<UUID, Optional<String>> lifecycleRefusal = new HashMap<>();

    /** lower(strip(reference number)) → matter. */
    final Map<String, UUID> matterByRef = new HashMap<>();

    /** clientId + "|" + lower(strip(name)) → matter. */
    final Map<String, UUID> matterByClientAndName = new HashMap<>();

    /** lower(email) → member. */
    final Map<String, UUID> memberByEmail = new HashMap<>();

    MattersValidationContext(ValidationContext base) {
      super(base);
    }
  }

  /** What every row of one matters commit run reads. */
  public record MattersCommitContext(UUID batchId, UUID actorMemberId) {}

  private final GenericTakeOnDatasetHandler generic;
  private final CustomerRepository customers;
  private final TakeOnRowRepository takeOnRows;
  private final CustomerLifecycleGuard lifecycleGuard;
  private final ProjectRepository projects;
  private final MemberRepository members;
  private final ProjectService projectService;
  private final ProjectMemberService projectMembers;

  public MattersDatasetHandler(
      GenericTakeOnDatasetHandler generic,
      CustomerRepository customers,
      TakeOnRowRepository takeOnRows,
      CustomerLifecycleGuard lifecycleGuard,
      ProjectRepository projects,
      MemberRepository members,
      ProjectService projectService,
      ProjectMemberService projectMembers) {
    this.generic = generic;
    this.customers = customers;
    this.takeOnRows = takeOnRows;
    this.lifecycleGuard = lifecycleGuard;
    this.projects = projects;
    this.members = members;
    this.projectService = projectService;
    this.projectMembers = projectMembers;
  }

  @Override
  public TakeOnDataset dataset() {
    return TakeOnDataset.MATTERS;
  }

  @Override
  public List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return generic.fields(dataset, section);
  }

  @Override
  public ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources) {
    var context = new MattersValidationContext(generic.loadContext(batch, sources));
    for (var c : customers.findTakeOnDedupCandidates()) {
      context.liveClients.add(c.getId());
      if (c.getEmail() != null) {
        context.clientByEmail.putIfAbsent(lower(c.getEmail()), c.getId());
      }
      if (c.getIdNumber() != null && !c.getIdNumber().isBlank()) {
        context
            .clientsByIdNumber
            .computeIfAbsent(lower(c.getIdNumber().strip()), k -> new ArrayList<>())
            .add(c.getId());
      }
    }
    for (var m : projects.findTakeOnDedupCandidates()) {
      if (m.getReferenceNumber() != null && !m.getReferenceNumber().isBlank()) {
        context.matterByRef.putIfAbsent(lower(m.getReferenceNumber().strip()), m.getId());
      }
      if (m.getCustomerId() != null && m.getName() != null) {
        context.matterByClientAndName.putIfAbsent(
            clientNameKey(m.getCustomerId(), m.getName()), m.getId());
      }
    }
    for (var member : members.findAll()) {
      if (member.getEmail() != null) {
        context.memberByEmail.putIfAbsent(lower(member.getEmail().strip()), member.getId());
      }
    }
    return context;
  }

  @Override
  public void mapAndValidate(TakeOnRow row, ValidationContext base) {
    var context = (MattersValidationContext) base;
    var check = generic.checkGeneric(row, context);
    var mapped = check.mapped();

    // 1. Work type, in its canonical form (the stored value; field groups match on it).
    if (mapped.get("workType") instanceof String workType) {
      try {
        String canonical = WorkTypes.normalizeForStorage(workType);
        if (canonical == null) {
          mapped.remove("workType");
        } else {
          mapped.put("workType", canonical);
        }
      } catch (InvalidStateException e) {
        mapped.remove("workType");
        check.addError(WORK_TYPE_INVALID, "workType", WORK_TYPE_INVALID_MESSAGE);
      }
    }

    // 2. The client: the first reference that resolves wins (D-157).
    UUID clientId = null;
    String resolvedBy = null;
    String firstField = null;
    String ambiguousIdNumber = null;
    for (String field : CLIENT_REFERENCE_FIELDS) {
      String value = text(mapped.get(field));
      if (value == null) {
        continue;
      }
      if (firstField == null) {
        firstField = field;
      }
      if (clientId != null) {
        continue;
      }
      var resolution = resolve(context, field, value);
      if (resolution.clientId() != null) {
        clientId = resolution.clientId();
        resolvedBy = field;
      } else if (resolution.ambiguous()) {
        ambiguousIdNumber = value;
      }
    }
    if (firstField == null) {
      check.addError(
          GenericTakeOnDatasetHandler.REQUIRED, "clientEmail", CLIENT_REFERENCE_REQUIRED_MESSAGE);
    } else if (clientId == null && ambiguousIdNumber != null) {
      check.addError(
          CLIENT_AMBIGUOUS, "clientIdNumber", ambiguousIdNumberMessage(ambiguousIdNumber));
    } else if (clientId == null) {
      check.addError(
          CLIENT_UNRESOLVED, firstField, orphanMessage(text(mapped.get(firstField)).strip()));
    } else {
      mapped.put(CLIENT_ID, clientId.toString());
      mapped.put(CLIENT_RESOLVED_BY, resolvedBy);
      // 3. The client may take a matter: the guard's own text, never re-typed (AC-4, C-9).
      var refusal = context.lifecycleRefusal.computeIfAbsent(clientId, this::lifecycleRefusal);
      if (refusal.isPresent()) {
        check.addError(CLIENT_LIFECYCLE, resolvedBy, refusal.get());
      }
    }

    // 4. The responsible attorney: an unknown email is a warning, the committer stays the lead.
    if (mapped.get("leadMemberEmail") instanceof String leadEmail) {
      UUID lead = context.memberByEmail.get(lower(leadEmail.strip()));
      if (lead != null) {
        mapped.put(LEAD_MEMBER_ID, lead.toString());
      } else {
        check.addWarning(LEAD_MEMBER_UNKNOWN, "leadMemberEmail", LEAD_MEMBER_UNKNOWN_MESSAGE);
      }
    }

    // 5. Dedup: the matter reference, else the name on the resolved client (C-6).
    String name = text(mapped.get("name"));
    String ref = text(mapped.get("referenceNumber"));
    DedupKey dedup = generic.dedupKey(TakeOnDataset.MATTERS, mapped);
    if (dedup == null && clientId != null && name != null) {
      dedup = new DedupKey("name", TakeOnDedupKeys.matterName(clientId, name));
    }
    check.dedupKey(dedup);

    // 6. An existing matter with the same key (D-170: the match follows the dedup key).
    if (check.errors().isEmpty()) {
      UUID existing =
          ref != null
              ? context.matterByRef.get(lower(ref.strip()))
              : (clientId != null && name != null
                  ? context.matterByClientAndName.get(clientNameKey(clientId, name))
                  : null);
      if (existing != null) {
        check.duplicateOf(existing);
      }
    }

    GenericTakeOnDatasetHandler.finish(row, check, context);
  }

  private record Resolution(UUID clientId, boolean ambiguous) {}

  private Resolution resolve(MattersValidationContext context, String field, String value) {
    String key = lower(value.strip());
    return switch (field) {
      case "clientEmail" -> new Resolution(context.clientByEmail.get(key), false);
      case "clientRef" ->
          new Resolution(
              context
                  .bySourceRef
                  .computeIfAbsent(
                      key,
                      k ->
                          takeOnRows
                              .clientBySourceRef(value.strip())
                              .filter(context.liveClients::contains))
                  .orElse(null),
              false);
      case "clientIdNumber" -> {
        var ids = context.clientsByIdNumber.getOrDefault(key, List.of());
        yield ids.size() == 1
            ? new Resolution(ids.getFirst(), false)
            : new Resolution(null, ids.size() > 1);
      }
      default -> new Resolution(null, false);
    };
  }

  /** The guard's refusal for a new matter of {@code clientId}, if any (plain component: safe). */
  private Optional<String> lifecycleRefusal(UUID clientId) {
    var client = customers.findById(clientId).orElse(null);
    if (client == null) {
      return Optional.empty(); // a live candidate of this snapshot; never absent here
    }
    try {
      lifecycleGuard.requireActionPermitted(client, LifecycleAction.CREATE_PROJECT);
      return Optional.empty();
    } catch (InvalidStateException e) {
      return Optional.of(e.getBody().getDetail());
    }
  }

  @Override
  public DedupKey dedupKey(TakeOnDataset dataset, Map<String, Object> mapped) {
    return generic.dedupKey(dataset, mapped);
  }

  @Override
  public void validateBatchLevel(TakeOnBatch batch, ValidationContext context) {
    generic.validateBatchLevel(batch, context);
  }

  @Override
  public Map<String, Object> summary(ValidationContext context) {
    return generic.summary(context);
  }

  // --- Commit (§17.3.6; no sign-off: requiredSignOff() stays null) ---

  @Override
  public MattersCommitContext prepare(TakeOnBatch batch, TakeOnSignOff signOff) {
    return new MattersCommitContext(batch.getId(), RequestScopes.requireMemberId());
  }

  /**
   * One matter. Every refusal is checked first and recorded without throwing: an exception thrown
   * through a {@code @Transactional} proxy marks the row's transaction rollback-only, so it can
   * never be caught to keep the matter (C-4, D-164). A residual exception rolls the row back and
   * the engine marks it FAILED with {@link #failureMessage}.
   */
  @Override
  public void commitRow(TakeOnRow row, MattersCommitContext context) {
    Map<String, Object> v = row.getMappedValues();
    UUID clientId = uuid(v.get(CLIENT_ID));
    if (clientId == null) {
      // D-178: the row was redacted because its client was anonymised after the dry run — read
      // nothing else from its values; it fails with the client-gone text (D-171).
      row.markFailed(CLIENT_GONE_MESSAGE);
      return;
    }
    String name = text(v.get("name"));
    String ref = text(v.get("referenceNumber"));

    // 1. Duplicates: the row's key again, by the dry run's rule (D-170) — a matter may have been
    //    created in the UI since the dry run, or the validated match's reference or name changed.
    var again =
        ref != null
            ? projects.findIdByReferenceNumber(ref)
            : projects.findIdByCustomerIdAndName(clientId, name);
    if (again.isPresent()) {
      row.markSkippedDuplicate(RESULT_ENTITY_TYPE, again.get());
      return;
    }

    // 2. The client still exists and may still take a matter.
    var client =
        customers
            .findById(clientId)
            .filter(c -> c.getLifecycleStatus() != LifecycleStatus.ANONYMIZED)
            .orElse(null);
    if (client == null) {
      row.markFailed(CLIENT_GONE_MESSAGE);
      return;
    }
    try {
      lifecycleGuard.requireActionPermitted(client, LifecycleAction.CREATE_PROJECT);
    } catch (InvalidStateException e) {
      row.markFailed(e.getBody().getDetail());
      return;
    }

    // 3. The matter, through the ordinary path (lead = the committer, client link, portal
    //    upsert, project.created) — no template.
    Project project =
        projectService.createProject(
            name,
            text(v.get("description")),
            context.actorMemberId(),
            map(v.get(GenericTakeOnDatasetHandler.CUSTOM_FIELDS_KEY)),
            null,
            clientId,
            date(v.get("dueDate")),
            ref,
            priority(v.get("priority")),
            text(v.get("workType")));
    mergeParked(project, map(v.get(GenericTakeOnDatasetHandler.PARKED_KEY)));

    // 4. The responsible attorney, pre-checked so nothing throws through the member service.
    UUID lead = uuid(v.get(LEAD_MEMBER_ID));
    if (lead != null && !lead.equals(context.actorMemberId())) {
      if (members.existsById(lead)) {
        projectMembers.addMemberAsSystem(project.getId(), lead, context.actorMemberId());
        projectMembers.transferLead(
            project.getId(),
            new ActorContext(context.actorMemberId(), RequestScopes.getOrgRole()),
            lead);
      } else {
        row.addCommitWarning(LEAD_NOT_APPLIED, "leadMemberEmail", LEAD_NOT_APPLIED_MESSAGE);
      }
    }

    row.markCreated(RESULT_ENTITY_TYPE, project.getId(), null);
  }

  @Override
  public String failureMessage(RuntimeException failure) {
    return GenericTakeOnDatasetHandler.fixedFailureMessage(failure);
  }

  // --- helpers ---

  /**
   * D-163: the parked columns are kept as {@code customFields.takeon_parked} on the matter. The
   * field validator in {@code createProject} keeps only defined slugs, so they are merged onto the
   * managed matter afterwards, in the same transaction.
   */
  private static void mergeParked(Project project, Map<String, Object> parked) {
    if (parked == null || parked.isEmpty()) {
      return;
    }
    var merged =
        new LinkedHashMap<String, Object>(
            project.getCustomFields() == null ? Map.of() : project.getCustomFields());
    merged.put(GenericTakeOnDatasetHandler.PARKED_KEY, parked);
    project.setCustomFields(merged);
  }

  private static String clientNameKey(UUID clientId, String name) {
    return clientId + "|" + lower(name.strip());
  }

  private static String lower(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  private static String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  private static LocalDate date(Object value) {
    String s = text(value);
    return s == null ? null : LocalDate.parse(s);
  }

  private static UUID uuid(Object value) {
    String s = text(value);
    return s == null ? null : UUID.fromString(s);
  }

  private static ProjectPriority priority(Object value) {
    String s = text(value);
    return s == null ? null : ProjectPriority.valueOf(s);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
  }
}
