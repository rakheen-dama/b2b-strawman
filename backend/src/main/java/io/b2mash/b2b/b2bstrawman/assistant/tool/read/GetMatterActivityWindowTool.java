package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.activity.ActivityItem;
import io.b2mash.b2b.b2bstrawman.activity.ActivityService;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentService;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectUpcomingDeadline;
import io.b2mash.b2b.b2bstrawman.project.ProjectUpcomingDeadlinesService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * One-shot fetch of a bounded activity bundle for a matter across six source types: comments,
 * domain events, information requests, information request responses, trust transactions (legal-za
 * only), and deadline-approaching flags.
 */
@Component
public class GetMatterActivityWindowTool implements AssistantTool {

  private static final Logger log = LoggerFactory.getLogger(GetMatterActivityWindowTool.class);
  private static final String LEGAL_ZA = "legal-za";

  private final CommentService commentService;
  private final ActivityService activityService;
  private final InformationRequestService informationRequestService;
  private final ProjectUpcomingDeadlinesService upcomingDeadlinesService;
  private final OrgSettingsService orgSettingsService;
  private final TrustTransactionRepository trustTransactionRepository;

  public GetMatterActivityWindowTool(
      CommentService commentService,
      ActivityService activityService,
      InformationRequestService informationRequestService,
      ProjectUpcomingDeadlinesService upcomingDeadlinesService,
      OrgSettingsService orgSettingsService,
      TrustTransactionRepository trustTransactionRepository) {
    this.commentService = commentService;
    this.activityService = activityService;
    this.informationRequestService = informationRequestService;
    this.upcomingDeadlinesService = upcomingDeadlinesService;
    this.orgSettingsService = orgSettingsService;
    this.trustTransactionRepository = trustTransactionRepository;
  }

  @Override
  public String name() {
    return "GetMatterActivityWindow";
  }

  @Override
  public String description() {
    return "Fetch a bounded activity bundle for a matter. Sources are vertical-conditional:"
        + " legal-za includes trust transactions, others omit.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "matterId",
            Map.of("type", "string", "format", "uuid"),
            "lookback",
            Map.of("type", "string", "description", "ISO-8601 duration, e.g. P7D")),
        "required",
        List.of("matterId", "lookback"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("AI_ASSISTANT_USE");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var matterIdStr = (String) input.get("matterId");
    UUID matterId;
    try {
      matterId = UUID.fromString(matterIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid matterId format: " + matterIdStr);
    }

    var lookbackStr = (String) input.get("lookback");
    Duration lookback;
    try {
      lookback = Duration.parse(lookbackStr);
    } catch (Exception e) {
      return Map.of("error", "Invalid lookback duration: " + lookbackStr);
    }

    Instant to = Instant.now();
    Instant from = to.minus(lookback);

    // Determine vertical profile for trust-transaction gate
    var settings = orgSettingsService.getOrCreateForCurrentTenant();
    String verticalProfile = settings.getVerticalProfile();
    boolean isLegalZa = LEGAL_ZA.equals(verticalProfile);

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var result = new LinkedHashMap<String, Object>();
    result.put("matterId", matterId.toString());
    result.put("from", from.toString());
    result.put("to", to.toString());

    var events = new ArrayList<Map<String, Object>>();

    // 1. Comments — fetched via CommentService (access-controlled, time-windowed)
    try {
      var pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
      var comments = commentService.listByProjectAndWindow(matterId, from, pageable, actor);
      for (Comment c : comments) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("source", "COMMENT");
        entry.put("entityType", "comment");
        entry.put("entityId", c.getId().toString());
        entry.put("body", truncate(c.getBody(), 500));
        entry.put("visibility", c.getVisibility());
        entry.put("occurredAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        events.add(entry);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch comments for matter {}: {}", matterId, e.getMessage());
    }

    // 2. Domain events / activity feed
    try {
      var pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurredAt"));
      var activityPage = activityService.getProjectActivity(matterId, null, from, pageable, actor);
      for (ActivityItem item : activityPage) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("source", "ACTIVITY");
        entry.put("entityType", item.entityType());
        entry.put("entityId", item.entityId() != null ? item.entityId().toString() : null);
        entry.put("message", item.message());
        entry.put("actorName", item.actorName());
        entry.put("occurredAt", item.occurredAt() != null ? item.occurredAt().toString() : null);
        events.add(entry);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch activity for matter {}: {}", matterId, e.getMessage());
    }

    // 3. Information requests + responses (filtered by lookback window)
    try {
      var requests = informationRequestService.listByProject(matterId);
      for (var req : requests) {
        // Filter by creation date to stay within the lookback window
        if (req.createdAt() != null && req.createdAt().isBefore(from)) continue;
        var entry = new LinkedHashMap<String, Object>();
        entry.put("source", "INFORMATION_REQUEST");
        entry.put("entityType", "information_request");
        entry.put("entityId", req.id().toString());
        entry.put("status", req.status());
        entry.put("requestNumber", req.requestNumber());
        entry.put("itemCount", req.items() != null ? req.items().size() : 0);
        events.add(entry);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch information requests for matter {}: {}", matterId, e.getMessage());
    }

    // 4. Deadline-approaching flags
    try {
      var deadlines = upcomingDeadlinesService.getUpcomingDeadlines(matterId, actor);
      for (ProjectUpcomingDeadline d : deadlines) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("source", "DEADLINE");
        entry.put("entityType", "deadline");
        entry.put("type", d.type());
        entry.put("date", d.date().toString());
        entry.put("description", d.description());
        entry.put("status", d.status());
        events.add(entry);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch deadlines for matter {}: {}", matterId, e.getMessage());
    }

    // 5. Trust transactions (legal-za only)
    if (isLegalZa) {
      try {
        var transactions =
            trustTransactionRepository.findByProjectIdOrderByTransactionDateAsc(matterId);
        for (var tx : transactions) {
          var entry = new LinkedHashMap<String, Object>();
          entry.put("source", "TRUST_TRANSACTION");
          entry.put("entityType", "trust_transaction");
          entry.put("entityId", tx.getId().toString());
          entry.put("type", tx.getTransactionType());
          entry.put("amount", tx.getAmount().toString());
          entry.put("status", tx.getStatus());
          entry.put(
              "transactionDate",
              tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null);
          events.add(entry);
        }
      } catch (Exception e) {
        log.warn("Failed to fetch trust transactions for matter {}: {}", matterId, e.getMessage());
      }
    }

    result.put("events", events);
    result.put("trustTransactionsIncluded", isLegalZa);

    return result;
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return null;
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
  }
}
