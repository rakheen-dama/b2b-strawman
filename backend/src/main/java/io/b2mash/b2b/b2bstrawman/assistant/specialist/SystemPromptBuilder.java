package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads specialist system-prompt markdown files from {@code classpath:assistant/specialists/*.md},
 * parses YAML front-matter, caches by specialist id, and assembles the final prompt at request time
 * (Phase 52 behavioural prefix + tenant context block + specialist body).
 *
 * <p>Exposes {@link #reload()} for the dev-only reload endpoint.
 */
@Service
public class SystemPromptBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPromptBuilder.class);
  private static final String CLASSPATH_PATTERN = "classpath:assistant/specialists/*.md";
  private static final String BEHAVIOURAL_PREFIX =
      "You are a specialist AI assistant for Kazi (b2mash). Always use tools to look up data rather"
          + " than guessing. For write actions, clearly describe what will be created or changed"
          + " before invoking the tool. Never claim to have performed an action that requires"
          + " confirmation unless the user confirmed it.";

  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();
  private final OrganizationRepository organizationRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final Map<String, ParsedPrompt> cache = new ConcurrentHashMap<>();

  public SystemPromptBuilder(
      OrganizationRepository organizationRepository, OrgSettingsRepository orgSettingsRepository) {
    this.organizationRepository = organizationRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    loadAll();
  }

  /** Re-scans the classpath and refreshes the cache. Dev-only callers. */
  public synchronized void reload() {
    cache.clear();
    loadAll();
  }

  /** Returns the assembled system prompt for the given specialist + (optional) context ref. */
  public String buildFor(Specialist specialist, ContextRef ref) {
    var parsed = cache.get(specialist.id());
    if (parsed == null) {
      throw new IllegalStateException(
          "No system prompt loaded for specialist id: " + specialist.id());
    }
    var sb = new StringBuilder();
    sb.append(BEHAVIOURAL_PREFIX).append("\n\n");
    sb.append("## Current Context\n");
    sb.append("- Organization: ").append(getOrgName()).append('\n');
    var orgRole = RequestScopes.getOrgRole();
    if (orgRole != null) {
      sb.append("- Your role: ").append(orgRole).append('\n');
    }
    var terminology = getTerminologyNamespace();
    if (terminology != null && !terminology.isBlank()) {
      sb.append("- Terminology: ").append(terminology).append('\n');
    }
    if (ref != null) {
      if (ref.currentPage() != null) {
        sb.append("- Current page: ").append(ref.currentPage()).append('\n');
      }
      if (ref.entityType() != null) {
        sb.append("- Context entity: ").append(ref.entityType());
        if (ref.entityId() != null) {
          sb.append(" (").append(ref.entityId()).append(')');
        }
        sb.append('\n');
      }
    }
    sb.append("\n## Specialist Instructions\n");
    sb.append(parsed.body());
    return sb.toString();
  }

  /** Returns the prompt-version metadata (from front-matter) for the given specialist. */
  public String promptVersion(String specialistId) {
    var parsed = cache.get(specialistId);
    if (parsed == null) return null;
    var v = parsed.frontMatter().get("version");
    return v == null ? null : String.valueOf(v);
  }

  /** Returns a stable hash of the assembled prompt body — used by SessionHandle. */
  public String bodyHash(String specialistId) {
    var parsed = cache.get(specialistId);
    if (parsed == null) return null;
    return sha256(parsed.body());
  }

  /** Test/internal hook: returns whether the given specialist id has a parsed prompt loaded. */
  public boolean isLoaded(String specialistId) {
    return cache.containsKey(specialistId);
  }

  private String getOrgName() {
    try {
      return organizationRepository
          .findByClerkOrgId(RequestScopes.requireOrgId())
          .map(o -> o.getName())
          .orElse("Unknown");
    } catch (RuntimeException e) {
      // RequestScopes unbound (e.g. during static prompt validation) — return placeholder.
      return "Unknown";
    }
  }

  private String getTerminologyNamespace() {
    try {
      return orgSettingsRepository
          .findForCurrentTenant()
          .map(s -> s.getTerminologyNamespace())
          .orElse(null);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private void loadAll() {
    try {
      var resources = resolver.getResources(CLASSPATH_PATTERN);
      var fresh = new HashMap<String, ParsedPrompt>();
      for (Resource resource : resources) {
        var filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".md")) continue;
        var id = filename.substring(0, filename.length() - 3);
        try (InputStream in = resource.getInputStream()) {
          var raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          fresh.put(id, parse(raw));
        }
      }
      cache.putAll(fresh);
      LOG.info("Loaded {} specialist prompt resources", fresh.size());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan specialist prompt resources", e);
    }
  }

  static ParsedPrompt parse(String raw) {
    if (!raw.startsWith("---\n") && !raw.startsWith("---\r\n")) {
      throw new IllegalStateException("Specialist prompt missing YAML front-matter delimiter");
    }
    int startOffset = raw.startsWith("---\r\n") ? 5 : 4;
    int end = raw.indexOf("\n---", startOffset);
    if (end < 0) {
      throw new IllegalStateException("Specialist prompt missing closing front-matter delimiter");
    }
    String yaml = raw.substring(startOffset, end);
    int bodyStart = end + 4; // skip "\n---"
    // skip optional trailing newline after closing ---
    while (bodyStart < raw.length()
        && (raw.charAt(bodyStart) == '\n' || raw.charAt(bodyStart) == '\r')) {
      bodyStart++;
    }
    String body = raw.substring(bodyStart);
    Map<String, Object> fm = new Yaml().load(yaml);
    if (fm == null) fm = Map.of();
    return new ParsedPrompt(fm, body);
  }

  private static String sha256(String input) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Visible for tests. */
  record ParsedPrompt(Map<String, Object> frontMatter, String body) {}
}
