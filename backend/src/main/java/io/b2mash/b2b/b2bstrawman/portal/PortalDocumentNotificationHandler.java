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
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentSharedEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GAP-L-72 (E5.1, slice 23) — sibling listener to {@code
 * NotificationEventHandler#onDocumentGenerated} that fires per-event portal-contact "Document
 * ready" emails. Without it the only notification path is the weekly digest (Mondays 08:00).
 *
 * <p>Phase 88 U13 / ADR-350: the client is emailed only on <b>explicit intent</b> — the per-tenant
 * template allowlist ({@code OrgSettings.portalNotificationDocTypes}) is gone. Two triggers feed
 * the same pipeline:
 *
 * <ul>
 *   <li>{@link DocumentGeneratedEvent} whose {@code details.notify_client} is {@code true} (SoA
 *       "Email the client", closure {@code notifyClient}). Absent or {@code false} → skip
 *       (fail-closed).
 *   <li>{@link GeneratedDocumentSharedEvent} with {@code notifyClient=true} (the "Share with
 *       client" action on a generated document).
 * </ul>
 *
 * <h2>Filter rules (after the intent gate)</h2>
 *
 * <ol>
 *   <li>Project-scoped ({@code projectId} non-null).
 *   <li>Portal-visible (details {@code visibility} is in {SHARED, PORTAL}, OR — when details are
 *       silent — the linked {@link Document}'s persisted visibility is in {SHARED, PORTAL}). Intent
 *       to email a document the client cannot open is skipped.
 *   <li>5-minute Caffeine dedup keyed on {@code tenant + ":" + customer + ":" + project + ":" +
 *       template} — closure packs fan out into multiple documents within seconds; each distinct
 *       document type notifies once, and re-emissions of the same document type within the window
 *       are deduped (LZKC-015). Known consequence (ADR-350, kept deliberately): two statements of
 *       account on the same matter, each with "Email the client" ticked within 5 minutes, produce
 *       one email — as does "Share with client" + notify right after notify-at-generation.
 * </ol>
 *
 * <p>Runs {@code AFTER_COMMIT} on a fresh ScopedValue binding via {@link
 * io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes#runForTenantOnShard}. Fire-and-forget: any
 * failure is logged and swallowed so a malformed event never poisons the original commit.
 *
 * <p>Coexists with {@code NotificationEventHandler.onDocumentGenerated} (which targets firm-side
 * member notifications and the activity feed). The two handlers are deliberately parallel.
 */
@Component
public class PortalDocumentNotificationHandler {

  private static final Logger log =
      LoggerFactory.getLogger(PortalDocumentNotificationHandler.class);

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);
  private static final long DEDUP_MAX_SIZE = 10_000L;

  private final ProjectRepository projectRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final DocumentRepository documentRepository;
  private final PortalContactRepository portalContactRepository;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final TransactionTemplate transactionTemplate;
  private final PortalLinkBuilder portalLinkBuilder;
  private final Cache<String, Boolean> dedupCache;

  public PortalDocumentNotificationHandler(
      ProjectRepository projectRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      DocumentRepository documentRepository,
      PortalContactRepository portalContactRepository,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      TransactionTemplate transactionTemplate,
      PortalLinkBuilder portalLinkBuilder) {
    this.projectRepository = projectRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.documentRepository = documentRepository;
    this.portalContactRepository = portalContactRepository;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.transactionTemplate = transactionTemplate;
    this.portalLinkBuilder = portalLinkBuilder;
    this.dedupCache =
        Caffeine.newBuilder().expireAfterWrite(DEDUP_WINDOW).maximumSize(DEDUP_MAX_SIZE).build();
  }

  /**
   * Test-only hook to clear the per-(tenant, customer, project, template) dedup cache. The 5-minute
   * Caffeine window outlives most test classes' JVM run, so without an explicit reset successive
   * test cases dedup against each other and the assertions get tangled. Package-private to keep
   * production call-sites honest.
   */
  void clearDedupCacheForTesting() {
    dedupCache.invalidateAll();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentGenerated(DocumentGeneratedEvent event) {
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            // ADR-350 intent gate FIRST (before any DB read): fail-closed on absent/false.
            Map<String, Object> details = event.details();
            if (details == null || !Boolean.TRUE.equals(details.get("notify_client"))) {
              log.info(
                  "Skipping portal-document-ready: notify_client not requested (template={},"
                      + " doc={})",
                  event.templateName(),
                  event.generatedDocumentId());
              return;
            }
            process(
                new Notice(
                    event.tenantId(),
                    event.orgId(),
                    event.projectId(),
                    event.generatedDocumentId(),
                    event.templateName(),
                    event.fileName(),
                    details));
          } catch (Exception e) {
            log.warn(
                "PortalDocumentNotificationHandler: unexpected error processing event={}",
                event.entityId(),
                e);
          }
        });
  }

  /** ADR-350 "Share with client": emails only when the firm ticked "Email the client". */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGeneratedDocumentShared(GeneratedDocumentSharedEvent event) {
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            if (!event.notifyClient()) {
              log.info(
                  "Skipping portal-document-ready: shared without notifyClient (doc={})",
                  event.generatedDocumentId());
              return;
            }
            Map<String, Object> details = new HashMap<>();
            details.put("scope", Document.Scope.PROJECT);
            if (event.visibility() != null) {
              details.put("visibility", event.visibility());
            }
            process(
                new Notice(
                    event.tenantId(),
                    event.orgId(),
                    event.projectId(),
                    event.generatedDocumentId(),
                    event.templateSlug(),
                    event.fileName(),
                    details));
          } catch (Exception e) {
            log.warn(
                "PortalDocumentNotificationHandler: unexpected error processing share of doc={}",
                event.generatedDocumentId(),
                e);
          }
        });
  }

  /** Filter + dedup + send. Runs inside the tenant ScopedValue scope, after the intent gate. */
  private void process(Notice notice) {
    // OBS-2106 Part 1 (diagnostic uplift): every entry into process(...) is logged at INFO so that
    // closure-pack regressions are visible at standard log level. Pairs with the skip-path log
    // lines below (also INFO) so on-call can see *which* gate rejected an event.
    log.info(
        "PortalDocumentNotificationHandler.process entered: tenant={}, template={}, project={},"
            + " generatedDoc={}",
        notice.tenantId(),
        notice.templateName(),
        notice.projectId(),
        notice.generatedDocumentId());

    UUID projectId = notice.projectId();
    if (projectId == null) {
      return; // Not project-scoped — nothing for the portal to see.
    }
    String templateName = notice.templateName();

    // 1. Visibility gate. Prefer the notice details, fall back to the persisted Document row.
    if (!isPortalVisible(notice)) {
      log.info(
          "Skipping portal-document-ready: not portal-visible (template={}, doc={})",
          templateName,
          notice.generatedDocumentId());
      return;
    }

    // 2. Resolve project + customer + portal contact.
    String orgId = notice.orgId();
    if (orgId == null) {
      log.info(
          "Skipping portal-document-ready: missing orgId (doc={})", notice.generatedDocumentId());
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
      log.info(
          "Skipping portal-document-ready: no portal contact resolved (project={}, template={})",
          projectId,
          templateName);
      return;
    }

    // 3. Dedup on (tenant, customer, project, template) — LZKC-015: the key must include the
    //    document type, otherwise the first document of a closure batch (the closure letter)
    //    claims the key and the sibling Statement of Account's "Document ready" email is silently
    //    swallowed. Distinct doc types each notify; re-emissions of the SAME doc type within the
    //    5-minute window stay deduped. A missing slug (template since deleted) keys as "document".
    String dedupKey =
        notice.tenantId()
            + ":"
            + ctx.customerId()
            + ":"
            + projectId
            + ":"
            + (templateName != null ? templateName : "document");
    if (dedupCache.getIfPresent(dedupKey) != null) {
      log.info(
          "Skipping portal-document-ready: dedup hit (key={}, template={})",
          dedupKey,
          templateName);
      return;
    }
    dedupCache.put(dedupKey, Boolean.TRUE);

    // 4. Build template context and dispatch.
    Map<String, Object> context = buildContext(notice, ctx);
    portalEmailService.sendDocumentReadyEmail(ctx.contact(), context);
  }

  /**
   * Returns true if the notice represents a portal-visible artefact. Prefers {@code
   * details.visibility} (set by every intent-carrying publisher), falls back to the persisted
   * Document row's visibility (the AFTER_COMMIT listener observes the committed state).
   */
  private boolean isPortalVisible(Notice notice) {
    Map<String, Object> details = notice.details();
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
    UUID generatedDocId = notice.generatedDocumentId();
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

  private Map<String, Object> buildContext(Notice notice, ResolvedContext ctx) {
    Map<String, Object> context =
        transactionTemplate.execute(
            tx -> emailContextBuilder.buildBaseContext(ctx.contact().getDisplayName(), null));
    if (context == null) {
      context = new HashMap<>();
    }
    context.put("contactName", ctx.contact().getDisplayName());
    context.put("documentTitle", resolveDocumentTitle(notice));
    context.put("matterName", ctx.project() != null ? ctx.project().getName() : null);
    // ADR-350/351: the portal's matter page (there is no /projects route), carrying orgId.
    context.put(
        "documentUrl",
        portalLinkBuilder.authenticated(notice.orgId(), "/matters/" + ctx.project().getId()));

    String orgName = String.valueOf(context.getOrDefault("orgName", "Kazi"));
    String headline;
    String bodyMessage;
    if ("matter-closure-letter".equals(notice.templateName())) {
      headline = "Your matter has been closed";
      bodyMessage = "We have closed the matter and prepared a closure letter for your records.";
    } else if ("statement-of-account".equals(notice.templateName())) {
      headline = "Your statement of account is ready";
      bodyMessage = "A new statement of account has been prepared and is available on the portal.";
    } else {
      headline = "A new document is ready for you";
      bodyMessage = "A new document has been shared with you on the portal.";
    }
    context.put("headline", headline);
    context.put("bodyMessage", bodyMessage);
    context.put("subject", "Document ready: " + resolveDocumentTitle(notice) + " from " + orgName);
    return context;
  }

  private String resolveDocumentTitle(Notice notice) {
    if (notice.fileName() != null && !notice.fileName().isBlank()) {
      return notice.fileName();
    }
    return notice.templateName() != null ? notice.templateName() : "document";
  }

  /** Common payload of both triggers (generation with intent, explicit share). */
  private record Notice(
      String tenantId,
      String orgId,
      UUID projectId,
      UUID generatedDocumentId,
      String templateName,
      String fileName,
      Map<String, Object> details) {}

  private record ResolvedContext(Project project, UUID customerId, PortalContact contact) {}
}
