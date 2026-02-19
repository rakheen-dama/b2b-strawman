package io.b2mash.b2b.b2bstrawman.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.spring6.expression.SPELVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

/**
 * Wraps the standard Spring EL evaluator with lenient error handling. When an expression cannot be
 * resolved (e.g. a custom field that doesn't exist on the entity), returns "________" instead of
 * throwing. This prevents 500 errors from document templates that reference optional/custom fields.
 */
public class LenientSpELEvaluator implements IStandardVariableExpressionEvaluator {

  private static final Logger log = LoggerFactory.getLogger(LenientSpELEvaluator.class);
  private static final String PLACEHOLDER = "________";

  static final LenientSpELEvaluator INSTANCE = new LenientSpELEvaluator();

  @Override
  public Object evaluate(
      IExpressionContext context,
      IStandardVariableExpression expression,
      StandardExpressionExecutionContext expContext) {
    try {
      return SPELVariableExpressionEvaluator.INSTANCE.evaluate(context, expression, expContext);
    } catch (Exception e) {
      log.debug(
          "Template expression '{}' could not be resolved, using placeholder: {}",
          expression.getExpression(),
          e.getMessage());
      return PLACEHOLDER;
    }
  }
}
