package io.b2mash.b2b.b2bstrawman.portal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GAP-L-72 (E5.1, slice 23) — sibling listener to {@code
 * NotificationEventHandler#onDocumentGenerated} that fires per-event portal-contact emails when a
 * portal-visible matter-closure or Statement-of-Account artefact is generated. Without this handler
 * the only notification path is the weekly digest (Mondays 08:00), which can leave a client waiting
 * up to seven days to learn that their matter has been closed or their statement is ready.
 *
 * <h2>Filter rules</h2>
 *
 * <ol>
 *   <li>Project-scoped (the event's {@code primaryEntityType} is PROJECT and {@code projectId} is
 *       non-null).
 *   <li>Portal-visible (event {@code details.visibility} is in {SHARED, PORTAL}, OR — when details
 *       are silent — the linked {@link Document}'s persisted visibility is in {SHARED, PORTAL}).
 *       The fallback path matters because canonical {@code GeneratedDocumentService} emits events
 *       without scope/visibility in details, but {@code
 *       MatterClosureService.generateClosureLetterSafely} flips the linked Document to PORTAL after
 *       generation (slice 22 / GAP-L-74-followup).
 *   <li>Template name is in the per-tenant {@code OrgSettings.portalNotificationDocTypes} allowlist
 *       (default {@code ["matter-closure-letter", "statement-of-account"]}).
 *   <li>5-minute Caffeine dedup keyed on {@code tenant + ":" + customer + ":" + project} — closure
 *       packs may fan out into multiple documents within seconds; only the first send wins.
 * </ol>
 *
 * <p>Runs {@code AFTER_COMMIT} on a fresh ScopedValue binding (mirrors {@code
 * NotificationEventHandler.handleInTenantScope}). Fire-and-forget: any failure is logged and
 * swallowed so a malformed event never poisons the original commit.
 *
 * <p>Coexists with {@code NotificationEventHandler.onDocumentGenerated} (which targets firm-side
 * member notifications and the activity feed). The two handlers are deliberately parallel — slice
 * 23 must not touch the canonical handler.
 */
@Component
public class PortalDocumentNotificationHandler {

  private static final Logger log =
      LoggerFactory.getLogger(PortalDocumentNotificationHandler.class);

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);
  private static final long DEDUP_MAX_SIZE = 10_000L;

  private final OrgSettingsRepository orgSettingsRepository;
  private final ProjectRepository projectRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final DocumentRepository documentRepository;
  private final PortalContactRepository portalContactRepository;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final TransactionTemplate transactionTemplate;
  private final String portalBaseUrl;
  private final Cache<String, Boolean> dedupCache;

  public PortalDocumentNotificationHandler(
      OrgSettingsRepository orgSettingsRepository,
      ProjectRepository projectRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      DocumentRepository documentRepository,
      PortalContactRepository portalContactRepository,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      TransactionTemplate transactionTemplate,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.projectRepository = projectRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.documentRepository = documentRepository;
    this.portalContactRepository = portalContactRepository;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.transactionTemplate = transactionTemplate;
    this.portalBaseUrl = portalBaseUrl;
    this.dedupCache =
        Caffeine.newBuilder().expireAfterWrite(DEDUP_WINDOW).maximumSize(DEDUP_MAX_SIZE).build();
  }

  /**
   * Test-only hook to clear the per-(tenant, customer, project) dedup cache. The 5-minute Caffeine
   * window outlives most test classes' JVM run, so without an explicit reset successive test cases
   * dedup against each other and the assertions get tangled. Package-private to keep production
   * call-sites honest.
   */
  void clearDedupCacheForTesting() {
    dedupCache.invalidateAll();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentGenerated(DocumentGeneratedEvent event) {
    if (event == null || event.tenantId() == null) {
      return;
    }
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            process(event);
          } catch (Exception e) {
            log.warn(
                "PortalDocumentNotificationHandler: unexpected error processing event={}",
                event.entityId(),
                e);
          }
        });
  }

  /** Filter + dedup + send. Runs inside the tenant ScopedValue scope. */
  private void process(DocumentGeneratedEvent event) {
    UUID projectId = event.projectId();
    if (projectId == null) {
      return; // Not project-scoped — nothing for the portal to see.
    }

    String templateName = event.templateName();
    if (templateName == null || templateName.isBlank()) {
      return;
    }

    // 1. Per-tenant allowlist gate.
    Optional<OrgSettings> settingsOpt =
        transactionTemplate.execute(tx -> orgSettingsRepository.findForCurrentTenant());
    if (settingsOpt == null || settingsOpt.isEmpty()) {
      log.debug("Skipping portal-document-ready: no OrgSettings for tenant={}", event.tenantId());
      return;
    }
    List<String> allowlist = settingsOpt.get().getPortalNotificationDocTypes();
    if (allowlist == null || allowlist.isEmpty()) {
      log.debug(
          "Skipping portal-document-ready: per-tenant allowlist empty (tenant={})",
          event.tenantId());
      return;
    }
    if (!allowlist.contains(templateName)) {
      log.debug(
          "Skipping portal-document-ready: template={} not in allowlist (tenant={})",
          templateName,
          event.tenantId());
      return;
    }

    // 2. Visibility gate. Prefer event.details, fall back to the persisted Document row so the
    //    closure-letter path (canonical GeneratedDocumentService emission, no details.visibility)
    //    still works once the visibility flip has been applied.
    if (!isPortalVisible(event)) {
      log.debug(
          "Skipping portal-document-ready: not portal-visible (template={}, doc={})",
          templateName,
          event.generatedDocumentId());
      return;
    }

    // 3. Resolve project + customer + portal contact.
    String orgId = event.orgId();
    if (orgId == null) {
      log.debug("Skipping portal-document-ready: missing orgId on event {}", event.entityId());
      return;
    }
    var ctx =
        transactionTemplate.execute(
            tx -> {
              Optional<Project> projectOpt = projectRepository.findById(projectId);
              if (projectOpt.isEmpty()) {
                return null;
              }
              Project project = projectOpt.get();
              UUID customerId = project.getCustomerId();
              if (customerId == null) {
                return null;
              }
              Optional<PortalContact> contactOpt =
                  portalContactRepository.findPreferredByCustomerIdAndOrgId(customerId, orgId);
              if (contactOpt.isEmpty()) {
                return null;
              }
              return new ResolvedContext(project, customerId, contactOpt.get());
            });
    if (ctx == null) {
      log.debug(
          "Skipping portal-document-ready: no portal contact resolved (project={}, template={})",
          projectId,
          templateName);
      return;
    }

    // 4. Dedup on (tenant, customer, project) — coalesces multi-document closure batches into a
    //    single email. The first event in the 5-minute window wins.
    String dedupKey = event.tenantId() + ":" + ctx.customerId() + ":" + projectId;
    if (dedupCache.getIfPresent(dedupKey) != null) {
      log.debug(
          "Skipping portal-document-ready: dedup hit (key={}, template={})",
          dedupKey,
          templateName);
      return;
    }
    dedupCache.put(dedupKey, Boolean.TRUE);

    // 5. Build template context and dispatch.
    Map<String, Object> context = buildContext(event, ctx);
    portalEmailService.sendDocumentReadyEmail(ctx.contact(), context);
  }

  /**
   * Returns true if the event represents a portal-visible artefact. Prefers the event's {@code
   * details.visibility} (slice 22 SoA path), falls back to the persisted Document row's visibility
   * (canonical GeneratedDocumentService path, where the closure-letter visibility flip happens
   * post-publish but the AFTER_COMMIT listener observes the committed state).
   */
  private boolean isPortalVisible(DocumentGeneratedEvent event) {
    Map<String, Object> details = event.details();
    if (details != null) {
      Object scope = details.get("scope");
      Object visibility = details.get("visibility");
      if (visibility instanceof String visStr && Document.Visibility.isPortalVisible(visStr)) {
        // scope, when present, must be PROJECT — otherwise fall through to the DB fallback.
        if (scope == null || "PROJECT".equals(scope)) {
          return true;
        }
      }
    }
    // Fallback: read the persisted Document row.
    UUID generatedDocId = event.generatedDocumentId();
    if (generatedDocId == null) {
      return false;
    }
    return Boolean.TRUE.equals(
        transactionTemplate.execute(
            tx ->
                generatedDocumentRepository
                    .findById(generatedDocId)
                    .map(
                        gd -> {
                          UUID linkedDocId = gd.getDocumentId();
                          if (linkedDocId == null) {
                            return false;
                          }
                          return documentRepository
                              .findById(linkedDocId)
                              .filter(d -> Document.Scope.PROJECT.equals(d.getScope()))
                              .map(d -> Document.Visibility.isPortalVisible(d.getVisibility()))
                              .orElse(false);
                        })
                    .orElse(false)));
  }

  private Map<String, Object> buildContext(DocumentGeneratedEvent event, ResolvedContext ctx) {
    Map<String, Object> context =
        transactionTemplate.execute(
            tx -> emailContextBuilder.buildBaseContext(ctx.contact().getDisplayName(), null));
    if (context == null) {
      context = new HashMap<>();
    }
    context.put("contactName", ctx.contact().getDisplayName());
    context.put("documentTitle", resolveDocumentTitle(event));
    context.put("matterName", ctx.project() != null ? ctx.project().getName() : null);
    context.put("portalBaseUrl", portalBaseUrl);
    context.put("documentUrl", portalBaseUrl + "/projects/" + ctx.project().getId());

    String orgName = String.valueOf(context.getOrDefault("orgName", "Kazi"));
    String headline;
    String bodyMessage;
    if ("matter-closure-letter".equals(event.templateName())) {
      headline = "Your matter has been closed";
      bodyMessage = "We have closed the matter and prepared a closure letter for your records.";
    } else if ("statement-of-account".equals(event.templateName())) {
      headline = "Your statement of account is ready";
      bodyMessage = "A new statement of account has been prepared and is available on the portal.";
    } else {
      headline = "A new document is ready for you";
      bodyMessage = "A new document has been shared with you on the portal.";
    }
    context.put("headline", headline);
    context.put("bodyMessage", bodyMessage);
    context.put("subject", "Document ready: " + resolveDocumentTitle(event) + " from " + orgName);
    return context;
  }

  private String resolveDocumentTitle(DocumentGeneratedEvent event) {
    if (event.fileName() != null && !event.fileName().isBlank()) {
      return event.fileName();
    }
    return event.templateName();
  }

  /**
   * Mirrors {@code NotificationEventHandler.handleInTenantScope}: re-binds tenant + org
   * ScopedValues so the AFTER_COMMIT listener runs against the correct schema.
   */
  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      action.run();
      return;
    }
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }

  private record ResolvedContext(Project project, UUID customerId, PortalContact contact) {}
}
