package de.insulink.api.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.annotation.Annotation;

@Component
public final class EndpointRepository {
  @Autowired
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  /**
   * Whether the request routes to a handler method carrying the annotation.
   * Asks the same handler mapping the dispatcher routes with, so an encoded,
   * doubled or parameterised path can never reach a guarded method while
   * looking unguarded here. A request the mapping cannot even resolve (a wrong
   * method on a known path) counts as annotated: the guard fails closed.
   */
  public boolean routesToAnnotated(
    HttpServletRequest request, Class<? extends Annotation> annotation
  ) {
    try {
      ServletRequestPathUtils.parseAndCache(request);
      var chain = requestMappingHandlerMapping.getHandler(request);
      return chain != null && chain.getHandler() instanceof HandlerMethod method
        && method.hasMethodAnnotation(annotation);
    } catch (Exception exception) {
      return true;
    }
  }
}
