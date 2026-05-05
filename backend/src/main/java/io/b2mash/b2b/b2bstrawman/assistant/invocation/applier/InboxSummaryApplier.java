package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload;
import io.b2mash.b2b.b2bstrawman.comment.CommentService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies an approved (or auto-applied) inbox summary by posting a comment on the matter. Uses
 * source {@code "AI_ASSISTANT"} so the frontend renders the "Posted by Inbox Assistant" sparkle
 * pill.
 */
@Component("inboxSummaryApplier")
public class InboxSummaryApplier implements OutputApplier<InboxSummaryPayload> {

  private static final Logger log = LoggerFactory.getLogger(InboxSummaryApplier.class);
  private static final String COMMENT_SOURCE = "AI_ASSISTANT";

  private final CommentService commentService;

  public InboxSummaryApplier(CommentService commentService) {
    this.commentService = commentService;
  }

  @Override
  public Class<InboxSummaryPayload> payloadType() {
    return InboxSummaryPayload.class;
  }

  @Override
  public void apply(InboxSummaryPayload payload, UUID actorId) {
    UUID matterId = payload.matterId();
    String body = payload.summaryMarkdown();

    log.info(
        "Applying inbox summary to matter {} (lookback {} to {})",
        matterId,
        payload.lookbackFrom(),
        payload.lookbackTo());

    // Post as a SHARED project-level comment with AI_ASSISTANT source.
    // PROJECT-level comments require SHARED visibility per CommentService validation.
    var actor = new ActorContext(actorId, "owner");
    commentService.createComment(
        matterId, "PROJECT", matterId, body, "SHARED", COMMENT_SOURCE, actor);
  }
}
