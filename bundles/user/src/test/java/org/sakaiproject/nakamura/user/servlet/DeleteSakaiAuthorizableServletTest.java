package org.sakaiproject.nakamura.user.servlet;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

public class DeleteSakaiAuthorizableServletTest {

  @Test
  public void testApplyToAllowsNullResources() {
    Session session = mock(Session.class);
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable)adaptable).getSession()).thenReturn(session);
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    ResourceResolver resolver = mock(ResourceResolver.class);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    // This test checks that processing of a list of ":applyTo" IDs will continue
    // without throwing an exception, even if the list is empty.
    when(request.getParameterValues(":applyTo")).thenReturn(new String[] {});
    when(request.getResource()).thenReturn(null);

    AuthorizablePostProcessService authorizablePostProcessService = mock(AuthorizablePostProcessService.class);
    List<Modification> changes = new ArrayList<Modification>();
    HtmlResponse response = new HtmlResponse();
    DeleteSakaiAuthorizableServlet dsas = new DeleteSakaiAuthorizableServlet();
    dsas.postProcessorService = authorizablePostProcessService;
    dsas.handleOperation(request, response, changes);

    // Nothing happened (since we didn't get an exception). Did an unwanted error get
    // reported?
    assertFalse(response.getStatusCode() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
