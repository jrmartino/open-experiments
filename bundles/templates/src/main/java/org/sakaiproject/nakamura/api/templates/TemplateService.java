package org.sakaiproject.nakamura.api.templates;

import java.util.Collection;
import java.util.Map;

/**
 * Service to provide templating functionality for replacing variable markers in Strings.
 */
public interface TemplateService {

  String evaluateTemplate(Map<String, ? extends Object> parameters, String template);

  Collection<String> missingTerms(Map<String, ? extends Object> parameters,
      String template);
}
