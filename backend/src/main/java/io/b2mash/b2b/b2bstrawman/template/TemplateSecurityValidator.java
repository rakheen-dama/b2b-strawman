package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates Thymeleaf template content for dangerous patterns that could lead to Server-Side
 * Template Injection (SSTI). Rejects templates containing SpringEL type expressions, object
 * instantiation, preprocessing expressions, and dangerous utility object access.
 */
public final class TemplateSecurityValidator {

  private TemplateSecurityValidator() {}

  private static final List<DangerousPattern> DANGEROUS_PATTERNS =
      List.of(
          new DangerousPattern(
              Pattern.compile("T\\s*\\(", Pattern.CASE_INSENSITIVE),
              "Type expressions (T()) are not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("\\bnew\\s+[A-Z]"),
              "Object instantiation (new) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("__\\$\\{"),
              "Preprocessing expressions (__${) are not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("\\$\\{\\s*#"),
              "Utility object access (${#) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("#ctx\\b"),
              "Context object access (#ctx) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("#root\\b"),
              "Root object access (#root) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("#execInfo\\b"),
              "ExecInfo access (#execInfo) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("#vars\\b"), "Vars access (#vars) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("getClass\\s*\\("),
              "Reflective access (getClass()) is not allowed in templates"),
          new DangerousPattern(
              Pattern.compile("Runtime|ProcessBuilder|ClassLoader", Pattern.CASE_INSENSITIVE),
              "Access to dangerous Java classes is not allowed in templates"));

  /**
   * Validates that the given template content does not contain dangerous patterns.
   *
   * @param content the Thymeleaf template content to validate
   * @throws TemplateSecurityException if a dangerous pattern is detected
   */
  public static void validate(String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    for (var pattern : DANGEROUS_PATTERNS) {
      if (pattern.pattern().matcher(content).find()) {
        throw new TemplateSecurityException(pattern.message());
      }
    }
  }

  private record DangerousPattern(Pattern pattern, String message) {}
}
