package io.b2mash.b2b.b2bstrawman.template;

import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;

/**
 * Extends the standard Thymeleaf dialect (OGNL-based) to use lenient expression evaluation.
 * Unresolvable expressions (e.g. missing custom fields) produce a placeholder instead of an error.
 *
 * <p>Uses OGNL instead of SpEL, which natively supports {@code map.key} syntax for Map property
 * access — resolving the root cause of PDF generation failures where SpEL's {@code
 * ReflectivePropertyAccessor} cannot resolve properties on {@code LinkedHashMap} context objects.
 */
public class LenientStandardDialect extends StandardDialect {

  @Override
  public IStandardVariableExpressionEvaluator getVariableExpressionEvaluator() {
    return LenientOGNLEvaluator.INSTANCE;
  }
}
