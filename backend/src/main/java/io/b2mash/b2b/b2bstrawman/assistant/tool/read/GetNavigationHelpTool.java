package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class GetNavigationHelpTool implements AssistantTool {

  private final String systemGuide;

  public GetNavigationHelpTool(
      @Value("classpath:assistant/system-guide.md") Resource systemGuideResource)
      throws IOException {
    this.systemGuide = systemGuideResource.getContentAsString(StandardCharsets.UTF_8);
  }

  @Override
  public String name() {
    return "get_navigation_help";
  }

  @Override
  public String description() {
    return "Get navigation instructions for a feature or area of DocTeams.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "feature",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "Feature or area to get help for (e.g., invoices, time tracking, profitability)")),
        "required", List.of("feature"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var feature = (String) input.get("feature");
    if (feature == null || feature.isBlank()) {
      return Map.of("error", "feature is required");
    }

    var lowerFeature = feature.toLowerCase();
    var lines = systemGuide.split("\n");
    var guidance = new StringBuilder();

    // Find sections containing the feature keyword
    boolean inMatchingSection = false;
    int sectionLevel = 0;

    for (var line : lines) {
      if (line.startsWith("## ") || line.startsWith("### ")) {
        int level = line.startsWith("### ") ? 3 : 2;

        if (line.toLowerCase().contains(lowerFeature)) {
          inMatchingSection = true;
          sectionLevel = level;
          guidance.append(line).append("\n");
        } else if (inMatchingSection && level <= sectionLevel) {
          inMatchingSection = false;
        } else if (inMatchingSection) {
          guidance.append(line).append("\n");
        }
      } else if (inMatchingSection) {
        guidance.append(line).append("\n");
      } else if (line.toLowerCase().contains(lowerFeature)) {
        // Include individual matching lines even outside a matching section header
        guidance.append(line).append("\n");
      }
    }

    var guidanceText = guidance.toString().trim();

    // Fallback: return the Navigation section if no specific match found
    if (guidanceText.isEmpty()) {
      var navSection = extractSection("## Navigation");
      guidanceText = navSection.isEmpty() ? "No guidance found for: " + feature : navSection;
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("feature", feature);
    result.put("guidance", guidanceText);
    return result;
  }

  private String extractSection(String header) {
    var lines = systemGuide.split("\n");
    var section = new StringBuilder();
    boolean inSection = false;
    int headerLevel = header.startsWith("### ") ? 3 : 2;

    for (var line : lines) {
      if (line.equals(header)) {
        inSection = true;
        section.append(line).append("\n");
      } else if (inSection) {
        if (line.startsWith("## ") && headerLevel == 2) {
          break;
        }
        section.append(line).append("\n");
      }
    }

    return section.toString().trim();
  }
}
